<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  confirmWorkspaceSourceOmissions,
  getWorkspaceSourcePdf,
  unconfirmWorkspaceSourceOmissions,
} from '@/api/workspace'
import ErrorState from '@/components/common/ErrorState.vue'
import type {
  WorkspaceSaveResult,
  WorkspaceSourceBlock,
  WorkspaceSourceReference,
} from '@/types/workspace'

const props = defineProps<{
  optimizationTaskId: number
  source: WorkspaceSourceReference | null
  loading: boolean
  error: string | null
  selectedOccurrenceIds?: string[]
  /** 有本地修改、正在保存或 SOURCE revision 尚未同步时，由 Workspace 禁止省略操作。 */
  omissionDisabledReason?: string | null
}>()

const emit = defineEmits<{
  retry: []
  focusTarget: [targetNodeId: string]
  omissionSaved: [expectedRevision: number, result: WorkspaceSaveResult, confirmed: boolean]
  omissionConcurrent: []
  omissionBusy: [busy: boolean]
}>()

const mode = ref<'text' | 'pdf'>('text')
const pdfUrl = ref<string | null>(null)
const pdfLoading = ref(false)
const pdfError = ref<string | null>(null)
const blockRoot = ref<HTMLElement | null>(null)
const omissionOperationKey = ref<string | null>(null)
const omissionError = ref<{ blockId: string; message: string } | null>(null)
let omissionRequestSequence = 0

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
const omissionActionLabel = (plan: OmissionActionPlan) => {
  if (plan.projectBlockCount > 1) {
    return plan.confirmed
      ? `取消省略此项目对应的 ${plan.projectBlockCount} 段原文`
      : `确认省略此项目对应的 ${plan.projectBlockCount} 段原文`
  }
  return plan.confirmed ? '取消省略' : '确认省略'
}

const omissionDescription = (plan: OmissionActionPlan) => {
  if (plan.projectBlockCount > 1) {
    return plan.confirmed
      ? '这些项目原文已作为有意省略处理；取消后会重新进入导出检查。'
      : '这些内容属于同一个项目，且均未出现在当前简历中。请只在确认是有意删除时操作。'
  }
  return plan.confirmed
    ? '这段原文已作为有意省略处理；取消后会重新进入导出检查。'
    : '这段原文未出现在当前简历中。请只在确认是有意删除时操作。'
}

type ApiErrorLike = Error & { code?: number }

const performOmission = async (plan: OmissionActionPlan) => {
  const source = props.source
  if (
    !source ||
    props.omissionDisabledReason ||
    omissionOperationKey.value !== null ||
    plan.sourceOccurrenceIds.length === 0
  ) {
    return
  }

  const expectedRevision = source.targetRevision
  const taskAtRequest = props.optimizationTaskId
  const requestSequence = ++omissionRequestSequence
  omissionOperationKey.value = plan.key
  omissionError.value = null
  emit('omissionBusy', true)
  try {
    const request = {
      expectedRevision,
      // 只提交 omissionEligible blocks 暴露的 occurrence IDs；不扩张服务端授权范围。
      sourceOccurrenceIds: plan.sourceOccurrenceIds,
    }
    const result = plan.confirmed
      ? await unconfirmWorkspaceSourceOmissions(taskAtRequest, request)
      : await confirmWorkspaceSourceOmissions(taskAtRequest, request)
    if (requestSequence !== omissionRequestSequence || taskAtRequest !== props.optimizationTaskId) {
      return
    }
    if (!result.saved || result.conflict) {
      omissionError.value = {
        blockId: plan.leaderBlockId,
        message: '当前简历已有更新，本次操作未生效。请刷新后重试。',
      }
      emit('omissionConcurrent')
      return
    }
    emit('omissionSaved', expectedRevision, result, !plan.confirmed)
  } catch (error) {
    if (requestSequence !== omissionRequestSequence) return
    const concurrent = (error as ApiErrorLike)?.code === 409
    omissionError.value = {
      blockId: plan.leaderBlockId,
      message: concurrent
        ? '当前简历已有更新，本次操作未生效。请刷新后重试。'
        : error instanceof Error
          ? error.message
          : '省略状态更新失败，请稍后重试。',
    }
    if (concurrent) emit('omissionConcurrent')
  } finally {
    // While a request is in flight the action key remains occupied, even if a revision watcher
    // invalidates its response. Clearing here prevents duplicate CAS operations and busy leaks.
    omissionOperationKey.value = null
    emit('omissionBusy', false)
  }
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
  () => props.optimizationTaskId,
  () => {
    omissionRequestSequence += 1
    omissionError.value = null
  },
)

