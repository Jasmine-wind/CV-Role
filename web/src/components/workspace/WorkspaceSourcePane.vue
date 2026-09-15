<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getWorkspaceSourcePdf } from '@/api/workspace'
import ErrorState from '@/components/common/ErrorState.vue'
import {
  buildOmissionPlansByBlockId,
  buildRestorePlansByBlockId,
  type OmissionActionPlan,
  type RestoreActionPlan,
} from '@/components/workspace/sourceMutationPlans'
import type {
  WorkspaceSourceBlock,
  WorkspaceSourceReference,
} from '@/types/workspace'

/**
 * SourcePane 只做两件事：展示服务端 verdict，emit 用户意图。
 * 所有写操作（保存、CAS、API 调用、并发处理）由 WorkspacePanel 编排。
 */
const props = defineProps<{
  optimizationTaskId: number
  source: WorkspaceSourceReference | null
  loading: boolean
  error: string | null
  selectedOccurrenceIds?: string[]
  /** failed / conflict / busy 时的统一门禁说明；dirty 不再阻止操作。 */
  sourceMutationDisabledReason?: string | null
  /** SOURCE mutation 在途：按钮显示进度并整体禁用。 */
  mutationBusy?: boolean
  /** WorkspacePanel 权威的 mutation 失败说明，仅用于就地展示。 */
  mutationError?: { blockId: string; message: string } | null
  /** Preview → “查看并处理”递增该 key：打开结构问题区域并定位第一个 blocker。 */
  reviewRequestKey?: number
}>()

const emit = defineEmits<{
  retry: []
  focusTarget: [targetNodeId: string]
  restoreRequested: [blockId: string]
  omissionRequested: [blockId: string, confirm: boolean]
  locateIssueTarget: [targetNodeId: string]
}>()

const mode = ref<'text' | 'pdf'>('text')
const pdfUrl = ref<string | null>(null)
const pdfLoading = ref(false)
const pdfError = ref<string | null>(null)
const blockRoot = ref<HTMLElement | null>(null)
const issueArea = ref<HTMLElement | null>(null)
// 建议检查默认折叠：主要任务永远是右边改简历，问题面板不抢视觉中心。
const issuesOpen = ref(false)
const openIssueGroups = ref<string[]>([])
const showAllGroups = ref(false)
const MAX_VISIBLE_GROUPS = 3
const mutationError = computed(() => props.mutationError ?? null)
// 纯展示状态：点击后立即显示“正在恢复/确认…”，mutationBusy 结束后清空。
const pendingMutationKey = ref<string | null>(null)

const issueCount = computed(() => props.source?.fidelityIssues.length ?? 0)
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

/** 内部 lineage 枚举只能在这里转换成用户文案，不能直接进入模板。正常映射成功的长句不再占位。 */
const mappingLabel = (block: WorkspaceSourceBlock) => {
  if (block.omissionConfirmed) return '已确认省略'
  switch (block.status) {
    case 'EXACT':
      return blockTextChanged(block) ? '内容已修改' : ''
    case 'MERGED':
      return blockTextChanged(block) ? '合并来源 · 内容已修改' : '合并来源'
    case 'SPLIT':
      return blockTextChanged(block) ? '拆分来源 · 内容已修改' : '拆分来源'
    case 'UNMAPPED':
      return '需要处理'
    case 'AMBIGUOUS':
      return '需要核对'
    default:
      return '需要核对'
  }
}

const blockAriaLabel = (block: WorkspaceSourceBlock) => {
  const prefix = block.reliable ? '在当前简历中定位' : '尚未确认该原文对应位置'
  const state = mappingLabel(block)
  return `${prefix}第 ${block.order + 1} 段冻结原文：${block.text}${state ? `。${state}` : ''}`
}

const omissionPlansByBlockId = computed(() => buildOmissionPlansByBlockId(props.source))

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

const restorePlansByBlockId = computed(() => buildRestorePlansByBlockId(props.source))

const restorePlan = (blockId: string) => restorePlansByBlockId.value.get(blockId) ?? null

const restoreActionLabel = (plan: RestoreActionPlan) =>
  plan.scope === 'PROJECT_ENTRY' ? '恢复整个项目' : '恢复原文'

