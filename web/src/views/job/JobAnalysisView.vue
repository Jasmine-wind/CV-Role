<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import { getOptimizationAnalysisResult } from '@/api/job-analysis'
import type { AiJobMatchItem } from '@/types/ai-job-match'
import type { EvidenceRequirementItem } from '@/types/evidence-analysis'
import type { OptimizationAnalysisResult } from '@/types/job-analysis'
import {
  isKnownRequirementImportance,
  isKnownRequirementMatchLevel,
  sortEvidenceRequirements,
} from '@/utils/analysisPresentation'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref<string | null>(null)
const optimizationResult = ref<OptimizationAnalysisResult | null>(null)
const expandedPriorityIds = ref<Set<number>>(new Set())
const collapsedMatchedIds = ref<Set<number>>(new Set())
const priorityOpen = ref(true)
const matchedOpen = ref(true)
const unknownOpen = ref(false)

const optimizationTaskId = computed(() => parsePositiveId(route.params.optimizationTaskId))
const evidenceAnalysis = computed(() => optimizationResult.value?.evidenceAnalysis ?? null)
const legacyResult = computed(() => optimizationResult.value?.legacyAnalysis ?? null)

const totalChecked = computed(() => evidenceAnalysis.value?.requirements.length ?? 0)

const sortedRequirements = computed(() =>
  sortEvidenceRequirements(evidenceAnalysis.value?.requirements ?? []),
)

const priorityRequirements = computed(() =>
  sortedRequirements.value.filter(
    (item) =>
      isKnownRequirementImportance(item.importance) &&
      (item.matchLevel === 'PARTIAL_EVIDENCE' || item.matchLevel === 'NO_EVIDENCE'),
  ),
)

const matchedRequirements = computed(() =>
  sortedRequirements.value.filter(
    (item) => isKnownRequirementImportance(item.importance) && item.matchLevel === 'MATCHED',
  ),
)

const unknownRequirements = computed(() =>
  sortedRequirements.value.filter(
    (item) =>
      !isKnownRequirementImportance(item.importance) ||
      !isKnownRequirementMatchLevel(item.matchLevel),
  ),
)

const partialCount = computed(() => evidenceAnalysis.value?.partialEvidenceCount ?? 0)
const matchedCount = computed(() => evidenceAnalysis.value?.matchedCount ?? 0)
const missingCount = computed(() => evidenceAnalysis.value?.noEvidenceCount ?? 0)

