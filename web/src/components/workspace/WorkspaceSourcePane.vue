<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  confirmWorkspaceSourceOmissions,
  getWorkspaceSourcePdf,
  restoreWorkspaceSourceContent,
  unconfirmWorkspaceSourceOmissions,
} from '@/api/workspace'
import ErrorState from '@/components/common/ErrorState.vue'
import type {
  WorkspaceSaveResult,
  WorkspaceSourceBlock,
  WorkspaceSourceReference,
  WorkspaceSourceRestoreScope,
} from '@/types/workspace'

const props = defineProps<{
  optimizationTaskId: number
  source: WorkspaceSourceReference | null
  loading: boolean
  error: string | null
  selectedOccurrenceIds?: string[]
  /** 有本地修改、正在保存或 SOURCE revision 尚未同步时，由 Workspace 禁止省略操作。 */
  omissionDisabledReason?: string | null
  /** 同样的保存门禁；Restore 不能用服务端结果覆盖尚未持久化的本地编辑。 */
  restoreDisabledReason?: string | null
  /** Preview → “查看并处理”递增该 key：打开结构问题区域并定位第一个 blocker。 */
  reviewRequestKey?: number
}>()

const emit = defineEmits<{
  retry: []
  focusTarget: [targetNodeId: string]
  omissionSaved: [expectedRevision: number, result: WorkspaceSaveResult, confirmed: boolean]
  omissionConcurrent: []
  restoreSaved: [expectedRevision: number, result: WorkspaceSaveResult, wholeProject: boolean]
  restoreConcurrent: []
  sourceMutationBusy: [busy: boolean]
  locateIssueTarget: [targetNodeId: string]
  restartUpload: []
}>()

const mode = ref<'text' | 'pdf'>('text')
const pdfUrl = ref<string | null>(null)
const pdfLoading = ref(false)
const pdfError = ref<string | null>(null)
const blockRoot = ref<HTMLElement | null>(null)
const issueArea = ref<HTMLElement | null>(null)
const issuesOpen = ref(true)
const mutationOperationKey = ref<string | null>(null)
const mutationError = ref<{ blockId: string; message: string } | null>(null)
let mutationRequestSequence = 0
let activeMutationOperation: symbol | null = null

const blockers = computed(
  () => props.source?.fidelityIssues.filter((issue) => issue.severity === 'BLOCKER') ?? [],
)
const warnings = computed(
  () => props.source?.fidelityIssues.filter((issue) => issue.severity === 'WARNING') ?? [],
)
const selectedSet = computed(() => new Set(props.selectedOccurrenceIds ?? []))

const loadPdf = async () => {
  if (pdfUrl.value || pdfLoading.value) return
  pdfLoading.value = true
  pdfError.value = null
  try {
    const blob = await getWorkspaceSourcePdf(props.optimizationTaskId)
    pdfUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    pdfError.value = error instanceof Error ? error.message : '原始 PDF 加载失败'
  } finally {
    pdfLoading.value = false
  }
}

const setMode = (value: 'text' | 'pdf') => {
  mode.value = value
  if (value === 'pdf') void loadPdf()
}

const selectBlock = (targetNodeIds: string[], reliable: boolean) => {
  if (reliable && targetNodeIds.length === 1) emit('focusTarget', targetNodeIds[0]!)
}

const blockTextChanged = (block: WorkspaceSourceBlock) =>
  block.targetNodeIds.some((targetNodeId) =>
    props.source?.mappings.some(
      (mapping) => mapping.targetNodeId === targetNodeId && mapping.textChanged,
    ),
  )

/** 内部 lineage 枚举只能在这里转换成用户文案，不能直接进入模板。 */
const mappingLabel = (block: WorkspaceSourceBlock) => {
  if (block.omissionConfirmed) return '已确认省略'
  switch (block.status) {
    case 'EXACT':
      return blockTextChanged(block) ? '已定位 · 内容已修改' : '已定位'
    case 'MERGED':
      return blockTextChanged(block) ? '合并来源 · 内容已修改' : '合并来源'
    case 'SPLIT':
      return blockTextChanged(block) ? '拆分来源 · 内容已修改' : '拆分来源'
    case 'UNMAPPED':
      return '未映射'
    case 'AMBIGUOUS':
      return '待确认'
    default:
      return '待确认'
  }
}

type OmissionActionPlan = {
  key: string
  leaderBlockId: string
  blockIds: string[]
  sourceOccurrenceIds: string[]
  confirmed: boolean
  projectBlockCount: number
}

