import { describe, expect, it } from 'vitest'
import type { ResumeListItem } from '@/types/resume'
import type { AsyncTaskVO } from '@/types/task'
import {
  canRetryResumePreparation,
  formatResumeDate,
  formatResumeFileSize,
  getResumeLibraryStatus,
  getResumeLibrarySummary,
} from '../resumeLibraryPresentation'

const resume = (overrides: Partial<ResumeListItem> = {}): ResumeListItem => ({
  id: 1,
  originalFilename: 'resume.pdf',
  fileType: 'PDF',
  fileSize: 1024,
  uploadStatus: 'SUCCESS',
  parseStatus: 'SUCCESS',
  qualityStatus: 'READY',
  canonicalReady: true,
  parseErrorMessage: null,
  createdAt: '2026-01-02T09:30:00Z',
  ...overrides,
})

const task = (overrides: Partial<AsyncTaskVO> = {}): AsyncTaskVO => ({
  taskId: 42,
  taskType: 'RESUME_PREPARATION',
  status: 'RUNNING',
  progress: 0,
  message: '正在整理工作经历',
  ...overrides,
})

describe('resumeLibraryPresentation', () => {
  it('presents READY as usable for job analysis', () => {
    expect(getResumeLibraryStatus(resume())).toMatchObject({
      kind: 'ready',
      label: '可用于岗位分析',
      description: '内容准备已完成。',
      primaryAction: null,
      canDelete: true,
    })
  })

  it('presents an active task with a real task message', () => {
    expect(getResumeLibraryStatus(resume(), task())).toMatchObject({
      kind: 'preparing',
      label: '正在准备',
      description: '正在整理工作经历',
      primaryAction: null,
      canDelete: false,
    })
  })

  it('uses a safe preparing fallback when a task has no message', () => {
    expect(getResumeLibraryStatus(resume(), task({ message: null })).description).toBe(
      '系统正在读取并整理内容。',
    )
  })

  it('presents a quality PENDING resume as preparing', () => {
    expect(getResumeLibraryStatus(resume({ qualityStatus: 'PENDING' })).kind).toBe('preparing')
  })

  it('keeps deletion disabled while quality is pending without a task', () => {
    expect(getResumeLibraryStatus(resume({ qualityStatus: 'PENDING' })).canDelete).toBe(false)
  })

  it('presents NEEDS_REVIEW with a confirmation action', () => {
    expect(getResumeLibraryStatus(resume({ qualityStatus: 'NEEDS_REVIEW' }))).toMatchObject({
      kind: 'needs-review',
      label: '需要确认',
      primaryAction: 'review',
    })
  })

  it('explains why confirmation is required', () => {
    expect(getResumeLibraryStatus(resume({ qualityStatus: 'NEEDS_REVIEW' })).description).toContain(
      '确认后才能用于岗位分析与导出',
    )
  })

  it('presents a stale canonical version with reprepare action', () => {
    expect(getResumeLibraryStatus(resume({ canonicalReady: false }))).toMatchObject({
      kind: 'reprepare',
      label: '需要重新准备',
      primaryAction: 'prepare',
    })
  })

  it('presents a quality failure with its safe backend message', () => {
    expect(getResumeLibraryStatus(resume({ qualityStatus: 'FAILED', parseErrorMessage: '内容结构不完整' }))).toMatchObject({
      kind: 'failed',
      label: '准备失败',
      description: '内容结构不完整',
      primaryAction: 'retry',
    })
  })

  it('presents a parse failure with retry action', () => {
    expect(getResumeLibraryStatus(resume({ parseStatus: 'FAILED' })).primaryAction).toBe('retry')
  })

  it('uses a fallback description for a parse failure without a message', () => {
    expect(getResumeLibraryStatus(resume({ parseStatus: 'FAILED' })).description).toBe(
      '未能读取这份简历，请重试。',
    )
  })

  it('presents a not-yet-started preparation with a start action', () => {
    expect(getResumeLibraryStatus(resume({ qualityStatus: 'PENDING' }))).toMatchObject({
      kind: 'preparing',
      primaryAction: null,
    })
  })

  it('presents an unknown non-success parse state as waiting', () => {
    expect(getResumeLibraryStatus(resume({ parseStatus: 'PROCESSING', qualityStatus: null }))).toMatchObject({
      kind: 'waiting',
      label: '等待准备',
      primaryAction: 'prepare',
    })
  })

  it('does not allow retry while a preparation task is active', () => {
    expect(canRetryResumePreparation(resume({ parseStatus: 'FAILED' }), task())).toBe(false)
  })

  it('allows retry for a failed resume without an active task', () => {
    expect(canRetryResumePreparation(resume({ parseStatus: 'FAILED' }))).toBe(true)
  })

  it('allows reprepare for a stale canonical version', () => {
    expect(canRetryResumePreparation(resume({ canonicalReady: false }))).toBe(true)
  })

  it('does not offer retry for a resume that only needs review', () => {
    expect(canRetryResumePreparation(resume({ qualityStatus: 'NEEDS_REVIEW' }))).toBe(false)
  })

  it('formats megabyte file sizes for upload and list display', () => {
    expect(formatResumeFileSize(2 * 1024 * 1024)).toBe('2.00 MB')
  })

  it('formats small file sizes with a readable minimum', () => {
    expect(formatResumeFileSize(1)).toBe('0.1 KB')
  })

  it('formats dates in an explicit user-facing form', () => {
    expect(formatResumeDate('2026-01-02T09:30:00Z')).toBe('2026年01月02日')
  })

  it('keeps malformed date text safe instead of inventing a date', () => {
    expect(formatResumeDate('unknown')).toBe('unknown')
  })

  it('counts total, usable and actionable resumes from real states', () => {
    const resumes = [
      resume({ id: 1 }),
      resume({ id: 2, qualityStatus: 'NEEDS_REVIEW' }),
      resume({ id: 3, parseStatus: 'FAILED' }),
      resume({ id: 4, qualityStatus: 'PENDING' }),
    ]
    expect(getResumeLibrarySummary(resumes, { 4: task() })).toEqual({
      total: 4,
      usable: 1,
      needsAction: 2,
    })
  })

  it('does not count active preparation as an actionable interruption', () => {
    expect(getResumeLibrarySummary([resume()], { 1: task() })).toEqual({
      total: 1,
      usable: 0,
      needsAction: 0,
    })
  })
})