const ISSUE_COPY: Record<string, { title: string; detail: string }> = {
  SOURCE_CONTENT_UNMAPPED: {
    title: '原文中有内容未进入当前简历',
    detail:
      '这段内容存在于原始简历，但当前简历中找不到。你可以恢复原文或确认省略；什么都不做也不影响继续编辑、预览或导出。',
  },
  PROJECT_BOUNDARY_LOST: {
    title: '有一个原始项目没有完整对应到当前简历',
    detail:
      '建议检查项目内容是否被删除或合并到了其它项目。可以恢复整个项目或确认省略；不处理也不影响继续编辑、预览或导出。',
  },
  AMBIGUOUS_MAPPING: {
    title: '原文对应关系不明确',
    detail:
      '系统无法确认这段内容来自原始简历的哪个位置，建议你对照原始 PDF 检查。不影响继续编辑、预览或导出。',
  },
  DUPLICATE_MAPPING: {
    title: '可能存在重复内容',
    detail: '同一段原文似乎出现在多个位置，建议检查是否需要删除重复内容。不影响继续编辑、预览或导出。',
  },
  SOURCE_MANIFEST_INVALID: {
    title: '原文定位信息不可用',
    detail: '当前简历仍可正常编辑和导出，但部分“查看原文 / 自动恢复”能力可能不可用。',
  },
  SOURCE_MANIFEST_UNAVAILABLE: {
    title: '原文定位信息不可用',
    detail: '当前简历仍可正常编辑和导出，但部分“查看原文 / 自动恢复”能力可能不可用。',
  },
  CONFIRMED_OMISSION_INVALID: {
    title: '原文定位信息不可用',
    detail: '当前简历仍可正常编辑和导出，但部分“查看原文 / 自动恢复”能力可能不可用。',
  },
  SECTION_HEADING_LOST: {
    title: '章节标题缺失',
    detail: '章节标题为空但内容仍在。可以补回标题或删除残留内容；不影响继续编辑、预览或导出。',
  },
  DUPLICATE_CONTACT_SUSPECTED: {
    title: '检测到疑似重复联系方式',
    detail: '建议检查联系方式列表，删除重复的一项。不影响继续编辑、预览或导出。',
  },
  SOURCE_CONTENT_SPLIT: {
    title: '一段原文被拆分到多个位置',
    detail: '可以核对拆分边界是否合理；不影响继续编辑、预览或导出。',
  },
  TARGET_CONTENT_UNMAPPED: {
    title: '当前内容没有可靠的原文定位',
    detail: '建议核对这段内容是否属于本人的真实经历；不影响继续编辑、预览或导出。',
  },
  DUPLICATE_DATE_SUSPECTED: {
    title: '多个位置出现相同日期',
    detail: '建议确认不是结构恢复造成的重复；不影响继续编辑、预览或导出。',
  },
}

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
  reviewTarget: boolean
}

type IssueGroup = {
  code: string
  title: string
  count: number
  cards: IssueCard[]
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
      reviewTarget: boundaryBlocks.some((block) =>
        block.occurrenceIds.some((id) => selectedSet.value.has(id)),
      ),
    }
  })
})

/** 按 issue.code 聚合，避免给用户刷屏：同一类问题只列一条，展开后才看具体项。 */
const issueGroups = computed<IssueGroup[]>(() => {
  const groups = new Map<string, IssueGroup>()
  for (const card of issueCards.value) {
    const existing = groups.get(card.code)
    if (existing) {
      existing.count += 1
      existing.cards.push(card)
    } else {
      groups.set(card.code, { code: card.code, title: card.title, count: 1, cards: [card] })
    }
  }
  return [...groups.values()]
})
const visibleIssueGroups = computed(() =>
  showAllGroups.value ? issueGroups.value : issueGroups.value.slice(0, MAX_VISIBLE_GROUPS),
)
const issueGroupOpen = (code: string) => openIssueGroups.value.includes(code)
const toggleIssueGroup = (code: string) => {
  openIssueGroups.value = issueGroupOpen(code)
    ? openIssueGroups.value.filter((item) => item !== code)
    : [...openIssueGroups.value, code]
}

const locateActionLabel = (card: IssueCard, index: number) =>
  card.code === 'DUPLICATE_MAPPING' && card.locateTargets.length > 1
    ? `定位第 ${index + 1} 处`
    : '定位问题内容'

// 写操作全部交给 WorkspacePanel：这里只把点击变成意图，并做纯展示的“进行中”反馈。
const requestRestore = (plan: RestoreActionPlan) => {
  pendingMutationKey.value = plan.key
  emit('restoreRequested', plan.leaderBlockId)
}

const requestOmission = (plan: OmissionActionPlan) => {
  pendingMutationKey.value = plan.key
  emit('omissionRequested', plan.leaderBlockId, !plan.confirmed)
}

const mutationInFlight = (key: string) =>
  Boolean(props.mutationBusy) && pendingMutationKey.value === key

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

