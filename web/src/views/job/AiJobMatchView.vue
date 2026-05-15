<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAiJobMatch, getAiJobMatches, triggerAiJobMatch } from '@/api/ai-job-match'
import { getAiResumeSuggestionByMatchResult, triggerAiResumeSuggestion } from '@/api/ai-resume-suggestion'
import { getAiRewriteSuggestions, triggerAiRewriteSuggestion, updateAiRewriteAcceptStatus } from '@/api/ai-rewrite-suggestion'
import { getJobDescriptionList } from '@/api/job-description'
import { getResumeList, getResumeParseResult } from '@/api/resume'
import type { AiJobMatchResult } from '@/types/ai-job-match'
import type { AiResumeSuggestionItem, AiResumeSuggestionResult } from '@/types/ai-resume-suggestion'
import type { AiRewriteSuggestionResult } from '@/types/ai-rewrite-suggestion'
import type { JobDescriptionDetail } from '@/types/job-description'
import type { ResumeListItem, ResumeParseResult } from '@/types/resume'

const route = useRoute()
const router = useRouter()

const resumes = ref<ResumeListItem[]>([])
const jobDescriptions = ref<JobDescriptionDetail[]>([])
const selectedResumeId = ref<number | null>(null)
const selectedJobDescriptionId = ref<number | null>(null)
const selectedResumeParseResult = ref<ResumeParseResult | null>(null)
const selectedMatch = ref<AiJobMatchResult | null>(null)
const selectedSuggestion = ref<AiResumeSuggestionResult | null>(null)
const selectedRewriteSuggestion = ref<AiRewriteSuggestionResult | null>(null)
const matchResults = ref<AiJobMatchResult[]>([])
const rewriteSuggestions = ref<AiRewriteSuggestionResult[]>([])
const loading = ref(false)
const loadingResumeParse = ref(false)
const matching = ref(false)
const loadingResult = ref(false)
const generatingSuggestion = ref(false)
const loadingSuggestion = ref(false)
const rewriteDialogVisible = ref(false)
const generatingRewrite = ref(false)
const loadingRewriteSuggestions = ref(false)
const updatingRewriteAcceptStatus = ref(false)

const rewriteForm = reactive({
  rewriteType: 'PROJECT',
  targetSection: '项目经历',
  originalText: '',
})

const rewriteTypeOptions = [
  { label: '项目经历', value: 'PROJECT' },
  { label: '技能描述', value: 'SKILL' },
  { label: '实习或工作经历', value: 'INTERNSHIP' },
  { label: '个人总结', value: 'SUMMARY' },
  { label: '教育经历', value: 'EDUCATION' },
  { label: '其他', value: 'OTHER' },
]

const parsedJobDescriptions = computed(() => {
  return jobDescriptions.value.filter((item) => item.parseStatus === 'SUCCESS')
})

const selectedResume = computed(() => {
  return resumes.value.find((item) => item.id === selectedResumeId.value) || null
})

const selectedJobDescription = computed(() => {
  return jobDescriptions.value.find((item) => item.id === selectedJobDescriptionId.value) || null
})

const canMatch = computed(() => {
  return Boolean(selectedResumeId.value && selectedJobDescriptionId.value && selectedJobDescription.value?.parseStatus === 'SUCCESS')
})

const canGenerateSuggestion = computed(() => {
  return Boolean(
    selectedResumeId.value
    && selectedJobDescriptionId.value
    && selectedMatch.value?.matchId
    && selectedMatch.value.matchStatus === 'SUCCESS',
  )
})

const canGenerateRewrite = computed(() => {
  return Boolean(selectedResumeId.value && rewriteForm.rewriteType && rewriteForm.targetSection.trim() && rewriteForm.originalText.trim())
})

const highPrioritySuggestions = computed(() => {
  return suggestionsByPriority('HIGH')
})

const skillGapSuggestions = computed(() => {
  return suggestionsByType('SKILL_GAP')
})

const experienceSuggestions = computed(() => {
  return selectedSuggestion.value?.suggestions.filter((item) => {
    return item.type === 'EXPERIENCE_WEAKNESS' || item.type === 'PROJECT_DESCRIPTION'
  }) ?? []
})

const strengthSuggestions = computed(() => {
  return suggestionsByType('HIGHLIGHT_STRENGTH')
})

const generalSuggestions = computed(() => {
  return selectedSuggestion.value?.suggestions.filter((item) => {
    return item.type === 'STRUCTURE' || item.type === 'GENERAL'
  }) ?? []
})

const strengthAndGeneralSuggestions = computed(() => {
  return [...strengthSuggestions.value, ...generalSuggestions.value]
})

const formatDateTime = (value: string | null) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const resolveMatchStatusText = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return '匹配成功'
  }
  if (status === 'FAILED') {
    return '匹配失败'
  }
  if (status === 'PENDING') {
    return '待匹配'
  }
  return status || '-'
}

const resolveMatchStatusType = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

const resolveSuggestionStatusText = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return '建议生成成功'
  }
  if (status === 'FAILED') {
    return '建议生成失败'
  }
  if (status === 'PENDING') {
    return '待生成'
  }
  return status || '-'
}

const resolveSuggestionStatusType = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

const resolveSuggestionTypeText = (type: string | null | undefined) => {
  const typeMap: Record<string, string> = {
    SKILL_GAP: '技能缺口',
    EXPERIENCE_WEAKNESS: '经历表达不足',
    PROJECT_DESCRIPTION: '项目描述优化',
    HIGHLIGHT_STRENGTH: '优势突出',
    STRUCTURE: '结构优化',
    GENERAL: '综合建议',
  }
  return type ? typeMap[type] || type : '-'
}

