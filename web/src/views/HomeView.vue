<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseCard from '@/components/common/BaseCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ProcessStepper from '@/components/common/ProcessStepper.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import NextActionCard from '@/components/workflow/NextActionCard.vue'
import { getAiResultPage } from '@/api/history'
import { getJobDescriptionList } from '@/api/job-description'
import { getResumeList } from '@/api/resume'
import { useAuthStore } from '@/stores/auth'
import type { AiResultRecord } from '@/types/history'
import type { JobDescriptionDetail } from '@/types/job-description'
import type { ResumeListItem } from '@/types/resume'

interface AssetReadinessItem {
  label: string
  value: string | number
  status: 'ready' | 'pending' | 'warning'
  description?: string
}

interface ProcessStep {
  title: string
  description?: string
  status?: 'done' | 'current' | 'pending' | 'failed'
}

interface DashboardMetricCard {
  label: string
  value: string | number
  note: string
  status: 'ready' | 'pending' | 'warning'
}

const router = useRouter()
const authStore = useAuthStore()

const resumes = ref<ResumeListItem[]>([])
const targetJobs = ref<JobDescriptionDetail[]>([])
const aiResults = ref<AiResultRecord[]>([])
const dashboardLoading = ref(false)

const dashboardColdLoading = computed(() => {
  return dashboardLoading.value && resumes.value.length === 0 && targetJobs.value.length === 0 && aiResults.value.length === 0
})

const parsedTargetJobs = computed(() => targetJobs.value.filter((item) => item.parseStatus === 'SUCCESS'))

const successfulDiagnosisCount = computed(() => {
  return aiResults.value.filter((item) => item.resultType === 'RESUME_DIAGNOSIS' && item.status === 'SUCCESS').length
})

const successfulMatchCount = computed(() => {
  return aiResults.value.filter((item) => item.resultType === 'MATCH_ANALYSIS' && item.status === 'SUCCESS').length
})

const successfulSuggestionCount = computed(() => {
  return aiResults.value.filter((item) => item.resultType === 'JOB_OPTIMIZATION_SUGGESTION' && item.status === 'SUCCESS').length
})

const latestMatchResult = computed(() => {
  return aiResults.value.find((item) => item.resultType === 'MATCH_ANALYSIS' && item.status === 'SUCCESS') ?? null
})

const recentResults = computed(() => aiResults.value.slice(0, 3))

const readinessItems = computed<AssetReadinessItem[]>(() => [
  {
    label: '简历资产',
    value: resumes.value.length,
    status: resumes.value.length > 0 ? 'ready' : 'pending',
    description: resumes.value.length > 0 ? '可进入解析和诊断' : '先上传第一份简历',
  },
  {
    label: '目标岗位',
    value: `${parsedTargetJobs.value.length}/${targetJobs.value.length}`,
    status: parsedTargetJobs.value.length > 0 ? 'ready' : targetJobs.value.length > 0 ? 'warning' : 'pending',
    description: targetJobs.value.length > 0 ? '解析后可用于匹配' : '粘贴真实 JD',
  },
  {
    label: '匹配分析',
    value: successfulMatchCount.value,
    status: successfulMatchCount.value > 0 ? 'ready' : 'pending',
    description: successfulMatchCount.value > 0 ? '已有可回看的结果' : '等待简历和岗位就绪',
  },
])

const metricCards = computed<DashboardMetricCard[]>(() => [
  {
    label: '简历资产',
    value: resumes.value.length,
    note: resumes.value.length > 0 ? '可进入诊断和匹配' : '等待上传',
    status: resumes.value.length > 0 ? 'ready' : 'pending',
  },
  {
    label: '目标岗位',
    value: `${parsedTargetJobs.value.length}/${targetJobs.value.length}`,
    note: targetJobs.value.length > 0 ? '已解析 / 全部岗位' : '等待新增 JD',
    status: parsedTargetJobs.value.length > 0 ? 'ready' : targetJobs.value.length > 0 ? 'warning' : 'pending',
  },
  {
    label: '匹配报告',
    value: successfulMatchCount.value,
    note: successfulMatchCount.value > 0 ? '可回看匹配依据' : '流程推进后生成',
    status: successfulMatchCount.value > 0 ? 'ready' : 'pending',
  },
  {
    label: '优化建议',
    value: successfulSuggestionCount.value,
    note: successfulSuggestionCount.value > 0 ? '可继续局部改写' : '匹配后生成建议',
    status: successfulSuggestionCount.value > 0 ? 'ready' : 'pending',
  },
])

