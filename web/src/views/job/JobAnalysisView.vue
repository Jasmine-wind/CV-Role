<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ErrorState from '@/components/common/ErrorState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import RequirementNavigator from '@/components/task/RequirementNavigator.vue'
import TaskHeader from '@/components/task/TaskHeader.vue'
import { getOptimizationAnalysisResult } from '@/api/job-analysis'
import type { AiJobMatchItem } from '@/types/ai-job-match'
import type { OptimizationAnalysisResult } from '@/types/job-analysis'
import { sortEvidenceRequirements } from '@/utils/analysisPresentation'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref<string | null>(null)
const optimizationResult = ref<OptimizationAnalysisResult | null>(null)
const selectedRequirementId = ref<number | null>(null)

const parsePositiveId = (value: unknown) => {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

const optimizationTaskId = computed(() => parsePositiveId(route.params.optimizationTaskId))
const requestedRequirementId = computed(() => parsePositiveId(route.query?.requirement))
const evidenceAnalysis = computed(() => optimizationResult.value?.evidenceAnalysis ?? null)
const legacyResult = computed(() => optimizationResult.value?.legacyAnalysis ?? null)
const requirements = computed(() => evidenceAnalysis.value?.requirements ?? [])
const totalChecked = computed(() => requirements.value.length)
const matchedCount = computed(() => evidenceAnalysis.value?.matchedCount ?? 0)
const partialCount = computed(() => evidenceAnalysis.value?.partialEvidenceCount ?? 0)
const missingCount = computed(() => evidenceAnalysis.value?.noEvidenceCount ?? 0)

const legacyPriorityItems = computed(() => {
  const match = legacyResult.value
  if (!match) return []
  return [
    ...match.weakMatches.map((item) => ({
      title: item.item,
      description: item.reason,
      kind: 'requirement' as const,
    })),
    ...match.weakExperienceDescriptions.map((item) => ({
      title: item.section,
      description: item.issue,
      kind: 'expression' as const,
    })),
  ]
})

const selectedRequirement = computed(() => {
  const requested = selectedRequirementId.value
  if (requested) {
    const found = requirements.value.find((item) => item.evidenceRequirementId === requested)
    if (found) return found
  }

  const preferred = sortEvidenceRequirements(requirements.value).find(
    (item) => item.matchLevel !== 'MATCHED',
  )
  return preferred ?? requirements.value[0] ?? null
})

const selectedRequirementIndex = computed(() => {
  const selected = selectedRequirement.value
  if (!selected) return 0
  return requirements.value.findIndex(
    (item) => item.evidenceRequirementId === selected.evidenceRequirementId,
  )
})

const taskTitle = computed(() => optimizationResult.value?.jobTitle || '岗位分析')
const taskResumeName = computed(() => optimizationResult.value?.resumeName || '当前简历')

const matchLevelLabel = (value: string) => {
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

const matchLevelClass = (value: string) => {
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
const evidenceLocation = (sectionLabel: string | null) => sectionLabel || '简历材料'
const itemKey = (item: AiJobMatchItem, index: number) => `${item.item}-${index}`

watch(
  [evidenceAnalysis, requestedRequirementId],
  () => {
    const requested = requestedRequirementId.value
    if (requested && requirements.value.some((item) => item.evidenceRequirementId === requested)) {
      selectedRequirementId.value = requested
      return
    }
    if (!selectedRequirement.value) {
      selectedRequirementId.value = null
      return
    }
    if (!requirements.value.some((item) => item.evidenceRequirementId === selectedRequirementId.value)) {
      selectedRequirementId.value = selectedRequirement.value.evidenceRequirementId
    }
  },
  { immediate: true },
)

const selectRequirement = (requirementId: number) => {
  selectedRequirementId.value = requirementId
}

const goToWorkspace = (requirementId = selectedRequirement.value?.evidenceRequirementId) => {
  if (!optimizationTaskId.value) return
  router.push({
    path: `/workspace/${optimizationTaskId.value}`,
    ...(requirementId ? { query: { requirement: String(requirementId) } } : {}),
  })
}

const loadResult = async () => {
  if (!optimizationTaskId.value) {
    error.value = '岗位分析地址无效，请从首页重新开始。'
    return
  }

  loading.value = true
  error.value = null
  try {
    optimizationResult.value = await getOptimizationAnalysisResult(optimizationTaskId.value)
  } catch (loadError) {
    error.value = loadError instanceof Error ? loadError.message : '岗位分析结果加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadResult)
</script>

<template>
  <section class="analysis-task-page">
    <TaskHeader
      :job-title="taskTitle"
      :resume-name="taskResumeName"
      active-step="evidence"
      back-label="返回开始优化"
      @back="router.push('/app')"
    >
      <template #actions>
        <el-button class="analysis-new-job-action" @click="router.push('/app')">分析新岗位</el-button>
        <details class="analysis-mobile-more">
          <summary>更多</summary>
          <div>
            <button type="button" @click="router.push('/app')">分析新岗位</button>
          </div>
        </details>
        <el-button
          v-if="optimizationResult?.status === 'SUCCESS'"
          type="primary"
          @click="goToWorkspace()"
        >
          修改简历 →
        </el-button>
      </template>
    </TaskHeader>

    <main class="analysis-task-content">
      <SkeletonBlock v-if="loading" title :rows="8" />
      <ErrorState
        v-else-if="error"
        title="暂时无法查看分析结果"
        :description="error"
        action-text="返回首页"
        @action="router.push('/app')"
      />

      <template v-else-if="evidenceAnalysis">
        <section class="analysis-summary-strip" aria-label="证据摘要">
          <div class="analysis-summary-counts">
            <span class="analysis-summary-item is-matched">
              <strong>{{ matchedCount }}</strong><span>已匹配</span>
            </span>
            <span class="analysis-summary-item is-partial">
              <strong>{{ partialCount }}</strong><span>部分证据</span>
            </span>
            <span class="analysis-summary-item is-missing">
              <strong>{{ missingCount }}</strong><span>当前未体现</span>
            </span>
          </div>
          <p>
            共核对 {{ totalChecked }} 条岗位要求。分析只依据本次冻结的简历材料；没有证据的要求不会自动写入简历。
          </p>
        </section>

        <section class="analysis-review-layout" aria-label="岗位要求与证据审阅">
          <RequirementNavigator
            :requirements="requirements"
            :selected-requirement-id="selectedRequirement?.evidenceRequirementId ?? null"
            :job-title="taskTitle"
            @select="selectRequirement"
          />

          <article v-if="selectedRequirement" class="analysis-evidence-detail">
            <header class="analysis-detail-header">
              <div>
                <span class="analysis-detail-eyebrow">EVIDENCE REVIEW</span>
                <p class="analysis-detail-reference">
                  要求 {{ String(selectedRequirementIndex + 1).padStart(2, '0') }}
                  <span aria-hidden="true">·</span>
                  <span :class="['analysis-status', matchLevelClass(selectedRequirement.matchLevel)]">
                    {{ matchLevelLabel(selectedRequirement.matchLevel) }}
                  </span>
                  <span>{{ importanceLabel(selectedRequirement.importance) }}</span>
                </p>
              </div>
              <span class="analysis-detail-state" :class="matchLevelClass(selectedRequirement.matchLevel)">
                {{ selectedRequirement.evidences.length }} 条引用
              </span>
            </header>

            <h2>{{ selectedRequirement.requirementText }}</h2>

            <section class="analysis-detail-block">
              <span class="analysis-block-label">岗位要求</span>
              <blockquote>“{{ selectedRequirement.requirementText }}”</blockquote>
              <p class="analysis-source-note">来自本次岗位分析时冻结的目标岗位。</p>
            </section>

            <section class="analysis-detail-block">
              <div class="analysis-block-heading">
                <h3>证据</h3>
                <span>{{ selectedRequirement.evidences.length }} 条关联引用</span>
              </div>
              <div v-if="selectedRequirement.evidences.length" class="analysis-evidence-list">
                <article
                  v-for="evidence in selectedRequirement.evidences"
                  :key="evidence.requirementEvidenceId"
                  class="analysis-evidence-quote"
                >
                  <div>
                    <span>{{ evidenceLocation(evidence.sectionLabel) }}</span>
                    <span>{{ evidence.supportLevel === 'SUFFICIENT' ? '足够支持' : '部分支持' }}</span>
                  </div>
                  <p>“{{ evidence.evidenceText }}”</p>
                  <small>来自本次分析冻结的简历材料</small>
                </article>
              </div>
              <p v-else class="analysis-detail-muted">当前简历未找到支持该要求的证据。</p>
            </section>

            <section class="analysis-detail-block analysis-conclusion-block">
              <span class="analysis-block-label">
                {{ selectedRequirement.matchLevel === 'MATCHED' ? '核对结论' : '结论与边界' }}
              </span>
              <p>{{ selectedRequirement.conclusion || '当前材料暂时无法支持这项要求。' }}</p>
              <p v-if="selectedRequirement.matchLevel === 'NO_EVIDENCE'" class="analysis-boundary">
                当前简历未找到支持该要求的证据，不代表你没有这项能力。只有在确有真实经历时，才手动补充到简历；系统不会自动加入。
              </p>
              <p v-else-if="selectedRequirement.matchLevel === 'PARTIAL_EVIDENCE'" class="analysis-boundary">
                这代表当前材料中的证据不完整。建议先核对真实经历，再决定如何表达；本页面不会替你增加新事实。
              </p>
            </section>

            <section class="analysis-detail-block analysis-suggestion-block">
              <div class="analysis-block-heading">
                <h3>建议完善</h3>
                <span>保留真实材料</span>
              </div>
              <p>{{ selectedRequirement.suggestion || '当前材料已足够支持这项要求，无需为了匹配关键词额外增加事实。' }}</p>
              <div class="analysis-detail-actions">
                <el-button type="primary" @click="goToWorkspace(selectedRequirement.evidenceRequirementId)">
                  修改简历 →
                </el-button>
                <el-button @click="selectRequirement(requirements[0]?.evidenceRequirementId ?? selectedRequirement.evidenceRequirementId)">
                  回到第一条要求
                </el-button>
              </div>
            </section>
          </article>
          <div v-else class="analysis-detail-empty" role="status">
            <strong>暂无逐条证据</strong>
            <p>当前分析没有可展示的岗位要求，请稍后重试或返回首页重新分析。</p>
          </div>
        </section>
      </template>

      <template v-else-if="legacyResult">
        <section class="analysis-summary-strip" aria-label="历史分析摘要">
          <div class="analysis-summary-counts">
            <span class="analysis-summary-item is-matched">
              <strong>{{ legacyResult.strongMatches.length }}</strong><span>已有优势</span>
            </span>
            <span class="analysis-summary-item is-partial">
              <strong>{{ legacyPriorityItems.length }}</strong><span>建议完善</span>
            </span>
            <span class="analysis-summary-item is-missing">
              <strong>{{ legacyResult.missingSkills.length }}</strong><span>当前未体现</span>
            </span>
          </div>
          <p>这是较早版本的分析结果。当前页面保留读取兼容；重新分析该岗位可以获得逐条可追溯的证据核对。</p>
        </section>

        <section class="analysis-legacy-layout" aria-label="历史分析兼容内容">
          <div class="legacy-warning">
            <span class="analysis-detail-eyebrow">LEGACY COMPATIBILITY</span>
            <strong>这是较早版本的分析结果</strong>
            <p>历史结果可以继续查看，但不会启用新的逐条证据建议。重新分析后，系统才会建立当前材料与岗位要求的 Evidence 关系。</p>
            <el-button type="primary" @click="router.push('/app')">重新分析岗位</el-button>
          </div>
          <div class="legacy-detail-list">
            <section class="legacy-detail-section">
              <header><h2>建议完善</h2><span>{{ legacyPriorityItems.length }} 条</span></header>
              <article v-for="(item, index) in legacyPriorityItems" :key="`${item.kind}-${item.title}-${index}`">
                <span class="analysis-status is-partial">建议完善</span>
                <strong>{{ item.title || '相关经历' }}</strong>
                <p>{{ item.description || '建议回看对应经历并补充真实场景。' }}</p>
                <el-button size="small" type="primary" @click="goToWorkspace()">进入编辑器</el-button>
              </article>
              <p v-if="!legacyPriorityItems.length" class="analysis-detail-muted">当前没有明显的表达缺口。</p>
            </section>

            <section class="legacy-detail-section">
              <header><h2>已有优势</h2><span>{{ legacyResult.strongMatches.length }} 条</span></header>
              <article v-for="(item, index) in legacyResult.strongMatches" :key="itemKey(item, index)">
                <span class="analysis-status is-matched">已有优势</span>
                <strong>{{ item.item }}</strong>
                <p>{{ item.reason }}</p>
              </article>
              <p v-if="!legacyResult.strongMatches.length" class="analysis-detail-muted">暂未识别到明确优势。</p>
            </section>

            <section class="legacy-detail-section">
              <header><h2>当前材料未体现</h2><span>{{ legacyResult.missingSkills.length }} 条</span></header>
              <article v-for="(item, index) in legacyResult.missingSkills" :key="itemKey(item, index)">
                <span class="analysis-status is-missing">当前材料未体现</span>
                <strong>{{ item.item }}</strong>
                <p>{{ item.reason }}</p>
                <small>如确有真实经历，可手动补充；不要写入未经证实的内容。</small>
              </article>
              <p v-if="!legacyResult.missingSkills.length" class="analysis-detail-muted">主要岗位要求在简历中已有体现。</p>
            </section>
          </div>
        </section>
      </template>

      <EmptyState v-else title="分析结果尚未生成" description="请从首页重新开始一次岗位分析。" />
    </main>
  </section>
</template>

<style scoped>
.analysis-task-page {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  color: var(--app-text);
  background: var(--app-stage);
}

.analysis-task-content {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  gap: var(--app-space-5);
  padding: var(--app-space-5) var(--app-content-gutter) var(--app-space-8);
}

.analysis-summary-strip {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-6);
  border-top: 1px solid var(--app-border-strong);
  border-bottom: 1px solid var(--app-border);
  padding: var(--app-space-3) var(--app-space-1);
}

.analysis-summary-counts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-5);
  align-items: center;
}

.analysis-summary-item {
  display: inline-flex;
  align-items: baseline;
  gap: var(--app-space-2);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
}

.analysis-summary-item strong {
  color: var(--app-text);
  font-size: 18px;
  line-height: 1;
}

.analysis-summary-item::before {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  content: '';
}

.analysis-summary-item.is-matched::before {
  background: var(--app-success);
}

.analysis-summary-item.is-partial::before {
  background: var(--app-warning);
}

.analysis-summary-item.is-missing::before {
  background: var(--app-primary);
}

.analysis-summary-strip > p {
  max-width: 620px;
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  line-height: var(--app-line-height-body);
  text-align: right;
}

.analysis-review-layout {
  display: grid;
  min-height: 0;
  flex: 1 1 auto;
  grid-template-columns: var(--app-workspace-requirements-width) minmax(0, 1fr);
  overflow: hidden;
  border-top: 1px solid var(--app-border-strong);
  background: var(--app-surface);
}

.analysis-review-layout :deep(.requirements-rail) {
  overflow: hidden;
}

.analysis-evidence-detail {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  scrollbar-color: var(--app-scroll-thumb) transparent;
  scrollbar-width: thin;
}

.analysis-detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-4);
  padding: var(--app-space-6) var(--app-space-8) 0;
}