const omissionPlansByBlockId = computed(() => {
  const result = new Map<string, OmissionActionPlan>()
  const groups = new Map<string, WorkspaceSourceBlock[]>()

  // Group every block first. Filtering before grouping could make a partially
  // mapped/ineligible Project entry look like a complete omission boundary.
  for (const block of props.source?.sourceBlocks ?? []) {
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
})

const omissionPlan = (blockId: string) => omissionPlansByBlockId.value.get(blockId) ?? null
const isProjectPlan = (plan: OmissionActionPlan) => plan.key.startsWith('project:')

const omissionActionLabel = (plan: OmissionActionPlan) => {
  if (isProjectPlan(plan)) {
    if (plan.projectBlockCount > 1) {
      return plan.confirmed
        ? `取消省略此项目对应的 ${plan.projectBlockCount} 段原文`
        : `确认省略此项目对应的 ${plan.projectBlockCount} 段原文`
    }
    return plan.confirmed ? '取消省略此项目原文' : '确认省略此项目原文'
  }
  return plan.confirmed ? '取消省略' : '确认省略'
}

const omissionDescription = (plan: OmissionActionPlan) => {
  if (isProjectPlan(plan)) {
    return plan.confirmed
      ? '这些项目原文已作为有意省略处理；取消后会重新进入导出检查。'
      : '这些内容属于同一个项目，且均未出现在当前简历中。请只在确认是有意删除时操作。'
  }
  return plan.confirmed
    ? '这段原文已作为有意省略处理；取消后会重新进入导出检查。'
    : '这段原文未出现在当前简历中。请只在确认是有意删除时操作。'
}

/** 结构问题卡片的省略按钮：完整 Project 边界统一表达为“确认省略整个项目”。 */
const omissionPlanLabel = (plan: OmissionActionPlan) =>
  isProjectPlan(plan) ? '确认省略整个项目' : '确认省略'

type ApiErrorLike = Error & { code?: number }

type RestoreActionPlan = {
  key: string
  leaderBlockId: string
  blockIds: string[]
  scope: WorkspaceSourceRestoreScope
  occurrenceIds: string[]
}

/** 恢复边界由服务端 resolved verdict 分组；前端不拼 occurrence ID，也不推导 provenance。 */
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

const restorePlansByBlockId = computed(() => {
  const result = new Map<string, RestoreActionPlan>()
  const groups = new Map<string, WorkspaceSourceBlock[]>()
  for (const block of props.source?.sourceBlocks ?? []) {
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
})

const restorePlan = (blockId: string) => restorePlansByBlockId.value.get(blockId) ?? null

const restoreActionLabel = (plan: RestoreActionPlan) =>
  plan.scope === 'PROJECT_ENTRY' ? '恢复整个项目' : '恢复原文'

const ISSUE_COPY: Record<string, { title: string; detail: string }> = {
  SOURCE_CONTENT_UNMAPPED: {
    title: '原文内容未进入当前简历',
    detail: '这段内容存在于原始简历，但当前简历中找不到。',
  },
  PROJECT_BOUNDARY_LOST: {
    title: '项目未进入当前简历',
    detail: '原始简历中这是一个独立项目，当前简历中没有找到完整项目结构。',
  },
  AMBIGUOUS_MAPPING: {
    title: '对应关系不明确',
    detail:
      '系统无法确认这段当前内容来自哪一段原文，为避免把错误内容当作合法修改，当前禁止导出。',
  },
  DUPLICATE_MAPPING: {
    title: '同一段原文出现了多份',
    detail: '请保留正确的一份，删除重复内容。',
  },
  SOURCE_MANIFEST_INVALID: {
    title: '原文校验数据异常',
    detail: '该问题无法通过编辑简历内容解决。请重新上传原始简历并创建新的优化任务。',
  },
  SOURCE_MANIFEST_UNAVAILABLE: {
    title: '原文校验数据异常',
    detail: '该问题无法通过编辑简历内容解决。请重新上传原始简历并创建新的优化任务。',
  },
  CONFIRMED_OMISSION_INVALID: {
    title: '原文校验数据异常',
    detail: '该问题无法通过编辑简历内容解决。',
  },
  SECTION_HEADING_LOST: {
    title: '章节标题缺失',
    detail: '章节标题为空但内容仍在。请补回标题，或删除残留内容。',
  },
  DUPLICATE_CONTACT_SUSPECTED: {
    title: '检测到疑似重复联系方式',
    detail: '请检查联系方式列表，删除重复的一项。',
  },
  SOURCE_CONTENT_SPLIT: {
    title: '一段原文被拆分到多个位置',
    detail: '不影响导出，但建议核对拆分边界是否合理。',
  },
  TARGET_CONTENT_UNMAPPED: {
    title: '当前内容没有可靠的原文定位',
    detail: '不影响导出，但建议核对这段内容是否属于本人的真实经历。',
  },
  DUPLICATE_DATE_SUSPECTED: {
    title: '多个位置出现相同日期',
    detail: '请确认不是结构恢复造成的重复。',
  },
}

const RESTART_UPLOAD_CODES = new Set(['SOURCE_MANIFEST_INVALID', 'SOURCE_MANIFEST_UNAVAILABLE'])

type IssueCard = {
  key: string
  code: string
  severity: 'BLOCKER' | 'WARNING'
  title: string
  detail: string
  excerpt: string | null
  blockIds: string[]
  restorePlan: RestoreActionPlan | null
  omissionPlan: OmissionActionPlan | null
  locateTargets: string[]
  reasonCopy: string | null
  canRestartUpload: boolean
  reviewTarget: boolean
}

const truncate = (value: string, limit = 120) =>
  value.length > limit ? `${value.slice(0, limit)}…` : value

const boundaryBlocksForIssue = (issueSourceOccurrenceIds: string[]) => {
  const blocks = (props.source?.sourceBlocks ?? []).filter((block) =>
    block.occurrenceIds.some((id) => issueSourceOccurrenceIds.includes(id)),
  )
  const leader = blocks[0]
  if (!leader) return []
  if (
    leader.sourceSectionKind?.toUpperCase() === 'PROJECT' &&
    leader.sourceSectionId &&
    leader.sourceEntryId
  ) {
    return (props.source?.sourceBlocks ?? []).filter(
      (block) =>
        block.sourceSectionKind?.toUpperCase() === 'PROJECT' &&
        block.sourceSectionId === leader.sourceSectionId &&
        block.sourceEntryId === leader.sourceEntryId,
    )
  }
  return [leader]
}

const blockedReasonCopy = (reason: string | null, isProject: boolean) => {
  switch (reason) {
    case 'BOUNDARY_HAS_OTHER_MAPPINGS':
      return isProject
        ? '项目内容仍存在于其它位置，无法安全自动恢复。请定位错误内容并修正项目归属。'
        : '当前问题无法自动处理，请检查结构关系。'
    case 'PARENT_SECTION_MISSING':
      return isProject
        ? '原项目章节已不存在，当前无法安全局部恢复。可以恢复优化前版本，或重新创建本次优化。'
        : '原章节已不存在，当前无法安全局部恢复。可以恢复优化前版本，或重新创建本次优化。'
    case 'PARENT_LINEAGE_MISMATCH':
      return '来源归属关系不一致，当前无法安全自动恢复。请检查结构关系。'
    case 'ENTRY_ALREADY_PRESENT':
      return '当前条目仍存在于简历中，请直接核对并修正对应内容。'
    case 'TARGET_VALUE_CONFLICT':
      return '当前简历中已经存在相同内容，无需重复恢复。'
    default:
      return '当前问题无法自动处理，请检查结构关系。'
  }
}

const issueCards = computed<IssueCard[]>(() => {
  const source = props.source
  if (!source) return []
  return source.fidelityIssues.map((issue, index) => {
    const copy = ISSUE_COPY[issue.code]
    const isBoundaryCode =
      issue.code === 'SOURCE_CONTENT_UNMAPPED' || issue.code === 'PROJECT_BOUNDARY_LOST'
    const boundaryBlocks = isBoundaryCode ? boundaryBlocksForIssue(issue.sourceOccurrenceIds) : []
    const isProject = boundaryBlocks.some(
      (block) => block.sourceSectionKind?.toUpperCase() === 'PROJECT',
    )
    const restorable = boundaryBlocks.find(
      (block) => block.restoreEligible && block.restoreScope !== 'NONE',
    )
    const omission =
      isBoundaryCode && boundaryBlocks[0] ? omissionPlan(boundaryBlocks[0].id) : null
    const blockedReason =
      boundaryBlocks.find((block) => block.restoreBlockedReason)?.restoreBlockedReason ?? null
    let locateTargets: string[] = []
    if (issue.code === 'AMBIGUOUS_MAPPING' && issue.targetNodeIds.length) {
      locateTargets = [issue.targetNodeIds[0]!]
    }
    if (issue.code === 'DUPLICATE_MAPPING') locateTargets = [...new Set(issue.targetNodeIds)]
    const actionable = Boolean(
      restorable || (omission && !omission.confirmed) || locateTargets.length,
    )
    return {
      key: `${issue.code}:${index}`,
      code: issue.code,
      severity: issue.severity,
      title: copy?.title ?? issue.message,
      detail: copy?.detail ?? issue.message,
      excerpt: boundaryBlocks[0] ? truncate(boundaryBlocks[0].text) : null,
      blockIds: boundaryBlocks.map((block) => block.id),
      restorePlan: restorable ? restorePlan(restorable.id) : null,
      omissionPlan: omission && !omission.confirmed ? omission : null,
      locateTargets,
      reasonCopy: isBoundaryCode && !actionable ? blockedReasonCopy(blockedReason, isProject) : null,
      canRestartUpload: RESTART_UPLOAD_CODES.has(issue.code),
      reviewTarget: boundaryBlocks.some((block) =>
        block.occurrenceIds.some((id) => selectedSet.value.has(id)),
      ),
    }
  })
})

const locateActionLabel = (card: IssueCard, index: number) =>
  card.code === 'DUPLICATE_MAPPING' && card.locateTargets.length > 1
    ? `定位第 ${index + 1} 处`
    : '定位问题内容'

type SourceMutation =
  | { kind: 'omission'; plan: OmissionActionPlan }
  | { kind: 'restore'; plan: RestoreActionPlan }

const mutationConflictMessage = (mutation: SourceMutation) =>
  mutation.kind === 'omission'
    ? '当前简历已有更新，本次操作未生效。请刷新后重试。'
    : '当前简历已有更新，本次恢复未生效。请刷新后重试。'

// Restore / confirm / unconfirm 共享同一个 SOURCE mutation mutex，
// 三者竞争同一个 TARGET revision，必须互斥。
const performSourceMutation = async (mutation: SourceMutation) => {
  const source = props.source
  const disabledReason =
    mutation.kind === 'omission' ? props.omissionDisabledReason : props.restoreDisabledReason
  const occurrenceIds =
    mutation.kind === 'omission' ? mutation.plan.sourceOccurrenceIds : mutation.plan.occurrenceIds
  if (!source || disabledReason || mutationOperationKey.value !== null || occurrenceIds.length === 0) {
    return
  }

  const expectedRevision = source.targetRevision
  const taskAtRequest = props.optimizationTaskId
  const requestSequence = ++mutationRequestSequence
  const operationToken = Symbol(mutation.plan.key)
  const blockId = mutation.plan.leaderBlockId
  activeMutationOperation = operationToken
  mutationOperationKey.value = mutation.plan.key
  mutationError.value = null
  emit('sourceMutationBusy', true)
  try {
    const request = {
      expectedRevision,
      // 只提交服务端给出的 occurrence 边界；恢复范围仍由服务端重新解析并重新验证。
      sourceOccurrenceIds: occurrenceIds,
    }
    const result =
      mutation.kind === 'omission'
        ? mutation.plan.confirmed
          ? await unconfirmWorkspaceSourceOmissions(taskAtRequest, request)
          : await confirmWorkspaceSourceOmissions(taskAtRequest, request)
        : await restoreWorkspaceSourceContent(taskAtRequest, request)
    if (requestSequence !== mutationRequestSequence || taskAtRequest !== props.optimizationTaskId) {
      return
    }
    if (!result.saved || result.conflict) {
      mutationError.value = { blockId, message: mutationConflictMessage(mutation) }
      if (mutation.kind === 'omission') emit('omissionConcurrent')
      else emit('restoreConcurrent')
      return
    }
    if (mutation.kind === 'omission') {
      emit('omissionSaved', expectedRevision, result, !mutation.plan.confirmed)
    } else {
      emit('restoreSaved', expectedRevision, result, mutation.plan.scope === 'PROJECT_ENTRY')
    }
  } catch (error) {
    if (requestSequence !== mutationRequestSequence) return
    const concurrent = (error as ApiErrorLike)?.code === 409
    mutationError.value = {
      blockId,
      message: concurrent
        ? mutationConflictMessage(mutation)
        : error instanceof Error
          ? error.message
          : mutation.kind === 'omission'
            ? '省略状态更新失败，请稍后重试。'
            : '恢复原文失败，请稍后重试。',
    }
    if (concurrent) {
      if (mutation.kind === 'omission') emit('omissionConcurrent')
      else emit('restoreConcurrent')
    }
  } finally {
    // A route change can release this task's lock before its old request settles. Only the
    // operation that still owns the lock may clear it, otherwise a late response could unlock
    // a newer task's CAS request.
    if (activeMutationOperation === operationToken) {
      activeMutationOperation = null
      mutationOperationKey.value = null
      emit('sourceMutationBusy', false)
    }
  }
}

const performOmission = (plan: OmissionActionPlan) => performSourceMutation({ kind: 'omission', plan })
const performRestore = (plan: RestoreActionPlan) => performSourceMutation({ kind: 'restore', plan })

const mutationInFlight = (key: string) => mutationOperationKey.value === key

const releaseMutationOperation = () => {
  if (activeMutationOperation === null) return
  activeMutationOperation = null
  mutationOperationKey.value = null
  emit('sourceMutationBusy', false)
}

watch(
  () => props.selectedOccurrenceIds?.join('|'),
  async () => {
    await nextTick()
    blockRoot.value?.querySelector<HTMLElement>('.source-block.is-selected')?.scrollIntoView?.({
      block: 'center',
      behavior: 'auto',
    })
  },
)

watch(
  () => props.reviewRequestKey,
  async () => {
    await nextTick()
    issuesOpen.value = true
    await nextTick()
    // 选中 block 的滚动仍由现有 semantic anchor 负责；没有可定位 block 时回到结构问题顶部。
    const hasSelectedBlock = props.selectedOccurrenceIds?.length
      ? Boolean(blockRoot.value?.querySelector('.source-block.is-selected'))
      : false
    if (!hasSelectedBlock) {
      issueArea.value?.scrollIntoView?.({ block: 'start', behavior: 'auto' })
    }
  },
)

const handleIssuesToggle = (event: Event) => {
  issuesOpen.value = (event.target as HTMLDetailsElement).open
}

watch(
  () => props.optimizationTaskId,
  () => {
    mutationRequestSequence += 1
    mutationError.value = null
    // The old request is task-scoped and can no longer affect this view. Release the parent UI,
    // while the operation token prevents its eventual finally block from unlocking a new request.
    releaseMutationOperation()
  },
)

watch(
  () => props.source?.targetRevision,
  () => {
    // Invalidate any response tied to the old revision, but keep a CAS-conflict
    // explanation visible after the parent adopts and reloads the winning version.
    mutationRequestSequence += 1
  },
)

watch(
  () => props.source?.exportBlocked,
  (blocked) => {
    if (blocked) issuesOpen.value = true
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  mutationRequestSequence += 1
  releaseMutationOperation()
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
})
</script>

<template>
  <aside class="source-pane" aria-label="冻结原始简历">
    <header class="source-header">
      <div>
        <span class="source-kicker">SOURCE · 冻结原文</span>
        <strong>{{ source?.sourceFilename || '原始简历' }}</strong>
      </div>
      <div class="source-view-switch" role="tablist" aria-label="原文视图">
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'text'"
          :class="{ 'is-active': mode === 'text' }"
          @click="setMode('text')"
        >
          原文
        </button>
        <button
          v-if="source?.sourcePdfAvailable"
          type="button"
          role="tab"
          :aria-selected="mode === 'pdf'"
          :class="{ 'is-active': mode === 'pdf' }"
          @click="setMode('pdf')"
        >
          版式
        </button>
      </div>
    </header>

    <div
      v-if="source"
      class="fidelity-strip"
      :class="source.exportBlocked ? 'is-blocked' : 'is-ready'"
    >
      <span class="fidelity-dot" aria-hidden="true" />
      <div>
        <strong>{{
          source.exportBlocked ? `结构保真：${blockers.length} 项阻断` : '结构保真检查通过'
        }}</strong>
        <small v-if="source.confirmedOmissionCount"
          >已确认省略 {{ source.confirmedOmissionCount }} 段原文</small
        >
        <small v-if="warnings.length">另有 {{ warnings.length }} 项待核对</small>
        <small v-else>导出仍会执行文档与版式检查</small>
      </div>
    </div>

    <div v-if="loading" class="source-loading">正在读取任务冻结原文…</div>
    <ErrorState
      v-else-if="error"
      title="原文暂不可用"
      :description="error"
      action-text="重新加载"
      @action="emit('retry')"
    />

    <template v-else-if="source">
      <div v-show="mode === 'text'" ref="blockRoot" class="source-scroll">
        <div
          v-if="source.fidelityIssues.length"
          ref="issueArea"
          class="fidelity-issues"
          aria-label="结构问题"
        >
          <details :open="issuesOpen" @toggle="handleIssuesToggle">
            <summary>结构问题 · {{ source.fidelityIssues.length }}</summary>
            <ul>
              <li
                v-for="card in issueCards"
                :key="card.key"
                class="issue-card"
                :class="[
                  `is-${card.severity.toLowerCase()}`,
                  { 'is-review-target': card.reviewTarget },
                ]"
                :data-issue-code="card.code"
              >
                <div class="issue-card-heading">
                  <span class="issue-severity">{{
                    card.severity === 'BLOCKER' ? '阻断' : '提醒'
                  }}</span>
                  <strong class="issue-card-title">{{ card.title }}</strong>
                </div>
                <p v-if="card.excerpt" class="issue-card-excerpt">“{{ card.excerpt }}”</p>
                <p class="issue-card-detail">{{ card.detail }}</p>
                <p v-if="card.reasonCopy" class="issue-card-reason">{{ card.reasonCopy }}</p>
                <div
                  v-if="
                    card.restorePlan ||
                    card.omissionPlan ||
                    card.locateTargets.length ||
                    card.canRestartUpload
                  "
                  class="issue-actions"
                >
                  <button
                    v-if="card.restorePlan"
                    type="button"
                    class="issue-action issue-restore-action"
                    :disabled="Boolean(restoreDisabledReason) || mutationOperationKey !== null"
                    @click="performRestore(card.restorePlan)"
                  >
                    {{
                      mutationInFlight(card.restorePlan.key)
                        ? '正在恢复…'
                        : restoreActionLabel(card.restorePlan)
                    }}
                  </button>
                  <button
                    v-if="card.omissionPlan"
                    type="button"
                    class="issue-action issue-omission-action"
                    :disabled="Boolean(omissionDisabledReason) || mutationOperationKey !== null"
                    @click="performOmission(card.omissionPlan)"
                  >
                    {{
                      mutationInFlight(card.omissionPlan.key)
                        ? '正在确认…'
                        : omissionPlanLabel(card.omissionPlan)
                    }}
                  </button>
                  <button
                    v-for="(targetId, index) in card.locateTargets"
                    :key="`${card.key}:locate:${index}`"
                    type="button"
                    class="issue-action issue-locate-action"
                    @click="emit('locateIssueTarget', targetId)"
                  >
                    {{ locateActionLabel(card, index) }}
                  </button>
                  <button
                    v-if="card.canRestartUpload"
                    type="button"
                    class="issue-action issue-restart-action"
                    @click="emit('restartUpload')"
                  >
                    返回首页重新上传
                  </button>
                </div>
                <small v-if="card.restorePlan && restoreDisabledReason" class="issue-disabled-reason">{{
                  restoreDisabledReason
                }}</small>
                <small v-if="card.omissionPlan && omissionDisabledReason" class="issue-disabled-reason">{{
                  omissionDisabledReason
                }}</small>
                <small
                  v-if="mutationError && card.blockIds.includes(mutationError.blockId)"
                  class="mutation-error"
                  role="alert"
                  >{{ mutationError.message }}</small
                >
              </li>
            </ul>
          </details>
        </div>
        <ol v-if="source.sourceBlocks.length" class="source-blocks">
          <li
            v-for="block in source.sourceBlocks"
            :key="block.id"
            class="source-block"
            :class="{ 'is-selected': block.occurrenceIds.some((id) => selectedSet.has(id)) }"
            :data-source-block-id="block.id"
            :data-target-node-id="
              block.reliable && block.targetNodeIds.length === 1
                ? block.targetNodeIds[0]
                : undefined
            "
          >
            <button
              type="button"
              class="source-block-main"
              :disabled="!block.reliable || block.targetNodeIds.length !== 1"
              :aria-label="`${block.reliable ? '在当前简历中定位' : '尚未确认该原文对应位置'}第 ${block.order + 1} 段冻结原文：${block.text}。${mappingLabel(block)}`"
              @click="selectBlock(block.targetNodeIds, block.reliable)"
            >
              <span class="source-order">{{ String(block.order + 1).padStart(2, '0') }}</span>
              <span class="source-text">{{ block.text }}</span>
              <span class="mapping-state">{{ mappingLabel(block) }}</span>
            </button>

            <div
              v-if="
                omissionPlan(block.id)?.leaderBlockId === block.id ||
                restorePlan(block.id)?.leaderBlockId === block.id
              "
              class="omission-review"
            >
              <p
                v-if="omissionPlan(block.id)?.leaderBlockId === block.id"
                :id="`omission-description-${block.order}`"
              >
                {{ omissionDescription(omissionPlan(block.id)!) }}
              </p>
              <p v-else>这段原文未出现在当前简历中，可以恢复到原始位置。</p>
              <div class="review-actions">
                <button
                  v-if="restorePlan(block.id)?.leaderBlockId === block.id"
                  type="button"
                  class="restore-action"
                  :disabled="Boolean(restoreDisabledReason) || mutationOperationKey !== null"
                  :aria-label="`${restoreActionLabel(restorePlan(block.id)!)}：第 ${block.order + 1} 段冻结原文：${block.text}`"
                  @click="performRestore(restorePlan(block.id)!)"
                >
                  {{
                    mutationInFlight(restorePlan(block.id)!.key)
                      ? '正在恢复…'
                      : restoreActionLabel(restorePlan(block.id)!)
                  }}
                </button>
                <button
                  v-if="omissionPlan(block.id)?.leaderBlockId === block.id"
                  type="button"
                  class="omission-action"
                  :class="{ 'is-cancel': omissionPlan(block.id)!.confirmed }"
                  :disabled="Boolean(omissionDisabledReason) || mutationOperationKey !== null"
                  :aria-label="`${omissionActionLabel(omissionPlan(block.id)!)}：第 ${block.order + 1} 段冻结原文：${block.text}`"
                  :aria-describedby="
                    omissionDisabledReason
                      ? `omission-description-${block.order} omission-disabled-${block.order}`
                      : `omission-description-${block.order}`
                  "
                  @click="performOmission(omissionPlan(block.id)!)"
                >
                  {{
                    mutationInFlight(omissionPlan(block.id)!.key)
                      ? omissionPlan(block.id)!.confirmed
                        ? '正在取消…'
                        : '正在确认…'
                      : omissionActionLabel(omissionPlan(block.id)!)
                  }}
                </button>
              </div>
              <small
                v-if="restorePlan(block.id)?.leaderBlockId === block.id && restoreDisabledReason"
                class="omission-disabled-reason"
                >{{ restoreDisabledReason }}</small
              >
              <small
                v-if="omissionPlan(block.id)?.leaderBlockId === block.id && omissionDisabledReason"
                :id="`omission-disabled-${block.order}`"
                class="omission-disabled-reason"
                >{{ omissionDisabledReason }}</small
              >
              <small
                v-if="mutationError?.blockId === block.id"
                class="mutation-error"
                role="alert"
                >{{ mutationError.message }}</small
              >
            </div>
            <small
              v-else-if="
                omissionPlan(block.id)?.projectBlockCount &&
                omissionPlan(block.id)!.projectBlockCount > 1
              "
              class="omission-group-note"
              >与同一项目的其他原文一并处理</small
            >
            <small v-else-if="!block.reliable" class="mapping-help"
              >尚未确认该原文对应位置，已停止自动跳转</small
            >
          </li>
        </ol>
        <div v-else class="source-empty">没有可验证的原文内容。系统不会用模糊匹配伪造定位。</div>
      </div>

      <div v-show="mode === 'pdf'" class="source-pdf">
        <div v-if="pdfLoading" class="source-loading">正在安全加载原始 PDF…</div>
        <ErrorState
          v-else-if="pdfError"
          title="PDF 暂不可用"
          :description="pdfError"
          action-text="重试"
          @action="loadPdf"
        />
        <iframe v-else-if="pdfUrl" :src="pdfUrl" title="原始简历 PDF" />
      </div>
    </template>
  </aside>
</template>

<style scoped>
.source-pane {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid var(--app-border-strong);
  background: #f4f0e8;
}
.source-header {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--app-border-strong);
  background: rgba(255, 255, 255, 0.58);
}
.source-header > div:first-child {
  display: grid;
  min-width: 0;
  gap: 2px;
}
.source-header strong {
  overflow: hidden;
  color: var(--app-text);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-kicker {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.source-view-switch {
  display: flex;
  flex: 0 0 auto;
  padding: 2px;
  border: 1px solid var(--app-border);
  border-radius: 5px;
  background: var(--app-surface);
}
.source-view-switch button {
  min-height: 26px;
  border: 0;
  border-radius: 3px;
  padding: 0 8px;
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}
.source-view-switch button.is-active {
  color: var(--app-text);
  background: var(--app-bg-soft);
  box-shadow: var(--app-shadow-card);
}
.fidelity-strip {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--app-border);
}
.fidelity-strip.is-blocked {
  background: var(--app-danger-soft);
}
.fidelity-strip.is-ready {
  background: var(--app-success-soft);
}
.fidelity-strip > div {
  display: grid;
  gap: 1px;
}
.fidelity-strip strong {
  font-size: 11px;
}
.fidelity-strip small {
  color: var(--app-text-muted);
  font-size: 10px;
}
.fidelity-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--app-success);
}
.is-blocked .fidelity-dot {
  background: var(--app-danger);
}
.source-scroll {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 12px;
  scrollbar-gutter: stable;
}
.fidelity-issues {
  margin-bottom: 12px;
  border: 1px solid var(--app-border);
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.58);
}
.fidelity-issues summary {
  padding: 9px 10px;
  color: var(--app-text-secondary);
  font-size: 11px;
  font-weight: 750;
  cursor: pointer;
}
.fidelity-issues ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0 10px 10px;
  list-style: none;
}
.fidelity-issues li {
  color: var(--app-text-secondary);
  font-size: 11px;
  line-height: 1.5;
}
.fidelity-issues li span {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 4px;
  border-radius: 2px;
  color: var(--app-danger);
  background: var(--app-danger-soft);
  font-size: 9px;
  font-weight: 800;
}
.fidelity-issues li.is-warning span {
  color: var(--app-warning);
  background: var(--app-warning-soft);
}
.issue-card {
  display: grid;
  gap: 6px;
  padding: 8px 9px;
  border: 1px solid var(--app-border);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.5);
}
.issue-card.is-review-target {
  border-color: var(--app-focus);
  background: var(--app-focus-soft);
}
.issue-card-heading {
  display: flex;
  align-items: center;
  gap: 2px;
}
.issue-card-title {
  color: var(--app-text);
  font-size: 11px;
}
.issue-card-excerpt,
.issue-card-detail,
.issue-card-reason {
  margin: 0;
  line-height: 1.6;
}
.issue-card-excerpt {
  color: var(--app-text-secondary);
  font-size: 10px;
}
.issue-card-reason {
  color: var(--app-text-secondary);
}
.issue-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.issue-action {
  min-height: 26px;
  border: 1px solid var(--app-primary);
  border-radius: 4px;
  padding: 0 8px;
  color: var(--app-primary-active);
  font: inherit;
  font-size: 10px;
  font-weight: 750;
  background: var(--app-surface);
  cursor: pointer;
}
.issue-action:hover:not(:disabled),
.issue-action:focus-visible:not(:disabled) {
  color: #fff;
  background: var(--app-primary);
}
.issue-action:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.issue-restart-action {
  border-color: var(--app-border-strong);
  color: var(--app-text-secondary);
}
.issue-disabled-reason {
  color: var(--app-text-muted);
  font-size: 9px;
  line-height: 1.5;
}
.mutation-error {
  color: var(--app-danger);
  font-size: 9px;
  line-height: 1.5;
}
.source-blocks {
  display: grid;
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.source-block {
  border-left: 2px solid transparent;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.43);
}
.source-block-main {
  display: grid;
  width: 100%;
  grid-template-columns: 25px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
  border: 1px solid transparent;
  border-radius: 4px;
  padding: 8px;
  color: var(--app-text-secondary);
  text-align: left;
  background: transparent;
  cursor: pointer;
}
.source-block-main:hover:not(:disabled),
.source-block-main:focus-visible {
  border-color: var(--app-primary);
  background: var(--app-surface);
}
.source-block-main:disabled {
  cursor: not-allowed;
}
.source-block.is-selected {
  border-left-color: var(--app-focus);
}
.source-block.is-selected .source-block-main {
  background: var(--app-focus-soft);
}
.source-order {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 9px;
  line-height: 1.8;
}
.source-text {
  white-space: pre-wrap;
  font-size: 12px;
  line-height: 1.65;
}
.mapping-state {
  color: var(--app-text-muted);
  font-size: 9px;
  font-weight: 750;
  white-space: nowrap;
}
.mapping-help,
.omission-group-note {
  display: block;
  padding: 3px 8px 7px 41px;
  color: var(--app-text-muted);
  font-size: 9px;
}
.omission-review {
  display: grid;
  gap: 7px;
  margin: 0 8px 8px 41px;
  padding: 8px 9px;
  border-left: 2px solid var(--app-warning);
  background: rgba(255, 255, 255, 0.48);
}
.omission-review p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 10px;
  line-height: 1.55;
}
.review-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.restore-action,
.omission-action {
  justify-self: start;
  min-height: 28px;
  border: 1px solid var(--app-primary);
  border-radius: 4px;
  padding: 0 9px;
  color: var(--app-primary-active);
  font: inherit;
  font-size: 10px;
  font-weight: 750;
  background: var(--app-surface);
  cursor: pointer;
}
.restore-action:hover:not(:disabled),
.restore-action:focus-visible:not(:disabled),
.omission-action:hover:not(:disabled),
.omission-action:focus-visible:not(:disabled) {
  color: #fff;
  background: var(--app-primary);
}
.restore-action:disabled,
.omission-action:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.omission-action.is-cancel {
  border-color: var(--app-border-strong);
  color: var(--app-text-secondary);
}
.omission-disabled-reason {
  color: var(--app-text-muted);
  font-size: 9px;
  line-height: 1.5;
}
.source-loading,
.source-empty {
  padding: 24px 16px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.7;
}
.source-pdf {
  min-height: 0;
  flex: 1;
}
.source-pdf iframe {
  width: 100%;
  height: 100%;
  border: 0;
  background: #777;
}
@media (max-width: 1119px) {
  .source-pane {
    border-right: 0;
  }
}
</style>