/**
 * 展开包含首个待检查项的聚合组；没有命中时展开第一组。
 * Pane 会随 edit/preview 切换重建，因此挂载时带 reviewRequestKey 的场景也要生效。
 */
const applyReviewRequest = () => {
  issuesOpen.value = true
  const groups = issueGroups.value
  if (!groups.length) return
  // 同一段原文可能同时命中多个 issue code（如项目边界 + 未映射），全部展开。
  const matched = groups.filter((group) => group.cards.some((card) => card.reviewTarget))
  const next = new Set(openIssueGroups.value)
  for (const group of matched.length ? matched : [groups[0]!]) {
    next.add(group.code)
  }
  openIssueGroups.value = [...next]
}
const appliedReviewRequestKey = ref(0)
const maybeApplyReviewRequest = () => {
  const requested = props.reviewRequestKey ?? 0
  if (requested <= appliedReviewRequestKey.value || !props.source) return false
  appliedReviewRequestKey.value = requested
  applyReviewRequest()
  return true
}

watch(
  () => [props.reviewRequestKey, props.source] as const,
  async () => {
    await nextTick()
    if (!maybeApplyReviewRequest()) return
    await nextTick()
    // 选中 block 的滚动仍由现有 semantic anchor 负责；没有可定位 block 时回到结构问题顶部。
    const hasSelectedBlock = props.selectedOccurrenceIds?.length
      ? Boolean(blockRoot.value?.querySelector('.source-block.is-selected'))
      : false
    if (!hasSelectedBlock) {
      issueArea.value?.scrollIntoView?.({ block: 'start', behavior: 'auto' })
    }
  },
  { immediate: true },
)

const handleIssuesToggle = (event: Event) => {
  issuesOpen.value = (event.target as HTMLDetailsElement).open
}

watch(
  () => props.mutationBusy,
  (busy) => {
    if (!busy) pendingMutationKey.value = null
  },
)

const optimizationTaskIdWatcher = () => {
  pendingMutationKey.value = null
  openIssueGroups.value = []
  showAllGroups.value = false
  appliedReviewRequestKey.value = 0
}

watch(
  () => props.optimizationTaskId,
  optimizationTaskIdWatcher,
)