.analysis-detail-eyebrow {
  display: block;
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.analysis-detail-reference {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-2);
  align-items: center;
  margin: var(--app-space-2) 0 0;
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
}

.analysis-status {
  font-weight: 700;
}

.analysis-status.is-matched {
  color: var(--app-success);
}

.analysis-status.is-partial {
  color: var(--app-warning);
}

.analysis-status.is-missing {
  color: var(--app-primary-active);
}

.analysis-status.is-unknown {
  color: var(--app-danger);
}

.analysis-detail-state {
  flex: 0 0 auto;
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
}

.analysis-detail-state.is-matched {
  color: var(--app-success);
}

.analysis-detail-state.is-partial {
  color: var(--app-warning);
}

.analysis-detail-state.is-missing {
  color: var(--app-primary-active);
}

.analysis-evidence-detail > h2 {
  margin: var(--app-space-2) var(--app-space-8) var(--app-space-6);
  color: var(--app-text);
  font-size: 25px;
  font-weight: 750;
  line-height: 1.3;
  letter-spacing: -0.035em;
}

.analysis-detail-block {
  display: grid;
  gap: var(--app-space-2);
  border-top: 1px solid var(--app-border);
  padding: var(--app-space-5) var(--app-space-8);
}

.analysis-block-label,
.analysis-block-heading > span {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.analysis-block-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--app-space-3);
}