const resolvePriorityType = (priority: string | null | undefined) => {
  if (priority === 'HIGH') {
    return 'danger'
  }
  if (priority === 'MEDIUM') {
    return 'warning'
  }
  if (priority === 'LOW') {
    return 'info'
  }
  return 'info'
}

const resolvePriorityText = (priority: string | null | undefined) => {
  if (priority === 'HIGH') {
    return '高优先级'
  }
  if (priority === 'MEDIUM') {
    return '中优先级'
  }
  if (priority === 'LOW') {
    return '低优先级'
  }
  return priority || '-'
}

const resolveRewriteTypeText = (type: string | null | undefined) => {
  const typeMap: Record<string, string> = {
    PROJECT: '项目经历',
    SKILL: '技能描述',
    INTERNSHIP: '实习或工作经历',
    SUMMARY: '个人总结',
    EDUCATION: '教育经历',
    OTHER: '其他',
  }
  return type ? typeMap[type] || type : '-'
}

const resolveRewriteStatusText = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return '改写成功'
  }
  if (status === 'FAILED') {
    return '改写失败'
  }
  if (status === 'PENDING') {
    return '待改写'
  }
  return status || '-'
}

const resolveRewriteStatusType = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

const resolveAcceptStatusText = (status: string | null | undefined) => {
  if (status === 'PENDING') {
    return '待确认'
  }
  if (status === 'ACCEPTED') {
    return '已采纳'
  }
  if (status === 'REJECTED') {
    return '已拒绝'
  }
  return status || '-'
}

const rewriteNeedsSupplement = (suggestion: AiRewriteSuggestionResult | null) => {
  return Boolean(suggestion?.caution?.includes('需要用户补充：'))
}

const resolveParseStatusText = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return '已解析'
  }
  if (status === 'FAILED') {
    return '解析失败'
  }
  return status || '未解析'
}

const suggestionKey = (suggestion: AiResumeSuggestionItem, index: number) => {
  return `${suggestion.type}-${suggestion.priority}-${suggestion.issue}-${index}`
}

const rewriteSuggestionKey = (suggestion: AiRewriteSuggestionResult, index: number) => {
  return `${suggestion.rewriteId}-${suggestion.rewriteType}-${index}`
}

const suggestionsByType = (type: string) => {
  return selectedSuggestion.value?.suggestions.filter((item) => item.type === type) ?? []
}

const suggestionsByPriority = (priority: string) => {
  return selectedSuggestion.value?.suggestions.filter((item) => item.priority === priority) ?? []
}

const loadInitialData = async () => {
  loading.value = true

  try {
    const [resumeList, jobDescriptionList] = await Promise.all([
      getResumeList(),
      getJobDescriptionList(),
    ])
    resumes.value = resumeList
    jobDescriptions.value = jobDescriptionList
    applyRouteDefaults()
    await loadSelectedResumeParseResult()
    await loadCurrentMatch()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取匹配基础数据失败')
  } finally {
    loading.value = false
  }
}

const applyRouteDefaults = () => {
  const routeResumeId = Number(route.query.resumeId)
  const routeJobDescriptionId = Number(route.query.jobDescriptionId)

  if (Number.isFinite(routeResumeId) && resumes.value.some((item) => item.id === routeResumeId)) {
    selectedResumeId.value = routeResumeId
  } else if (!selectedResumeId.value && resumes.value.length > 0) {
    selectedResumeId.value = resumes.value[0]?.id ?? null
  }

  if (Number.isFinite(routeJobDescriptionId) && jobDescriptions.value.some((item) => item.id === routeJobDescriptionId)) {
    selectedJobDescriptionId.value = routeJobDescriptionId
  } else if (!selectedJobDescriptionId.value && parsedJobDescriptions.value.length > 0) {
    selectedJobDescriptionId.value = parsedJobDescriptions.value[0]?.id ?? null
  }
}

const loadSelectedResumeParseResult = async () => {
  selectedResumeParseResult.value = null
  if (!selectedResumeId.value) {
    return
  }

  loadingResumeParse.value = true

  try {
    selectedResumeParseResult.value = await getResumeParseResult(selectedResumeId.value)
  } catch {
    selectedResumeParseResult.value = null
  } finally {
    loadingResumeParse.value = false
  }
}

const loadCurrentMatch = async () => {
  selectedMatch.value = null
  selectedSuggestion.value = null
  selectedRewriteSuggestion.value = null
  rewriteSuggestions.value = []
  matchResults.value = []
  if (!selectedResumeId.value) {
    return
  }

  loadingResult.value = true

  try {
    matchResults.value = await getAiJobMatches(selectedResumeId.value)
    if (selectedJobDescriptionId.value) {
      selectedMatch.value = await getAiJobMatch(selectedResumeId.value, selectedJobDescriptionId.value)
    } else {
      selectedMatch.value = matchResults.value[0] || null
    }
    await loadCurrentSuggestion()
    await loadRewriteSuggestions()
  } catch (error) {
    selectedMatch.value = null
    selectedSuggestion.value = null
    selectedRewriteSuggestion.value = null
    rewriteSuggestions.value = []
    if (error instanceof Error && error.message !== 'AI 岗位匹配结果不存在') {
      ElMessage.warning(error.message)
    }
  } finally {
    loadingResult.value = false
  }
}

const loadCurrentSuggestion = async () => {
  selectedSuggestion.value = null
  if (!selectedResumeId.value || !selectedMatch.value?.matchId) {
    return
  }

  loadingSuggestion.value = true

  try {
    selectedSuggestion.value = await getAiResumeSuggestionByMatchResult(selectedResumeId.value, selectedMatch.value.matchId)
  } catch (error) {
    selectedSuggestion.value = null
    if (error instanceof Error && error.message !== 'AI 优化建议结果不存在') {
      ElMessage.warning(error.message)
    }
  } finally {
    loadingSuggestion.value = false
  }
}

