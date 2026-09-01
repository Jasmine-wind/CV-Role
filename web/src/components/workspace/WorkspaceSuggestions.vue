<script setup lang="ts">
import { computed } from 'vue'
import type { OptimizationAnalysisResult } from '@/types/job-analysis'
import {
  isKnownRequirementImportance,
  isKnownRequirementMatchLevel,
  sortEvidenceRequirements,
} from '@/utils/analysisPresentation'

const props = defineProps<{
  result: OptimizationAnalysisResult | null
  loading: boolean
  error: string | null
  selectedRequirementId?: number | null
}>()

const emit = defineEmits<{
  retryLoad: []
  close: []
}>()

const requirements = computed(() => props.result?.evidenceAnalysis?.requirements ?? [])
const sortedRequirements = computed(() => sortEvidenceRequirements(requirements.value))

const priority = computed(() =>
  sortedRequirements.value.filter(
    (item) =>
      (item.importance === 'REQUIRED' || item.importance === 'BONUS') &&
      (item.matchLevel === 'PARTIAL_EVIDENCE' || item.matchLevel === 'NO_EVIDENCE'),
  ),
)
const matched = computed(() =>
  sortedRequirements.value.filter(
    (item) =>
      (item.importance === 'REQUIRED' || item.importance === 'BONUS') &&
      item.matchLevel === 'MATCHED',
  ),
)
const selected = computed(() => {
  const selectedId = props.selectedRequirementId
  if (selectedId) {
    const found = requirements.value.find((item) => item.evidenceRequirementId === selectedId)
    if (
      found &&
      isKnownRequirementImportance(found.importance) &&
      isKnownRequirementMatchLevel(found.matchLevel)
    ) {
      return found
    }
  }
  return priority.value[0] ?? matched.value[0] ?? null
})

const statusLabel = (value: string) => {
  switch (value) {
    case 'MATCHED':
      return '已有优势'
    case 'PARTIAL_EVIDENCE':
      return '建议完善'
    case 'NO_EVIDENCE':
      return '当前材料未体现'
    default:
      return '需要核对'
  }
}

const statusClass = (value: string) => {
  switch (value) {
    case 'MATCHED':
      return 'is-matched'
    case 'PARTIAL_EVIDENCE':
      return 'is-partial'
    case 'NO_EVIDENCE':
      return 'is-missing'
    default:
      return 'is-unknown'
  }
}

const importanceLabel = (value: string) => (value === 'BONUS' ? '加分项' : '必需项')
</script>

<template>
  <aside class="workspace-inspector" aria-label="优化建议">
    <header class="inspector-header">
      <div>
        <h2>优化建议</h2>
        <p>只查看当前岗位分析时冻结的材料。编辑简历后，分析不会实时重算。</p>
      </div>
      <button type="button" class="inspector-close" @click="emit('close')">收起</button>
    </header>

    <p v-if="loading" class="inspector-status">正在读取分析结论…</p>
    <div v-else-if="error" class="inspector-error" role="alert">
      <p>{{ error }}</p>
      <el-button size="small" @click="emit('retryLoad')">重新加载</el-button>
    </div>

    <template v-else-if="result?.evidenceAnalysis">
      <p class="inspector-context-note">当前只聚焦一条岗位要求；完整分析仍保留在分析页。</p>
      <section v-if="selected" class="inspector-detail">
        <div class="inspector-detail-heading">
          <span :class="['inspector-status', statusClass(selected.matchLevel)]">{{
            statusLabel(selected.matchLevel)
          }}</span>
          <span class="inspector-importance">{{ importanceLabel(selected.importance) }}</span>
        </div>
        <h3>{{ selected.requirementText }}</h3>
        <div class="inspector-detail-block">
          <span>当前材料</span>
          <div v-if="selected.evidences.length" class="inspector-evidence">
            <p v-for="evidence in selected.evidences" :key="evidence.requirementEvidenceId">
              <span v-if="evidence.sectionLabel">{{ evidence.sectionLabel }}：</span>
              「{{ evidence.evidenceText }}」
            </p>
          </div>
          <p v-else class="inspector-muted">当前材料中没有找到可引用的证据。</p>
        </div>
        <p v-if="selected.conclusion" class="inspector-conclusion">{{ selected.conclusion }}</p>
        <p v-if="selected.suggestion" class="inspector-suggestion">{{ selected.suggestion }}</p>
        <p v-if="selected.matchLevel === 'NO_EVIDENCE'" class="inspector-boundary">
          只有在确有真实经历时，才手动补充到左侧简历；系统不会自动加入。
        </p>
        <p v-else-if="selected.matchLevel === 'PARTIAL_EVIDENCE'" class="inspector-boundary">
          请在左侧简历中核对并编辑已有事实。本侧栏不会替你写入新事实。
        </p>
      </section>
      <p v-else class="inspector-quiet">当前结论需要核对，暂不在这里展开自动建议。</p>
    </template>

    <template v-else-if="result?.legacyAnalysis">
      <section class="inspector-section">
        <h3>历史分析</h3>
        <p class="inspector-muted">这是较早版本的分析结果，重新分析可以获得逐条 Evidence。</p>
      </section>
    </template>

    <p v-else class="inspector-quiet">暂无逐条证据分析，可以直接编辑简历内容。</p>
  </aside>
</template>

<style scoped>
.workspace-inspector {
  display: grid;
  align-content: start;
  gap: 18px;
  min-width: 0;
}

.inspector-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
}

.inspector-header h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.inspector-header p {
  max-width: 280px;
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.inspector-close {
  flex: 0 0 auto;
  border: 0;
  padding: 4px 0;
  color: var(--app-primary);
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.inspector-context-note {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.inspector-section {
  display: grid;
  gap: 8px;
}

.inspector-section h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 13px;
}

.inspector-status,
.inspector-importance {
  font-size: 11px;
  font-weight: 700;
}

.inspector-status.is-partial {
  color: var(--app-primary);
}
.inspector-status.is-missing {
  color: var(--app-warning);
}
.inspector-status.is-matched {
  color: var(--app-success);
}
.inspector-status.is-unknown {
  color: var(--app-danger);
}
.inspector-importance {
  color: var(--app-text-muted);
  font-weight: 600;
}

.inspector-detail {
  display: grid;
  gap: 9px;
  padding-top: 2px;
}

.inspector-detail-heading {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.inspector-detail h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.6;
}

.inspector-detail-block {
  display: grid;
  gap: 6px;
}

.inspector-detail-block > span {
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 700;
}

.inspector-evidence {
  display: grid;
  gap: 5px;
  padding: 9px 10px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
}

.inspector-evidence p,
.inspector-conclusion,
.inspector-suggestion,
.inspector-boundary,
.inspector-muted,
.inspector-quiet,
.inspector-status {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
}

.inspector-evidence p,
.inspector-conclusion,
.inspector-suggestion,
.inspector-boundary,
.inspector-muted,
.inspector-quiet {
  color: var(--app-text-secondary);
}

.inspector-evidence span {
  color: var(--app-text);
  font-weight: 700;
}

.inspector-conclusion,
.inspector-suggestion {
  color: var(--app-text);
}

.inspector-suggestion {
  font-weight: 600;
}

.inspector-error {
  display: grid;
  gap: 8px;
  justify-items: start;
  padding: 12px;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: var(--app-radius-md);
  background: var(--app-danger-soft);
}

.inspector-error p {
  margin: 0;
  color: var(--app-text);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 720px) {
  .inspector-header p {
    max-width: none;
  }
}
</style>