.analysis-block-heading h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 15px;
}

.analysis-detail-block blockquote {
  margin: 0;
  border-left: 2px solid var(--app-primary);
  padding-left: var(--app-space-3);
  color: var(--app-text);
  font-size: var(--app-font-size-md);
  line-height: var(--app-line-height-body);
}

.analysis-source-note,
.analysis-detail-block > p,
.analysis-detail-muted,
.legacy-detail-section p,
.legacy-warning p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.analysis-evidence-list {
  border-top: 1px solid var(--app-border);
}

.analysis-evidence-quote {
  display: grid;
  gap: var(--app-space-2);
  border-bottom: 1px solid var(--app-border);
  padding: var(--app-space-3) 0;
}

.analysis-evidence-quote > div {
  display: flex;
  gap: var(--app-space-3);
  align-items: baseline;
}

.analysis-evidence-quote > div span:first-child {
  color: var(--app-text);
  font-size: var(--app-font-size-xs);
  font-weight: 700;
}

.analysis-evidence-quote > div span:last-child,
.analysis-evidence-quote small {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-xs);
}

.analysis-evidence-quote p {
  margin: 0;
  color: var(--app-text);
  font-size: var(--app-font-size-md);
  line-height: var(--app-line-height-body);
}

.analysis-conclusion-block > p:first-of-type {
  color: var(--app-text);
}