const nextStep = computed(() => {
  if (resumes.value.length === 0) {
    return {
      title: '上传第一份简历',
      description: '先建立简历资产。解析、诊断、匹配和局部改写都基于已上传简历继续。',
      actionText: '去上传简历',
      route: '/resumes',
    }
  }

  if (successfulDiagnosisCount.value === 0) {
    return {
      title: '生成简历诊断',
      description: '先确认简历本身质量，再进入目标岗位匹配。诊断只分析简历，不判断岗位适配。',
      actionText: '进入我的简历',
      route: '/resumes',
    }
  }

  if (targetJobs.value.length === 0) {
    return {
      title: '新增目标岗位',
      description: '粘贴真实招聘 JD，系统会保存为你的目标岗位，不进入系统岗位库。',
      actionText: '新增目标岗位',
      route: '/job-descriptions/new',
    }
  }

  if (parsedTargetJobs.value.length === 0) {
    return {
      title: '解析目标岗位',
      description: '先抽取岗位职责、技能、关键词和经验要求，再进入匹配分析。',
      actionText: '查看目标岗位',
      route: '/job-descriptions',
    }
  }

  if (successfulMatchCount.value === 0) {
    return {
      title: '开始匹配分析',
      description: '选择一份简历和一个已解析目标岗位，生成匹配分、强弱项、缺失技能和风险提示。',
      actionText: '进入匹配与优化',
      route: '/ai-job-matches',
    }
  }

  if (successfulSuggestionCount.value === 0) {
    return {
      title: '生成岗位优化建议',
      description: '基于匹配差距生成策略建议。系统不会自动改写或写回原始简历。',
      actionText: '继续优化',
      route: '/ai-job-matches',
    }
  }

  return {
    title: '查看岗位优化报告',
    description: '回看匹配分析、优化建议、局部改写和下一步修改清单。',
    actionText: '查看匹配与优化',
    route: '/ai-job-matches',
  }
})

const flowSteps = computed(() => {
  const steps = [
    {
      title: '上传简历',
      description: `${resumes.value.length} 份`,
      done: resumes.value.length > 0,
    },
    {
      title: '简历诊断',
      description: `${successfulDiagnosisCount.value} 条成功结果`,
      done: successfulDiagnosisCount.value > 0,
    },
    {
      title: '目标岗位',
      description: `${targetJobs.value.length} 个，已解析 ${parsedTargetJobs.value.length} 个`,
      done: parsedTargetJobs.value.length > 0,
    },
    {
      title: '匹配分析',
      description: `${successfulMatchCount.value} 条成功结果`,
      done: successfulMatchCount.value > 0,
    },
    {
      title: '优化建议',
      description: `${successfulSuggestionCount.value} 条成功结果`,
      done: successfulSuggestionCount.value > 0,
    },
  ]
  const currentIndex = Math.max(steps.findIndex((item) => !item.done), 0)

  return steps.map<ProcessStep>((item, index) => ({
    title: item.title,
    description: item.description,
    status: item.done ? 'done' : index === currentIndex ? 'current' : 'pending',
  }))
})

const resultTypeText = (resultType: string) => {
  const resultTypeMap: Record<string, string> = {
    RESUME_DIAGNOSIS: '简历诊断',
    TARGET_JOB_PARSE: '目标岗位解析',
    MATCH_ANALYSIS: '匹配分析',
    JOB_OPTIMIZATION_SUGGESTION: '岗位优化建议',
    LOCAL_REWRITE: '局部改写',
  }

  return resultTypeMap[resultType] ?? resultType
}

const formatDateTime = (value: string | null) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const normalizeSummary = (summary: string | null | undefined, fallback = '暂无摘要') => {
  const value = summary?.trim()
  if (!value) {
    return fallback
  }

  if (value.startsWith('{') || value.startsWith('[')) {
    return '已生成结构化结果，进入详情查看。'
  }

  return value.length > 80 ? `${value.slice(0, 80)}...` : value
}

const loadDashboard = async () => {
  if (!authStore.isAuthenticated) {
    return
  }

  dashboardLoading.value = true

  try {
    const [resumeResult, targetJobResult, aiResult] = await Promise.all([
      getResumeList(),
      getJobDescriptionList(),
      getAiResultPage({ page: 1, size: 8 }),
    ])

    resumes.value = resumeResult
    targetJobs.value = targetJobResult
    aiResults.value = aiResult.records
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载工作台数据失败')
  } finally {
    dashboardLoading.value = false
  }
}

