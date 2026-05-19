<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getAiResultPage } from '@/api/history'
import { getJobDescriptionList } from '@/api/job-description'
import { getResumeList } from '@/api/resume'
import { useAuthStore } from '@/stores/auth'
import type { AiResultRecord } from '@/types/history'
import type { JobDescriptionDetail } from '@/types/job-description'
import type { ResumeListItem } from '@/types/resume'

const router = useRouter()
const authStore = useAuthStore()

const resumes = ref<ResumeListItem[]>([])
const targetJobs = ref<JobDescriptionDetail[]>([])
const aiResults = ref<AiResultRecord[]>([])
const dashboardLoading = ref(false)

const displayName = computed(() => {
  const user = authStore.currentUser

  return user?.nickname || user?.username || '已登录用户'
})

const parsedTargetJobs = computed(() => {
  return targetJobs.value.filter((item) => item.parseStatus === 'SUCCESS')
})

const latestAiResult = computed(() => {
  return aiResults.value[0] ?? null
})

const successfulMatchCount = computed(() => {
  return aiResults.value.filter((item) => item.resultType === 'MATCH_ANALYSIS' && item.status === 'SUCCESS').length
})

const successfulSuggestionCount = computed(() => {
  return aiResults.value.filter((item) => item.resultType === 'JOB_OPTIMIZATION_SUGGESTION' && item.status === 'SUCCESS').length
})

const progressItems = computed(() => [
  {
    label: '简历',
    detail: `${resumes.value.length} 份`,
    done: resumes.value.length > 0,
    route: '/resumes',
  },
  {
    label: '目标岗位',
    detail: `${targetJobs.value.length} 个，已解析 ${parsedTargetJobs.value.length} 个`,
    done: parsedTargetJobs.value.length > 0,
    route: '/job-descriptions',
  },
  {
    label: '匹配分析',
    detail: `${successfulMatchCount.value} 条成功结果`,
    done: successfulMatchCount.value > 0,
    route: '/ai-job-matches',
  },
  {
    label: '岗位优化建议',
    detail: `${successfulSuggestionCount.value} 条成功结果`,
    done: successfulSuggestionCount.value > 0,
    route: '/ai-job-matches',
  },
])

