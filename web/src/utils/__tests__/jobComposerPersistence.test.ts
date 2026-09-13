import { beforeEach, describe, expect, it } from 'vitest'
import {
  ACTIVE_ANALYSIS_STORAGE_KEY,
  JOB_COMPOSER_DRAFT_STORAGE_KEY,
  JOB_COMPOSER_STORAGE_SCHEMA_VERSION,
  JOB_COMPOSER_STORAGE_TTL_MS,
  clearJobComposerDraft,
  clearJobComposerTemporaryStateForUser,
  readActiveJobAnalysis,
  readJobComposerDraft,
  saveJobComposerDraft,
  saveStoredActiveJobAnalysis,
} from '../jobComposerPersistence'

const createStorage = (): Storage => {
  const values = new Map<string, string>()
  return {
    get length() {
      return values.size
    },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => [...values.keys()][index] ?? null,
    removeItem: (key) => void values.delete(key),
    setItem: (key, value) => void values.set(key, value),
  }
}

const analysis = {
  taskId: 11,
  optimizationTaskId: 12,
  sourceResumeVersionId: 13,
  targetResumeVersionId: 14,
  jobTargetId: 15,
}

let storage: Storage
const now = Date.parse('2026-04-01T00:00:00.000Z')

beforeEach(() => {
  storage = createStorage()
})

describe('job composer temporary persistence', () => {
  it('round-trips an independently versioned user draft', () => {
    saveJobComposerDraft(7, 42, '完整岗位 JD', storage, now)

    expect(readJobComposerDraft(7, storage, now)).toEqual({
      userId: 7,
      schemaVersion: JOB_COMPOSER_STORAGE_SCHEMA_VERSION,
      savedAt: '2026-04-01T00:00:00.000Z',
      selectedResumeId: 42,
      jobDescription: '完整岗位 JD',
    })
  })

  it('never restores another user draft or active analysis', () => {
    saveJobComposerDraft(7, 42, 'A 的岗位 JD', storage, now)
    expect(readJobComposerDraft(8, storage, now)).toBeNull()
    expect(storage.getItem(JOB_COMPOSER_DRAFT_STORAGE_KEY)).toBeNull()

    saveStoredActiveJobAnalysis(7, analysis, 42, 'A 的运行中 JD', storage, now)
    expect(readActiveJobAnalysis(8, storage, now)).toBeNull()
    expect(storage.getItem(ACTIVE_ANALYSIS_STORAGE_KEY)).toBeNull()
  })

  it('rejects expired, future and unknown-schema state', () => {
    saveJobComposerDraft(7, 42, '已过期 JD', storage, now - JOB_COMPOSER_STORAGE_TTL_MS - 1)
    expect(readJobComposerDraft(7, storage, now)).toBeNull()

    saveJobComposerDraft(7, 42, '未来 JD', storage, now + 1)
    expect(readJobComposerDraft(7, storage, now)).toBeNull()

    storage.setItem(JOB_COMPOSER_DRAFT_STORAGE_KEY, JSON.stringify({
      userId: 7,
      schemaVersion: JOB_COMPOSER_STORAGE_SCHEMA_VERSION + 1,
      savedAt: new Date(now).toISOString(),
      selectedResumeId: 42,
      jobDescription: '未知结构 JD',
    }))
    expect(readJobComposerDraft(7, storage, now)).toBeNull()
  })

  it('stores active analysis with the same user, schema and TTL envelope', () => {
    saveStoredActiveJobAnalysis(7, analysis, 42, '失败后可恢复的 JD', storage, now)

    expect(readActiveJobAnalysis(7, storage, now)).toEqual({
      userId: 7,
      schemaVersion: JOB_COMPOSER_STORAGE_SCHEMA_VERSION,
      savedAt: '2026-04-01T00:00:00.000Z',
      ...analysis,
      resumeId: 42,
      jobDescription: '失败后可恢复的 JD',
    })
  })

  it('clears synchronously and logout only removes the current user state', () => {
    saveJobComposerDraft(7, 42, '立即清空', storage, now)
    clearJobComposerDraft(storage)
    expect(storage.getItem(JOB_COMPOSER_DRAFT_STORAGE_KEY)).toBeNull()

    saveJobComposerDraft(8, 81, 'B 的 JD', storage, now)
    saveStoredActiveJobAnalysis(8, analysis, 81, 'B 的分析', storage, now)
    clearJobComposerTemporaryStateForUser(7, storage)
    expect(readJobComposerDraft(8, storage, now)?.jobDescription).toBe('B 的 JD')
    expect(readActiveJobAnalysis(8, storage, now)?.jobDescription).toBe('B 的分析')

    clearJobComposerTemporaryStateForUser(8, storage)
    expect(storage.length).toBe(0)
  })
})
