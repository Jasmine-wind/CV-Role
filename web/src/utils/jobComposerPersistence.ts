import type { JobAnalysisStartResult } from '@/types/job-analysis'

export const JOB_COMPOSER_DRAFT_STORAGE_KEY = 'cv-role:job-composer-draft'
export const ACTIVE_ANALYSIS_STORAGE_KEY = 'cv-role:active-job-analysis'
export const JOB_COMPOSER_STORAGE_SCHEMA_VERSION = 1
export const JOB_COMPOSER_STORAGE_TTL_MS = 24 * 60 * 60 * 1000

interface StoredTemporaryState {
  userId: number
  schemaVersion: number
  savedAt: string
}

export interface JobComposerDraft extends StoredTemporaryState {
  selectedResumeId: number | null
  jobDescription: string
}

export interface StoredActiveJobAnalysis extends StoredTemporaryState, JobAnalysisStartResult {
  resumeId?: number
  jobDescription: string
}

const isPositiveInteger = (value: unknown): value is number =>
  typeof value === 'number' && Number.isInteger(value) && value > 0

const isCurrentState = (
  value: unknown,
  userId: number,
  now: number,
): value is StoredTemporaryState => {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<StoredTemporaryState>
  if (
    candidate.userId !== userId
    || candidate.schemaVersion !== JOB_COMPOSER_STORAGE_SCHEMA_VERSION
    || typeof candidate.savedAt !== 'string'
  ) {
    return false
  }
  const savedAt = Date.parse(candidate.savedAt)
  return Number.isFinite(savedAt)
    && savedAt <= now
    && now - savedAt <= JOB_COMPOSER_STORAGE_TTL_MS
}

const readState = <T extends StoredTemporaryState>(
  storage: Storage,
  key: string,
  userId: number,
  validate: (value: unknown) => value is T,
  now: number,
): T | null => {
  const raw = storage.getItem(key)
  if (!raw) return null
  try {
    const parsed: unknown = JSON.parse(raw)
    if (isCurrentState(parsed, userId, now) && validate(parsed)) return parsed
  } catch {
    // Corrupt browser state is discarded below.
  }
  storage.removeItem(key)
  return null
}

const isDraft = (value: unknown): value is JobComposerDraft => {
  const candidate = value as Partial<JobComposerDraft>
  return (
    (candidate.selectedResumeId === null || isPositiveInteger(candidate.selectedResumeId))
    && typeof candidate.jobDescription === 'string'
    && candidate.jobDescription.length <= 10_000
  )
}

const isActiveAnalysis = (value: unknown): value is StoredActiveJobAnalysis => {
  const candidate = value as Partial<StoredActiveJobAnalysis>
  return (
    isPositiveInteger(candidate.taskId)
    && isPositiveInteger(candidate.optimizationTaskId)
    && isPositiveInteger(candidate.sourceResumeVersionId)
    && isPositiveInteger(candidate.targetResumeVersionId)
    && isPositiveInteger(candidate.jobTargetId)
    && (candidate.resumeId === undefined || isPositiveInteger(candidate.resumeId))
    && typeof candidate.jobDescription === 'string'
    && candidate.jobDescription.length <= 10_000
  )
}

export const readJobComposerDraft = (
  userId: number,
  storage: Storage = window.sessionStorage,
  now = Date.now(),
) => readState(storage, JOB_COMPOSER_DRAFT_STORAGE_KEY, userId, isDraft, now)

export const saveJobComposerDraft = (
  userId: number,
  selectedResumeId: number | null,
  jobDescription: string,
  storage: Storage = window.sessionStorage,
  now = Date.now(),
) => {
  const state: JobComposerDraft = {
    userId,
    schemaVersion: JOB_COMPOSER_STORAGE_SCHEMA_VERSION,
    savedAt: new Date(now).toISOString(),
    selectedResumeId,
    jobDescription,
  }
  storage.setItem(JOB_COMPOSER_DRAFT_STORAGE_KEY, JSON.stringify(state))
}

export const clearJobComposerDraft = (storage: Storage = window.sessionStorage) => {
  storage.removeItem(JOB_COMPOSER_DRAFT_STORAGE_KEY)
}

export const readActiveJobAnalysis = (
  userId: number,
  storage: Storage = window.sessionStorage,
  now = Date.now(),
) => readState(storage, ACTIVE_ANALYSIS_STORAGE_KEY, userId, isActiveAnalysis, now)

export const saveStoredActiveJobAnalysis = (
  userId: number,
  analysis: JobAnalysisStartResult,
  resumeId: number | null,
  jobDescription: string,
  storage: Storage = window.sessionStorage,
  now = Date.now(),
) => {
  const state: StoredActiveJobAnalysis = {
    userId,
    schemaVersion: JOB_COMPOSER_STORAGE_SCHEMA_VERSION,
    savedAt: new Date(now).toISOString(),
    ...analysis,
    resumeId: resumeId ?? undefined,
    jobDescription,
  }
  storage.setItem(ACTIVE_ANALYSIS_STORAGE_KEY, JSON.stringify(state))
}

export const clearStoredActiveJobAnalysis = (storage: Storage = window.sessionStorage) => {
  storage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY)
}

export const clearJobComposerTemporaryState = (storage: Storage = window.sessionStorage) => {
  clearJobComposerDraft(storage)
  clearStoredActiveJobAnalysis(storage)
}

export const clearJobComposerTemporaryStateForUser = (
  userId: number,
  storage: Storage = window.sessionStorage,
) => {
  for (const key of [JOB_COMPOSER_DRAFT_STORAGE_KEY, ACTIVE_ANALYSIS_STORAGE_KEY]) {
    const raw = storage.getItem(key)
    if (!raw) continue
    try {
      const parsed = JSON.parse(raw) as { userId?: unknown }
      if (parsed.userId !== userId) continue
    } catch {
      // Invalid transient state should never survive an explicit logout.
    }
    storage.removeItem(key)
  }
}