.analysis-boundary {
  color: var(--app-text-muted) !important;
}

.analysis-suggestion-block {
  background: var(--app-surface-soft);
}

.analysis-detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-2);
  padding-top: var(--app-space-2);
}

.analysis-detail-empty {
  display: grid;
  align-content: start;
  gap: var(--app-space-2);
  min-width: 0;
  padding: var(--app-space-8);
  border-left: 1px solid var(--app-border-strong);
}

.analysis-detail-empty strong {
  color: var(--app-text);
  font-size: var(--app-font-size-lg);
}

.analysis-legacy-layout {
  display: grid;
  min-height: 0;
  flex: 1 1 auto;
  grid-template-columns: minmax(240px, 0.34fr) minmax(0, 1fr);
  gap: var(--app-space-8);
  border-top: 1px solid var(--app-border-strong);
  padding-top: var(--app-space-5);
}

.legacy-warning {
  display: grid;
  align-content: start;
  gap: var(--app-space-3);
  min-width: 0;
  padding: var(--app-space-2) var(--app-space-5) var(--app-space-5) 0;
  border-right: 1px solid var(--app-border-strong);
}

.legacy-warning strong {
  color: var(--app-text);
  font-size: 18px;
}

.legacy-warning .el-button {
  justify-self: start;
}

