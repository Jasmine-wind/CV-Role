<script setup lang="ts">
import { computed } from 'vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { EvidenceRequirementItem } from '@/types/evidence-analysis'
import type { OptimizationAnalysisResult } from '@/types/job-analysis'

const props = defineProps<{
  result: OptimizationAnalysisResult | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{
  retryLoad: []
}>()

const requirements = computed(() => props.result?.evidenceAnalysis?.requirements ?? [])

const byLevel = (level: string): EvidenceRequirementItem[] =>
  requirements.value.filter((item) => item.matchLevel === level)

const matched = computed(() => byLevel('MATCHED'))
const partial = computed(() => byLevel('PARTIAL_EVIDENCE'))
const missing = computed(() => byLevel('NO_EVIDENCE'))
</script>

<template>
  <aside class="workspace-suggestions">
    <header>
      <h2>优化建议</h2>
      <p class="suggestions-note">
        以下结论基于本次分析时冻结的简历材料与岗位要求，右侧编辑不会实时重新计算。
      </p>
    </header>

    <p v-if="loading" class="suggestions-status">正在读取分析结论…</p>
    <div v-else-if="error" class="suggestions-error" role="alert">
      <p>{{ error }}</p>
      <el-button size="small" @click="emit('retryLoad')">重新加载</el-button>
    </div>

    <template v-else-if="requirements.length">
      <p class="suggestions-summary" aria-label="三态数量摘要">
        建议完善 {{ partial.length }} · 已有优势 {{ matched.length }} · 当前材料未体现 {{ missing.length }}
      </p>

      <section v-if="partial.length" class="suggestion-group">
        <h3>建议完善 · {{ partial.length }}</h3>
        <article v-for="item in partial" :key="item.evidenceRequirementId" class="suggestion-card">
          <h4>{{ item.requirementText }}</h4>
          <p v-if="item.conclusion">{{ item.conclusion }}</p>
          <div v-if="item.evidences.length" class="suggestion-evidence">
            <p v-for="evidence in item.evidences" :key="evidence.requirementEvidenceId">
              <span v-if="evidence.sectionLabel">{{ evidence.sectionLabel }}：</span>
              「{{ evidence.evidenceText }}」
            </p>
          </div>
          <p v-if="item.suggestion" class="suggestion-tip">{{ item.suggestion }}</p>
          <small>请只补充真实经历；部分证据不代表可以写入未被材料证明的事实。</small>
        </article>
      </section>

      <section v-if="matched.length" class="suggestion-group">
        <h3>已有优势 · {{ matched.length }}</h3>
        <article v-for="item in matched" :key="item.evidenceRequirementId" class="suggestion-card">
          <h4>{{ item.requirementText }}</h4>
          <p v-if="item.conclusion">{{ item.conclusion }}</p>
          <div v-if="item.evidences.length" class="suggestion-evidence">
            <p v-for="evidence in item.evidences" :key="evidence.requirementEvidenceId">
              <span v-if="evidence.sectionLabel">{{ evidence.sectionLabel }}：</span>
              「{{ evidence.evidenceText }}」
            </p>
          </div>
        </article>
      </section>

      <section v-if="missing.length" class="suggestion-group">
        <h3>当前材料未体现 · {{ missing.length }}</h3>
        <article
          v-for="item in missing"
          :key="item.evidenceRequirementId"
          class="suggestion-card is-gap"
        >
          <h4>{{ item.requirementText }}</h4>
          <p v-if="item.conclusion">{{ item.conclusion }}</p>
          <p v-if="item.suggestion" class="suggestion-tip">{{ item.suggestion }}</p>
          <small>这只代表分析时的材料中没有证据；如确有真实经历，可自行补充。</small>
        </article>
      </section>
    </template>

    <EmptyState
      v-else
      title="暂无逐条证据分析"
      description="该任务没有正式证据分析结论，可以直接在右侧编辑简历内容。"
    />
  </aside>
</template>

<style scoped>
.workspace-suggestions {
  display: grid;
  gap: 16px;
  align-content: start;
}

.workspace-suggestions h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.suggestions-note {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.suggestions-status {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.suggestions-error {
  display: grid;
  gap: 8px;
  justify-items: start;
  padding: 12px;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: var(--app-radius-sm);
  background: var(--app-danger-soft);
}

.suggestions-error p {
  margin: 0;
  color: var(--app-text);
  font-size: 13px;
  line-height: 1.6;
}

.suggestions-summary {
  margin: 0;
  padding: 8px 12px;
  border-radius: var(--app-radius-sm);
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
  background: var(--app-surface-soft);
}

.suggestion-group {
  display: grid;
  gap: 10px;
}

.suggestion-group h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 800;
}

.suggestion-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  padding: 14px;
  display: grid;
  gap: 8px;
}

.suggestion-card.is-gap {
  border-color: var(--el-color-warning-light-7);
}

.suggestion-card h4 {
  margin: 0;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.6;
}

.suggestion-card p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.suggestion-card small {
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.suggestion-evidence {
  border-left: 3px solid var(--el-border-color-light);
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  padding: 8px 10px;
  display: grid;
  gap: 4px;
}

.suggestion-evidence p {
  font-size: 12px;
}

.suggestion-evidence span {
  font-weight: 700;
}

.suggestion-tip {
  color: var(--app-text);
  font-weight: 600;
}
</style>
