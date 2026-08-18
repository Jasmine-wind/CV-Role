<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import { getOptimizationAnalysisResult } from '@/api/job-analysis'
import type { AiJobMatchItem } from '@/types/ai-job-match'
import type { OptimizationAnalysisResult } from '@/types/job-analysis'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref<string | null>(null)
const optimizationResult = ref<OptimizationAnalysisResult | null>(null)

const optimizationTaskId = computed(() => parsePositiveId(route.params.optimizationTaskId))
const result = computed(() => optimizationResult.value?.analysis ?? null)

const priorityItems = computed(() => {
  const match = result.value
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
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

const parsePositiveId = (value: unknown) => {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

const itemKey = (item: AiJobMatchItem, index: number) => `${item.item}-${index}`

onMounted(loadResult)
</script>

<template>
  <section class="analysis-result-page">
    <PageHeader
      eyebrow="岗位分析"
      :title="optimizationResult?.jobTitle || '岗位分析结果'"
      :description="optimizationResult?.resumeName ? `基于 ${optimizationResult.resumeName} 与目标岗位要求整理` : '基于你的真实简历与目标岗位要求整理'"
    >
      <template #actions>
        <el-button @click="router.push('/app')">分析新岗位</el-button>
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

    <template v-else-if="result">
      <section class="analysis-summary app-card">
        <div>
          <span>分析结论</span>
          <strong>已找到 {{ result.strongMatches.length }} 项已有优势</strong>
          <p>
            {{ priorityItems.length }} 项值得优先检查，{{ result.missingSkills.length }} 项在当前简历中尚未体现。
          </p>
        </div>
        <p>以下结论只基于当前简历中的真实内容。没有证据的岗位要求不会被自动写入简历。</p>
      </section>

      <section class="analysis-section">
        <header>
          <div>
            <span>优先修改</span>
            <h2>现有表达与岗位要求仍有差距</h2>
          </div>
          <strong>{{ priorityItems.length }}</strong>
        </header>
        <div v-if="priorityItems.length" class="analysis-list">
          <article v-for="(item, index) in priorityItems" :key="`${item.kind}-${item.title}-${index}`" class="app-card">
            <span>{{ item.kind === 'expression' ? '表达可以更清楚' : '建议核对相关经历' }}</span>
            <h3>{{ item.title || '相关经历' }}</h3>
            <p>{{ item.description || '建议回看对应经历并补充真实场景。' }}</p>
          </article>
        </div>
        <EmptyState v-else title="当前没有明显的表达缺口" description="现有简历已经较清楚地覆盖了主要岗位要求。" />
      </section>

      <section class="analysis-section">
        <header>
          <div>
            <span>已有优势</span>
            <h2>简历中已经有证据支持</h2>
          </div>
          <strong>{{ result.strongMatches.length }}</strong>
        </header>
        <div v-if="result.strongMatches.length" class="analysis-list is-compact">
          <article v-for="(item, index) in result.strongMatches" :key="itemKey(item, index)" class="app-card">
            <h3>{{ item.item }}</h3>
            <p>{{ item.reason }}</p>
          </article>
        </div>
        <EmptyState v-else title="暂未识别到明确优势" description="这不代表你没有相关经历，可以检查简历是否遗漏了重要事实。" />
      </section>

      <section class="analysis-section">
        <header>
          <div>
            <span>简历当前未体现</span>
            <h2>需要你确认是否有真实经历</h2>
          </div>
          <strong>{{ result.missingSkills.length }}</strong>
        </header>
        <div v-if="result.missingSkills.length" class="analysis-list is-compact">
          <article v-for="(item, index) in result.missingSkills" :key="itemKey(item, index)" class="app-card is-gap">
            <h3>{{ item.item }}</h3>
            <p>{{ item.reason }}</p>
            <small>系统目前只能确认简历中没有证据；除非你确实有相关经历，否则不要加入简历。</small>
          </article>
        </div>
        <EmptyState v-else title="主要岗位要求在简历中已有体现" description="后续仍应结合真实经历检查表达是否准确。" />
      </section>

      <section v-if="result.riskNotes.length" class="analysis-notes app-card">
        <h2>需要你确认</h2>
        <ul>
          <li v-for="note in result.riskNotes" :key="note">{{ note }}</li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.analysis-result-page {
  display: grid;
  gap: 32px;
}

.analysis-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.7fr);
  gap: 28px;
  align-items: center;
  padding: 28px;
}

.analysis-summary > div {
  display: grid;
  gap: 8px;
}

.analysis-summary span,
.analysis-section header span {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
}

.analysis-summary strong {
  color: var(--app-navy);
  font-size: 26px;
}

.analysis-summary p,
.analysis-list p {
  margin: 0;
  color: var(--app-text-secondary);
  line-height: 1.7;
}

.analysis-section {
  display: grid;
  gap: 16px;
}

.analysis-section > header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}

.analysis-section h2,
.analysis-notes h2 {
  margin: 5px 0 0;
  color: var(--app-navy);
  font-size: 22px;
}

.analysis-section > header > strong {
  color: var(--app-primary);
  font-size: 28px;
}

.analysis-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.analysis-list.is-compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.analysis-list article {
  display: grid;
  gap: 8px;
  align-content: start;
  min-height: 150px;
  padding: 20px;
}

.analysis-list article > span,
.analysis-list article > small {
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.analysis-list article.is-gap {
  border-color: var(--el-color-warning-light-7);
}

.analysis-list h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 17px;
}

.analysis-notes {
  padding: 22px;
}

.analysis-notes ul {
  display: grid;
  gap: 8px;
  margin: 14px 0 0;
  padding-left: 20px;
  color: var(--app-text-secondary);
  line-height: 1.6;
}

@media (max-width: 900px) {
  .analysis-summary,
  .analysis-list,
  .analysis-list.is-compact {
    grid-template-columns: 1fr;
  }
}
</style>
