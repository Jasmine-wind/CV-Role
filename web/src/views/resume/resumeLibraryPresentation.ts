import type { ResumeListItem } from '@/types/resume'
import type { AsyncTaskVO } from '@/types/task'

export type ResumeLibraryStatusTone = 'success' | 'warning' | 'danger' | 'info'

export type ResumeLibraryStatusKind =
  | 'ready'
  | 'preparing'
  | 'needs-review'
  | 'reprepare'
  | 'failed'
  | 'waiting'

export interface ResumeLibraryStatus {
  kind: ResumeLibraryStatusKind
  label: string
  description: string
  tone: ResumeLibraryStatusTone
  primaryAction: 'review' | 'prepare' | 'retry' | null
  canDelete: boolean
}

const DEFAULT_PREPARING_DESCRIPTION = '系统正在读取并整理内容。'

/**
 * Resume 列表状态只回答两个相互独立的问题：
 * - canonicalReady：当前是否已有结构化 canonical 文档（无 → 需要重新准备）
 * - qualityStatus：这份 canonical 文档是否还有待确认内容（NEEDS_REVIEW → 有内容待确认）
 * 不得再把 NEEDS_REVIEW 当作“需要重新准备”，也不得当成“不能使用”：
 * 有 canonical 文档就可以继续优化，待确认内容不阻塞岗位分析与编辑。
 */
export const getResumeLibraryStatus = (
  resume: ResumeListItem,
  task?: AsyncTaskVO | null,
): ResumeLibraryStatus => {
  const activePreparation = task?.status === 'PENDING' || task?.status === 'RUNNING'
  if (activePreparation) {
    return {
      kind: 'preparing',
      label: '正在准备',
      description: task?.message || DEFAULT_PREPARING_DESCRIPTION,
      tone: 'warning',
      primaryAction: null,
      canDelete: false,
    }
  }

  if (resume.parseStatus === 'FAILED') {
    return {
      kind: 'failed',
      label: '准备失败',
      description: resume.parseErrorMessage || '未能读取这份简历，请重试。',
      tone: 'danger',
      primaryAction: 'retry',
      canDelete: true,
    }
  }

  if (resume.parseStatus === 'SUCCESS') {
    if (resume.qualityStatus === 'PENDING') {
      return {
        kind: 'preparing',
        label: '正在准备',
        description: DEFAULT_PREPARING_DESCRIPTION,
        tone: 'warning',
        primaryAction: null,
        canDelete: false,
      }
    }
    if (resume.qualityStatus === 'NEEDS_REVIEW' && resume.canonicalReady !== false) {
      return {
        kind: 'needs-review',
        label: '有内容待确认',
        description: '有内容待确认，不影响继续优化；建议在编辑过程中一并确认。',
        tone: 'warning',
        primaryAction: 'review',
        canDelete: true,
      }
    }
    if (resume.canonicalReady === false) {
      return {
        kind: 'reprepare',
        label: '需要重新准备',
        description: '这份简历来自旧版解析，需要重新读取后才能使用。',
        tone: 'warning',
        primaryAction: 'prepare',
        canDelete: true,
      }
    }
    if (resume.qualityStatus === 'FAILED') {
      return {
        kind: 'failed',
        label: '准备失败',
        description: resume.parseErrorMessage || '内容准备没有完成，请重试。',
        tone: 'danger',
        primaryAction: 'retry',
        canDelete: true,
      }
    }
    return {
      kind: 'ready',
      label: '可用于岗位分析',
      description: '内容准备已完成。',
      tone: 'success',
      primaryAction: null,
      canDelete: true,
    }
  }

  return {
    kind: 'waiting',
    label: '等待准备',
    description: '尚未完成内容准备。',
    tone: 'info',
    primaryAction: 'prepare',
    canDelete: true,
  }
}

export const canRetryResumePreparation = (
  resume: ResumeListItem,
  task?: AsyncTaskVO | null,
) => {
  if (task?.status === 'PENDING' || task?.status === 'RUNNING') return false
  const primaryAction = getResumeLibraryStatus(resume, task).primaryAction
  return primaryAction === 'prepare' || primaryAction === 'retry'
}

export const formatResumeFileSize = (size: number) => {
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(2)} MB`
  }
  return `${Math.max(size / 1024, 0.1).toFixed(1)} KB`
}

export const formatResumeDate = (createdAt: string) => {
  const date = createdAt.slice(0, 10)
  const [year, month, day] = date.split('-')
  if (!year || !month || !day) return date
  return `${year}年${month}月${day}日`
}

export interface ResumeLibrarySummary {
  total: number
  usable: number
  needsAction: number
}

export const getResumeLibrarySummary = (
  resumes: ResumeListItem[],
  activeTasks: Record<number, AsyncTaskVO> = {},
): ResumeLibrarySummary => {
  const statuses = resumes.map((resume) => getResumeLibraryStatus(resume, activeTasks[resume.id]))
  return {
    total: resumes.length,
    usable: statuses.filter((status) => status.kind === 'ready').length,
    needsAction: statuses.filter((status) =>
      status.kind === 'needs-review' ||
      status.kind === 'reprepare' ||
      status.kind === 'failed' ||
      status.kind === 'waiting'
    ).length,
  }
}
