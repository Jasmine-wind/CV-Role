<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getJobDirectionInsights } from '@/api/job-direction-insight'
import ErrorState from '@/components/common/ErrorState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import type {
  EvidenceCoverageLevel,
  JobDirectionCohort,
  JobDirectionInsights,
} from '@/types/job-direction-insight'

const router = useRouter()
const insights = ref<JobDirectionInsights | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const load = async () => {
  loading.value = true
  error.value = null
  try {
    insights.value = await getJobDirectionInsights()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '暂时无法读取方向洞察'
  } finally {
    loading.value = false
  }
}

const coverageLabel = (level: EvidenceCoverageLevel) => {
  switch (level) {
    case 'MATCHED': return '已有优势'
    case 'PARTIAL_EVIDENCE': return '建议完善'
    case 'NO_EVIDENCE': return '当前材料未体现'
  }
}

const windowText = (cohort: JobDirectionCohort) => {
  const start = cohort.windowStart?.slice(0, 10) ?? ''
  const end = cohort.newestAnalysisAt?.slice(0, 10) ?? ''
  return start && end ? `${start} 至 ${end}` : '最近 180 天'
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="job-direction-insights">
    <PageHeader
      title="岗位方向洞察"
      description="系统只汇总同一份冻结简历材料下的正式岗位分析，帮助你看清近期岗位中反复出现的要求。"
    >
      <template #actions>
        <el-button @click="router.push('/app')">返回首页</el-button>
      </template>
    </PageHeader>

    <SkeletonBlock v-if="loading" title :rows="10" />

    <ErrorState
      v-else-if="error"
      title="方向洞察加载失败"
      :description="error"
      action-text="重新加载"
      @action="load"
    />

    <section v-else-if="!insights?.cohorts.length" class="insight-empty app-card">
      <h2>继续按岗位分析，洞察会自然出现</h2>
      <p>
        当同一份冻结简历材料在最近 180 天内完成至少 8 个不同岗位的正式分析后，
        系统会在这里汇总常见要求。它不会替你推断现实能力，也不会改变任何已有分析结果。
      </p>
      <el-button type="primary" @click="router.push('/app')">分析新岗位</el-button>
    </section>

    <div v-else class="cohort-list">
      <article v-for="cohort in insights.cohorts" :key="`${cohort.resumeId}-${cohort.newestAnalysisAt}`" class="cohort-card app-card">
        <header class="cohort-header">
          <div>
            <p class="cohort-eyebrow">基于冻结材料的近期岗位样本</p>
            <h2>{{ cohort.resumeName }}</h2>
            <p>{{ windowText(cohort) }} · {{ cohort.sampleSize }} 个不同岗位</p>
          </div>
          <span class="cohort-count">{{ cohort.sampleSize }} 个岗位</span>
        </header>

        <p class="cohort-note">
          下方状态只描述当时冻结的简历材料是否支持这些岗位要求；不会判断你的真实能力，也不会实时随工作区编辑变化。
        </p>

        <div v-if="cohort.commonRequirements.length" class="requirement-list">
          <article v-for="requirement in cohort.commonRequirements" :key="requirement.label" class="requirement-card">
            <div class="requirement-head">
              <div>
                <h3>{{ requirement.label }}</h3>
                <p>出现在 {{ requirement.occurrenceCount }} / {{ requirement.sampleSize }} 个岗位中</p>
              </div>
              <span class="requirement-frequency">{{ requirement.occurrenceCount }}/{{ requirement.sampleSize }}</span>
            </div>
            <div class="coverage-distribution" aria-label="冻结材料证据分布">
              <span v-if="requirement.matchedCount">已有优势 {{ requirement.matchedCount }}</span>
              <span v-if="requirement.partialEvidenceCount">建议完善 {{ requirement.partialEvidenceCount }}</span>
              <span v-if="requirement.noEvidenceCount">当前材料未体现 {{ requirement.noEvidenceCount }}</span>
            </div>
            <details class="requirement-trace">
              <summary>查看分析来源</summary>
              <ul>
                <li v-for="source in requirement.sources" :key="source.evidenceRequirementId">
                  <strong>{{ coverageLabel(source.matchLevel) }}：</strong>{{ source.requirementText }}
                  <p v-for="evidence in source.evidences" :key="evidence.requirementEvidenceId">
                    <span v-if="evidence.sectionLabel">{{ evidence.sectionLabel }}：</span>「{{ evidence.evidenceText }}」
                  </p>
                </li>
              </ul>
            </details>
          </article>
        </div>
        <p v-else class="no-common-requirements">
          当前样本中还没有达到“至少半数岗位出现”的保守共性要求。
        </p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.job-direction-insights,
.cohort-list {
  display: grid;
  gap: 24px;
}

.insight-empty,
.cohort-card {
  display: grid;
  gap: 16px;
  padding: 24px;
}

.insight-empty h2,
.cohort-header h2,
.requirement-card h3 {
  margin: 0;
  color: var(--app-text);
}

.insight-empty p,
.cohort-header p,
.cohort-note,
.requirement-head p,
.requirement-trace p,
.no-common-requirements {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.cohort-header,
.requirement-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.cohort-header > div,
.requirement-head > div {
  display: grid;
  gap: 5px;
}

.cohort-eyebrow {
  color: var(--app-primary) !important;
  font-size: 12px !important;
  font-weight: 700;
}

.cohort-count,
.requirement-frequency {
  flex: 0 0 auto;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 5px 10px;
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.cohort-note {
  border-left: 3px solid var(--app-border);
  padding-left: 12px;
}

.requirement-list {
  display: grid;
  gap: 12px;
}

.requirement-card {
  display: grid;
  gap: 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 16px;
  background: var(--app-surface);
}

.requirement-card h3 {
  font-size: 15px;
  line-height: 1.6;
}

.coverage-distribution {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.coverage-distribution span {
  padding: 4px 8px;
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-soft);
  color: var(--app-text-secondary);
  font-size: 12px;
}

.requirement-trace {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.requirement-trace summary {
  cursor: pointer;
  color: var(--app-primary);
  font-weight: 700;
}

.requirement-trace ul {
  display: grid;
  gap: 10px;
  margin: 10px 0 0;
  padding-left: 20px;
}

.requirement-trace li {
  line-height: 1.7;
}

.requirement-trace p {
  padding-left: 4px;
}

@media (max-width: 640px) {
  .insight-empty,
  .cohort-card {
    padding: 18px;
  }

  .cohort-header,
  .requirement-head {
    flex-direction: column;
  }
}
</style>
