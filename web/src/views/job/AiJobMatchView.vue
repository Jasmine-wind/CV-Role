<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import ProcessStepper from '@/components/common/ProcessStepper.vue'
import { getAiJobMatch, getAiJobMatches, triggerAiJobMatch } from '@/api/ai-job-match'
import { getAiResumeSuggestionByMatchResult, triggerAiResumeSuggestion } from '@/api/ai-resume-suggestion'
import { getAiRewriteContext, getAiRewriteSuggestions, triggerAiRewriteSuggestion, updateAiRewriteAcceptStatus } from '@/api/ai-rewrite-suggestion'
import { getJobOptimizationReport } from '@/api/job-optimization-report'
import { getJobDescriptionList } from '@/api/job-description'
import { getResumeList, getResumeParseResult } from '@/api/resume'
import type { AiJobMatchResult } from '@/types/ai-job-match'
import type { AiResumeSuggestionItem, AiResumeSuggestionResult } from '@/types/ai-resume-suggestion'
import type { AiRewriteSuggestionResult, RecommendedRewriteSection, RewriteContext } from '@/types/ai-rewrite-suggestion'
import type { JobOptimizationReport, JobOptimizationRewriteSuggestion } from '@/types/job-optimization-report'
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
const optimizationReport = ref<JobOptimizationReport | null>(null)
const matchResults = ref<AiJobMatchResult[]>([])
const rewriteSuggestions = ref<AiRewriteSuggestionResult[]>([])
const loading = ref(false)
const loadingResumeParse = ref(false)
const matching = ref(false)
const loadingResult = ref(false)
const generatingSuggestion = ref(false)
const loadingSuggestion = ref(false)
const loadingOptimizationReport = ref(false)
const reportSectionsExpanded = reactive({
  conclusions: true,
  suggestions: true,
  rewrites: true,
  nextSteps: true,
})
const rewriteDialogVisible = ref(false)
const rewriteContext = ref<RewriteContext | null>(null)
const selectedRewriteSourceIndex = ref(0)
const selectedRewriteSource = ref<RecommendedRewriteSection | null>(null)
const selectedSuggestionItem = ref<AiResumeSuggestionItem | null>(null)
const selectedSuggestionItemIndex = ref<number | null>(null)
const latestDialogRewriteResult = ref<AiRewriteSuggestionResult | null>(null)
const generatingRewrite = ref(false)
const loadingRewriteContext = ref(false)
const rewriteContextError = ref<string | null>(null)
const loadingRewriteSuggestions = ref(false)
const rewriteHistoryExpanded = ref(false)
const updatingRewriteAcceptStatus = ref(false)
const activeMatchStage = ref<'select' | 'match' | 'suggestion' | 'rewrite' | 'report'>('select')

