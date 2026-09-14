<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getWorkspaceSourcePdf } from '@/api/workspace'
import ErrorState from '@/components/common/ErrorState.vue'
import type { WorkspaceSourceReference } from '@/types/workspace'

const props = defineProps<{
  optimizationTaskId: number
  source: WorkspaceSourceReference | null
  loading: boolean
  error: string | null
  selectedOccurrenceIds?: string[]
}>()

const emit = defineEmits<{
  retry: []
  focusTarget: [targetNodeId: string]
}>()

const mode = ref<'text' | 'pdf'>('text')
const pdfUrl = ref<string | null>(null)
const pdfLoading = ref(false)
const pdfError = ref<string | null>(null)
const blockRoot = ref<HTMLElement | null>(null)

const blockers = computed(() => props.source?.fidelityIssues.filter((issue) => issue.severity === 'BLOCKER') ?? [])
const warnings = computed(() => props.source?.fidelityIssues.filter((issue) => issue.severity === 'WARNING') ?? [])
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
        <button type="button" role="tab" :aria-selected="mode === 'text'" :class="{ 'is-active': mode === 'text' }" @click="setMode('text')">原文</button>
        <button v-if="source?.sourcePdfAvailable" type="button" role="tab" :aria-selected="mode === 'pdf'" :class="{ 'is-active': mode === 'pdf' }" @click="setMode('pdf')">版式</button>
      </div>
    </header>

    <div v-if="source" class="fidelity-strip" :class="source.exportBlocked ? 'is-blocked' : 'is-ready'">
      <span class="fidelity-dot" aria-hidden="true" />
      <div>
        <strong>{{ source.exportBlocked ? `结构保真：${blockers.length} 项阻断` : '结构保真检查通过' }}</strong>
        <small v-if="warnings.length">另有 {{ warnings.length }} 项待核对</small>
        <small v-else>导出仍会执行文档与版式检查</small>
      </div>
    </div>

    <div v-if="loading" class="source-loading">正在读取任务冻结原文…</div>
    <ErrorState v-else-if="error" title="原文暂不可用" :description="error" action-text="重新加载" @action="emit('retry')" />

    <template v-else-if="source">
      <div v-show="mode === 'text'" ref="blockRoot" class="source-scroll">
        <div v-if="source.fidelityIssues.length" class="fidelity-issues" aria-label="结构保真问题">
          <details :open="source.exportBlocked">
            <summary>结构核对清单 · {{ source.fidelityIssues.length }}</summary>
            <ul>
              <li v-for="issue in source.fidelityIssues" :key="`${issue.code}:${issue.targetNodeIds.join(',')}`" :class="`is-${issue.severity.toLowerCase()}`">
                <span>{{ issue.severity === 'BLOCKER' ? '阻断' : '提醒' }}</span>{{ issue.message }}
              </li>
            </ul>
          </details>
        </div>
        <ol v-if="source.sourceBlocks.length" class="source-blocks">
          <li
            v-for="block in source.sourceBlocks"
            :key="block.id"
            class="source-block"
            :class="[
              `is-${block.status.toLowerCase()}`,
              { 'is-selected': block.occurrenceIds.some((id) => selectedSet.has(id)) },
            ]"
          >
            <button
              type="button"
              :disabled="!block.reliable || block.targetNodeIds.length !== 1"
              :aria-label="block.reliable ? '在当前简历中定位' : '尚未确认该原文对应位置'"
              @click="selectBlock(block.targetNodeIds, block.reliable)"
            >
              <span class="source-order">{{ String(block.order + 1).padStart(2, '0') }}</span>
              <span class="source-text">{{ block.text }}</span>
              <span class="mapping-state">{{ block.status }}</span>
            </button>
            <small v-if="!block.reliable">尚未确认该原文对应位置，已停止自动跳转</small>
          </li>
        </ol>
        <div v-else class="source-empty">没有可验证的原文 blocks。系统不会用模糊匹配伪造定位。</div>
      </div>

      <div v-show="mode === 'pdf'" class="source-pdf">
        <div v-if="pdfLoading" class="source-loading">正在安全加载原始 PDF…</div>
        <ErrorState v-else-if="pdfError" title="PDF 暂不可用" :description="pdfError" action-text="重试" @action="loadPdf" />
        <iframe v-else-if="pdfUrl" :src="pdfUrl" title="原始简历 PDF" />
      </div>
    </template>
  </aside>