watch(
  () => props.source?.targetRevision,
  () => {
    // Invalidate any response tied to the old revision, but keep a CAS-conflict
    // explanation visible after the parent adopts and reloads the winning version.
    omissionRequestSequence += 1
  },
)

onBeforeUnmount(() => {
  omissionRequestSequence += 1
  if (omissionOperationKey.value !== null) emit('omissionBusy', false)
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
        <div v-if="source.fidelityIssues.length" class="fidelity-issues" aria-label="结构保真问题">
          <details :open="source.exportBlocked">
            <summary>结构核对清单 · {{ source.fidelityIssues.length }}</summary>
            <ul>
              <li
                v-for="issue in source.fidelityIssues"
                :key="`${issue.code}:${issue.targetNodeIds.join(',')}`"
                :class="`is-${issue.severity.toLowerCase()}`"
              >
                <span>{{ issue.severity === 'BLOCKER' ? '阻断' : '提醒' }}</span
                >{{ issue.message }}
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
              :aria-label="`${block.reliable ? '在当前简历中定位' : '尚未确认该原文对应位置'}：${block.text}。${mappingLabel(block)}`"
              @click="selectBlock(block.targetNodeIds, block.reliable)"
            >
              <span class="source-order">{{ String(block.order + 1).padStart(2, '0') }}</span>
              <span class="source-text">{{ block.text }}</span>
              <span class="mapping-state">{{ mappingLabel(block) }}</span>
            </button>

            <div v-if="omissionPlan(block.id)?.leaderBlockId === block.id" class="omission-review">
              <p :id="`omission-description-${block.order}`">
                {{ omissionDescription(omissionPlan(block.id)!) }}
              </p>
              <button
                type="button"
                class="omission-action"
                :class="{ 'is-cancel': omissionPlan(block.id)!.confirmed }"
                :disabled="Boolean(omissionDisabledReason) || omissionOperationKey !== null"
                :aria-label="`${omissionActionLabel(omissionPlan(block.id)!)}：${block.text}`"
                :aria-describedby="
                  omissionDisabledReason
                    ? `omission-description-${block.order} omission-disabled-${block.order}`
                    : `omission-description-${block.order}`
                "
                @click="performOmission(omissionPlan(block.id)!)"
              >
                {{
                  omissionOperationKey === omissionPlan(block.id)!.key
                    ? omissionPlan(block.id)!.confirmed
                      ? '正在取消…'
                      : '正在确认…'
                    : omissionActionLabel(omissionPlan(block.id)!)
                }}
              </button>
              <small
                v-if="omissionDisabledReason"
                :id="`omission-disabled-${block.order}`"
                class="omission-disabled-reason"
                >{{ omissionDisabledReason }}</small
              >
              <small
                v-if="omissionError?.blockId === block.id"
                class="omission-error"
                role="alert"
                >{{ omissionError.message }}</small
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
.omission-action:hover:not(:disabled),
.omission-action:focus-visible:not(:disabled) {
  color: #fff;
  background: var(--app-primary);
}
.omission-action.is-cancel {
  border-color: var(--app-border-strong);
  color: var(--app-text-secondary);
}
.omission-action:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.omission-disabled-reason,
.omission-error {
  color: var(--app-text-muted);
  font-size: 9px;
  line-height: 1.5;
}
.omission-error {
  color: var(--app-danger);
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