const rewriteForm = reactive({
  rewriteType: 'PROJECT',
  targetSection: '项目经历',
  originalText: '',
  rewriteGoal: '',
  tone: '简洁专业',
  lengthLimit: 180,
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
  return Boolean(
    selectedResumeId.value
    && selectedResumeParseResult.value?.parseStatus === 'SUCCESS'
    && selectedJobDescriptionId.value
    && selectedJobDescription.value?.parseStatus === 'SUCCESS',
  )
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

const rewriteContextKeywords = computed(() => rewriteContext.value?.jobKeywords ?? [])

const rewriteContextGoals = computed(() => {
  const goals = rewriteContext.value?.rewriteGoals ?? []
  if (rewriteForm.rewriteGoal && !goals.includes(rewriteForm.rewriteGoal)) {
    return [rewriteForm.rewriteGoal, ...goals]
  }
  return goals
})

const rewriteContextTones = computed(() => {
  const tones = rewriteContext.value?.tones ?? ['简洁专业', '偏技术', '适合实习简历', '适合社招简历']
  if (rewriteForm.tone && !tones.includes(rewriteForm.tone)) {
    return [rewriteForm.tone, ...tones]
  }
  return tones
})

const activeGenerationStatus = computed(() => {
  if (matching.value) {
    return {
      title: 'AI 正在分析匹配差距',
      description: '正在对比简历解析结果和目标岗位要求，完成后会展示匹配结论、依据、问题和下一步。',
    }
  }

  if (generatingSuggestion.value) {
    return {
      title: 'AI 正在生成岗位优化建议',
      description: '正在把匹配差距转成可执行建议，建议仅作为候选方案，不会自动写回简历。',
    }
  }

  if (generatingRewrite.value) {
    return {
      title: 'AI 正在生成局部改写',
      description: '正在优化你选择的简历片段表达，生成结果需要用户确认后再采纳。',
    }
  }

  if (loadingOptimizationReport.value) {
    return {
      title: '正在整理岗位优化报告',
      description: '报告会聚合已有匹配分析、优化建议和局部改写结果，不重新调用 AI。',
    }
  }

  return null
})

const matchScoreSummary = computed(() => {
  const score = selectedMatch.value?.overallScore
  if (score == null) {
    return {
      level: 'empty',
      label: '暂无评分',
      description: '完成匹配分析后展示综合匹配度。',
    }
  }

  if (score >= 85) {
    return {
      level: 'high',
      label: '高度匹配',
      description: '可优先基于优势补充项目证据。',
    }
  }

  if (score >= 70) {
    return {
      level: 'medium',
      label: '中度匹配',
      description: '建议先处理弱匹配项和缺失技能。',
    }
  }

  return {
    level: 'low',
    label: '匹配不足',
    description: '需要补齐岗位关键要求或调整目标岗位。',
  }
})

const matchMetricCards = computed(() => {
  const match = selectedMatch.value
  return [
    {
      label: '强匹配',
      value: match?.strongMatches.length ?? 0,
      note: '简历已体现的岗位要求',
      tone: 'success',
    },
    {
      label: '弱匹配',
      value: match?.weakMatches.length ?? 0,
      note: '出现但证据不足的能力',
      tone: 'warning',
    },
    {
      label: '缺失技能',
      value: match?.missingSkills.length ?? 0,
      note: '岗位要求中未体现的关键词',
      tone: 'danger',
    },
    {
      label: '风险提示',
      value: match?.riskNotes.length ?? 0,
      note: '需要用户确认的匹配风险',
      tone: 'info',
    },
  ]
})

const matchNextAction = computed(() => {
  if (!selectedMatch.value) {
    return {
      title: '先生成匹配分析',
      description: '选择已解析简历和目标岗位后，生成匹配分、强弱项和缺失技能。',
    }
  }

  if (selectedMatch.value.matchStatus !== 'SUCCESS') {
    return {
      title: '处理匹配异常',
      description: selectedMatch.value.errorMessage || '当前匹配分析未成功，请重试或检查简历和目标岗位解析状态。',
    }
  }

  if (!selectedSuggestion.value) {
    return {
      title: '生成岗位优化建议',
      description: '匹配成功后可基于差距生成策略建议，不直接写回简历。',
    }
  }

  return {
    title: '查看岗位优化报告',
    description: '报告会聚合匹配分析、岗位优化建议和局部改写结果，不重新调用 AI。',
  }
})

const matchInsightSections = computed(() => {
  const match = selectedMatch.value
  return [
    {
      key: 'strong',
      title: '强匹配项',
      tone: 'success',
      emptyText: '暂无强匹配项',
      items: match?.strongMatches.map((item) => ({
        title: item.item || '-',
        description: item.reason || '-',
      })) ?? [],
    },
    {
      key: 'weak',
      title: '弱匹配项',
      tone: 'warning',
      emptyText: '暂无弱匹配项',
      items: match?.weakMatches.map((item) => ({
        title: item.item || '-',
        description: item.reason || '-',
      })) ?? [],
    },
    {
      key: 'missing',
      title: '缺失技能',
      tone: 'danger',
      emptyText: '暂无缺失技能',
      items: match?.missingSkills.map((item) => ({
        title: item.item || '-',
        description: item.reason || '-',
      })) ?? [],
    },
    {
      key: 'experience',
      title: '表达较弱经历',
      tone: 'info',
      emptyText: '暂无表达较弱经历',
      items: match?.weakExperienceDescriptions.map((item) => ({
        title: item.section || '-',
        description: item.issue || '-',
      })) ?? [],
    },
  ]
})

const matchWorkflowSteps = computed(() => [
  {
    title: '选择简历和目标岗位',
    description: selectedResume.value && selectedJobDescription.value ? '已选择简历和目标岗位' : '先选择简历和目标岗位',
    status: selectedResume.value && selectedJobDescription.value ? 'done' as const : 'current' as const,
  },
  {
    title: '匹配分析',
    description: selectedMatch.value?.matchStatus === 'SUCCESS' ? '匹配分析已完成' : '生成匹配分和强弱项',
    status: selectedMatch.value?.matchStatus === 'SUCCESS' ? 'done' as const : selectedResume.value && selectedJobDescription.value ? 'current' as const : 'pending' as const,
  },
  {
    title: '岗位优化建议',
    description: selectedSuggestion.value?.suggestionStatus === 'SUCCESS' ? `${selectedSuggestion.value.suggestions.length} 条建议` : '匹配成功后生成',
    status: selectedSuggestion.value?.suggestionStatus === 'SUCCESS' ? 'done' as const : selectedMatch.value?.matchStatus === 'SUCCESS' ? 'current' as const : 'pending' as const,
  },
  {
    title: '局部改写',
    description: currentRewriteSuggestions.value.length ? `${currentRewriteSuggestions.value.length} 条改写建议` : '从建议中选择片段',
    status: currentRewriteSuggestions.value.length ? 'done' as const : selectedSuggestion.value?.suggestionStatus === 'SUCCESS' ? 'current' as const : 'pending' as const,
  },
  {
    title: '优化报告',
    description: optimizationReport.value ? '报告已加载' : '聚合已有结果',
    status: optimizationReport.value ? 'done' as const : selectedMatch.value?.matchStatus === 'SUCCESS' ? 'current' as const : 'pending' as const,
  },
])

const stageTabs = [
  { key: 'select', label: '选择资产' },
  { key: 'match', label: '匹配分析' },
  { key: 'suggestion', label: '岗位优化建议' },
  { key: 'rewrite', label: '局部改写' },
  { key: 'report', label: '优化报告' },
] as const

const currentRewriteSuggestions = computed(() => {
  if (!selectedJobDescriptionId.value && !selectedMatch.value?.matchId) {
    return rewriteSuggestions.value
  }

  return rewriteSuggestions.value.filter((item) => {
    return item.aiJobMatchResultId === selectedMatch.value?.matchId
      || item.jobDescriptionId === selectedJobDescriptionId.value
  })
})

const visibleRewriteSuggestions = computed(() => {
  if (rewriteHistoryExpanded.value) {
    return currentRewriteSuggestions.value
  }
  return currentRewriteSuggestions.value.slice(0, 3)
})

const hasMoreRewriteSuggestions = computed(() => currentRewriteSuggestions.value.length > visibleRewriteSuggestions.value.length)

const selectedSuggestionRewriteSuggestions = computed(() => {
  if (!selectedSuggestion.value?.suggestionId) {
    return []
  }

  return currentRewriteSuggestions.value.filter((item) => item.aiResumeSuggestionId === selectedSuggestion.value?.suggestionId)
})

const priorityOrder: Record<string, number> = {
  HIGH: 0,
  MEDIUM: 1,
  LOW: 2,
}

const sortSuggestionsByPriority = (suggestions: AiResumeSuggestionItem[]) => {
  return [...suggestions].sort((left, right) => {
    const leftOrder = priorityOrder[left.priority] ?? 9
    const rightOrder = priorityOrder[right.priority] ?? 9
    return leftOrder - rightOrder
  })
}

const prioritizedSuggestions = computed(() => {
  return sortSuggestionsByPriority(selectedSuggestion.value?.suggestions ?? [])
})

const normalizeSuggestionText = (value: string | null | undefined) => {
  if (!value) {
    return ''
  }
  return value.trim().replace(/\s+/g, ' ').toLowerCase()
}

const resolveSuggestionRewriteType = (suggestion: AiResumeSuggestionItem) => {
  return resolveRewriteTypeFromSuggestion(suggestion)
}

const getSuggestionRewriteRecords = (suggestion: AiResumeSuggestionItem) => {
  const targetSection = normalizeSuggestionText(suggestion.targetSection)
  const rewriteType = resolveSuggestionRewriteType(suggestion)

  return selectedSuggestionRewriteSuggestions.value.filter((record) => {
    return normalizeSuggestionText(record.targetSection) === targetSection
      && record.rewriteType === rewriteType
  })
}

const getSuggestionRewriteSummary = (suggestion: AiResumeSuggestionItem) => {
  const records = getSuggestionRewriteRecords(suggestion)
  const latestRecord = [...records].sort((left, right) => {
    const leftTime = left.updatedAt || ''
    const rightTime = right.updatedAt || ''
    return rightTime.localeCompare(leftTime)
  })[0] || null

  return {
    count: records.length,
    latestRecord,
  }
}

const openExistingRewriteRecord = (record: AiRewriteSuggestionResult) => {
  selectedRewriteSuggestion.value = record
  activeMatchStage.value = 'rewrite'
}

const reportPrioritySuggestions = computed(() => {
  const report = optimizationReport.value
  if (!report) {
    return []
  }

  return sortSuggestionsByPriority([
    ...report.highPrioritySuggestions,
    ...report.mediumPrioritySuggestions,
    ...report.lowPrioritySuggestions,
  ])
})

const reportHasSuggestions = computed(() => {
  const report = optimizationReport.value
  if (!report) {
    return false
  }

  return Boolean(
    report.highPrioritySuggestions.length
    || report.mediumPrioritySuggestions.length
    || report.lowPrioritySuggestions.length,
  )
})

const reportHasRewriteSuggestions = computed(() => {
  const report = optimizationReport.value
  if (!report) {
    return false
  }

  return Boolean(report.acceptedRewriteSuggestions.length || report.pendingRewriteSuggestions.length)
})

const reportVisibleConclusionCount = computed(() => {
  const report = optimizationReport.value
  if (!report) {
    return 0
  }

  return report.strongMatches.length + report.weakMatches.length + report.missingSkills.length + report.riskTips.length
})

const reportConclusionsFoldable = computed(() => {
  const report = optimizationReport.value
  if (!report) {
    return false
  }

  return reportVisibleConclusionCount.value > 4 || report.matchEvidence.length > 4
})

const reportSuggestionsFoldable = computed(() => {
  return reportPrioritySuggestions.value.length > 4
})

const reportRewritesFoldable = computed(() => {
  if (!optimizationReport.value) {
    return false
  }

  const totalRewriteSuggestions = optimizationReport.value.acceptedRewriteSuggestions.length + optimizationReport.value.pendingRewriteSuggestions.length
  return totalRewriteSuggestions > 4
})

const reportNextStepsFoldable = computed(() => {
  return (optimizationReport.value?.nextStepChecklist.length ?? 0) > 4
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

const resolveMatchLevelText = (level: string | null | undefined) => {
  if (level === 'HIGH') {
    return '高匹配'
  }
  if (level === 'MEDIUM') {
    return '中匹配'
  }
  if (level === 'LOW') {
    return '低匹配'
  }
  return level || '-'
}

const resolveMatchLevelType = (level: string | null | undefined) => {
  if (level === 'HIGH') {
    return 'success'
  }
  if (level === 'MEDIUM') {
    return 'warning'
  }
  if (level === 'LOW') {
    return 'danger'
  }
  return 'info'
}

const resolveAcceptStatusText = (status: string | null | undefined) => {
  if (status === 'PENDING') {
    return '待确认'
  }
  if (status === 'ACCEPTED') {
    return '已标记采纳'
  }
  if (status === 'REJECTED') {
    return '已拒绝'
  }
  return status || '-'
}

const resolveAcceptStatusType = (status: string | null | undefined) => {
  if (status === 'ACCEPTED') {
    return 'success'
  }
  if (status === 'REJECTED') {
    return 'danger'
  }
  return 'warning'
}

const resolveReportSourceText = (source: string | null | undefined) => {
  const sourceMap: Record<string, string> = {
    MATCH: '匹配分析',
    SUGGESTION: '优化建议',
    REWRITE: '局部改写',
  }
  return source ? sourceMap[source] || source : '-'
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

const reportSuggestionKey = (suggestion: AiResumeSuggestionItem, index: number, priority: string) => {
  return `${priority}-${suggestion.type}-${suggestion.targetSection}-${suggestion.issue}-${index}`
}

const reportRewriteKey = (suggestion: JobOptimizationRewriteSuggestion, index: number, group: string) => {
  return `${group}-${suggestion.rewriteId}-${suggestion.rewriteType}-${index}`
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
  optimizationReport.value = null
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
    optimizationReport.value = null
    rewriteSuggestions.value = []
    if (error instanceof Error && error.message !== '匹配分析结果不存在') {
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

const handleLoadOptimizationReport = async () => {
  if (!selectedResumeId.value || !selectedJobDescriptionId.value) {
    ElMessage.warning('请先选择简历和目标岗位')
    return
  }

  loadingOptimizationReport.value = true

  try {
    optimizationReport.value = await getJobOptimizationReport(selectedResumeId.value, selectedJobDescriptionId.value)
    activeMatchStage.value = 'report'
  } catch (error) {
    optimizationReport.value = null
    ElMessage.error(error instanceof Error ? error.message : '获取岗位优化报告失败')
  } finally {
    loadingOptimizationReport.value = false
  }
}

const handleMatch = async () => {
  if (!selectedResumeId.value || !selectedJobDescriptionId.value) {
    ElMessage.warning('请先选择简历和目标岗位')
    return
  }

  if (selectedResumeParseResult.value?.parseStatus !== 'SUCCESS') {
    ElMessage.warning('请先完成简历解析')
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
      activeMatchStage.value = 'match'
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
      activeMatchStage.value = 'suggestion'
    }
    await loadCurrentSuggestion()
    optimizationReport.value = null
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
    selectedRewriteSuggestion.value = currentRewriteSuggestions.value[0] || null
  } catch (error) {
    rewriteSuggestions.value = []
    selectedRewriteSuggestion.value = null
    ElMessage.warning(error instanceof Error ? error.message : '获取 AI 局部改写建议失败')
  } finally {
    loadingRewriteSuggestions.value = false
  }
}

const resolveSuggestionOriginalIndex = (suggestion: AiResumeSuggestionItem) => {
  const suggestions = selectedSuggestion.value?.suggestions ?? []
  const index = suggestions.indexOf(suggestion)
  if (index >= 0) {
    return index
  }
  return suggestions.findIndex((item) => {
    return item.type === suggestion.type
      && item.priority === suggestion.priority
      && item.targetSection === suggestion.targetSection
      && item.issue === suggestion.issue
      && item.suggestion === suggestion.suggestion
  })
}

const resolveRewriteTypeFromSuggestion = (suggestion?: AiResumeSuggestionItem | null) => {
  if (!suggestion) {
    return 'PROJECT'
  }
  if (suggestion.type === 'SKILL_GAP' || suggestion.targetSection?.includes('技能')) {
    return 'SKILL'
  }
  if (suggestion.type === 'EXPERIENCE_WEAKNESS' || suggestion.targetSection?.includes('实习') || suggestion.targetSection?.includes('工作')) {
    return 'INTERNSHIP'
  }
  if (suggestion.targetSection?.includes('总结')) {
    return 'SUMMARY'
  }
  if (suggestion.targetSection?.includes('教育')) {
    return 'EDUCATION'
  }
  if (suggestion.type === 'PROJECT_DESCRIPTION' || suggestion.targetSection?.includes('项目')) {
    return 'PROJECT'
  }
  return 'OTHER'
}

const resolveRewriteTypeFromSection = (sectionType: string | null | undefined, fallback: string) => {
  const normalized = sectionType || ''
  if (normalized === 'SKILLS') {
    return 'SKILL'
  }
  if (normalized === 'SUMMARY') {
    return 'SUMMARY'
  }
  if (normalized === 'EDUCATION') {
    return 'EDUCATION'
  }
  if (normalized === 'INTERNSHIP' || normalized === 'WORK_EXPERIENCES' || normalized === 'CAMPUS_EXPERIENCES') {
    return 'INTERNSHIP'
  }
  if (normalized === 'PROJECTS') {
    return 'PROJECT'
  }
  return fallback
}

const resetRewriteContext = () => {
  rewriteContext.value = null
  selectedRewriteSourceIndex.value = 0
  selectedRewriteSource.value = null
  selectedSuggestionItem.value = null
  selectedSuggestionItemIndex.value = null
  latestDialogRewriteResult.value = null
  rewriteContextError.value = null
}

const applyRewriteSource = (index: number) => {
  selectedRewriteSourceIndex.value = index
  const source = rewriteContext.value?.recommendedSections[index] || null
  selectedRewriteSource.value = source

  if (!source) {
    return
  }

  rewriteForm.originalText = source.sourceText || rewriteForm.originalText
  rewriteForm.targetSection = source.sectionTitle || selectedSuggestionItem.value?.targetSection || rewriteForm.targetSection
  rewriteForm.rewriteType = resolveRewriteTypeFromSection(source.sectionType, rewriteForm.rewriteType)
}

const applyRewriteContext = (context: RewriteContext) => {
  rewriteContext.value = context
  rewriteForm.rewriteGoal = context.defaultRewriteGoal || rewriteForm.rewriteGoal
  rewriteForm.tone = context.tones[0] || rewriteForm.tone

  if (context.recommendedSections.length > 0) {
    applyRewriteSource(0)
  }
}

const loadRewriteContextForSuggestion = async (suggestion: AiResumeSuggestionItem, suggestionIndex: number | null) => {
  if (!selectedSuggestion.value?.suggestionId) {
    return
  }

  loadingRewriteContext.value = true
  rewriteContextError.value = null

  try {
    const context = await getAiRewriteContext(
      selectedSuggestion.value.suggestionId,
      suggestionIndex != null && suggestionIndex >= 0 ? suggestionIndex : undefined,
    )
    applyRewriteContext(context)
  } catch (error) {
    rewriteContext.value = null
    selectedRewriteSource.value = null
    rewriteContextError.value = error instanceof Error ? error.message : '获取改写上下文失败，请手动粘贴原文片段'
  } finally {
    loadingRewriteContext.value = false
  }
}

const copyRewriteResult = async (text?: string | null) => {
  if (!text) {
    ElMessage.warning('暂无可复制的改写结果')
    return
  }

  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制改写结果')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

const openRewriteDialog = (suggestion?: AiResumeSuggestionItem, suggestionIndex?: number | null) => {
  resetRewriteContext()
  if (suggestion) {
    selectedSuggestionItem.value = suggestion
    selectedSuggestionItemIndex.value = suggestionIndex ?? resolveSuggestionOriginalIndex(suggestion)
    rewriteForm.targetSection = suggestion.targetSection || '项目经历'
    rewriteForm.originalText = ''
    rewriteForm.rewriteType = resolveRewriteTypeFromSuggestion(suggestion)
    rewriteForm.rewriteGoal = suggestion.suggestion || ''
    rewriteForm.tone = '简洁专业'
    rewriteForm.lengthLimit = 180
    void loadRewriteContextForSuggestion(suggestion, selectedSuggestionItemIndex.value)
  } else {
    rewriteForm.originalText = ''
    rewriteForm.rewriteGoal = ''
    rewriteForm.tone = '简洁专业'
    rewriteForm.lengthLimit = 180
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
    const sourceText = rewriteForm.originalText.trim()
    const result = await triggerAiRewriteSuggestion(selectedResumeId.value, {
      rewriteType: rewriteForm.rewriteType,
      targetSection: rewriteForm.targetSection.trim(),
      originalText: sourceText,
      sourceText,
      jobDescriptionId: selectedJobDescriptionId.value || undefined,
      aiJobMatchResultId: selectedMatch.value?.matchId,
      matchId: selectedMatch.value?.matchId,
      aiResumeSuggestionId: selectedSuggestion.value?.suggestionId,
      suggestionId: selectedSuggestion.value?.suggestionId,
      rewriteGoal: rewriteForm.rewriteGoal.trim() || undefined,
      jobKeywords: rewriteContextKeywords.value,
      tone: rewriteForm.tone || undefined,
      lengthLimit: rewriteForm.lengthLimit || undefined,
    })
    selectedRewriteSuggestion.value = result
    latestDialogRewriteResult.value = result
    if (result.rewriteStatus === 'FAILED') {
      ElMessage.error(result.errorMessage || 'AI 局部改写生成失败')
    } else {
      ElMessage.success('AI 局部改写生成完成')
      activeMatchStage.value = 'rewrite'
    }
    await loadRewriteSuggestions()
    optimizationReport.value = null
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
    optimizationReport.value = null
    ElMessage.success(acceptStatus === 'ACCEPTED' ? '已标记采纳改写建议，不会写回原简历' : '已拒绝改写建议')
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
  <section class="ai-match-page">
    <section class="ai-match-shell">
      <PageHeader
        eyebrow="匹配与优化"
        title="按流程生成匹配分析、优化建议和岗位优化报告"
        description="先选择已解析简历和目标岗位，再按匹配分析、岗位优化建议、局部改写、优化报告逐步推进。局部改写只记录采纳状态，不写回原始简历。"
      >
        <template #actions>
          <el-button @click="router.push('/resumes')">我的简历</el-button>
          <el-button type="primary" @click="router.push('/job-descriptions')">目标岗位</el-button>
        </template>
      </PageHeader>

      <section v-loading="loading" class="ai-match-panel">
        <section class="ai-match-workflow">
          <ProcessStepper :steps="matchWorkflowSteps" />
        </section>

        <div class="ai-match-stage-tabs">
          <button
            v-for="tab in stageTabs"
            :key="tab.key"
            type="button"
            :class="{ 'is-active': activeMatchStage === tab.key }"
            @click="activeMatchStage = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <section v-if="activeGenerationStatus" class="ai-match-generation-status">
          <strong>{{ activeGenerationStatus.title }}</strong>
          <p>{{ activeGenerationStatus.description }}</p>
        </section>

        <section v-show="activeMatchStage === 'select'" class="ai-match-stage-panel">
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
              <el-button text type="primary" @click="router.push('/resumes')">去解析简历</el-button>
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
              <el-button text type="primary" @click="router.push('/job-descriptions')">去解析目标岗位</el-button>
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
        </section>

        <section v-if="activeMatchStage !== 'select'" v-loading="loadingResult" class="ai-match-result">
          <EmptyState
            v-if="!selectedMatch"
            title="还没有匹配分析结果"
            description="先选择已解析简历和目标岗位，再生成匹配分析。"
            secondary-text="下一步建议：返回选择资产，确认简历和目标岗位都已解析成功。"
            action-text="返回选择资产"
            @action="activeMatchStage = 'select'"
          />

          <template v-else>
            <template v-if="activeMatchStage === 'match'">
            <section class="ai-match-report-hero">
              <article class="ai-match-score" :class="`is-${matchScoreSummary.level}`">
                <span class="ai-match-score-value">{{ selectedMatch.overallScore ?? '-' }}</span>
                <span class="ai-match-score-label">总体匹配分</span>
                <strong>{{ matchScoreSummary.label }}</strong>
                <p>{{ matchScoreSummary.description }}</p>
              </article>

              <article class="ai-match-report-summary-card">
                <div class="ai-match-report-summary-header">
                  <div>
                    <h2 class="ai-match-section-title">匹配分析概览</h2>
                    <p class="ai-match-suggestion-note">匹配分析只判断简历和目标岗位是否匹配，不生成改写文本，不自动修改简历。</p>
                  </div>
                  <el-tag :type="resolveMatchStatusType(selectedMatch.matchStatus)">
                    {{ resolveMatchStatusText(selectedMatch.matchStatus) }}
                  </el-tag>
                </div>
                <div class="ai-match-metric-grid">
                  <article
                    v-for="metric in matchMetricCards"
                    :key="metric.label"
                    class="ai-match-metric-card"
                    :class="`is-${metric.tone}`"
                  >
                    <span>{{ metric.label }}</span>
                    <strong>{{ metric.value }}</strong>
                    <small>{{ metric.note }}</small>
                  </article>
                </div>
                <el-descriptions :column="2" border class="ai-match-descriptions">
                  <el-descriptions-item label="更新时间">{{ formatDateTime(selectedMatch.updatedAt) }}</el-descriptions-item>
                  <el-descriptions-item label="目标岗位">{{ selectedJobDescription?.title || '-' }}</el-descriptions-item>
                </el-descriptions>
              </article>
            </section>

            <el-alert
              v-if="selectedMatch.matchStatus === 'FAILED'"
              class="ai-match-alert"
              :title="selectedMatch.errorMessage || '匹配分析失败'"
              type="error"
              :closable="false"
              show-icon
            />

            <section class="ai-match-next-action">
              <div>
                <h3 class="ai-match-section-title">{{ matchNextAction.title }}</h3>
                <p>{{ matchNextAction.description }}</p>
              </div>
              <el-space wrap>
                <el-button
                  type="primary"
                  :loading="generatingSuggestion"
                  :disabled="!canGenerateSuggestion"
                  @click="handleGenerateSuggestion"
                >
                  生成岗位优化建议
                </el-button>
                <el-button
                  :loading="loadingOptimizationReport"
                  :disabled="!selectedResumeId || !selectedJobDescriptionId"
                  @click="handleLoadOptimizationReport"
                >
                  查看优化报告
                </el-button>
              </el-space>
            </section>
            </template>

            <section v-show="activeMatchStage === 'report'" class="ai-match-report-panel">
              <div class="ai-match-suggestion-header">
                <div>
                  <h2 class="ai-match-section-title">岗位优化报告</h2>
                  <p class="ai-match-suggestion-note">聚合匹配分析、岗位优化建议和局部改写结果，不重新调用 AI。</p>
                </div>
                <el-button
                  :loading="loadingOptimizationReport"
                  :disabled="!selectedResumeId || !selectedJobDescriptionId"
                  @click="handleLoadOptimizationReport"
                >
                  刷新报告
                </el-button>
              </div>

              <section v-loading="loadingOptimizationReport" class="ai-match-report-body">
                <EmptyState
                  v-if="!optimizationReport"
                  title="还没有岗位优化报告"
                  description="请先完成匹配分析，再点击查看优化报告。"
                  secondary-text="下一步建议：如果匹配分析已成功，点击右上角刷新报告。"
                />

                <template v-else>
                  <div class="ai-match-report-overview">
                    <div class="ai-match-report-score">
                      <span>{{ optimizationReport.matchScore ?? '-' }}</span>
                      <small>匹配分数</small>
                    </div>
                    <el-descriptions :column="2" border class="ai-match-descriptions">
                      <el-descriptions-item label="简历名称">{{ optimizationReport.resumeName || selectedResume?.originalFilename || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="目标岗位">{{ optimizationReport.jobTitle || selectedJobDescription?.title || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="匹配等级">
                        <el-tag :type="resolveMatchLevelType(optimizationReport.matchLevel)">
                          {{ resolveMatchLevelText(optimizationReport.matchLevel) }}
                        </el-tag>
                      </el-descriptions-item>
                      <el-descriptions-item label="生成时间">{{ formatDateTime(optimizationReport.generatedAt) }}</el-descriptions-item>
                    </el-descriptions>
                  </div>

                  <div v-if="optimizationReport.warnings.length" class="ai-match-report-warning-list">
                    <el-alert
                      v-for="warning in optimizationReport.warnings"
                      :key="`${warning.source}-${warning.code}`"
                      :title="warning.message"
                      type="warning"
                      :closable="false"
                      show-icon
                    />
                  </div>

                  <section class="ai-match-report-section">
                    <div class="ai-match-report-toggle-header">
                      <h3 class="ai-match-section-title">匹配结论</h3>
                      <el-button
                        v-if="reportConclusionsFoldable"
                        text
                        type="primary"
                        @click="reportSectionsExpanded.conclusions = !reportSectionsExpanded.conclusions"
                      >
                        {{ reportSectionsExpanded.conclusions ? '收起' : '展开' }}
                      </el-button>
                    </div>
                    <template v-if="!reportConclusionsFoldable || reportSectionsExpanded.conclusions">
                      <div class="ai-match-report-columns">
                        <div class="ai-match-report-card">
                          <h4>强匹配项</h4>
                          <div v-if="optimizationReport.strongMatches.length" class="ai-match-list">
                            <p v-for="item in optimizationReport.strongMatches" :key="`report-strong-${item.item}-${item.reason}`">
                              <strong>{{ item.item || '-' }}</strong>
                              <span>{{ item.reason || '-' }}</span>
                            </p>
                          </div>
                          <el-empty v-else description="暂无强匹配项" :image-size="56" />
                        </div>
                        <div class="ai-match-report-card">
                          <h4>弱匹配项</h4>
                          <div v-if="optimizationReport.weakMatches.length" class="ai-match-list">
                            <p v-for="item in optimizationReport.weakMatches" :key="`report-weak-${item.item}-${item.reason}`">
                              <strong>{{ item.item || '-' }}</strong>
                              <span>{{ item.reason || '-' }}</span>
                            </p>
                          </div>
                          <el-empty v-else description="暂无弱匹配项" :image-size="56" />
                        </div>
                        <div class="ai-match-report-card">
                          <h4>缺失技能</h4>
                          <div v-if="optimizationReport.missingSkills.length" class="ai-match-list">
                            <p v-for="item in optimizationReport.missingSkills" :key="`report-missing-${item.item}-${item.reason}`">
                              <strong>{{ item.item || '-' }}</strong>
                              <span>{{ item.reason || '-' }}</span>
                            </p>
                          </div>
                          <el-empty v-else description="暂无缺失技能" :image-size="56" />
                        </div>
                        <div class="ai-match-report-card">
                          <h4>风险提示</h4>
                          <div v-if="optimizationReport.riskTips.length" class="ai-match-list">
                            <p v-for="item in optimizationReport.riskTips" :key="`report-risk-${item}`">{{ item }}</p>
                          </div>
                          <el-empty v-else description="暂无风险提示" :image-size="56" />
                        </div>
                      </div>
                      <div class="ai-match-report-card ai-match-report-evidence-card">
                        <h4>匹配依据</h4>
                        <div v-if="optimizationReport.matchEvidence.length" class="ai-match-report-evidence-list">
                          <p
                            v-for="item in optimizationReport.matchEvidence"
                            :key="`report-evidence-${item.source}-${item.content}`"
                          >
                            <el-tag size="small" :type="item.source === 'job' ? 'warning' : 'success'">
                              {{ item.source === 'job' ? '岗位' : item.source === 'resume' ? '简历' : item.source }}
                            </el-tag>
                            <span>{{ item.content || '-' }}</span>
                          </p>
                        </div>
                        <el-empty v-else description="当前匹配结果缺少详细依据" :image-size="56" />
                      </div>
                    </template>
                    <p v-else class="ai-match-report-fold-summary">
                      已汇总 {{ reportVisibleConclusionCount }} 条结论，点击展开查看强匹配、弱匹配、缺失技能和风险提示。
                    </p>
                  </section>

                  <section class="ai-match-report-section">
                    <div class="ai-match-report-toggle-header">
                      <h3 class="ai-match-section-title">优化建议</h3>
                      <el-button
                        v-if="reportSuggestionsFoldable"
                        text
                        type="primary"
                        @click="reportSectionsExpanded.suggestions = !reportSectionsExpanded.suggestions"
                      >
                        {{ reportSectionsExpanded.suggestions ? '收起' : '展开' }}
                      </el-button>
                    </div>
                    <template v-if="!reportSuggestionsFoldable || reportSectionsExpanded.suggestions">
                      <el-alert
                        v-if="!reportHasSuggestions"
                        title="请先生成岗位优化建议"
                        type="info"
                        :closable="false"
                        show-icon
                      />
                      <div v-else class="ai-match-suggestion-list is-priority-list">
                        <article
                          v-for="(suggestion, index) in reportPrioritySuggestions"
                          :key="reportSuggestionKey(suggestion, index, suggestion.priority)"
                          class="ai-match-report-suggestion"
                        >
                          <div class="ai-match-suggestion-tags">
                            <el-tag size="small" :type="resolvePriorityType(suggestion.priority)">
                              {{ resolvePriorityText(suggestion.priority) }}
                            </el-tag>
                            <el-tag size="small">{{ resolveSuggestionTypeText(suggestion.type) }}</el-tag>
                            <el-tag v-if="suggestion.targetSection" size="small" type="info">
                              {{ suggestion.targetSection }}
                            </el-tag>
                          </div>
                          <p><strong>问题：</strong>{{ suggestion.issue || '-' }}</p>
                          <p><strong>建议：</strong>{{ suggestion.suggestion || '-' }}</p>
                          <div v-if="suggestion.evidence.length" class="ai-match-report-evidence-list">
                            <p v-for="evidence in suggestion.evidence" :key="`report-suggestion-evidence-${index}-${evidence}`">
                              <el-tag size="small" type="success">依据</el-tag>
                              <span>{{ evidence }}</span>
                            </p>
                          </div>
                          <p v-if="suggestion.caution" class="ai-match-suggestion-caution"><strong>注意：</strong>{{ suggestion.caution }}</p>
                        </article>
                      </div>
                    </template>
                    <p v-else class="ai-match-report-fold-summary">
                      共有 {{ reportPrioritySuggestions.length }} 条建议，点击展开查看完整优先级清单。
                    </p>
                  </section>

                  <section class="ai-match-report-section">
                    <div class="ai-match-report-toggle-header">
                      <h3 class="ai-match-section-title">局部改写建议</h3>
                      <el-button
                        v-if="reportRewritesFoldable"
                        text
                        type="primary"
                        @click="reportSectionsExpanded.rewrites = !reportSectionsExpanded.rewrites"
                      >
                        {{ reportSectionsExpanded.rewrites ? '收起' : '展开' }}
                      </el-button>
                    </div>
                    <template v-if="!reportRewritesFoldable || reportSectionsExpanded.rewrites">
                      <el-alert
                        v-if="!reportHasRewriteSuggestions"
                        title="可选择关键片段生成局部改写"
                        type="info"
                        :closable="false"
                        show-icon
                      />
                      <div v-else class="ai-match-report-columns">
                        <div class="ai-match-report-card">
                          <h4>已标记采纳</h4>
                          <div v-if="optimizationReport.acceptedRewriteSuggestions.length" class="ai-match-suggestion-list">
                            <article
                              v-for="(suggestion, index) in optimizationReport.acceptedRewriteSuggestions"
                              :key="reportRewriteKey(suggestion, index, 'ACCEPTED')"
                              class="ai-match-report-rewrite"
                            >
                              <div class="ai-match-suggestion-tags">
                                <el-tag size="small">{{ resolveRewriteTypeText(suggestion.rewriteType) }}</el-tag>
                                <el-tag size="small" :type="resolveAcceptStatusType(suggestion.acceptStatus)">
                                  {{ resolveAcceptStatusText(suggestion.acceptStatus) }}
                                </el-tag>
                              </div>
                              <p><strong>目标部分：</strong>{{ suggestion.targetSection || '-' }}</p>
                              <p><strong>原文：</strong>{{ suggestion.originalText || '-' }}</p>
                              <p><strong>改写：</strong>{{ suggestion.rewrittenText || '-' }}</p>
                              <p><strong>理由：</strong>{{ suggestion.rewriteReason || '-' }}</p>
                              <p v-if="suggestion.caution" class="ai-match-suggestion-caution"><strong>注意：</strong>{{ suggestion.caution }}</p>
                            </article>
                          </div>
                          <el-empty v-else description="暂无已标记采纳改写" :image-size="56" />
                        </div>
                        <div class="ai-match-report-card">
                          <h4>待确认</h4>
                          <div v-if="optimizationReport.pendingRewriteSuggestions.length" class="ai-match-suggestion-list">
                            <article
                              v-for="(suggestion, index) in optimizationReport.pendingRewriteSuggestions"
                              :key="reportRewriteKey(suggestion, index, 'PENDING')"
                              class="ai-match-report-rewrite"
                            >
                              <div class="ai-match-suggestion-tags">
                                <el-tag size="small">{{ resolveRewriteTypeText(suggestion.rewriteType) }}</el-tag>
                                <el-tag size="small" :type="resolveAcceptStatusType(suggestion.acceptStatus)">
                                  {{ resolveAcceptStatusText(suggestion.acceptStatus) }}
                                </el-tag>
                              </div>
                              <p><strong>目标部分：</strong>{{ suggestion.targetSection || '-' }}</p>
                              <p><strong>原文：</strong>{{ suggestion.originalText || '-' }}</p>
                              <p><strong>改写：</strong>{{ suggestion.rewrittenText || '-' }}</p>
                              <p><strong>理由：</strong>{{ suggestion.rewriteReason || '-' }}</p>
                              <p v-if="suggestion.caution" class="ai-match-suggestion-caution"><strong>注意：</strong>{{ suggestion.caution }}</p>
                            </article>
                          </div>
                          <el-empty v-else description="暂无待确认改写" :image-size="56" />
                        </div>
                      </div>
                    </template>
                    <p v-else class="ai-match-report-fold-summary">
                      共有 {{ optimizationReport.acceptedRewriteSuggestions.length + optimizationReport.pendingRewriteSuggestions.length }} 条改写建议，点击展开查看。
                    </p>
                  </section>

                  <section class="ai-match-report-section">
                    <div class="ai-match-report-toggle-header">
                      <h3 class="ai-match-section-title">下一步修改清单</h3>
                      <el-button
                        v-if="reportNextStepsFoldable"
                        text
                        type="primary"
                        @click="reportSectionsExpanded.nextSteps = !reportSectionsExpanded.nextSteps"
                      >
                        {{ reportSectionsExpanded.nextSteps ? '收起' : '展开' }}
                      </el-button>
                    </div>
                    <template v-if="!reportNextStepsFoldable || reportSectionsExpanded.nextSteps">
                      <div v-if="optimizationReport.nextStepChecklist.length" class="ai-match-report-step-list">
                        <p v-for="step in optimizationReport.nextStepChecklist" :key="step.key">
                          <el-tag size="small" type="info">{{ resolveReportSourceText(step.source) }}</el-tag>
                          <span>{{ step.text }}</span>
                        </p>
                      </div>
                      <el-empty v-else description="暂无下一步修改清单" :image-size="56" />
                    </template>
                    <p v-else class="ai-match-report-fold-summary">
                      共有 {{ optimizationReport.nextStepChecklist.length }} 条下一步建议，点击展开查看。
                    </p>
                  </section>

                </template>
              </section>
            </section>

            <section v-show="activeMatchStage === 'suggestion'" class="ai-match-suggestion-panel">
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
                <EmptyState
                  v-if="!selectedSuggestion"
                  title="还没有岗位优化建议"
                  description="匹配分析成功后，可以把差距转成可执行的岗位优化建议。"
                  secondary-text="下一步建议：先确认当前匹配分析成功，再点击生成岗位优化建议。"
                  action-text="返回匹配分析"
                  @action="activeMatchStage = 'match'"
                />

                <template v-else>
                  <el-alert
                    v-if="selectedSuggestionRewriteSuggestions.length"
                    class="ai-match-rewrite-summary"
                    :title="`当前建议已改写 ${selectedSuggestionRewriteSuggestions.length} 次`"
                    :description="selectedSuggestionRewriteSuggestions[0]?.rewrittenText ? '可以直接查看最近一次改写结果，或继续生成新的版本。' : '可以直接查看最近一次改写结果，或继续生成新的版本。'"
                    type="success"
                    :closable="false"
                    show-icon
                  />

                  <el-descriptions :column="2" border class="ai-match-descriptions">
                    <el-descriptions-item label="建议状态">
                      <el-tag :type="resolveSuggestionStatusType(selectedSuggestion.suggestionStatus)">
                        {{ resolveSuggestionStatusText(selectedSuggestion.suggestionStatus) }}
                      </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="建议数量">{{ selectedSuggestion.suggestions.length }}</el-descriptions-item>
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

                  <section class="ai-match-suggestion-list is-priority-list">
                    <article
                      v-for="(suggestion, index) in prioritizedSuggestions"
                      :key="suggestionKey(suggestion, index)"
                      class="ai-match-suggestion-item"
                      :class="{ 'is-rewritten': getSuggestionRewriteSummary(suggestion).count > 0 }"
                    >
                      <div class="ai-match-suggestion-head">
                        <div class="ai-match-suggestion-tags">
                          <el-tag size="small" :type="resolvePriorityType(suggestion.priority)">
                            {{ resolvePriorityText(suggestion.priority) }}
                          </el-tag>
                          <el-tag size="small">{{ resolveSuggestionTypeText(suggestion.type) }}</el-tag>
                          <el-tag v-if="suggestion.targetSection" size="small" type="info">
                            {{ suggestion.targetSection }}
                          </el-tag>
                        </div>
                        <el-tag v-if="getSuggestionRewriteSummary(suggestion).count > 0" size="small" type="success">
                          已改写 {{ getSuggestionRewriteSummary(suggestion).count }} 次
                        </el-tag>
                      </div>
                      <p><strong>问题：</strong>{{ suggestion.issue || '-' }}</p>
                      <p><strong>建议：</strong>{{ suggestion.suggestion || '-' }}</p>
                      <div class="ai-match-inline-actions">
                        <div class="ai-match-inline-actions-hint">
                          <strong>{{ getSuggestionRewriteSummary(suggestion).count > 0 ? '这条建议已改写' : '建议驱动改写' }}</strong>
                          <span>{{ getSuggestionRewriteSummary(suggestion).count > 0 ? '可直接查看最近一次改写结果，或继续生成新的版本' : '自动带出候选原文、岗位关键词和改写目标' }}</span>
                        </div>
                        <el-space wrap>
                          <el-button
                            v-if="getSuggestionRewriteSummary(suggestion).latestRecord"
                            plain
                            type="success"
                            @click="openExistingRewriteRecord(getSuggestionRewriteSummary(suggestion).latestRecord!)"
                          >
                            查看已改写
                          </el-button>
                          <el-button
                            type="primary"
                            size="default"
                            :icon="ArrowRight"
                            @click="openRewriteDialog(suggestion, resolveSuggestionOriginalIndex(suggestion))"
                          >
                            {{ getSuggestionRewriteSummary(suggestion).count > 0 ? '再次改写' : '基于此建议改写' }}
                          </el-button>
                        </el-space>
                      </div>
                      <div v-if="getSuggestionRewriteSummary(suggestion).latestRecord" class="ai-match-suggestion-rewrite-note">
                        <span>最近改写：</span>
                        <el-tag size="small" :type="resolveRewriteStatusType(getSuggestionRewriteSummary(suggestion).latestRecord!.rewriteStatus)">
                          {{ resolveRewriteStatusText(getSuggestionRewriteSummary(suggestion).latestRecord!.rewriteStatus) }}
                        </el-tag>
                        <small>{{ getSuggestionRewriteSummary(suggestion).latestRecord!.rewrittenText ? '已生成改写内容' : '未生成改写内容' }}</small>
                      </div>
                      <div v-if="suggestion.evidence.length" class="ai-match-suggestion-evidence">
                        <strong>依据：</strong>
                        <span v-for="evidence in suggestion.evidence" :key="evidence">{{ evidence }}</span>
                      </div>
                      <div v-if="suggestion.relatedItems.length" class="ai-match-related-items">
                        <el-tag v-for="item in suggestion.relatedItems" :key="item" size="small" type="warning">{{ item }}</el-tag>
                      </div>
                      <p v-if="suggestion.caution" class="ai-match-suggestion-caution">
                        <strong>注意：</strong>{{ suggestion.caution }}
                      </p>
                    </article>
                  </section>
                </template>
              </section>
            </section>

            <section v-show="activeMatchStage === 'rewrite'" class="ai-match-rewrite-panel">
              <div class="ai-match-suggestion-header">
                <div>
                  <h2 class="ai-match-section-title">AI 局部改写</h2>
                  <p class="ai-match-suggestion-note">局部改写建议需用户确认，只记录采纳状态，不会自动写回原简历。</p>
                </div>
                <el-space>
                  <el-button type="primary" :disabled="!selectedResumeId" @click="openRewriteDialog()">进行局部改写</el-button>
                  <el-button :loading="loadingRewriteSuggestions" :disabled="!selectedResumeId" @click="loadRewriteSuggestions">
                    刷新改写
                  </el-button>
                </el-space>
              </div>

              <section v-loading="loadingRewriteSuggestions" class="ai-match-rewrite-body">
                <EmptyState
                  v-if="!selectedRewriteSuggestion"
                  title="还没有局部改写建议"
                  description="选择一段真实简历片段后，AI 可以生成表达优化建议。"
                  secondary-text="下一步建议：从岗位优化建议进入改写，或手动粘贴要优化的片段。"
                  action-text="进行局部改写"
                  @action="openRewriteDialog()"
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
                    <el-descriptions-item label="生成时间">{{ formatDateTime(selectedRewriteSuggestion.updatedAt) }}</el-descriptions-item>
                  </el-descriptions>

                  <div class="ai-match-rewrite-actions">
                    <el-button
                      type="success"
                      :loading="updatingRewriteAcceptStatus"
                      :disabled="selectedRewriteSuggestion.acceptStatus === 'ACCEPTED' || selectedRewriteSuggestion.rewriteStatus !== 'SUCCESS'"
                      @click="handleUpdateRewriteAcceptStatus('ACCEPTED')"
                    >
                      标记采纳
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

                <div v-if="currentRewriteSuggestions.length" class="ai-match-rewrite-history">
                  <div class="ai-match-rewrite-history-header">
                    <h3 class="ai-match-section-title">历史改写建议</h3>
                    <el-button
                      text
                      type="primary"
                      @click="rewriteHistoryExpanded = !rewriteHistoryExpanded"
                    >
                      {{ rewriteHistoryExpanded ? '收起历史' : `展开历史 (${currentRewriteSuggestions.length})` }}
                    </el-button>
                  </div>
                  <div class="ai-match-rewrite-history-list">
                    <button
                      v-for="(suggestion, index) in visibleRewriteSuggestions"
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

            <section v-show="activeMatchStage === 'match'" class="ai-match-insight-grid">
              <article
                v-for="section in matchInsightSections"
                :key="section.key"
                class="ai-match-insight-card"
                :class="`is-${section.tone}`"
              >
                <div class="ai-match-insight-card-header">
                  <h2 class="ai-match-section-title">{{ section.title }}</h2>
                  <el-tag size="small" :type="section.tone">{{ section.items.length }} 条</el-tag>
                </div>
                <div v-if="section.items.length" class="ai-match-insight-list">
                  <p v-for="item in section.items" :key="`${section.key}-${item.title}-${item.description}`">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.description }}</span>
                  </p>
                </div>
                <el-empty v-else :description="section.emptyText" :image-size="72" />
              </article>
            </section>

            <section v-show="activeMatchStage === 'match'" class="ai-match-evidence-layout">
              <article class="ai-match-block">
                <h2 class="ai-match-section-title">匹配依据</h2>
                <div v-if="selectedMatch.evidence.length" class="ai-match-evidence-list">
                  <p v-for="item in selectedMatch.evidence" :key="`${item.source}-${item.content}`">
                    <el-tag size="small" :type="item.source === 'job' ? 'warning' : 'success'">
                      {{ item.source === 'job' ? '岗位' : '简历' }}
                    </el-tag>
                    <span>{{ item.content || '-' }}</span>
                  </p>
                </div>
                <el-empty v-else description="暂无匹配依据" :image-size="72" />
              </article>

              <article class="ai-match-block">
                <h2 class="ai-match-section-title">风险提示</h2>
                <div v-if="selectedMatch.riskNotes.length" class="ai-match-risk-list">
                  <p v-for="item in selectedMatch.riskNotes" :key="item">{{ item }}</p>
                </div>
                <el-empty v-else description="暂无风险提示" :image-size="72" />
              </article>
            </section>
          </template>
        </section>
      </section>
    </section>

    <el-dialog
      v-model="rewriteDialogVisible"
      title="AI 局部改写"
      width="860px"
      destroy-on-close
    >
      <el-alert
        class="ai-match-dialog-alert"
        title="局部改写只优化表达，只记录采纳状态，不会自动写回原简历。"
        type="warning"
        :closable="false"
        show-icon
      />

      <section v-if="selectedSuggestionItem || loadingRewriteContext || rewriteContextError" class="ai-match-dialog-context">
        <article v-if="selectedSuggestionItem" class="ai-match-dialog-suggestion">
          <div class="ai-match-suggestion-tags">
            <el-tag size="small" :type="resolvePriorityType(selectedSuggestionItem.priority)">
              {{ resolvePriorityText(selectedSuggestionItem.priority) }}
            </el-tag>
            <el-tag size="small">{{ resolveSuggestionTypeText(selectedSuggestionItem.type) }}</el-tag>
            <el-tag v-if="selectedSuggestionItem.targetSection" size="small" type="info">
              {{ selectedSuggestionItem.targetSection }}
            </el-tag>
          </div>
          <p><strong>当前建议：</strong>{{ selectedSuggestionItem.suggestion || selectedSuggestionItem.issue || '-' }}</p>
        </article>

        <el-alert
          v-if="rewriteContextError"
          class="ai-match-dialog-alert"
          :title="rewriteContextError"
          type="warning"
          :closable="false"
          show-icon
        />

        <div v-loading="loadingRewriteContext" class="ai-match-context-body">
          <div v-if="rewriteContextKeywords.length" class="ai-match-keyword-row">
            <span>岗位关键词</span>
            <el-tag
              v-for="keyword in rewriteContextKeywords"
              :key="keyword"
              size="small"
              type="info"
            >
              {{ keyword }}
            </el-tag>
          </div>

          <div v-if="rewriteContext?.recommendedSections.length" class="ai-match-source-picker">
            <h3 class="ai-match-section-title">推荐候选原文</h3>
            <button
              v-for="(source, index) in rewriteContext.recommendedSections"
              :key="`${source.sectionType}-${source.sectionTitle}-${index}`"
              type="button"
              class="ai-match-source-option"
              :class="{ 'is-active': selectedRewriteSourceIndex === index }"
              @click="applyRewriteSource(index)"
            >
              <span>
                <strong>{{ source.sectionTitle || '候选片段' }}</strong>
                <small>{{ source.reason }}</small>
              </span>
              <el-tag size="small" :type="source.confidence >= 0.7 ? 'success' : 'info'">
                {{ Math.round(source.confidence * 100) }}%
              </el-tag>
            </button>
          </div>

          <el-alert
            v-else-if="selectedSuggestionItem && !loadingRewriteContext && !rewriteContextError"
            class="ai-match-dialog-alert"
            title="当前建议没有自动推荐到可靠候选片段，请手动粘贴要改写的简历原文。"
            type="info"
            :closable="false"
            show-icon
          />
        </div>
      </section>

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
        <el-form-item label="改写目标">
          <el-select
            v-model="rewriteForm.rewriteGoal"
            class="ai-match-select"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入这次改写要达成的目标"
          >
            <el-option
              v-for="goal in rewriteContextGoals"
              :key="goal"
              :label="goal"
              :value="goal"
            />
          </el-select>
        </el-form-item>
        <div class="ai-match-dialog-form-grid">
          <el-form-item label="表达风格">
            <el-select v-model="rewriteForm.tone" class="ai-match-select">
              <el-option
                v-for="tone in rewriteContextTones"
                :key="tone"
                :label="tone"
                :value="tone"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="期望长度">
            <el-input-number
              v-model="rewriteForm.lengthLimit"
              :min="80"
              :max="500"
              :step="20"
              controls-position="right"
              class="ai-match-length-input"
            />
          </el-form-item>
        </div>
        <el-form-item label="原文片段">
          <el-input
            v-model="rewriteForm.originalText"
            type="textarea"
            :rows="7"
            maxlength="3000"
            show-word-limit
            placeholder="确认或粘贴需要优化表达的简历片段"
          />
        </el-form-item>
        <el-descriptions :column="1" border class="ai-match-descriptions">
          <el-descriptions-item label="关联目标岗位">{{ selectedJobDescription?.title || '未关联' }}</el-descriptions-item>
          <el-descriptions-item label="关联匹配分析">{{ selectedMatch ? resolveMatchStatusText(selectedMatch.matchStatus) : '未关联' }}</el-descriptions-item>
          <el-descriptions-item label="关联岗位优化建议">{{ selectedSuggestion ? resolveSuggestionStatusText(selectedSuggestion.suggestionStatus) : '未关联' }}</el-descriptions-item>
        </el-descriptions>
      </el-form>

      <section v-if="latestDialogRewriteResult" class="ai-match-dialog-result">
        <div class="ai-match-dialog-result-header">
          <h3 class="ai-match-section-title">生成结果</h3>
          <el-tag :type="resolveRewriteStatusType(latestDialogRewriteResult.rewriteStatus)">
            {{ resolveRewriteStatusText(latestDialogRewriteResult.rewriteStatus) }}
          </el-tag>
        </div>
        <el-alert
          v-if="latestDialogRewriteResult.rewriteStatus === 'FAILED'"
          class="ai-match-dialog-alert"
          :title="latestDialogRewriteResult.errorMessage || 'AI 局部改写生成失败'"
          type="error"
          :closable="false"
          show-icon
        />
        <div v-else class="ai-match-rewrite-compare">
          <article class="ai-match-rewrite-card">
            <h3>原文</h3>
            <p>{{ latestDialogRewriteResult.originalText || '-' }}</p>
          </article>
          <article class="ai-match-rewrite-card">
            <h3>改写建议</h3>
            <p>{{ latestDialogRewriteResult.rewrittenText || '-' }}</p>
          </article>
        </div>
        <div v-if="latestDialogRewriteResult.rewriteReason || latestDialogRewriteResult.caution" class="ai-match-rewrite-detail">
          <p v-if="latestDialogRewriteResult.rewriteReason"><strong>改写理由：</strong>{{ latestDialogRewriteResult.rewriteReason }}</p>
          <p v-if="latestDialogRewriteResult.caution" class="ai-match-suggestion-caution"><strong>注意事项：</strong>{{ latestDialogRewriteResult.caution }}</p>
        </div>
      </section>

      <template #footer>
        <el-button @click="rewriteDialogVisible = false">取消</el-button>
        <el-button
          v-if="latestDialogRewriteResult?.rewriteStatus === 'SUCCESS'"
          @click="copyRewriteResult(latestDialogRewriteResult.rewrittenText)"
        >
          复制改写结果
        </el-button>
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
  </section>
</template>

<style scoped>
.ai-match-page {
  min-height: 0;
  padding: 0;
  background: transparent;
}

.ai-match-shell {
  display: grid;
  gap: 18px;
  width: 100%;
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
  color: var(--app-color-text);
  font-size: 28px;
  font-weight: 700;
}

.ai-match-subtitle {
  margin: 8px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 15px;
  line-height: 1.7;
}

.ai-match-panel {
  min-height: 520px;
  padding: 28px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.ai-match-selectors {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.ai-match-selector,
.ai-match-block {
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 16px;
  background: var(--app-color-surface);
}

.ai-match-section-title {
  margin: 0 0 12px;
  color: var(--app-color-text);
  font-size: 16px;
  font-weight: 700;
}

.ai-match-select {
  width: 100%;
}

.ai-match-option-status {
  float: right;
  color: var(--app-color-text-secondary);
  font-size: 13px;
}

.ai-match-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: var(--app-color-text-secondary);
  font-size: 14px;
}

.ai-match-alert,
.ai-match-actions,
.ai-match-result {
  margin-top: 24px;
}

.ai-match-generation-status {
  display: grid;
  gap: 6px;
  margin: 0 0 18px;
  padding: 14px 16px;
  border: 1px solid rgba(37, 111, 108, 0.2);
  border-radius: 14px;
  background: var(--app-color-primary-soft);
}

.ai-match-generation-status strong {
  color: var(--app-color-text);
  font-size: 15px;
}

.ai-match-generation-status p {
  margin: 0;
  color: var(--app-color-text-secondary);
  line-height: 1.7;
}

.ai-match-workflow {
  margin-top: 18px;
}

.ai-match-stage-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 20px 0 16px;
  padding: 8px;
  border: 1px solid var(--app-color-border);
  border-radius: 16px;
  background: var(--app-color-surface-soft);
}

.ai-match-stage-tabs button {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--app-color-text-secondary);
  background: transparent;
  cursor: pointer;
}

.ai-match-stage-tabs button.is-active {
  border-color: rgba(37, 111, 108, 0.24);
  color: var(--app-color-primary);
  font-weight: 700;
  background: var(--app-color-surface);
  box-shadow: 0 8px 20px rgba(35, 32, 29, 0.05);
}

.ai-match-suggestion-panel {
  margin-top: 24px;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface-soft);
}

.ai-match-rewrite-panel {
  margin-top: 24px;
  padding: 18px;
  border: 1px solid #ead7c5;
  border-radius: 18px;
  background: var(--app-color-accent-soft);
}

.ai-match-report-panel {
  margin-top: 24px;
  padding: 18px;
  border: 1px solid #cfe7dc;
  border-radius: 18px;
  background: var(--app-color-primary-soft);
}

.ai-match-suggestion-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.ai-match-suggestion-note {
  margin: 0;
  color: var(--app-color-text-secondary);
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

.ai-match-report-body {
  min-height: 120px;
  margin-top: 16px;
}

.ai-match-report-overview {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 20px;
  align-items: stretch;
}

.ai-match-report-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 132px;
  border: 1px solid #a7d7c5;
  border-radius: 16px;
  background: var(--app-color-primary-soft);
}

.ai-match-report-score span {
  color: var(--app-color-success);
  font-size: 42px;
  font-weight: 700;
  line-height: 1;
}

.ai-match-report-score small {
  margin-top: 10px;
  color: var(--app-color-text);
  font-size: 14px;
}

.ai-match-report-warning-list,
.ai-match-report-section {
  margin-top: 18px;
}

.ai-match-report-toggle-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ai-match-report-fold-summary {
  margin: 10px 0 0;
  color: var(--app-color-text-secondary);
  line-height: 1.7;
}

.ai-match-report-warning-list {
  display: grid;
  gap: 10px;
}

.ai-match-report-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.ai-match-report-card {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface);
}

.ai-match-report-card h4 {
  margin: 0 0 12px;
  color: var(--app-color-text);
  font-size: 15px;
  font-weight: 700;
}

.ai-match-report-evidence-card {
  margin-top: 16px;
}

.ai-match-report-suggestion,
.ai-match-report-rewrite {
  display: grid;
  gap: 10px;
}

.ai-match-report-suggestion p,
.ai-match-report-rewrite p,
.ai-match-report-step-list p {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-match-report-suggestion strong,
.ai-match-report-rewrite strong {
  color: var(--app-color-text);
}

.ai-match-report-step-list {
  display: grid;
  gap: 10px;
}

.ai-match-report-evidence-list {
  display: grid;
  gap: 8px;
}

.ai-match-report-evidence-list p {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.7;
  word-break: break-word;
}

.ai-match-report-step-list p {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.ai-match-report-model-list {
  display: grid;
  gap: 10px;
}

.ai-match-report-model-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface);
  color: var(--app-color-text);
  line-height: 1.6;
}

.ai-match-report-hero {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
  align-items: stretch;
}

.ai-match-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  padding: 24px;
  border: 1px solid #b7d6d1;
  border-radius: 18px;
  background: var(--app-color-primary-soft);
  text-align: center;
}

.ai-match-score.is-high {
  border-color: rgba(63, 143, 104, 0.35);
  background: #edf7f1;
}

.ai-match-score.is-medium {
  border-color: rgba(37, 111, 108, 0.35);
  background: var(--app-color-primary-soft);
}

.ai-match-score.is-low {
  border-color: rgba(201, 138, 46, 0.4);
  background: #fbf3e5;
}

.ai-match-score-value {
  color: var(--app-color-primary);
  font-size: 54px;
  font-weight: 700;
  line-height: 1;
}

.ai-match-score-label {
  margin-top: 8px;
  color: var(--app-color-text-secondary);
  font-size: 14px;
}

.ai-match-score strong {
  margin-top: 14px;
  color: var(--app-color-text);
  font-size: 18px;
}

.ai-match-score p {
  margin: 8px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.ai-match-report-summary-card {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface-soft);
}

.ai-match-report-summary-header,
.ai-match-insight-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-match-metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.ai-match-metric-card {
  display: grid;
  gap: 4px;
  min-height: 92px;
  align-content: center;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface);
}

.ai-match-metric-card span {
  color: var(--app-color-text-secondary);
  font-size: 13px;
}

.ai-match-metric-card strong {
  color: var(--app-color-text);
  font-size: 28px;
  line-height: 1;
}

.ai-match-metric-card small {
  color: var(--app-color-text-secondary);
  line-height: 1.5;
}

.ai-match-metric-card.is-success {
  border-color: rgba(63, 143, 104, 0.32);
}

.ai-match-metric-card.is-warning {
  border-color: rgba(201, 138, 46, 0.34);
}

.ai-match-metric-card.is-danger {
  border-color: rgba(184, 92, 92, 0.32);
}

.ai-match-next-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 20px;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-primary-soft);
}

.ai-match-next-action p {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.7;
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

.ai-match-insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 24px;
}

.ai-match-insight-card {
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface-soft);
}

.ai-match-insight-card.is-success {
  border-color: rgba(63, 143, 104, 0.32);
}

.ai-match-insight-card.is-warning {
  border-color: rgba(201, 138, 46, 0.34);
}

.ai-match-insight-card.is-danger {
  border-color: rgba(184, 92, 92, 0.32);
}

.ai-match-insight-list {
  display: grid;
  gap: 12px;
}

.ai-match-insight-list p {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  color: var(--app-color-text);
  line-height: 1.7;
  background: var(--app-color-surface);
}

.ai-match-insight-list strong {
  color: var(--app-color-text);
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
  color: var(--app-color-text);
  line-height: 1.7;
}

.ai-match-list strong {
  color: var(--app-color-text);
  font-weight: 700;
}

.ai-match-evidence-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
  gap: 20px;
  margin-top: 20px;
}

.ai-match-evidence-list,
.ai-match-risk-list {
  display: grid;
  gap: 10px;
}

.ai-match-evidence-list p,
.ai-match-risk-list p {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  color: var(--app-color-text);
  line-height: 1.7;
  background: var(--app-color-surface);
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
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface);
}

.ai-match-suggestion-item.is-rewritten {
  border-color: rgba(34, 197, 94, 0.34);
  background: linear-gradient(180deg, rgba(236, 253, 245, 0.9), var(--app-color-surface));
}

.ai-match-suggestion-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-match-suggestion-item p {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.7;
}

.ai-match-suggestion-item strong {
  color: var(--app-color-text);
}

.ai-match-suggestion-tags,
.ai-match-related-items,
.ai-match-inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-match-inline-actions {
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border: 1px solid rgba(37, 111, 108, 0.18);
  border-radius: 14px;
  background: var(--app-color-primary-soft);
}

.ai-match-inline-actions-hint {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.ai-match-inline-actions-hint strong {
  color: var(--app-color-text);
  font-size: 14px;
  font-weight: 700;
}

.ai-match-inline-actions-hint span {
  color: var(--app-color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.ai-match-suggestion-rewrite-note {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--app-color-text-secondary);
  font-size: 12px;
}

.ai-match-suggestion-rewrite-note span {
  color: var(--app-color-text-secondary);
}

.ai-match-suggestion-rewrite-note small {
  color: var(--app-color-text-muted);
}

.ai-match-suggestion-evidence {
  display: grid;
  gap: 6px;
  color: var(--app-color-text);
  line-height: 1.7;
}

.ai-match-suggestion-evidence span {
  padding-left: 10px;
  border-left: 3px solid var(--app-color-border);
}

.ai-match-suggestion-caution {
  color: var(--app-color-danger) !important;
}

.ai-match-rewrite-summary {
  margin-bottom: 16px;
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
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface);
}

.ai-match-rewrite-card h3 {
  margin: 0 0 10px;
  color: var(--app-color-text);
  font-size: 15px;
  font-weight: 700;
}

.ai-match-rewrite-card p,
.ai-match-rewrite-detail p {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-match-rewrite-detail {
  display: grid;
  gap: 10px;
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface);
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

.ai-match-rewrite-history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface);
  color: var(--app-color-text);
  cursor: pointer;
  text-align: left;
}

.ai-match-rewrite-history-item.is-active {
  border-color: var(--app-color-primary);
  background: var(--app-color-primary-soft);
}

.ai-match-rewrite-history-tip {
  margin: 10px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 12px;
}

.ai-match-dialog-alert,
.ai-match-rewrite-form {
  margin-bottom: 16px;
}

.ai-match-dialog-context {
  display: grid;
  gap: 14px;
  margin-bottom: 16px;
}

.ai-match-dialog-suggestion {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.ai-match-dialog-suggestion p {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.7;
}

.ai-match-context-body {
  min-height: 42px;
}

.ai-match-keyword-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.ai-match-keyword-row span {
  color: var(--app-color-text-secondary);
  font-size: 13px;
}

.ai-match-source-picker {
  display: grid;
  gap: 10px;
}

.ai-match-source-option {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface);
  color: var(--app-color-text);
  cursor: pointer;
  text-align: left;
}

.ai-match-source-option.is-active {
  border-color: var(--app-color-primary);
  background: var(--app-color-primary-soft);
}

.ai-match-source-option span {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.ai-match-source-option strong {
  color: var(--app-color-text);
  font-size: 14px;
}

.ai-match-source-option small {
  color: var(--app-color-text-secondary);
  line-height: 1.5;
}

.ai-match-dialog-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 14px;
}

.ai-match-length-input {
  width: 100%;
}

.ai-match-dialog-result {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--app-color-border);
}

.ai-match-dialog-result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 760px) {
  .ai-match-header,
  .ai-match-report-hero,
  .ai-match-report-overview {
    align-items: stretch;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .ai-match-selectors,
  .ai-match-grid,
  .ai-match-metric-grid,
  .ai-match-insight-grid,
  .ai-match-evidence-layout,
  .ai-match-suggestion-groups,
  .ai-match-report-columns,
  .ai-match-rewrite-compare,
  .ai-match-dialog-form-grid {
    grid-template-columns: 1fr;
  }

  .ai-match-next-action,
  .ai-match-suggestion-header,
  .ai-match-rewrite-history-item {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