.legacy-detail-list {
  min-width: 0;
  overflow: auto;
  scrollbar-color: var(--app-scroll-thumb) transparent;
  scrollbar-width: thin;
}

.legacy-detail-section {
  display: grid;
  gap: var(--app-space-2);
  border-bottom: 1px solid var(--app-border);
  padding-bottom: var(--app-space-5);
}

.legacy-detail-section + .legacy-detail-section {
  margin-top: var(--app-space-5);
}

.legacy-detail-section header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--app-space-3);
}

.legacy-detail-section header h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.legacy-detail-section header span {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
}

.legacy-detail-section article {
  display: grid;
  gap: var(--app-space-1);
  border-top: 1px solid var(--app-border-soft);
  padding: var(--app-space-3) 0;
}

.legacy-detail-section article strong {
  color: var(--app-text);
  font-size: var(--app-font-size-md);
}

.legacy-detail-section article small {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-xs);
}

.legacy-detail-section article .el-button {
  justify-self: start;
}

@media (max-width: 1250px) and (min-width: 1120px) {
  .analysis-review-layout {
    grid-template-columns: var(--app-workspace-requirements-width-tablet) minmax(0, 1fr);
  }

  .analysis-detail-header,
  .analysis-evidence-detail > h2,
  .analysis-detail-block {
    padding-right: var(--app-space-5);
    padding-left: var(--app-space-5);
  }

  .analysis-evidence-detail > h2 {
    margin-right: 0;
    margin-left: 0;
  }
}

