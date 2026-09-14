import type {
  WorkspaceSourceBlock,
  WorkspaceSourceReference,
  WorkspaceSourceRestoreScope,
} from '@/types/workspace'

/**
 * SOURCE mutation 分组只基于服务端 verdict（omission 边界 / restoreEligible / restoreScope），
 * 前端不按文本或相邻位置猜测 provenance。
 * WorkspaceSourcePane 用它决定按钮与文案，WorkspacePanel 用它把点击解析成 occurrence 边界。
 */
export interface OmissionActionPlan {
  key: string
  leaderBlockId: string
  blockIds: string[]
  sourceOccurrenceIds: string[]
  confirmed: boolean
  projectBlockCount: number
}

export interface RestoreActionPlan {
  key: string
  leaderBlockId: string
  blockIds: string[]
  scope: WorkspaceSourceRestoreScope
  occurrenceIds: string[]
}

export const buildOmissionPlansByBlockId = (source: WorkspaceSourceReference | null) => {
  const result = new Map<string, OmissionActionPlan>()
  const groups = new Map<string, WorkspaceSourceBlock[]>()

  // Group every block first. Filtering before grouping could make a partially
  // mapped/ineligible Project entry look like a complete omission boundary.
  for (const block of source?.sourceBlocks ?? []) {
    // 只有服务端给出的 PROJECT section + entry 或 BULLET 边界完整时才批量；
    // 前端不按文本或相邻位置猜测，也不把部分确认状态拆成两个独立操作。
    const projectEntryKey =
      block.sourceSectionKind?.toUpperCase() === 'PROJECT' &&
      block.sourceSectionId &&
      block.sourceEntryId
        ? `project:${block.sourceSectionId}:${block.sourceEntryId}`
        : block.sourceNodeType === 'BULLET' &&
            block.sourceSectionId &&
            block.sourceEntryId &&
            block.sourceBulletId
          ? `bullet:${block.sourceSectionId}:${block.sourceEntryId}:${block.sourceBulletId}`
          : `block:${block.id}`
    const group = groups.get(projectEntryKey) ?? []
    group.push(block)
    groups.set(projectEntryKey, group)
  }

  for (const [key, blocks] of groups) {
    const ordered = [...blocks].sort((left, right) => left.order - right.order)
    const completeActionableBoundary = ordered.every(
      (block) =>
        block.occurrenceIds.length > 0 &&
        (block.omissionConfirmed || (block.status === 'UNMAPPED' && block.omissionEligible)),
    )
    if (!completeActionableBoundary) continue
    const occurrenceIds = [...new Set(ordered.flatMap((block) => block.occurrenceIds))]
    if (!ordered[0] || occurrenceIds.length === 0) continue
    const plan: OmissionActionPlan = {
      key,
      leaderBlockId: ordered[0].id,
      blockIds: ordered.map((block) => block.id),
      sourceOccurrenceIds: occurrenceIds,
      confirmed: ordered.every((block) => block.omissionConfirmed),
      projectBlockCount: key.startsWith('project:') ? ordered.length : 1,
    }
    for (const block of ordered) result.set(block.id, plan)
  }
  return result
}

export const buildRestorePlansByBlockId = (source: WorkspaceSourceReference | null) => {
  const result = new Map<string, RestoreActionPlan>()
  const groups = new Map<string, WorkspaceSourceBlock[]>()
  for (const block of source?.sourceBlocks ?? []) {
    // 只有服务端 restoreEligible 的 block 才进入恢复计划；eligible 由整个边界的安全 verdict 决定。
    if (!block.restoreEligible || block.restoreScope === 'NONE') continue
    const key = restoreBoundaryKey(block)
    const group = groups.get(key) ?? []
    group.push(block)
    groups.set(key, group)
  }
  for (const [key, blocks] of groups) {
    const ordered = [...blocks].sort((left, right) => left.order - right.order)
    if (!ordered[0]) continue
    const plan: RestoreActionPlan = {
      key: `restore:${key}`,
      leaderBlockId: ordered[0].id,
      blockIds: ordered.map((block) => block.id),
      scope: ordered[0].restoreScope,
      occurrenceIds: [...new Set(ordered.flatMap((block) => block.occurrenceIds))],
    }
    for (const block of ordered) result.set(block.id, plan)
  }
  return result
}

const restoreBoundaryKey = (block: WorkspaceSourceBlock) => {
  switch (block.restoreScope) {
    case 'PROJECT_ENTRY':
      return `project:${block.sourceSectionId}:${block.sourceEntryId}`
    case 'ENTRY':
      return `entry:${block.sourceSectionId}:${block.sourceEntryId}`
    case 'BULLET':
      return `bullet:${block.sourceSectionId}:${block.sourceEntryId}:${block.sourceBulletId}`
    case 'CONTACT':
      return `contact:${block.id}`
    default:
      return `block:${block.id}`
  }
}