const handleResumeChange = async () => {
  await loadSelectedResumeParseResult()
  await loadCurrentMatch()
}

const handleJobDescriptionChange = async () => {
  await loadCurrentMatch()
}

const handleMatch = async () => {
  if (!selectedResumeId.value || !selectedJobDescriptionId.value) {
    ElMessage.warning('请先选择简历和目标岗位')
    return
  }

  if (selectedJobDescription.value?.parseStatus !== 'SUCCESS') {
    ElMessage.warning('请先完成目标岗位解析')
    return
  }

  matching.value = true

  try {
    const triggerResult = await triggerAiJobMatch(selectedResumeId.value, {
      jobDescriptionId: selectedJobDescriptionId.value,
    })
    if (triggerResult.matchStatus === 'FAILED') {
      ElMessage.error(triggerResult.errorMessage || '匹配分析失败')
    } else {
      ElMessage.success('匹配分析完成')
    }
    await loadCurrentMatch()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '匹配分析失败')
  } finally {
    matching.value = false
  }
}

const handleGenerateSuggestion = async () => {
  if (!selectedResumeId.value || !selectedJobDescriptionId.value || !selectedMatch.value?.matchId) {
    ElMessage.warning('请先完成匹配分析')
    return
  }

  if (selectedMatch.value.matchStatus !== 'SUCCESS') {
    ElMessage.warning('匹配分析成功后才能生成岗位优化建议')
    return
  }

  generatingSuggestion.value = true

  try {
    const triggerResult = await triggerAiResumeSuggestion(selectedResumeId.value, {
      jobDescriptionId: selectedJobDescriptionId.value,
      aiJobMatchResultId: selectedMatch.value.matchId,
    })
    if (triggerResult.suggestionStatus === 'FAILED') {
      ElMessage.error(triggerResult.errorMessage || '岗位优化建议生成失败')
    } else {
      ElMessage.success('岗位优化建议生成完成')
    }
    await loadCurrentSuggestion()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '岗位优化建议生成失败')
  } finally {
    generatingSuggestion.value = false
  }
}

const loadRewriteSuggestions = async () => {
  rewriteSuggestions.value = []
  selectedRewriteSuggestion.value = null
  if (!selectedResumeId.value) {
    return
  }

  loadingRewriteSuggestions.value = true

  try {
    rewriteSuggestions.value = await getAiRewriteSuggestions(selectedResumeId.value)
    selectedRewriteSuggestion.value = rewriteSuggestions.value[0] || null
  } catch (error) {
    rewriteSuggestions.value = []
    selectedRewriteSuggestion.value = null
    ElMessage.warning(error instanceof Error ? error.message : '获取 AI 局部改写建议失败')
  } finally {
    loadingRewriteSuggestions.value = false
  }
}

const openRewriteDialog = (suggestion?: AiResumeSuggestionItem) => {
  if (suggestion) {
    rewriteForm.targetSection = suggestion.targetSection || '项目经历'
    rewriteForm.originalText = suggestion.suggestion || ''
    if (suggestion.type === 'SKILL_GAP') {
      rewriteForm.rewriteType = 'SKILL'
    } else if (suggestion.type === 'PROJECT_DESCRIPTION' || suggestion.type === 'EXPERIENCE_WEAKNESS') {
      rewriteForm.rewriteType = 'PROJECT'
    }
  }
  rewriteDialogVisible.value = true
}