@media (max-width: 1119px) {
  .analysis-task-page {
    min-height: 100%;
  }

  .analysis-task-content {
    padding: var(--app-space-4) var(--app-content-gutter-narrow) var(--app-space-8);
  }

  .analysis-summary-strip {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--app-space-3);
  }

  .analysis-summary-strip > p {
    max-width: none;
    text-align: left;
  }

  .analysis-review-layout {
    display: block;
    overflow: visible;
  }

  .analysis-review-layout :deep(.requirements-rail) {
    overflow: visible;
  }

  .analysis-evidence-detail {
    overflow: visible;
    border-top: 1px solid var(--app-border-strong);
  }

  .analysis-legacy-layout {
    display: block;
  }

  .legacy-warning {
    margin-bottom: var(--app-space-5);
    padding: 0 0 var(--app-space-5);
    border-right: 0;
    border-bottom: 1px solid var(--app-border-strong);
  }

  .legacy-detail-list {
    overflow: visible;
  }
}

.analysis-mobile-more {
  display: none;
  position: relative;
}

.analysis-mobile-more summary {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  padding: 0 var(--app-space-3);
  border-radius: var(--app-radius-sm);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  font-weight: 700;
  cursor: pointer;
  list-style: none;
}

.analysis-mobile-more summary::-webkit-details-marker {
  display: none;
}

.analysis-mobile-more summary:hover,
.analysis-mobile-more summary:focus-visible,
.analysis-mobile-more[open] summary {
  color: var(--app-text);
  background: var(--app-surface-soft);
}

.analysis-mobile-more > div {
  position: absolute;
  z-index: 5;
  top: calc(100% + 6px);
  left: 0;
  min-width: 116px;
  padding: 4px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-soft);
}

.analysis-mobile-more button {
  width: 100%;
  border: 0;
  padding: 8px 9px;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: var(--app-font-size-xs);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.analysis-mobile-more button:hover,
.analysis-mobile-more button:focus-visible {
  color: var(--app-text);
  background: var(--app-bg-soft);
}

@media (max-width: 640px) {
  .analysis-new-job-action {
    display: none;
  }

  .analysis-mobile-more {
    display: block;
  }

  .analysis-summary-counts {
    width: 100%;
    justify-content: space-between;
    gap: var(--app-space-2);
  }

  .analysis-summary-item {
    display: grid;
    grid-template-columns: 7px 1fr;
    gap: var(--app-space-1) var(--app-space-2);
  }

  .analysis-summary-item strong {
    grid-column: 2;
    grid-row: 1;
    font-size: 17px;
  }

  .analysis-summary-item span {
    grid-column: 2;
    grid-row: 2;
  }

  .analysis-summary-item::before {
    grid-row: 1 / span 2;
    align-self: center;
  }

  .analysis-detail-header {
    padding: var(--app-space-5) var(--app-space-4) 0;
  }

  .analysis-evidence-detail > h2 {
    margin: var(--app-space-2) var(--app-space-4) var(--app-space-5);
    font-size: 21px;
  }

  .analysis-detail-block {
    padding: var(--app-space-4);
  }

  .analysis-detail-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
