<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseCard from '@/components/common/BaseCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import AssetReadinessCard from '@/components/workflow/AssetReadinessCard.vue'
import NextActionCard from '@/components/workflow/NextActionCard.vue'
import ProcessStepper from '@/components/workflow/ProcessStepper.vue'
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

const router = useRouter()
const authStore = useAuthStore()

const resumes = ref<ResumeListItem[]>([])
const targetJobs = ref<JobDescriptionDetail[]>([])
const aiResults = ref<AiResultRecord[]>([])
const dashboardLoading = ref(false)

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

const statusType = (status: string) => {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'warning'
}

const formatDateTime = (value: string | null) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
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
      title="围绕目标岗位推进简历优化闭环"
      description="首页只保留当前状态、下一步主动作和最近结果。具体诊断、匹配、建议和局部改写进入对应业务页完成。"
    >
      <template #actions>
        <el-button :loading="dashboardLoading" @click="refreshDashboard">刷新</el-button>
      </template>
    </PageHeader>

    <section v-loading="dashboardLoading" class="home-workspace">
      <section class="home-hero-grid">
        <NextActionCard
          :title="nextStep.title"
          :description="nextStep.description"
          :action-text="nextStep.actionText"
          @action="router.push(nextStep.route)"
        />
        <AssetReadinessCard :items="readinessItems" />
      </section>

      <BaseCard title="主流程进度" subtitle="从简历资产到岗位优化报告，按当前可用数据推进。">
        <ProcessStepper :steps="flowSteps" />
      </BaseCard>

      <section class="home-summary-grid">
        <BaseCard title="最近匹配报告" subtitle="只展示最新一条摘要，完整内容进入匹配与优化。">
          <article v-if="latestMatchResult" class="home-result-summary">
            <el-tag :type="statusType(latestMatchResult.status)">
              {{ resultTypeText(latestMatchResult.resultType) }}
            </el-tag>
            <strong>{{ latestMatchResult.jobTitle || latestMatchResult.title || '匹配分析' }}</strong>
            <p>{{ latestMatchResult.summary || latestMatchResult.resumeName || '暂无摘要' }}</p>
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

        <BaseCard title="最近 AI 结果" subtitle="最多展示 3 条，AI 历史只负责回看。">
          <div v-if="recentResults.length" class="home-recent-list">
            <button
              v-for="item in recentResults"
              :key="`${item.resultType}-${item.recordId}`"
              type="button"
              @click="router.push('/history')"
            >
              <span>
                <el-tag size="small" :type="statusType(item.status)">{{ resultTypeText(item.resultType) }}</el-tag>
                <strong>{{ item.title || '-' }}</strong>
              </span>
              <small>{{ item.summary || formatDateTime(item.updatedAt || item.createdAt) }}</small>
            </button>
          </div>
          <EmptyState
            v-else
            title="暂无 AI 结果"
            description="完成简历诊断、目标岗位解析或匹配分析后会出现在这里。"
          />
        </BaseCard>
      </section>

      <BaseCard title="辅助入口" subtitle="岗位库只是参考入口，不作为主流程必经节点。">
        <div class="home-secondary-links">
          <el-button text type="primary" @click="router.push('/jobs')">岗位库参考</el-button>
          <el-button text type="primary" @click="router.push('/resumes')">查看全部简历</el-button>
          <el-button text type="primary" @click="router.push('/history')">AI 历史回看</el-button>
        </div>
      </BaseCard>
    </section>
  </section>
</template>