const refreshDashboard = async () => {
  await loadDashboard()
  ElMessage.success('工作台已刷新')
}

onMounted(async () => {
  if (authStore.isAuthenticated && !authStore.currentUser) {
    await authStore.fetchMe().catch(() => undefined)
  }
  await loadDashboard()
})
</script>

<template>
  <section class="home-page">
    <PageHeader
      eyebrow="AI 求职工作台"
      title="继续优化你的求职材料"
      :description="`当前推荐动作：${nextStep.title}`"
    >
      <template #actions>
        <el-button :loading="dashboardLoading" @click="refreshDashboard">刷新</el-button>
      </template>
    </PageHeader>

    <section class="home-workspace">
      <SkeletonBlock v-if="dashboardColdLoading" title :rows="8" />

      <template v-else>
      <section class="home-hero-grid">
        <NextActionCard
          :title="nextStep.title"
          :description="nextStep.description"
          :action-text="nextStep.actionText"
          @action="router.push(nextStep.route)"
        />

        <div class="home-hero-side">
          <BaseCard title="当前状态" subtitle="确认资产是否可用于匹配。">
            <div class="home-readiness-compact">
              <article
                v-for="item in readinessItems"
                :key="item.label"
                :class="`is-${item.status}`"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
                <small>{{ item.description }}</small>
              </article>
            </div>
          </BaseCard>

        </div>
      </section>

      <BaseCard title="主流程进度" subtitle="按顺序完成简历、岗位和匹配分析。">
        <ProcessStepper :steps="flowSteps" />
      </BaseCard>

      <section class="home-summary-grid">
        <BaseCard title="最近一次匹配结果" subtitle="进入匹配与优化查看完整结论。">
          <article v-if="latestMatchResult" class="home-result-summary">
            <StatusTag :status="latestMatchResult.status" />
            <strong>{{ latestMatchResult.jobTitle || latestMatchResult.title || '匹配分析' }}</strong>
            <p>{{ normalizeSummary(latestMatchResult.summary, latestMatchResult.resumeName || '暂无摘要') }}</p>
            <el-button
              type="primary"
              plain
              @click="router.push({
                path: '/ai-job-matches',
                query: {
                  ...(latestMatchResult.resumeId ? { resumeId: String(latestMatchResult.resumeId) } : {}),
                  ...(latestMatchResult.jobDescriptionId ? { jobDescriptionId: String(latestMatchResult.jobDescriptionId) } : {}),
                },
              })"
            >
              查看匹配与优化
            </el-button>
          </article>
          <EmptyState
            v-else
            title="还没有匹配报告"
            description="简历和目标岗位都解析完成后，可以生成第一份匹配分析。"
            action-text="进入匹配与优化"
            @action="router.push('/ai-job-matches')"
          />
        </BaseCard>

        <BaseCard title="最近 AI 结果" subtitle="用于快速回看，不触发新的生成。">
          <div v-if="recentResults.length" class="home-recent-list">
            <button
              v-for="item in recentResults"
              :key="`${item.resultType}-${item.recordId}`"
              type="button"
              @click="router.push('/history')"
            >
              <span>
                <StatusTag :status="item.status" />
              <small>{{ resultTypeText(item.resultType) }}</small>
                <strong>{{ item.title || '-' }}</strong>
              </span>
              <small>{{ normalizeSummary(item.summary, formatDateTime(item.updatedAt || item.createdAt)) }}</small>
            </button>
          </div>
          <EmptyState
            v-else
            title="暂无 AI 结果"
            description="完成简历诊断、目标岗位解析或匹配分析后会出现在这里。"
          />
        </BaseCard>
      </section>

      <section class="home-overview-grid" aria-label="工作台数据概览">
        <article
          v-for="item in metricCards"
          :key="item.label"
          class="home-overview-card"
          :class="`is-${item.status}`"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </article>
      </section>

      <BaseCard title="辅助入口" subtitle="需要参考岗位要求？先看看岗位库。">
        <div class="home-secondary-links">
          <el-button text type="primary" @click="router.push('/jobs')">岗位库参考</el-button>
          <el-button text type="primary" @click="router.push('/resumes')">查看全部简历</el-button>
          <el-button text type="primary" @click="router.push('/history')">AI 历史回看</el-button>
        </div>
      </BaseCard>
      </template>
    </section>
  </section>
</template>