const legacyPriorityItems = computed(() => {
  const match = legacyResult.value
  if (!match) {
    return []
  }
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

const parsePositiveId = (value: unknown) => {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

const itemKey = (item: AiJobMatchItem, index: number) => `${item.item}-${index}`

const importanceLabel = (value: string) => (value === 'BONUS' ? '加分项' : '必需项')

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

const resetExpandedState = () => {
  const next = new Set<number>()
  const firstPriority = priorityRequirements.value[0]
  if (firstPriority) next.add(firstPriority.evidenceRequirementId)
  expandedPriorityIds.value = next

  const collapsedMatched = new Set(
    matchedRequirements.value.map((item) => item.evidenceRequirementId),
  )
  if (!firstPriority && matchedRequirements.value[0]) {
    collapsedMatched.delete(matchedRequirements.value[0].evidenceRequirementId)
  }
  collapsedMatchedIds.value = collapsedMatched
  priorityOpen.value = priorityRequirements.value.length > 0
  matchedOpen.value = true
  unknownOpen.value = false
}

watch(evidenceAnalysis, resetExpandedState, { immediate: true })

const isPriorityExpanded = (item: EvidenceRequirementItem) =>
  expandedPriorityIds.value.has(item.evidenceRequirementId)

const isMatchedExpanded = (item: EvidenceRequirementItem) =>
  !collapsedMatchedIds.value.has(item.evidenceRequirementId)

const togglePriorityItem = (item: EvidenceRequirementItem) => {
  const next = new Set(expandedPriorityIds.value)
  if (next.has(item.evidenceRequirementId)) next.delete(item.evidenceRequirementId)
  else next.add(item.evidenceRequirementId)
  expandedPriorityIds.value = next
}

const toggleMatchedItem = (item: EvidenceRequirementItem) => {
  const next = new Set(collapsedMatchedIds.value)
  if (next.has(item.evidenceRequirementId)) next.delete(item.evidenceRequirementId)
  else next.add(item.evidenceRequirementId)
  collapsedMatchedIds.value = next
}

const togglePriority = () => {
  if (!priorityRequirements.value.length) return
  priorityOpen.value = !priorityOpen.value
}

const toggleMatched = () => {
  if (!matchedRequirements.value.length) return
  matchedOpen.value = !matchedOpen.value
}

const goToWorkspace = (requirementId?: number) => {
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
  <section class="analysis-result-page">
    <PageHeader
      :title="optimizationResult?.jobTitle || '岗位分析'"
      :description="
        optimizationResult?.resumeName
          ? `基于 ${optimizationResult.resumeName} 与目标岗位要求整理`
          : '基于你的真实简历与目标岗位要求整理'
      "
    >
      <template #actions>
        <el-button @click="router.push('/app')">分析新岗位</el-button>
        <el-button
          v-if="optimizationResult && optimizationResult.status === 'SUCCESS'"
          type="primary"
          @click="goToWorkspace()"
        >
          修改简历
        </el-button>
      </template>
    </PageHeader>

    <SkeletonBlock v-if="loading" title :rows="8" />
    <ErrorState
      v-else-if="error"
      title="暂时无法查看分析结果"
      :description="error"
      action-text="返回首页"
      @action="router.push('/app')"
    />

    <template v-else-if="evidenceAnalysis">
      <section class="analysis-overview" aria-label="分析摘要">
        <div class="analysis-counts">
          <span class="analysis-count is-action"
            >建议完善 <strong>{{ partialCount }}</strong></span
          >
          <span class="analysis-count is-missing"
            >当前材料未体现 <strong>{{ missingCount }}</strong></span
          >
          <span class="analysis-count is-matched"
            >已有优势 <strong>{{ matchedCount }}</strong></span
          >
        </div>
        <p>
          共核对
          {{ totalChecked }}
          条岗位要求。分析只依据本次冻结的简历材料；没有证据的要求不会自动写入简历。
        </p>
      </section>

      <section class="analysis-section analysis-priority">
        <button
          class="analysis-section-header"
          type="button"
          :aria-expanded="priorityRequirements.length ? priorityOpen : undefined"
          :disabled="!priorityRequirements.length"
          @click="togglePriority"
        >
          <span>
            <strong>优先处理</strong>
            <small>{{ priorityRequirements.length }} 条</small>
          </span>
          <span v-if="priorityRequirements.length" class="section-toggle">{{
            priorityOpen ? '收起' : '查看'
          }}</span>
        </button>
        <p class="analysis-section-note">
          先处理已有部分证据的必需项；没有证据的要求只在你确认确有真实经历时手动补充。
        </p>

        <div v-if="priorityOpen && priorityRequirements.length" class="analysis-list">
          <article
            v-for="item in priorityRequirements"
            :key="`priority-${item.evidenceRequirementId}`"
            class="analysis-item"
            :class="{ 'is-expanded': isPriorityExpanded(item) }"
          >
            <button
              class="analysis-item-toggle"
              type="button"
              :aria-expanded="isPriorityExpanded(item)"
              @click="togglePriorityItem(item)"
            >
              <span class="analysis-item-heading">
                <span :class="['analysis-status', matchLevelClass(item.matchLevel)]">
                  {{ matchLevelLabel(item.matchLevel) }}
                </span>
                <span class="analysis-importance">{{ importanceLabel(item.importance) }}</span>
                <strong>{{ item.requirementText }}</strong>
              </span>
              <span class="item-toggle-label">{{
                isPriorityExpanded(item) ? '收起依据' : '查看依据'
              }}</span>
            </button>

            <div v-if="isPriorityExpanded(item)" class="analysis-item-detail">
              <div class="analysis-detail-block">
                <span class="analysis-detail-label">当前材料</span>
                <div v-if="item.evidences.length" class="evidence-list">
                  <p v-for="evidence in item.evidences" :key="evidence.requirementEvidenceId">
                    <span v-if="evidence.sectionLabel">{{ evidence.sectionLabel }}：</span>
                    「{{ evidence.evidenceText }}」
                  </p>
                </div>
                <p v-else class="detail-muted">当前材料中没有找到可引用的证据。</p>
              </div>
              <p
                v-if="
                  item.conclusion &&
                  (item.matchLevel !== 'NO_EVIDENCE' || item.evidences.length > 0)
                "
                class="analysis-conclusion"
              >
                {{ item.conclusion }}
              </p>
              <p v-if="item.suggestion" class="analysis-suggestion">{{ item.suggestion }}</p>
              <p v-if="item.matchLevel === 'NO_EVIDENCE'" class="analysis-boundary">
                这只代表当前材料未体现，不代表你没有这项能力。只有在确有真实经历时，才手动补充到简历；系统不会自动加入。
              </p>
              <div class="analysis-item-action">
                <el-button
                  v-if="item.matchLevel === 'PARTIAL_EVIDENCE'"
                  type="primary"
                  size="small"
                  @click="goToWorkspace(item.evidenceRequirementId)"
                >
                  进入编辑器
                </el-button>
                <el-button v-else size="small" @click="goToWorkspace(item.evidenceRequirementId)">
                  手动补充
                </el-button>
              </div>
            </div>
          </article>
        </div>
        <p v-if="!priorityRequirements.length" class="analysis-quiet-empty">
          当前没有需要优先处理的岗位要求。
        </p>
      </section>

      <section class="analysis-section analysis-strengths">
        <button
          class="analysis-section-header"
          type="button"
          :aria-expanded="matchedRequirements.length ? matchedOpen : undefined"
          :disabled="!matchedRequirements.length"
          @click="toggleMatched"
        >
          <span>
            <strong>已有优势</strong>
            <small>{{ matchedRequirements.length }} 条</small>
          </span>
          <span v-if="matchedRequirements.length" class="section-toggle">{{
            matchedOpen ? '收起' : '查看'
          }}</span>
        </button>
        <p class="analysis-section-note">
          保留证据引用，帮助你快速确认哪些要求已经被当前材料支持。
        </p>

        <div v-if="matchedOpen && matchedRequirements.length" class="analysis-list is-strengths">
          <article
            v-for="item in matchedRequirements"
            :key="`matched-${item.evidenceRequirementId}`"
            class="analysis-item"
            :class="{ 'is-expanded': isMatchedExpanded(item) }"
          >
            <button
              class="analysis-item-toggle"
              type="button"
              :aria-expanded="isMatchedExpanded(item)"
              @click="toggleMatchedItem(item)"
            >
              <span class="analysis-item-heading">
                <span class="analysis-status is-matched">已有优势</span>
                <span class="analysis-importance">{{ importanceLabel(item.importance) }}</span>
                <strong>{{ item.requirementText }}</strong>
              </span>
              <span class="item-toggle-label">{{
                isMatchedExpanded(item) ? '收起依据' : '查看依据'
              }}</span>
            </button>
            <div v-if="isMatchedExpanded(item)" class="analysis-item-detail">
              <div v-if="item.evidences.length" class="evidence-list">
                <p v-for="evidence in item.evidences" :key="evidence.requirementEvidenceId">
                  <span v-if="evidence.sectionLabel">{{ evidence.sectionLabel }}：</span>
                  「{{ evidence.evidenceText }}」
                </p>
              </div>
            </div>
          </article>
        </div>
        <p v-else-if="matchedOpen" class="analysis-quiet-empty">暂未识别到明确优势。</p>
      </section>

      <section v-if="unknownRequirements.length" class="analysis-section analysis-unknown">
        <button
          class="analysis-section-header"
          type="button"
          :aria-expanded="unknownOpen"
          @click="unknownOpen = !unknownOpen"
        >
          <span>
            <strong>需要核对</strong>
            <small>{{ unknownRequirements.length }} 条</small>
          </span>
          <span class="section-toggle">{{ unknownOpen ? '收起' : '查看' }}</span>
        </button>
        <p v-if="unknownOpen" class="analysis-section-note">
          这部分状态无法安全归类，暂不把它当作优势或缺口，也不会触发自动修改。
        </p>
        <div v-if="unknownOpen" class="analysis-list">
          <article
            v-for="item in unknownRequirements"
            :key="`unknown-${item.evidenceRequirementId}`"
            class="analysis-item"
          >
            <div class="analysis-item-heading">
              <span class="analysis-status is-unknown">需要核对</span>
              <strong>{{ item.requirementText }}</strong>
            </div>
            <p class="detail-muted">当前结论暂无法安全展示，请重新分析或回到简历中人工检查。</p>
          </article>
        </div>
      </section>
    </template>

    <template v-else-if="legacyResult">
      <section class="analysis-overview" aria-label="历史分析摘要">
        <div class="analysis-counts">
          <span class="analysis-count is-action"
            >建议完善 <strong>{{ legacyPriorityItems.length }}</strong></span
          >
          <span class="analysis-count is-missing"
            >当前材料未体现 <strong>{{ legacyResult.missingSkills.length }}</strong></span
          >
          <span class="analysis-count is-matched"
            >已有优势 <strong>{{ legacyResult.strongMatches.length }}</strong></span
          >
        </div>
        <p>这是较早版本的分析结果。重新分析该岗位可以获得逐条可追溯的核对结果。</p>
      </section>

      <section class="analysis-section">
        <div class="analysis-section-header is-static">
          <span
            ><strong>优先处理</strong><small>{{ legacyPriorityItems.length }} 条</small></span
          >
        </div>
        <div v-if="legacyPriorityItems.length" class="analysis-list">
          <article
            v-for="(item, index) in legacyPriorityItems"
            :key="`${item.kind}-${item.title}-${index}`"
            class="analysis-item is-expanded"
          >
            <div class="analysis-item-heading">
              <span class="analysis-status is-partial">建议完善</span>
              <strong>{{ item.title || '相关经历' }}</strong>
            </div>
            <p class="analysis-conclusion">
              {{ item.description || '建议回看对应经历并补充真实场景。' }}
            </p>
            <div class="analysis-item-action">
              <el-button size="small" type="primary" @click="goToWorkspace()">进入编辑器</el-button>
            </div>
          </article>
        </div>
        <p v-else class="analysis-quiet-empty">当前没有明显的表达缺口。</p>
      </section>

      <section class="analysis-section analysis-strengths">
        <div class="analysis-section-header is-static">
          <span
            ><strong>已有优势</strong
            ><small>{{ legacyResult.strongMatches.length }} 条</small></span
          >
        </div>
        <div v-if="legacyResult.strongMatches.length" class="analysis-list is-strengths">
          <article
            v-for="(item, index) in legacyResult.strongMatches"
            :key="itemKey(item, index)"
            class="analysis-item is-expanded"
          >
            <div class="analysis-item-heading">
              <span class="analysis-status is-matched">已有优势</span>
              <strong>{{ item.item }}</strong>
            </div>
            <p class="analysis-conclusion">{{ item.reason }}</p>
          </article>
        </div>
        <p v-else class="analysis-quiet-empty">暂未识别到明确优势。</p>
      </section>

      <section class="analysis-section">
        <div class="analysis-section-header is-static">
          <span
            ><strong>当前材料未体现</strong
            ><small>{{ legacyResult.missingSkills.length }} 条</small></span
          >
        </div>
        <div v-if="legacyResult.missingSkills.length" class="analysis-list">
          <article
            v-for="(item, index) in legacyResult.missingSkills"
            :key="itemKey(item, index)"
            class="analysis-item is-expanded"
          >
            <div class="analysis-item-heading">
              <span class="analysis-status is-missing">当前材料未体现</span>
              <strong>{{ item.item }}</strong>
            </div>
            <p class="analysis-conclusion">{{ item.reason }}</p>
            <p class="analysis-boundary">如确有真实经历，可手动补充；不要写入未经证实的内容。</p>
          </article>
        </div>
        <p v-else class="analysis-quiet-empty">主要岗位要求在简历中已有体现。</p>
      </section>

      <section v-if="legacyResult.riskNotes.length" class="analysis-notes">
        <h2>需要你确认</h2>
        <ul>
          <li v-for="note in legacyResult.riskNotes" :key="note">{{ note }}</li>
        </ul>
      </section>
    </template>

    <EmptyState v-else title="分析结果尚未生成" description="请从首页重新开始一次岗位分析。" />
  </section>
</template>

<style scoped>
.analysis-result-page {
  display: grid;
  gap: 22px;
}

.analysis-overview {
  display: grid;
  gap: 10px;
  padding-bottom: 2px;
}

.analysis-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  align-items: center;
}

.analysis-count {
  color: var(--app-text-secondary);
  font-size: 14px;
}

.analysis-count strong {
  color: var(--app-text);
  font-size: 17px;
}

.analysis-count.is-action::before,
.analysis-count.is-missing::before,
.analysis-count.is-matched::before {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  content: '';
}

.analysis-count.is-action::before {
  background: var(--app-primary);
}
.analysis-count.is-missing::before {
  background: var(--app-warning);
}
.analysis-count.is-matched::before {
  background: var(--app-success);
}

.analysis-overview p,
.analysis-section-note {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.analysis-section {
  display: grid;
  gap: 10px;
}

.analysis-section-header {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 0;
  padding: 0;
  color: var(--app-text);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.analysis-section-header > span:first-child {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.analysis-section-header strong {
  font-size: 18px;
  line-height: 1.4;
}

.analysis-section-header small {
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 500;
}

.analysis-section-header.is-static,
.analysis-section-header:disabled {
  cursor: default;
}

.section-toggle,
.item-toggle-label {
  flex: 0 0 auto;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
}

.analysis-list {
  display: grid;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
}

.analysis-item {
  display: grid;
  gap: 0;
}

.analysis-item + .analysis-item {
  border-top: 1px solid var(--app-border-soft);
}

.analysis-item-toggle {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: start;
  width: 100%;
  border: 0;
  padding: 17px 18px;
  color: var(--app-text);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.analysis-item-toggle:hover,
.analysis-item-toggle:focus-visible {
  background: var(--app-surface-soft);
}

.analysis-item-heading {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 10px;
  align-items: baseline;
  min-width: 0;
}

.analysis-item-heading strong {
  flex: 1 1 100%;
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.6;
}

.analysis-item-heading > strong:first-child:last-child {
  flex: 1 1 auto;
}

.analysis-status,
.analysis-importance {
  font-size: 12px;
  font-weight: 700;
}

.analysis-status.is-partial {
  color: var(--app-primary);
}
.analysis-status.is-missing {
  color: var(--app-warning);
}
.analysis-status.is-matched {
  color: var(--app-success);
}
.analysis-status.is-unknown {
  color: var(--app-danger);
}
.analysis-importance {
  color: var(--app-text-muted);
  font-weight: 600;
}

.analysis-item-detail {
  display: grid;
  gap: 10px;
  padding: 0 18px 18px;
}

.analysis-detail-block {
  display: grid;
  gap: 7px;
}

.analysis-detail-label {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.evidence-list {
  display: grid;
  gap: 5px;
  padding: 10px 12px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
}

.evidence-list p,
.analysis-conclusion,
.analysis-suggestion,
.analysis-boundary,
.detail-muted {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.evidence-list span {
  color: var(--app-text);
  font-weight: 700;
}

.analysis-conclusion {
  color: var(--app-text);
}

.analysis-suggestion {
  color: var(--app-text);
  font-weight: 600;
}

.analysis-boundary {
  color: var(--app-text-secondary);
}

.analysis-item-action {
  display: flex;
  gap: 8px;
  padding-top: 2px;
}

.analysis-quiet-empty {
  margin: 0;
  padding: 13px 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.analysis-strengths .analysis-list {
  border-color: var(--app-border-soft);
}

.analysis-strengths .analysis-item-toggle {
  padding-top: 14px;
  padding-bottom: 14px;
}

.analysis-strengths .analysis-item-detail {
  padding-bottom: 14px;
}

.analysis-unknown {
  padding-top: 2px;
}

.analysis-unknown .analysis-list {
  border-color: var(--el-color-warning-light-7);
}

.analysis-notes {
  display: grid;
  gap: 8px;
}

.analysis-notes h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.analysis-notes ul {
  display: grid;
  gap: 7px;
  margin: 0;
  padding-left: 20px;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .analysis-result-page {
    gap: 20px;
  }

  .analysis-counts {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .analysis-count {
    display: grid;
    gap: 2px;
    font-size: 12px;
  }

  .analysis-count strong {
    font-size: 16px;
  }

  .analysis-count.is-action::before,
  .analysis-count.is-missing::before,
  .analysis-count.is-matched::before {
    margin-bottom: 2px;
  }

  .analysis-item-toggle {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
    padding: 15px 14px;
  }

  .item-toggle-label {
    justify-self: start;
  }

  .analysis-item-detail {
    padding: 0 14px 15px;
  }
}
</style>