</template>

<style scoped>
.source-pane { display:flex; min-width:0; min-height:0; flex-direction:column; overflow:hidden; border-right:1px solid var(--app-border-strong); background:#f4f0e8; }
.source-header { display:flex; min-height:58px; align-items:center; justify-content:space-between; gap:12px; padding:9px 14px; border-bottom:1px solid var(--app-border-strong); background:rgba(255,255,255,.58); }
.source-header > div:first-child { display:grid; min-width:0; gap:2px; }
.source-header strong { overflow:hidden; color:var(--app-text); font-size:13px; text-overflow:ellipsis; white-space:nowrap; }
.source-kicker { color:var(--app-text-muted); font-family:var(--app-font-mono); font-size:10px; font-weight:800; letter-spacing:.08em; }
.source-view-switch { display:flex; flex:0 0 auto; padding:2px; border:1px solid var(--app-border); border-radius:5px; background:var(--app-surface); }
.source-view-switch button { min-height:26px; border:0; border-radius:3px; padding:0 8px; color:var(--app-text-muted); font-size:11px; font-weight:700; background:transparent; cursor:pointer; }
.source-view-switch button.is-active { color:var(--app-text); background:var(--app-bg-soft); box-shadow:var(--app-shadow-card); }
.fidelity-strip { display:flex; align-items:center; gap:9px; padding:8px 14px; border-bottom:1px solid var(--app-border); }
.fidelity-strip.is-blocked { background:var(--app-danger-soft); }
.fidelity-strip.is-ready { background:var(--app-success-soft); }
.fidelity-strip > div { display:grid; gap:1px; }
.fidelity-strip strong { font-size:11px; }
.fidelity-strip small { color:var(--app-text-muted); font-size:10px; }
.fidelity-dot { width:7px; height:7px; flex:0 0 auto; border-radius:50%; background:var(--app-success); }
.is-blocked .fidelity-dot { background:var(--app-danger); }
.source-scroll { min-height:0; flex:1; overflow:auto; padding:12px; scrollbar-gutter:stable; }
.fidelity-issues { margin-bottom:12px; border:1px solid var(--app-border); border-radius:5px; background:rgba(255,255,255,.58); }
.fidelity-issues summary { padding:9px 10px; color:var(--app-text-secondary); font-size:11px; font-weight:750; cursor:pointer; }
.fidelity-issues ul { display:grid; gap:6px; margin:0; padding:0 10px 10px; list-style:none; }
.fidelity-issues li { color:var(--app-text-secondary); font-size:11px; line-height:1.5; }
.fidelity-issues li span { display:inline-block; margin-right:6px; padding:1px 4px; border-radius:2px; color:var(--app-danger); background:var(--app-danger-soft); font-size:9px; font-weight:800; }
.fidelity-issues li.is-warning span { color:var(--app-warning); background:var(--app-warning-soft); }
.source-blocks { display:grid; gap:7px; margin:0; padding:0; list-style:none; }
.source-block { border-left:2px solid transparent; }
.source-block button { display:grid; width:100%; grid-template-columns:25px minmax(0,1fr) auto; gap:8px; align-items:start; border:1px solid transparent; border-radius:4px; padding:8px; color:var(--app-text-secondary); text-align:left; background:rgba(255,255,255,.43); cursor:pointer; }
.source-block button:hover:not(:disabled), .source-block button:focus-visible { border-color:var(--app-primary); background:var(--app-surface); }
.source-block button:disabled { cursor:not-allowed; }
.source-block.is-selected { border-left-color:var(--app-focus); }
.source-block.is-selected button { background:var(--app-focus-soft); }
.source-order { color:var(--app-text-muted); font-family:var(--app-font-mono); font-size:9px; line-height:1.8; }
.source-text { white-space:pre-wrap; font-size:12px; line-height:1.65; }
.mapping-state { color:var(--app-text-muted); font-family:var(--app-font-mono); font-size:8px; font-weight:800; }
.source-block small { display:block; padding:3px 8px 0 41px; color:var(--app-danger); font-size:9px; }
.source-loading, .source-empty { padding:24px 16px; color:var(--app-text-muted); font-size:12px; line-height:1.7; }
.source-pdf { min-height:0; flex:1; }
.source-pdf iframe { width:100%; height:100%; border:0; background:#777; }
@media (max-width:1119px) { .source-pane { border-right:0; } }
</style>