const handleGenerateRewrite = async () => {
  if (!selectedResumeId.value) {
    ElMessage.warning('请先选择简历')
    return
  }
  if (!canGenerateRewrite.value) {
    ElMessage.warning('请填写改写类型、目标部分和原文片段')
    return
  }

  generatingRewrite.value = true

  try {
    const result = await triggerAiRewriteSuggestion(selectedResumeId.value, {
      rewriteType: rewriteForm.rewriteType,
      targetSection: rewriteForm.targetSection.trim(),
      originalText: rewriteForm.originalText.trim(),
      jobDescriptionId: selectedJobDescriptionId.value || undefined,
      aiJobMatchResultId: selectedMatch.value?.matchId,
      aiResumeSuggestionId: selectedSuggestion.value?.suggestionId,
    })
    selectedRewriteSuggestion.value = result
    if (result.rewriteStatus === 'FAILED') {
      ElMessage.error(result.errorMessage || 'AI 局部改写生成失败')
    } else {
      ElMessage.success('AI 局部改写生成完成')
      rewriteDialogVisible.value = false
    }
    await loadRewriteSuggestions()
    if (result.rewriteId) {
      selectedRewriteSuggestion.value = rewriteSuggestions.value.find((item) => item.rewriteId === result.rewriteId) || result
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 局部改写生成失败')
  } finally {
    generatingRewrite.value = false
  }
}

const handleUpdateRewriteAcceptStatus = async (acceptStatus: 'ACCEPTED' | 'REJECTED') => {
  if (!selectedRewriteSuggestion.value?.rewriteId) {
    ElMessage.warning('请先选择 AI 局部改写建议')
    return
  }

  updatingRewriteAcceptStatus.value = true

  try {
    const result = await updateAiRewriteAcceptStatus(selectedRewriteSuggestion.value.rewriteId, {
      acceptStatus,
    })
    selectedRewriteSuggestion.value = result
    const index = rewriteSuggestions.value.findIndex((item) => item.rewriteId === result.rewriteId)
    if (index >= 0) {
      rewriteSuggestions.value[index] = result
    }
    ElMessage.success(acceptStatus === 'ACCEPTED' ? '已采纳改写建议' : '已拒绝改写建议')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新采纳状态失败')
  } finally {
    updatingRewriteAcceptStatus.value = false
  }
}

onMounted(() => {
  loadInitialData()
})
</script>

<template>
  <main class="ai-match-page">
    <section class="ai-match-shell">
      <header class="ai-match-header">
        <div>
          <h1 class="ai-match-title">匹配与优化</h1>
          <p class="ai-match-subtitle">选择已解析简历和目标岗位，生成匹配分析、岗位优化建议和 AI 局部改写。</p>
        </div>
        <el-space>
          <el-button @click="router.push('/resumes')">我的简历</el-button>
          <el-button @click="router.push('/job-descriptions')">目标岗位</el-button>
          <el-button @click="router.push('/')">返回工作台</el-button>
        </el-space>
      </header>

      <section v-loading="loading" class="ai-match-panel">
        <section class="ai-match-selectors">
          <div class="ai-match-selector">
            <h2 class="ai-match-section-title">选择简历</h2>
            <el-select
              v-model="selectedResumeId"
              class="ai-match-select"
              placeholder="请选择简历"
              filterable
              @change="handleResumeChange"
            >
              <el-option
                v-for="resume in resumes"
                :key="resume.id"
                :label="resume.originalFilename"
                :value="resume.id"
              />
            </el-select>
            <div class="ai-match-meta">
              <span>解析状态：</span>
              <el-tag v-if="selectedResumeParseResult" :type="selectedResumeParseResult.parseStatus === 'SUCCESS' ? 'success' : 'warning'">
                {{ resolveParseStatusText(selectedResumeParseResult.parseStatus) }}
              </el-tag>
              <el-tag v-else type="info">{{ loadingResumeParse ? '读取中' : '未解析或暂无结果' }}</el-tag>
            </div>
          </div>

          <div class="ai-match-selector">
            <h2 class="ai-match-section-title">选择目标岗位</h2>
            <el-select
              v-model="selectedJobDescriptionId"
              class="ai-match-select"
              placeholder="请选择目标岗位"
              filterable
              @change="handleJobDescriptionChange"
            >
              <el-option
                v-for="jobDescription in jobDescriptions"
                :key="jobDescription.id"
                :label="jobDescription.title"
                :value="jobDescription.id"
                :disabled="jobDescription.parseStatus !== 'SUCCESS'"
              >
                <span>{{ jobDescription.title }}</span>
                <span class="ai-match-option-status">{{ resolveParseStatusText(jobDescription.parseStatus) }}</span>
              </el-option>
            </el-select>
            <div class="ai-match-meta">
              <span>目标岗位解析：</span>
              <el-tag v-if="selectedJobDescription" :type="selectedJobDescription.parseStatus === 'SUCCESS' ? 'success' : 'warning'">
                {{ resolveParseStatusText(selectedJobDescription.parseStatus) }}
              </el-tag>
              <el-tag v-else type="info">未选择</el-tag>
            </div>
          </div>
        </section>

        <el-alert
          v-if="selectedResumeParseResult && selectedResumeParseResult.parseStatus !== 'SUCCESS'"
          class="ai-match-alert"
          title="当前简历尚未解析成功，请先在“我的简历”中完成解析。"
          type="warning"
          :closable="false"
          show-icon
        />

        <el-alert
          v-if="selectedJobDescription && selectedJobDescription.parseStatus !== 'SUCCESS'"
          class="ai-match-alert"
          title="当前目标岗位尚未解析成功，请先完成目标岗位解析。"
          type="warning"
          :closable="false"
          show-icon
        />

        <div class="ai-match-actions">
          <el-button
            type="primary"
            :loading="matching"
            :disabled="!canMatch"
            @click="handleMatch"
          >
            开始匹配分析
          </el-button>
          <el-button :loading="loadingResult" :disabled="!selectedResumeId" @click="loadCurrentMatch">刷新结果</el-button>
        </div>

        <section v-loading="loadingResult" class="ai-match-result">
          <el-empty v-if="!selectedMatch" description="暂无匹配分析结果" :image-size="88" />

          <template v-else>
            <div class="ai-match-summary">
              <div class="ai-match-score">
                <span class="ai-match-score-value">{{ selectedMatch.overallScore ?? '-' }}</span>
                <span class="ai-match-score-label">总体匹配分</span>
              </div>
              <el-descriptions :column="2" border class="ai-match-descriptions">
                <el-descriptions-item label="匹配状态">
                  <el-tag :type="resolveMatchStatusType(selectedMatch.matchStatus)">
                    {{ resolveMatchStatusText(selectedMatch.matchStatus) }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="模型">{{ selectedMatch.modelName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Prompt 版本">{{ selectedMatch.promptVersion || '-' }}</el-descriptions-item>
                <el-descriptions-item label="更新时间">{{ formatDateTime(selectedMatch.updatedAt) }}</el-descriptions-item>
              </el-descriptions>
            </div>

            <el-alert
              v-if="selectedMatch.matchStatus === 'FAILED'"
              class="ai-match-alert"
              :title="selectedMatch.errorMessage || '匹配分析失败'"
              type="error"
              :closable="false"
              show-icon
            />

            <section class="ai-match-suggestion-panel">
              <div class="ai-match-suggestion-header">
                <div>
                  <h2 class="ai-match-section-title">岗位优化建议</h2>
                  <p class="ai-match-suggestion-note">AI 建议需用户确认，不应直接伪造经历、技能、证书、奖项或量化指标。</p>
                </div>
                <el-space>
                  <el-button
                    type="primary"
                    :loading="generatingSuggestion"
                    :disabled="!canGenerateSuggestion"
                    @click="handleGenerateSuggestion"
                  >
                    生成岗位优化建议
                  </el-button>
                  <el-button
                    :loading="loadingSuggestion"
                    :disabled="!selectedMatch?.matchId"
                    @click="loadCurrentSuggestion"
                  >
                    刷新建议
                  </el-button>
                </el-space>
              </div>

              <el-alert
                v-if="selectedMatch.matchStatus !== 'SUCCESS'"
                class="ai-match-alert"
                title="请先完成成功的匹配分析，再生成岗位优化建议。"
                type="warning"
                :closable="false"
                show-icon
              />

              <section v-loading="loadingSuggestion" class="ai-match-suggestion-body">
                <el-empty
                  v-if="!selectedSuggestion"
                  description="暂无岗位优化建议"
                  :image-size="72"
                />

                <template v-else>
                  <el-descriptions :column="2" border class="ai-match-descriptions">
                    <el-descriptions-item label="建议状态">
                      <el-tag :type="resolveSuggestionStatusType(selectedSuggestion.suggestionStatus)">
                        {{ resolveSuggestionStatusText(selectedSuggestion.suggestionStatus) }}
                      </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="建议数量">{{ selectedSuggestion.suggestions.length }}</el-descriptions-item>
                    <el-descriptions-item label="模型">{{ selectedSuggestion.modelName || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="生成时间">{{ formatDateTime(selectedSuggestion.updatedAt) }}</el-descriptions-item>
                  </el-descriptions>

                  <el-alert
                    v-if="selectedSuggestion.suggestionStatus === 'FAILED'"
                    class="ai-match-alert"
                    :title="selectedSuggestion.errorMessage || '岗位优化建议生成失败'"
                    type="error"
                    :closable="false"
                    show-icon
                  />

                  <section class="ai-match-suggestion-groups">
                    <div class="ai-match-block">
                      <h3 class="ai-match-section-title">高优先级建议</h3>
                      <div v-if="highPrioritySuggestions.length" class="ai-match-suggestion-list">
                        <article
                          v-for="(suggestion, index) in highPrioritySuggestions"
                          :key="suggestionKey(suggestion, index)"
                          class="ai-match-suggestion-item"
                        >
                          <div class="ai-match-suggestion-tags">
                            <el-tag size="small">{{ resolveSuggestionTypeText(suggestion.type) }}</el-tag>
                            <el-tag size="small" :type="resolvePriorityType(suggestion.priority)">
                              {{ resolvePriorityText(suggestion.priority) }}
                            </el-tag>
                            <el-tag v-if="suggestion.targetSection" size="small" type="info">
                              {{ suggestion.targetSection }}
                            </el-tag>
                          </div>
                          <p><strong>问题：</strong>{{ suggestion.issue || '-' }}</p>
                          <p><strong>建议：</strong>{{ suggestion.suggestion || '-' }}</p>
                          <div class="ai-match-inline-actions">
                            <el-button size="small" @click="openRewriteDialog(suggestion)">基于此建议改写</el-button>
                          </div>
                          <div v-if="suggestion.evidence.length" class="ai-match-suggestion-evidence">
                            <strong>依据：</strong>
                            <span v-for="evidence in suggestion.evidence" :key="evidence">{{ evidence }}</span>
                          </div>
                          <p v-if="suggestion.caution" class="ai-match-suggestion-caution">
                            <strong>注意：</strong>{{ suggestion.caution }}
                          </p>
                        </article>
                      </div>
                      <el-empty v-else description="暂无高优先级建议" :image-size="64" />
                    </div>

                    <div class="ai-match-block">
                      <h3 class="ai-match-section-title">技能缺口建议</h3>
                      <div v-if="skillGapSuggestions.length" class="ai-match-suggestion-list">
                        <article
                          v-for="(suggestion, index) in skillGapSuggestions"
                          :key="suggestionKey(suggestion, index)"
                          class="ai-match-suggestion-item"
                        >
                          <div class="ai-match-suggestion-tags">
                            <el-tag size="small">{{ resolveSuggestionTypeText(suggestion.type) }}</el-tag>
                            <el-tag size="small" :type="resolvePriorityType(suggestion.priority)">
                              {{ resolvePriorityText(suggestion.priority) }}
                            </el-tag>
                          </div>
                          <p><strong>问题：</strong>{{ suggestion.issue || '-' }}</p>
                          <p><strong>建议：</strong>{{ suggestion.suggestion || '-' }}</p>
                          <div class="ai-match-inline-actions">
                            <el-button size="small" @click="openRewriteDialog(suggestion)">基于此建议改写</el-button>
                          </div>
                          <div v-if="suggestion.relatedItems.length" class="ai-match-related-items">
                            <el-tag v-for="item in suggestion.relatedItems" :key="item" size="small" type="warning">{{ item }}</el-tag>
                          </div>
                        </article>
                      </div>
                      <el-empty v-else description="暂无技能缺口建议" :image-size="64" />
                    </div>

                    <div class="ai-match-block">
                      <h3 class="ai-match-section-title">经历表达建议</h3>
                      <div v-if="experienceSuggestions.length" class="ai-match-suggestion-list">
                        <article
                          v-for="(suggestion, index) in experienceSuggestions"
                          :key="suggestionKey(suggestion, index)"
                          class="ai-match-suggestion-item"
                        >
                          <div class="ai-match-suggestion-tags">
                            <el-tag size="small">{{ resolveSuggestionTypeText(suggestion.type) }}</el-tag>
                            <el-tag size="small" :type="resolvePriorityType(suggestion.priority)">
                              {{ resolvePriorityText(suggestion.priority) }}
                            </el-tag>
                            <el-tag v-if="suggestion.targetSection" size="small" type="info">
                              {{ suggestion.targetSection }}
                            </el-tag>
                          </div>
                          <p><strong>问题：</strong>{{ suggestion.issue || '-' }}</p>
                          <p><strong>建议：</strong>{{ suggestion.suggestion || '-' }}</p>
                          <div class="ai-match-inline-actions">
                            <el-button size="small" @click="openRewriteDialog(suggestion)">基于此建议改写</el-button>
                          </div>
                          <div v-if="suggestion.evidence.length" class="ai-match-suggestion-evidence">
                            <strong>依据：</strong>
                            <span v-for="evidence in suggestion.evidence" :key="evidence">{{ evidence }}</span>
                          </div>
                        </article>
                      </div>
                      <el-empty v-else description="暂无经历表达建议" :image-size="64" />
                    </div>

                    <div class="ai-match-block">
                      <h3 class="ai-match-section-title">优势与综合建议</h3>
                      <div v-if="strengthAndGeneralSuggestions.length" class="ai-match-suggestion-list">
                        <article
                          v-for="(suggestion, index) in strengthAndGeneralSuggestions"
                          :key="suggestionKey(suggestion, index)"
                          class="ai-match-suggestion-item"
                        >
                          <div class="ai-match-suggestion-tags">
                            <el-tag size="small">{{ resolveSuggestionTypeText(suggestion.type) }}</el-tag>
                            <el-tag size="small" :type="resolvePriorityType(suggestion.priority)">
                              {{ resolvePriorityText(suggestion.priority) }}
                            </el-tag>
                          </div>
                          <p><strong>问题：</strong>{{ suggestion.issue || '-' }}</p>
                          <p><strong>建议：</strong>{{ suggestion.suggestion || '-' }}</p>
                          <div class="ai-match-inline-actions">
                            <el-button size="small" @click="openRewriteDialog(suggestion)">基于此建议改写</el-button>
                          </div>
                          <p v-if="suggestion.caution" class="ai-match-suggestion-caution">
                            <strong>注意：</strong>{{ suggestion.caution }}
                          </p>
                        </article>
                      </div>
                      <el-empty v-else description="暂无优势或综合建议" :image-size="64" />
                    </div>
                  </section>
                </template>
              </section>
            </section>

            <section class="ai-match-rewrite-panel">
              <div class="ai-match-suggestion-header">
                <div>
                  <h2 class="ai-match-section-title">AI 局部改写</h2>
                  <p class="ai-match-suggestion-note">AI 改写建议需用户确认，只能作为表达优化参考，不应直接写入未发生的经历或指标。</p>
                </div>
                <el-space>
                  <el-button type="primary" :disabled="!selectedResumeId" @click="openRewriteDialog()">进行局部改写</el-button>
                  <el-button :loading="loadingRewriteSuggestions" :disabled="!selectedResumeId" @click="loadRewriteSuggestions">
                    刷新改写
                  </el-button>
                </el-space>
              </div>

              <section v-loading="loadingRewriteSuggestions" class="ai-match-rewrite-body">
                <el-empty
                  v-if="!selectedRewriteSuggestion"
                  description="暂无 AI 局部改写建议"
                  :image-size="72"
                />

                <template v-else>
                  <el-descriptions :column="2" border class="ai-match-descriptions">
                    <el-descriptions-item label="生成状态">
                      <el-tag :type="resolveRewriteStatusType(selectedRewriteSuggestion.rewriteStatus)">
                        {{ resolveRewriteStatusText(selectedRewriteSuggestion.rewriteStatus) }}
                      </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="采纳状态">{{ resolveAcceptStatusText(selectedRewriteSuggestion.acceptStatus) }}</el-descriptions-item>
                    <el-descriptions-item label="改写类型">{{ resolveRewriteTypeText(selectedRewriteSuggestion.rewriteType) }}</el-descriptions-item>
                    <el-descriptions-item label="目标部分">{{ selectedRewriteSuggestion.targetSection || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="模型">{{ selectedRewriteSuggestion.modelName || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="生成时间">{{ formatDateTime(selectedRewriteSuggestion.updatedAt) }}</el-descriptions-item>
                  </el-descriptions>

                  <div class="ai-match-rewrite-actions">
                    <el-button
                      type="success"
                      :loading="updatingRewriteAcceptStatus"
                      :disabled="selectedRewriteSuggestion.acceptStatus === 'ACCEPTED' || selectedRewriteSuggestion.rewriteStatus !== 'SUCCESS'"
                      @click="handleUpdateRewriteAcceptStatus('ACCEPTED')"
                    >
                      采纳
                    </el-button>
                    <el-button
                      type="danger"
                      plain
                      :loading="updatingRewriteAcceptStatus"
                      :disabled="selectedRewriteSuggestion.acceptStatus === 'REJECTED'"
                      @click="handleUpdateRewriteAcceptStatus('REJECTED')"
                    >
                      拒绝
                    </el-button>
                  </div>

                  <el-alert
                    v-if="selectedRewriteSuggestion.rewriteStatus === 'FAILED'"
                    class="ai-match-alert"
                    :title="selectedRewriteSuggestion.errorMessage || 'AI 局部改写生成失败'"
                    type="error"
                    :closable="false"
                    show-icon
                  />

                  <el-alert
                    v-if="rewriteNeedsSupplement(selectedRewriteSuggestion)"
                    class="ai-match-alert"
                    title="该改写建议需要用户补充或确认真实信息后再采用。"
                    type="warning"
                    :closable="false"
                    show-icon
                  />

                  <div class="ai-match-rewrite-compare">
                    <article class="ai-match-rewrite-card">
                      <h3>原文</h3>
                      <p>{{ selectedRewriteSuggestion.originalText || '-' }}</p>
                    </article>
                    <article class="ai-match-rewrite-card">
                      <h3>改写建议</h3>
                      <p>{{ selectedRewriteSuggestion.rewrittenText || '-' }}</p>
                    </article>
                  </div>

                  <div class="ai-match-rewrite-detail">
                    <p><strong>改写理由：</strong>{{ selectedRewriteSuggestion.rewriteReason || '-' }}</p>
                    <p class="ai-match-suggestion-caution"><strong>注意事项：</strong>{{ selectedRewriteSuggestion.caution || '-' }}</p>
                  </div>
                </template>

                <div v-if="rewriteSuggestions.length" class="ai-match-rewrite-history">
                  <h3 class="ai-match-section-title">历史改写建议</h3>
                  <div class="ai-match-rewrite-history-list">
                    <button
                      v-for="(suggestion, index) in rewriteSuggestions"
                      :key="rewriteSuggestionKey(suggestion, index)"
                      class="ai-match-rewrite-history-item"
                      :class="{ 'is-active': selectedRewriteSuggestion?.rewriteId === suggestion.rewriteId }"
                      type="button"
                      @click="selectedRewriteSuggestion = suggestion"
                    >
                      <span>{{ resolveRewriteTypeText(suggestion.rewriteType) }} / {{ suggestion.targetSection }}</span>
                      <el-tag size="small" :type="resolveRewriteStatusType(suggestion.rewriteStatus)">
                        {{ resolveRewriteStatusText(suggestion.rewriteStatus) }}
                      </el-tag>
                    </button>
                  </div>
                </div>
              </section>
            </section>

            <section class="ai-match-grid">
              <div class="ai-match-block">
                <h2 class="ai-match-section-title">强匹配项</h2>
                <div v-if="selectedMatch.strongMatches.length" class="ai-match-list">
                  <p v-for="item in selectedMatch.strongMatches" :key="`${item.item}-${item.reason}`">
                    <strong>{{ item.item || '-' }}</strong>
                    <span>{{ item.reason || '-' }}</span>
                  </p>
                </div>
                <el-empty v-else description="暂无强匹配项" :image-size="72" />
              </div>

              <div class="ai-match-block">
                <h2 class="ai-match-section-title">弱匹配项</h2>
                <div v-if="selectedMatch.weakMatches.length" class="ai-match-list">
                  <p v-for="item in selectedMatch.weakMatches" :key="`${item.item}-${item.reason}`">
                    <strong>{{ item.item || '-' }}</strong>
                    <span>{{ item.reason || '-' }}</span>
                  </p>
                </div>
                <el-empty v-else description="暂无弱匹配项" :image-size="72" />
              </div>

              <div class="ai-match-block">
                <h2 class="ai-match-section-title">缺失技能</h2>
                <div v-if="selectedMatch.missingSkills.length" class="ai-match-list">
                  <p v-for="item in selectedMatch.missingSkills" :key="`${item.item}-${item.reason}`">
                    <strong>{{ item.item || '-' }}</strong>
                    <span>{{ item.reason || '-' }}</span>
                  </p>
                </div>
                <el-empty v-else description="暂无缺失技能" :image-size="72" />
              </div>

              <div class="ai-match-block">
                <h2 class="ai-match-section-title">表达较弱经历</h2>
                <div v-if="selectedMatch.weakExperienceDescriptions.length" class="ai-match-list">
                  <p
                    v-for="item in selectedMatch.weakExperienceDescriptions"
                    :key="`${item.section}-${item.issue}`"
                  >
                    <strong>{{ item.section || '-' }}</strong>
                    <span>{{ item.issue || '-' }}</span>
                  </p>
                </div>
                <el-empty v-else description="暂无表达较弱经历" :image-size="72" />
              </div>
            </section>

            <section class="ai-match-block ai-match-wide">
              <h2 class="ai-match-section-title">匹配依据</h2>
              <div v-if="selectedMatch.evidence.length" class="ai-match-list">
                <p v-for="item in selectedMatch.evidence" :key="`${item.source}-${item.content}`">
                  <el-tag size="small" :type="item.source === 'job' ? 'warning' : 'success'">
                    {{ item.source === 'job' ? '岗位' : '简历' }}
                  </el-tag>
                  <span>{{ item.content || '-' }}</span>
                </p>
              </div>
              <el-empty v-else description="暂无匹配依据" :image-size="72" />
            </section>

            <section class="ai-match-block ai-match-wide">
              <h2 class="ai-match-section-title">风险提示</h2>
              <div v-if="selectedMatch.riskNotes.length" class="ai-match-list">
                <p v-for="item in selectedMatch.riskNotes" :key="item">{{ item }}</p>
              </div>
              <el-empty v-else description="暂无风险提示" :image-size="72" />
            </section>
          </template>
        </section>
      </section>
    </section>

    <el-dialog
      v-model="rewriteDialogVisible"
      title="AI 局部改写"
      width="720px"
      destroy-on-close
    >
      <el-alert
        class="ai-match-dialog-alert"
        title="AI 改写只优化表达，采用前请确认内容真实，不要补写不存在的经历、技能或量化指标。"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-form label-position="top" class="ai-match-rewrite-form">
        <el-form-item label="改写对象类型">
          <el-select v-model="rewriteForm.rewriteType" class="ai-match-select">
            <el-option
              v-for="item in rewriteTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标简历部分">
          <el-input v-model="rewriteForm.targetSection" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="原文片段">
          <el-input
            v-model="rewriteForm.originalText"
            type="textarea"
            :rows="7"
            maxlength="3000"
            show-word-limit
            placeholder="粘贴需要优化表达的简历片段"
          />
        </el-form-item>
        <el-descriptions :column="1" border class="ai-match-descriptions">
          <el-descriptions-item label="关联目标岗位">{{ selectedJobDescription?.title || '未关联' }}</el-descriptions-item>
          <el-descriptions-item label="关联匹配分析">{{ selectedMatch?.matchId ? `#${selectedMatch.matchId}` : '未关联' }}</el-descriptions-item>
          <el-descriptions-item label="关联岗位优化建议">{{ selectedSuggestion?.suggestionId ? `#${selectedSuggestion.suggestionId}` : '未关联' }}</el-descriptions-item>
        </el-descriptions>
      </el-form>

      <template #footer>
        <el-button @click="rewriteDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="generatingRewrite"
          :disabled="!canGenerateRewrite"
          @click="handleGenerateRewrite"
        >
          生成改写建议
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.ai-match-page {
  min-height: 100vh;
  padding: 40px 28px 56px;
  background: #f4f7fb;
}

.ai-match-shell {
  width: min(100%, 1180px);
  margin: 0 auto;
}

.ai-match-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.ai-match-title {
  margin: 0;
  color: #111827;
  font-size: 28px;
  font-weight: 700;
}

.ai-match-subtitle {
  margin: 8px 0 0;
  color: #667085;
  font-size: 15px;
  line-height: 1.7;
}

.ai-match-panel {
  min-height: 520px;
  padding: 28px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.ai-match-selectors {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.ai-match-selector,
.ai-match-block {
  padding: 18px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #ffffff;
}

.ai-match-section-title {
  margin: 0 0 12px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.ai-match-select {
  width: 100%;
}

.ai-match-option-status {
  float: right;
  color: #98a2b3;
  font-size: 13px;
}

.ai-match-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: #667085;
  font-size: 14px;
}

.ai-match-alert,
.ai-match-actions,
.ai-match-result {
  margin-top: 24px;
}

.ai-match-suggestion-panel {
  margin-top: 24px;
  padding: 18px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #f8fafc;
}

.ai-match-rewrite-panel {
  margin-top: 24px;
  padding: 18px;
  border: 1px solid #d6e3f8;
  border-radius: 8px;
  background: #f9fbff;
}

.ai-match-suggestion-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.ai-match-suggestion-note {
  margin: 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.7;
}

.ai-match-suggestion-body {
  min-height: 120px;
  margin-top: 16px;
}

.ai-match-rewrite-body {
  min-height: 120px;
  margin-top: 16px;
}

.ai-match-summary {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 20px;
  align-items: stretch;
}

.ai-match-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 132px;
  border: 1px solid #c7d7fe;
  border-radius: 8px;
  background: #eef4ff;
}

.ai-match-score-value {
  color: #175cd3;
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
}

.ai-match-score-label {
  margin-top: 10px;
  color: #344054;
  font-size: 14px;
}

.ai-match-descriptions {
  width: 100%;
}

.ai-match-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 24px;
}

.ai-match-wide {
  margin-top: 20px;
}

.ai-match-list {
  display: grid;
  gap: 10px;
}

.ai-match-list p {
  display: grid;
  gap: 6px;
  margin: 0;
  color: #344054;
  line-height: 1.7;
}

.ai-match-list strong {
  color: #111827;
  font-weight: 700;
}

.ai-match-suggestion-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.ai-match-suggestion-list {
  display: grid;
  gap: 12px;
}

.ai-match-suggestion-item {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #ffffff;
}

.ai-match-suggestion-item p {
  margin: 0;
  color: #344054;
  line-height: 1.7;
}

.ai-match-suggestion-item strong {
  color: #111827;
}

.ai-match-suggestion-tags,
.ai-match-related-items,
.ai-match-inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-match-inline-actions {
  justify-content: flex-start;
}

.ai-match-suggestion-evidence {
  display: grid;
  gap: 6px;
  color: #344054;
  line-height: 1.7;
}

.ai-match-suggestion-evidence span {
  padding-left: 10px;
  border-left: 3px solid #d0d5dd;
}

.ai-match-suggestion-caution {
  color: #b42318 !important;
}

.ai-match-rewrite-compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.ai-match-rewrite-card {
  min-height: 180px;
  padding: 16px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #ffffff;
}

.ai-match-rewrite-card h3 {
  margin: 0 0 10px;
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.ai-match-rewrite-card p,
.ai-match-rewrite-detail p {
  margin: 0;
  color: #344054;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-match-rewrite-detail {
  display: grid;
  gap: 10px;
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #ffffff;
}

.ai-match-rewrite-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.ai-match-rewrite-history {
  margin-top: 18px;
}

.ai-match-rewrite-history-list {
  display: grid;
  gap: 10px;
}

.ai-match-rewrite-history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #ffffff;
  color: #344054;
  cursor: pointer;
  text-align: left;
}

.ai-match-rewrite-history-item.is-active {
  border-color: #409eff;
  background: #eef6ff;
}

.ai-match-dialog-alert,
.ai-match-rewrite-form {
  margin-bottom: 16px;
}

@media (max-width: 760px) {
  .ai-match-header,
  .ai-match-summary {
    align-items: stretch;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .ai-match-selectors,
  .ai-match-grid,
  .ai-match-suggestion-groups,
  .ai-match-rewrite-compare {
    grid-template-columns: 1fr;
  }

  .ai-match-suggestion-header,
  .ai-match-rewrite-history-item {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