const nextStep = computed(() => {
  if (resumes.value.length === 0) {
    return {
      title: '上传并解析简历',
      description: '先建立简历资产，后续诊断、匹配和改写都基于已解析简历。',
      button: '去我的简历',
      route: '/resumes',
    }
  }
  if (targetJobs.value.length === 0) {
    return {
      title: '新增目标岗位',
      description: '粘贴真实招聘 JD，系统只保存为你的目标岗位，不写入系统岗位库。',
      button: '新增目标岗位',
      route: '/job-descriptions/new',
    }
  }
  if (parsedTargetJobs.value.length === 0) {
    return {
      title: '解析目标岗位',
      description: '先抽取岗位职责、技能和关键词，再进入匹配分析。',
      button: '查看目标岗位',
      route: '/job-descriptions',
    }
  }
  if (successfulMatchCount.value === 0) {
    return {
      title: '生成匹配分析',
      description: '选择已解析简历和目标岗位，判断当前简历是否匹配。',
      button: '进入匹配与优化',
      route: '/ai-job-matches',
    }
  }
  if (successfulSuggestionCount.value === 0) {
    return {
      title: '生成岗位优化建议',
      description: '基于匹配差距生成策略建议，不直接改写原简历。',
      button: '继续匹配与优化',
      route: '/ai-job-matches',
    }
  }
  return {
    title: '回看 AI 结果',
    description: '已生成的诊断、解析、匹配、建议和改写结果可以从 AI 历史统一回看。',
    button: '查看 AI 历史',
    route: '/history',
  }
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
  return 'info'
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
      getAiResultPage({ page: 1, size: 5 }),
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

const handleRefresh = async () => {
  try {
    await authStore.fetchMe()
    await loadDashboard()
    ElMessage.success('工作台已刷新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新工作台失败')
  }
}

const handleLogout = () => {
  authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(async () => {
  if (authStore.isAuthenticated && !authStore.currentUser) {
    await authStore.fetchMe().catch(() => undefined)
  }
  await loadDashboard()
})
</script>

<template>
  <main class="home-page">
    <section class="home-shell">
      <header class="home-header">
        <div>
          <h1 class="home-title">工作台</h1>
          <p class="home-subtitle">围绕简历资产和用户粘贴的目标岗位 JD，串联诊断、匹配、建议、改写和结果回看。</p>
        </div>
        <el-space v-if="!authStore.isAuthenticated" wrap>
          <el-button @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </el-space>
        <el-space v-else wrap>
          <el-button @click="router.push('/resumes')">我的简历</el-button>
          <el-button @click="router.push('/job-descriptions')">目标岗位</el-button>
          <el-button @click="router.push('/ai-job-matches')">匹配与优化</el-button>
          <el-button @click="router.push('/history')">AI 历史</el-button>
          <el-button :loading="authStore.loading || dashboardLoading" @click="handleRefresh">刷新</el-button>
          <el-button type="danger" plain @click="handleLogout">退出</el-button>
        </el-space>
      </header>

      <template v-if="authStore.isAuthenticated">
        <section v-loading="dashboardLoading" class="home-dashboard">
          <el-card class="home-card" shadow="never">
            <template #header>
              <div class="home-card-header">
                <span>当前进度</span>
                <el-tag type="info">{{ displayName }}</el-tag>
              </div>
            </template>
            <div class="home-progress-grid">
              <button
                v-for="item in progressItems"
                :key="item.label"
                class="home-progress-item"
                type="button"
                @click="router.push(item.route)"
              >
                <el-tag :type="item.done ? 'success' : 'info'">{{ item.done ? '已就绪' : '待处理' }}</el-tag>
                <strong>{{ item.label }}</strong>
                <span>{{ item.detail }}</span>
              </button>
            </div>
          </el-card>

          <el-card class="home-card" shadow="never">
            <template #header>
              <div class="home-card-header">
                <span>下一步推荐</span>
              </div>
            </template>
            <h2 class="home-section-title">{{ nextStep.title }}</h2>
            <p class="home-muted">{{ nextStep.description }}</p>
            <el-button type="primary" @click="router.push(nextStep.route)">{{ nextStep.button }}</el-button>
          </el-card>

          <el-card class="home-card" shadow="never">
            <template #header>
              <div class="home-card-header">
                <span>快捷入口</span>
              </div>
            </template>
            <div class="home-entry-grid">
              <el-button @click="router.push('/resumes')">我的简历</el-button>
              <el-button @click="router.push('/job-descriptions/new')">新增目标岗位</el-button>
              <el-button @click="router.push('/ai-job-matches')">匹配与优化</el-button>
              <el-button @click="router.push('/history')">AI 历史</el-button>
              <el-button plain @click="router.push('/jobs')">岗位库参考</el-button>
            </div>
          </el-card>

          <el-card class="home-card" shadow="never">
            <template #header>
              <div class="home-card-header">
                <span>最近结果</span>
                <el-button text type="primary" @click="router.push('/history')">查看全部</el-button>
              </div>
            </template>
            <template v-if="latestAiResult">
              <div class="home-result">
                <el-tag :type="statusType(latestAiResult.status)">{{ resultTypeText(latestAiResult.resultType) }}</el-tag>
                <strong>{{ latestAiResult.title }}</strong>
                <span>{{ latestAiResult.summary || '暂无摘要' }}</span>
                <small>{{ formatDateTime(latestAiResult.updatedAt || latestAiResult.createdAt) }}</small>
              </div>
            </template>
            <el-empty v-else description="暂无 AI 结果" :image-size="80" />
          </el-card>
        </section>
      </template>

      <el-card v-else class="home-card" shadow="never">
        <h2 class="home-section-title">登录后进入工作台</h2>
        <p class="home-muted">当前没有检测到 Token，请先登录后再继续简历优化流程。</p>
      </el-card>
    </section>
  </main>
</template>