onBeforeUnmount(() => {
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
          提取原文
        </button>
        <button
          v-if="source?.sourcePdfAvailable"
          type="button"
          role="tab"
          :aria-selected="mode === 'pdf'"
          :class="{ 'is-active': mode === 'pdf' }"
          @click="setMode('pdf')"
        >
          原始 PDF
        </button>
      </div>
    </header>

    <div
      v-if="source"
      class="fidelity-strip"
      :class="issueCount ? 'is-advisory' : 'is-ready'"
    >
      <span class="fidelity-dot" aria-hidden="true" />
      <div>
        <strong>{{ issueCount ? `发现 ${issueCount} 项建议检查` : '内容检查通过' }}</strong>
        <small v-if="issueCount">不影响继续编辑、预览或导出，你可以根据需要处理。</small>
        <small v-if="source.confirmedOmissionCount"
          >已确认省略 {{ source.confirmedOmissionCount }} 段原文</small
        >
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
          aria-label="建议检查"
        >
          <details :open="issuesOpen" @toggle="handleIssuesToggle">
            <summary>
              发现 {{ source.fidelityIssues.length }} 项建议检查
              <span v-if="!issuesOpen" class="issues-summary-hint">展开</span>
            </summary>
            <p class="issues-note">不影响继续编辑、预览或导出，你可以根据需要处理。</p>
            <ul class="issue-groups">
              <li v-for="group in visibleIssueGroups" :key="group.code" class="issue-group">
                <button
                  type="button"
                  class="issue-group-toggle"
                  :aria-expanded="issueGroupOpen(group.code)"
                  @click="toggleIssueGroup(group.code)"
                >
                  <strong>{{ group.title }}</strong>
                  <span class="issue-group-count">· {{ group.count }} 处</span>
                </button>
                <ul v-show="issueGroupOpen(group.code)" class="issue-cards">
                  <li
                    v-for="card in group.cards"
                    :key="card.key"
                    class="issue-card"
                    :class="[
                      `is-${card.severity.toLowerCase()}`,
                      { 'is-review-target': card.reviewTarget },
                    ]"
                    :data-issue-code="card.code"
                  >
                    <p v-if="card.excerpt" class="issue-card-excerpt">“{{ card.excerpt }}”</p>
                    <p class="issue-card-detail">{{ card.detail }}</p>
                    <p v-if="card.reasonCopy" class="issue-card-reason">{{ card.reasonCopy }}</p>
                    <div
                      v-if="card.restorePlan || card.omissionPlan || card.locateTargets.length"
                      class="issue-actions"
                    >
                      <button
                        v-if="card.restorePlan"
                        type="button"
                        class="issue-action issue-restore-action"
                        :disabled="Boolean(sourceMutationDisabledReason)"
                        @click="requestRestore(card.restorePlan)"
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
                        :disabled="Boolean(sourceMutationDisabledReason)"
                        @click="requestOmission(card.omissionPlan)"
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
                    </div>
                    <small
                      v-if="(card.restorePlan || card.omissionPlan) && sourceMutationDisabledReason"
                      class="issue-disabled-reason"
                      >{{ sourceMutationDisabledReason }}</small
                    >
                    <small
                      v-if="mutationError && card.blockIds.includes(mutationError.blockId)"
                      class="mutation-error"
                      role="alert"
                      >{{ mutationError.message }}</small
                    >
                  </li>
                </ul>
              </li>
            </ul>
            <button
              v-if="issueGroups.length > MAX_VISIBLE_GROUPS && !showAllGroups"
              type="button"
              class="issues-show-all"
              @click="showAllGroups = true"
            >
              查看全部（共 {{ source.fidelityIssues.length }} 项）
            </button>
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
              :aria-label="blockAriaLabel(block)"
              @click="selectBlock(block.targetNodeIds, block.reliable)"
            >
              <span class="source-order">{{ String(block.order + 1).padStart(2, '0') }}</span>
              <span class="source-text">{{ block.text }}</span>
              <span v-if="mappingLabel(block)" class="mapping-state">{{ mappingLabel(block) }}</span>
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
                  :disabled="Boolean(sourceMutationDisabledReason)"
                  :aria-label="`${restoreActionLabel(restorePlan(block.id)!)}：第 ${block.order + 1} 段冻结原文：${block.text}`"
                  @click="requestRestore(restorePlan(block.id)!)"
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
                  :disabled="Boolean(sourceMutationDisabledReason)"
                  :aria-label="`${omissionActionLabel(omissionPlan(block.id)!)}：第 ${block.order + 1} 段冻结原文：${block.text}`"
                  :aria-describedby="
                    sourceMutationDisabledReason
                      ? `omission-description-${block.order} omission-disabled-${block.order}`
                      : `omission-description-${block.order}`
                  "
                  @click="requestOmission(omissionPlan(block.id)!)"
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
                v-if="
                  (restorePlan(block.id)?.leaderBlockId === block.id ||
                    omissionPlan(block.id)?.leaderBlockId === block.id) &&
                  sourceMutationDisabledReason
                "
                :id="`omission-disabled-${block.order}`"
                class="omission-disabled-reason"
                >{{ sourceMutationDisabledReason }}</small
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
.fidelity-strip.is-advisory {
  background: var(--app-warning-soft);
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
.is-advisory .fidelity-dot {
  background: var(--app-warning);
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
.issues-summary-hint {
  margin-left: 6px;
  color: var(--app-primary);
  font-weight: 700;
}
.issues-note {
  margin: 0;
  padding: 0 10px 8px;
  color: var(--app-text-muted);
  font-size: 10px;
  line-height: 1.55;
}
.issue-groups {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 0 10px 10px;
  list-style: none;
}
.issue-groups li {
  color: var(--app-text-secondary);
  font-size: 11px;
  line-height: 1.5;
}
.issue-group {
  display: grid;
  gap: 6px;
  border: 1px solid var(--app-border);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.5);
}
.issue-group-toggle {
  display: flex;
  align-items: baseline;
  gap: 4px;
  width: 100%;
  border: 0;
  border-radius: 4px;
  padding: 8px 9px;
  color: var(--app-text);
  font: inherit;
  font-size: 11px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}
.issue-group-toggle:hover,
.issue-group-toggle:focus-visible {
  background: var(--app-surface);
}
.issue-group-toggle strong {
  font-size: 11px;
}
.issue-group-count {
  color: var(--app-text-muted);
  font-size: 10px;
  font-weight: 750;
  white-space: nowrap;
}
.issue-cards {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0 9px 9px;
  list-style: none;
}
.issues-show-all {
  justify-self: start;
  margin: 0 10px 10px;
  border: 1px solid var(--app-border-strong);
  border-radius: 4px;
  padding: 5px 9px;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: 10px;
  font-weight: 750;
  background: var(--app-surface);
  cursor: pointer;
}
.issues-show-all:hover,
.issues-show-all:focus-visible {
  border-color: var(--app-primary);
  color: var(--app-primary-active);
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
