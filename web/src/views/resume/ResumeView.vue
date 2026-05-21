<script setup lang="ts">
import type { UploadFile, UploadProps, UploadUserFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, h, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import ProcessStepper from '@/components/common/ProcessStepper.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import StatusTag from '@/components/common/StatusTag.vue'
import {
  deleteResume,
  getResumeAiAnalysis,
  getResumeList,
  getResumeParseResult,
  submitResumeDiagnosisTask,
  submitResumeEmbeddingTask,
  submitResumeParseTask,
  uploadResume,
} from '@/api/resume'
import type { AsyncTaskPollingController } from '@/utils/asyncTaskPolling'
import { startAsyncTaskPolling } from '@/utils/asyncTaskPolling'
import { buildResumeParseDisplaySections } from '@/utils/resumeParseDisplayAdapter'
import type { AsyncTaskVO } from '@/types/task'
import type {
  ResumeAiAnalysis,
  ResumeBlock,
  ResumeListItem,
  ResumeParseMeta,
  ResumeParseMode,
  ResumeParseResult,
  ResumeRawSection,
  ResumeStructuredContent,
  ResumeTextSection,
} from '@/types/resume'

const route = useRoute()
const router = useRouter()
const MAX_FILE_SIZE = 10 * 1024 * 1024
const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx']
const RESUME_ASYNC_TASK_TIMEOUT_MS = 5 * 60 * 1000
const RESUME_UPLOAD_LIMIT = 10
const RESUME_ORDER_STORAGE_KEY = 'ai-resume-optimizer:resume-order'
const ASYNC_TASK_PROGRESS_CAP = 95
const ASYNC_TASK_PROGRESS_INTERVAL_MS = 500

interface ProcessStep {
  title: string
  description?: string
  status?: 'done' | 'current' | 'pending' | 'failed'
}

const resumes = ref<ResumeListItem[]>([])
const uploadFiles = ref<UploadUserFile[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadProgressText = ref<string | null>(null)
const draggingResumeId = ref<number | null>(null)
const dragOverResumeId = ref<number | null>(null)
const parsingResumeId = ref<number | null>(null)
const loadingParseResult = ref(false)
const analyzingResumeId = ref<number | null>(null)
const loadingAiAnalysis = ref(false)
const embeddingResumeId = ref<number | null>(null)
const deletingResumeId = ref<number | null>(null)
const activeResume = ref<ResumeListItem | null>(null)
const parseResult = ref<ResumeParseResult | null>(null)
const aiAnalysis = ref<ResumeAiAnalysis | null>(null)
const activePanel = ref<'parse' | 'analysis' | null>(null)
const activeDetailTab = ref<'overview' | 'parse' | 'raw' | 'analysis'>('overview')
const uploadPanelRef = ref<HTMLElement | null>(null)
const confirmedParseResultIds = ref<Set<number>>(new Set())
const debugCollapseActive = ref<string[]>([])
const selectedParseMode = ref<ResumeParseMode>('BALANCED')
const expandedExperienceCards = ref<Set<string>>(new Set())
const expandedProjectCards = ref<Set<string>>(new Set())
const expandedExperienceSourceCards = ref<Set<string>>(new Set())
const expandedProjectSourceCards = ref<Set<string>>(new Set())
const expandedSkillGroups = ref<Set<string>>(new Set())
const summaryExpanded = ref(false)
const othersExpanded = ref(false)
const projectsExpanded = ref(false)
const activeAsyncTask = ref<AsyncTaskVO | null>(null)
const activeAsyncTaskResumeId = ref<number | null>(null)
const activeAsyncTaskPolling = ref<AsyncTaskPollingController | null>(null)
const asyncTaskError = ref<string | null>(null)
const asyncTaskTimedOut = ref(false)
const displayedAsyncTaskProgress = ref(0)
const asyncTaskProgressTimer = ref<number | null>(null)

const parseModeOptions: Array<{ label: string; value: ResumeParseMode; description: string }> = [
  { label: 'FAST', value: 'FAST', description: '快速预览' },
  { label: 'BALANCED', value: 'BALANCED', description: '默认平衡' },
  { label: 'ACCURATE', value: 'ACCURATE', description: '高精度' },
]

const isActiveAsyncTaskRunning = computed(() => {
  const status = activeAsyncTask.value?.status
  return status === 'PENDING' || status === 'RUNNING'
})

const asyncTaskProgress = computed(() => {
  return Math.min(Math.max(Math.round(displayedAsyncTaskProgress.value), 0), 100)
})

const activeAsyncTaskTitle = computed(() => {
  if (!activeAsyncTask.value) {
    return '异步任务'
  }

  const typeMap: Record<string, string> = {
    RESUME_PARSE: '简历解析',
    RESUME_DIAGNOSIS: '简历诊断',
    TARGET_JOB_PARSE: '目标岗位解析',
    MATCH_ANALYSIS: '匹配分析',
    JOB_SUGGESTION: '岗位优化建议',
    LOCAL_REWRITE: '局部改写',
    RESUME_EMBEDDING: '简历向量生成',
    JOB_DESCRIPTION_EMBEDDING: '岗位向量生成',
    RAG_INDEX_BUILD: '索引构建',
  }

  return typeMap[activeAsyncTask.value.taskType] ?? activeAsyncTask.value.taskType
})

const isRowBusy = (resumeId: number) => {
  return parsingResumeId.value === resumeId
    || analyzingResumeId.value === resumeId
    || embeddingResumeId.value === resumeId
    || deletingResumeId.value === resumeId
    || (isActiveAsyncTaskRunning.value && activeAsyncTaskResumeId.value === resumeId)
}

const selectedUploadFiles = computed(() => {
  const files: File[] = []
  for (const item of uploadFiles.value) {
    if (item.raw) {
      files.push(item.raw as File)
    }
  }
  return files
})

const selectedUploadSummary = computed(() => {
  const files = selectedUploadFiles.value
  if (!files.length) {
    return '未选择文件'
  }

  const totalSize = files.reduce((sum, file) => sum + file.size, 0)
  return `已选择 ${files.length} 份，合计 ${formatFileSize(totalSize)}`
})

const resumeOverviewStats = computed(() => [
  {
    label: '简历资产',
    value: resumes.value.length,
    note: resumes.value.length > 0 ? '已保存到我的简历' : '等待上传',
  },
  {
    label: '当前文件',
    value: activeResume.value?.fileType || '-',
    note: activeResume.value ? formatFileSize(activeResume.value.fileSize) : '先选择一份简历',
  },
  {
    label: '解析状态',
    value: activeResume.value ? resolveParseStatusText(parseResult.value?.parseStatus) : '-',
    note: parseResult.value?.updatedAt ? formatDateTime(parseResult.value.updatedAt) : '查看或发起解析后更新',
  },
  {
    label: '诊断状态',
    value: activeResume.value ? resolveAnalysisStatusText(aiAnalysis.value?.analysisStatus) : '-',
    note: aiAnalysis.value?.updatedAt ? formatDateTime(aiAnalysis.value.updatedAt) : '生成诊断后更新',
  },
])

const parseTaskActive = computed(() => {
  return activeAsyncTask.value?.taskType === 'RESUME_PARSE'
})

const diagnosisTaskActive = computed(() => {
  return activeAsyncTask.value?.taskType === 'RESUME_DIAGNOSIS'
})

const activeAsyncTaskFailed = computed(() => {
  return activeAsyncTask.value?.status === 'FAILED' || Boolean(asyncTaskError.value)
})

const canRetryActiveTask = computed(() => {
  return Boolean(
    activeAsyncTaskFailed.value
    && activeAsyncTaskResumeId.value
    && (parseTaskActive.value || diagnosisTaskActive.value),
  )
})

const resumeFlowSteps = computed<ProcessStep[]>(() => {
  const hasSelectedFiles = selectedUploadFiles.value.length > 0
  const hasUploadedResume = resumes.value.length > 0
  const hasActiveResume = Boolean(activeResume.value)
  const parseDone = parseResult.value?.parseStatus === 'SUCCESS'
  const parseFailed = parseResult.value?.parseStatus === 'FAILED'
    || (parseTaskActive.value && activeAsyncTask.value?.status === 'FAILED')
  const diagnosisDone = aiAnalysis.value?.analysisStatus === 'SUCCESS'
  const diagnosisFailed = aiAnalysis.value?.analysisStatus === 'FAILED'
    || (diagnosisTaskActive.value && activeAsyncTask.value?.status === 'FAILED')

  return [
    {
      title: '选择文件',
      description: selectedUploadSummary.value,
      status: hasSelectedFiles || hasUploadedResume ? 'done' : 'current',
    },
    {
      title: '上传简历',
      description: uploading.value ? uploadProgressText.value || '正在上传简历' : hasUploadedResume ? `${resumes.value.length} 份简历已保存` : '上传后进入解析',
      status: uploading.value ? 'current' : hasUploadedResume ? 'done' : hasSelectedFiles ? 'current' : 'pending',
    },
    {
      title: '解析简历',
      description: parseTaskActive.value && isActiveAsyncTaskRunning.value
        ? activeAsyncTask.value?.message || '解析任务执行中'
        : parseDone
          ? '解析成功，可确认结果或生成诊断'
          : parseFailed
            ? parseResult.value?.errorMessage || asyncTaskError.value || '解析失败，可重试'
            : hasActiveResume
              ? '选择简历后开始解析'
              : '等待选择简历',
      status: parseFailed ? 'failed' : parseTaskActive.value && isActiveAsyncTaskRunning.value ? 'current' : parseDone ? 'done' : hasActiveResume ? 'current' : 'pending',
    },
    {
      title: '简历诊断',
      description: diagnosisTaskActive.value && isActiveAsyncTaskRunning.value
        ? activeAsyncTask.value?.message || '诊断任务执行中'
        : diagnosisDone
          ? '诊断完成，可进入目标岗位'
          : diagnosisFailed
            ? aiAnalysis.value?.errorMessage || asyncTaskError.value || '诊断失败，可重试'
            : parseDone
              ? '解析成功后生成诊断'
              : '等待解析成功',
      status: diagnosisFailed ? 'failed' : diagnosisTaskActive.value && isActiveAsyncTaskRunning.value ? 'current' : diagnosisDone ? 'done' : parseDone ? 'current' : 'pending',
    },
    {
      title: '进入匹配',
      description: parseDone ? '选择目标岗位后生成匹配报告' : '匹配前需要解析成功的简历',
      status: parseDone ? 'current' : 'pending',
    },
  ]
})

const activeTaskStages = computed(() => {
  const task = activeAsyncTask.value
  const progress = asyncTaskProgress.value
  const failed = task?.status === 'FAILED' || Boolean(asyncTaskError.value)
  const success = task?.status === 'SUCCESS'

  return [
    {
      title: '提交任务',
      status: task ? 'done' : 'current',
    },
    {
      title: activeAsyncTaskTitle.value === '简历解析' ? '解析文本与结构' : '生成 AI 诊断',
      status: failed ? 'failed' : success || progress >= 70 ? 'done' : task ? 'current' : 'pending',
    },
    {
      title: '写入结果',
      status: failed ? 'pending' : success || progress >= 95 ? 'done' : task ? 'current' : 'pending',
    },
    {
      title: '刷新页面结果',
      status: success ? 'done' : failed ? 'pending' : 'pending',
    },
  ]
})

const structuredContent = computed<ResumeStructuredContent | null>(() => {
  if (!parseResult.value?.structuredJson) {
    return null
  }

  try {
    return JSON.parse(parseResult.value.structuredJson) as ResumeStructuredContent
  } catch (error) {
    return null
  }
})

const sectionResult = computed<ResumeTextSection[]>(() => {
  if (parseResult.value?.sectionResult) {
    try {
      const parsed = JSON.parse(parseResult.value.sectionResult) as unknown
      if (Array.isArray(parsed)) {
        return parsed.filter(isResumeTextSection)
      }
    } catch (error) {
      return []
    }
  }

  return structuredContent.value?.sections?.filter(isResumeTextSection) ?? []
})

const textQualityIssues = computed(() => parseJsonStringList(parseResult.value?.textQualityIssues))
const parseQualityWarnings = computed(() => parseJsonStringList(parseResult.value?.parseQualityWarnings))
const structuredQualityWarnings = computed(() => structuredContent.value?.qualityWarnings ?? [])
const allQualityWarnings = computed(() => {
  return [
    ...textQualityIssues.value,
    ...parseQualityWarnings.value,
    ...structuredQualityWarnings.value,
  ].filter((item, index, items) => item && items.indexOf(item) === index)
})
const basicInfo = computed<Record<string, string>>(() => structuredContent.value?.basicInfo ?? {})
const displaySections = computed(() => buildResumeParseDisplaySections(structuredContent.value))
const parseMeta = computed<ResumeParseMeta | null>(() => {
  const content = structuredContent.value
  if (!content) {
    return null
  }
  if (content.parseMeta) {
    return content.parseMeta
  }
  return buildLegacyParseMeta(content)
})
const parseModeText = computed(() => resolveParseModeText(parseMeta.value?.parseMode ?? structuredContent.value?.parseMode))
const cleanedResumeFullText = computed(() => {
  return parseResult.value?.cleanedText?.trim() || parseResult.value?.extractedText?.trim() || ''
})
const basicInfoDebugRows = computed(() => {
  return Object.entries(structuredContent.value?.basicInfoDebug ?? {}).map(([field, detail]) => ({
    field,
    value: detail?.value || '',
    confidence: detail?.confidence == null ? '-' : detail.confidence.toFixed(2),
    source: detail?.source || '-',
    status: detail?.status || '-',
    evidence: detail?.evidence || '-',
    rejectReason: detail?.rejectReason || '-',
  }))
})
const displayName = computed(() => resolveDisplayName(structuredContent.value?.name?.trim() || basicInfo.value.name))
const nameLowConfidenceVisible = computed(() => displayName.value === '未识别')
const aiConflictWarnings = computed(() => allQualityWarnings.value.filter((warning) => warning.startsWith('AI_SECTION_CONFLICT')))
const debugBlocks = computed(() => {
  const blocks = sectionResult.value.flatMap((section, sectionIndex) => {
    if (section.blocks?.length) {
      return section.blocks.map((block, blockIndex) => ({
        key: `${sectionIndex}-${block.originalIndex ?? block.index ?? blockIndex}`,
        index: block.index ?? null,
        originalIndex: block.originalIndex ?? block.index ?? null,
        displayOrder: block.displayOrder ?? null,
        sourceSection: block.sourceSection || section.sectionType,
        finalSection: section.sectionType,
        sourceSectionConfidence: block.sourceSectionConfidence ?? null,
        finalSectionSource: block.finalSectionSource ?? null,
        sectionLocked: block.sectionLocked ?? null,
        heading: section.heading,
        text: block.text || '',
      }))
    }

    return section.lines.map((line, lineIndex) => ({
      key: `${sectionIndex}-${lineIndex}`,
      index: lineIndex,
      originalIndex: lineIndex,
      displayOrder: lineIndex,
      sourceSection: section.sectionType,
      finalSection: section.sectionType,
      sourceSectionConfidence: null,
      finalSectionSource: null,
      sectionLocked: null,
      heading: section.heading,
      text: line,
    }))
  })

  return blocks.sort((left, right) => (left.originalIndex ?? 0) - (right.originalIndex ?? 0))
})
const aiClassifiedBlocks = computed(() => {
  return sectionResult.value
    .filter((section) => section.heading?.startsWith('AI 章节归类'))
    .flatMap((section, sectionIndex) => {
      if (section.blocks?.length) {
        return section.blocks.map((block, blockIndex) => ({
          key: `${sectionIndex}-${block.displayOrder ?? block.index ?? blockIndex}`,
          originalIndex: block.originalIndex ?? block.index ?? null,
          displayOrder: block.displayOrder ?? null,
          sourceSection: block.sourceSection || section.sectionType,
          finalSection: section.sectionType,
          sourceSectionConfidence: block.sourceSectionConfidence ?? null,
          finalSectionSource: block.finalSectionSource ?? null,
          sectionLocked: block.sectionLocked ?? null,
          text: block.text || '',
        }))
      }

      return section.lines.map((line, lineIndex) => ({
        key: `${sectionIndex}-${lineIndex}`,
        originalIndex: lineIndex,
        displayOrder: lineIndex,
        sourceSection: section.sectionType,
        finalSection: section.sectionType,
        sourceSectionConfidence: null,
        finalSectionSource: null,
        sectionLocked: null,
        text: line,
      }))
    })
    .sort((left, right) => (left.displayOrder ?? 0) - (right.displayOrder ?? 0))
})
const rawSectionDebugSections = computed(() => {
  const rawSections = structuredContent.value?.rawSections ?? []
  if (!rawSections.length) {
    return []
  }
  return rawSections.map((section, sectionIndex) => ({
    key: section.id || `raw-${sectionIndex}`,
    title: section.originalTitle || section.displayName || section.normalizedSection || '未识别章节',
    normalizedSection: section.normalizedSection || '-',
    confidence: section.confidence == null ? '-' : section.confidence.toFixed(2),
    source: section.source || '-',
    lines: (section.blocks ?? [])
      .map((block) => block.text || '')
      .filter((line) => line.length > 0),
  }))
})
const indexedLineDebugRows = computed(() => {
  return (structuredContent.value?.indexedLines ?? []).map((line) => ({
    lineId: line.lineId ?? '-',
    sectionHint: line.sectionHint || '-',
    rawSectionId: line.rawSectionId || '-',
    sourceType: line.sourceType || '-',
    noise: line.isNoise ? '是' : '否',
    text: line.text || '',
  }))
})
const parseResultConfirmed = computed(() => {
  return parseResult.value?.resumeId ? confirmedParseResultIds.value.has(parseResult.value.resumeId) : false
})

const aiAnalysisScoreSummary = computed(() => {
  const score = aiAnalysis.value?.score
  if (score == null) {
    return {
      label: '暂无评分',
      description: '等待简历诊断结果',
      level: 'empty',
    }
  }

  if (score >= 85) {
    return {
      label: '质量较好',
      description: '可以优先进入目标岗位匹配',
      level: 'strong',
    }
  }

  if (score >= 70) {
    return {
      label: '基础可用',
      description: '建议先处理主要问题再匹配',
      level: 'normal',
    }
  }

  return {
    label: '需要优化',
    description: '建议先补齐结构和经历表达',
    level: 'weak',
  }
})

const aiAnalysisResultCards = computed(() => {
  return [
    {
      key: 'strengths',
      title: '简历优势',
      tone: 'success',
      items: aiAnalysis.value?.strengths ?? [],
    },
    {
      key: 'problems',
      title: '主要问题',
      tone: 'danger',
      items: aiAnalysis.value?.problems ?? [],
    },
    {
      key: 'suggestions',
      title: '建议摘要',
      tone: 'warning',
      items: aiAnalysis.value?.suggestionsSummary ?? [],
    },
  ].filter((card) => card.items.length > 0)
})

const otherHiddenCount = computed(() => {
  return Math.max(displaySections.value.pendingItems.length - displaySections.value.pendingPreviewItems.length, 0)
})

const parseJsonStringList = (value: string | null | undefined) => {
  if (!value) {
    return []
  }

  try {
    const parsed = JSON.parse(value) as unknown
    if (Array.isArray(parsed)) {
      return parsed.filter((item): item is string => typeof item === 'string' && item.length > 0)
    }
  } catch (error) {
    return []
  }

  return []
}

const isResumeTextSection = (value: unknown): value is ResumeTextSection => {
  if (!value || typeof value !== 'object') {
    return false
  }

  const section = value as Partial<ResumeTextSection>
  const blocksValid = !section.blocks || section.blocks.every(isResumeBlock)
  return typeof section.sectionType === 'string'
    && typeof section.heading === 'string'
    && Array.isArray(section.lines)
    && blocksValid
}

const isResumeBlock = (value: unknown): value is ResumeBlock => {
  if (!value || typeof value !== 'object') {
    return false
  }

  const block = value as Partial<ResumeBlock>
  return typeof block.text === 'string'
}

const buildLegacyParseMeta = (content: ResumeStructuredContent): ResumeParseMeta => {
  const sectionEnabled = Boolean(content.aiSectionClassifyEnabled)
  const structuredEnabled = Boolean(content.aiStructuredParseEnabled)
  const sectionApplied = Boolean(content.aiSectionClassifyApplied)
  const structuredApplied = Boolean(content.aiStructuredParseApplied)
  const fallbackReason = [
    sectionEnabled && !sectionApplied ? content.aiSectionClassifyFallbackReason : null,
    structuredEnabled && !structuredApplied ? content.aiStructuredParseFallbackReason : null,
  ].filter((item): item is string => Boolean(item && looksLikeAiFailureReason(item))).join('；')
  const aiStatus = fallbackReason
    ? 'FALLBACK'
    : sectionApplied || structuredApplied
      ? 'USED'
      : sectionEnabled || structuredEnabled
        ? 'SKIPPED'
        : 'DISABLED'
  return {
    parseMode: content.parseMode,
    parserVersion: content.parserVersion,
    aiStatus,
    aiUsed: aiStatus === 'USED' || aiStatus === 'FALLBACK',
    aiSkippedReason: aiStatus === 'SKIPPED' ? 'LEGACY_AI_NOT_APPLIED' : null,
    aiFallbackOccurred: aiStatus === 'FALLBACK',
    aiFallbackReason: fallbackReason || null,
    aiCacheHit: Boolean(content.aiSectionClassifyCacheHit || content.aiStructuredParseCacheHit),
    aiCacheKeyDigest: '',
    totalParseDurationMs: content.totalParseDurationMs,
    ruleParseDurationMs: content.ruleParseDurationMs,
    aiSectionClassifyDurationMs: content.aiSectionClassifyDurationMs,
    aiStructuredParseDurationMs: content.aiStructuredParseDurationMs,
  }
}

const looksLikeAiFailureReason = (reason: string) => {
  return /失败|JSON|超时|timeout|未返回|结果为空|校验/.test(reason)
}

const formatFileSize = (size: number) => {
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(2)} MB`
  }

  return `${(size / 1024).toFixed(1)} KB`
}

const formatDateTime = (value: string) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 19)
}

const copyFullResumeText = async () => {
  if (!cleanedResumeFullText.value) {
    ElMessage.warning('当前没有可复制的简历原文')
    return
  }

  try {
    await navigator.clipboard.writeText(cleanedResumeFullText.value)
    ElMessage.success('完整原文已复制')
  } catch (error) {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

const getFileExtension = (filename: string) => {
  const index = filename.lastIndexOf('.')

  if (index < 0 || index === filename.length - 1) {
    return ''
  }

  return filename.slice(index + 1).toLowerCase()
}

const validateFile = (file: File) => {
  const extension = getFileExtension(file.name)

  if (!ACCEPTED_EXTENSIONS.includes(extension)) {
    ElMessage.error('仅支持 PDF、DOC、DOCX 简历文件')
    return false
  }

  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('简历文件大小不能超过 10 MB')
    return false
  }

  return true
}

const readStoredResumeOrder = () => {
  try {
    const value = window.localStorage.getItem(RESUME_ORDER_STORAGE_KEY)
    if (!value) {
      return []
    }

    const parsed = JSON.parse(value) as unknown
    if (!Array.isArray(parsed)) {
      return []
    }

    return parsed.filter((item): item is number => Number.isInteger(item))
  } catch (error) {
    return []
  }
}

const saveResumeOrder = () => {
  window.localStorage.setItem(
    RESUME_ORDER_STORAGE_KEY,
    JSON.stringify(resumes.value.map((resume) => resume.id)),
  )
}

const applyStoredResumeOrder = (records: ResumeListItem[]) => {
  const storedOrder = readStoredResumeOrder()
  if (!storedOrder.length) {
    return records
  }

  const orderMap = new Map(storedOrder.map((id, index) => [id, index]))
  return [...records].sort((left, right) => {
    const leftOrder = orderMap.get(left.id)
    const rightOrder = orderMap.get(right.id)

    if (leftOrder != null && rightOrder != null) {
      return leftOrder - rightOrder
    }

    if (leftOrder != null) {
      return -1
    }

    if (rightOrder != null) {
      return 1
    }

    return 0
  })
}

const loadResumes = async () => {
  loading.value = true

  try {
    resumes.value = applyStoredResumeOrder(await getResumeList())
    selectResumeFromRoute()
    await nextTick()
    bindResumeTableDragHandlers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取简历列表失败')
  } finally {
    loading.value = false
  }
}

const handleFileChange: UploadProps['onChange'] = (uploadFile: UploadFile, fileList) => {
  if (!uploadFile.raw) {
    return
  }

  if (!validateFile(uploadFile.raw)) {
    uploadFiles.value = fileList.filter((item) => item.uid !== uploadFile.uid)
    return
  }
}

const handleFileRemove: UploadProps['onRemove'] = () => {
  uploadProgressText.value = null
}

const handleFileExceed: UploadProps['onExceed'] = () => {
  ElMessage.warning(`一次最多选择 ${RESUME_UPLOAD_LIMIT} 份简历`)
}

const handleUpload = async () => {
  const uploadItems = uploadFiles.value.filter((item) => item.raw)
  if (!selectedUploadFiles.value.length) {
    ElMessage.warning('请先选择简历文件')
    return
  }

  uploading.value = true
  let successCount = 0
  let failedCount = 0
  const failedUids = new Set<number>()

  try {
    for (let index = 0; index < uploadItems.length; index += 1) {
      const uploadItem = uploadItems[index]
      if (!uploadItem) {
        continue
      }
      const file = uploadItem?.raw as File | undefined
      if (!file) {
        continue
      }
      uploadProgressText.value = `正在上传 ${index + 1}/${uploadItems.length}：${file.name}`
      try {
        await uploadResume(file)
        successCount += 1
      } catch (error) {
        failedCount += 1
        if (uploadItem.uid != null) {
          failedUids.add(uploadItem.uid)
        }
        ElMessage.error(`${file.name} 上传失败：${error instanceof Error ? error.message : '上传失败'}`)
      }
    }

    if (successCount > 0) {
      ElMessage.success(`上传完成：成功 ${successCount} 份${failedCount ? `，失败 ${failedCount} 份` : ''}`)
    }

    if (failedCount === 0) {
      uploadFiles.value = []
    } else {
      uploadFiles.value = uploadFiles.value.filter((item) => item.uid != null && failedUids.has(item.uid))
    }
    await loadResumes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
    uploadProgressText.value = null
  }
}

const moveResumeBefore = (draggedId: number, targetId: number) => {
  if (draggedId === targetId) {
    return
  }

  const draggedIndex = resumes.value.findIndex((item) => item.id === draggedId)
  const targetIndex = resumes.value.findIndex((item) => item.id === targetId)
  if (draggedIndex < 0 || targetIndex < 0) {
    return
  }

  const nextRecords = [...resumes.value]
  const [dragged] = nextRecords.splice(draggedIndex, 1)
  if (!dragged) {
    return
  }

  const nextTargetIndex = nextRecords.findIndex((item) => item.id === targetId)
  nextRecords.splice(nextTargetIndex, 0, dragged)
  resumes.value = nextRecords
  saveResumeOrder()
}

const getResumeTableRows = () => {
  return Array.from(document.querySelectorAll<HTMLTableRowElement>('.resume-table .el-table__body-wrapper tbody tr'))
}

const bindResumeTableDragHandlers = () => {
  getResumeTableRows().forEach((row, index) => {
    const resume = resumes.value[index]
    if (!resume) {
      return
    }

    row.draggable = true
    row.dataset.resumeId = String(resume.id)
    row.ondragstart = (event) => {
      draggingResumeId.value = resume.id
      event.dataTransfer?.setData('text/plain', String(resume.id))
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move'
      }
    }
    row.ondragover = (event) => {
      event.preventDefault()
      dragOverResumeId.value = resume.id
    }
    row.ondrop = (event) => {
      event.preventDefault()
      const draggedId = Number(event.dataTransfer?.getData('text/plain') || draggingResumeId.value)
      if (Number.isInteger(draggedId)) {
        moveResumeBefore(draggedId, resume.id)
      }
      draggingResumeId.value = null
      dragOverResumeId.value = null
    }
    row.ondragend = () => {
      draggingResumeId.value = null
      dragOverResumeId.value = null
    }
  })
}

const resolveResumeRowClass = ({ row }: { row: ResumeListItem }) => {
  const classes: string[] = []
  if (draggingResumeId.value === row.id) {
    classes.push('resume-row-dragging')
  }
  if (dragOverResumeId.value === row.id && draggingResumeId.value !== row.id) {
    classes.push('resume-row-drop-target')
  }
  return classes.join(' ')
}

const resolveParseStatusText = (status: string | null | undefined) => {
  const statusMap: Record<string, string> = {
    PENDING: '待解析',
    PROCESSING: '解析中',
    SUCCESS: '解析成功',
    FAILED: '解析失败',
  }

  return status ? (statusMap[status] ?? status) : '-'
}

const resolveParseStatusType = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return 'success'
  }

  if (status === 'FAILED') {
    return 'danger'
  }

  return 'info'
}

const resolveQualityStatusText = (status: string | null | undefined) => {
  const statusMap: Record<string, string> = {
    GOOD: '质量正常',
    WARNING: '存在提示',
    FAILED: '质量失败',
  }

  return status ? (statusMap[status] ?? status) : '-'
}

const resolveQualityStatusType = (status: string | null | undefined) => {
  if (status === 'GOOD') {
    return 'success'
  }

  if (status === 'FAILED') {
    return 'danger'
  }

  if (status === 'WARNING') {
    return 'warning'
  }

  return 'info'
}

const resolveWarningText = (warning: string) => {
  const warningMap: Record<string, string> = {
    EMPTY_TEXT: '未提取到有效文本',
    TOO_SHORT_TEXT: '提取文本过短',
    ABNORMAL_CHAR_RATIO: '异常字符比例过高',
    LOW_LINE_BREAKS: '换行较少',
    SCANNED_PDF: '疑似扫描版 PDF',
    STRUCTURED_RESULT_EMPTY: '结构化结果为空',
    CORE_FIELDS_MISSING: '核心字段全部缺失',
    PROJECTS_MISSING: '未识别项目经历',
    SKILLS_MISSING: '未识别技能列表',
    BASIC_INFO_INCOMPLETE: '个人联系信息不完整',
    NAME_MISSING: '未识别姓名',
    CONTACT_MISSING: '手机号或邮箱不完整',
    EDUCATION_MISSING: '未识别教育经历',
    RESUME_TYPE_UNKNOWN: '简历类型未明确识别',
    WORK_EXPERIENCE_MISSING: '未识别工作经历',
    CAMPUS_OR_INTERNSHIP_MISSING: '未识别实习或校园经历',
    EXPERIENCE_OR_PROJECT_MISSING: '未识别经历或项目',
    OTHERS_PRESENT: '存在未归类内容',
    OTHERS_TOO_MANY: '未归类内容较多',
    DUPLICATE_CONTENT_TOO_MANY: '重复内容较多',
    SECTION_TOO_FEW: '章节识别较少',
    TEXT_STRUCTURE_MISMATCH: '文本与结构化字段不匹配',
    TEXT_QUALITY_WARNING: '文本质量存在提示',
    AI_NAME_INVALID_FALLBACK_TO_RULE: 'AI 姓名识别不可信，已回退规则结果',
    AI_PHONE_INVALID_FALLBACK_TO_RULE: 'AI 手机号格式不可信，已回退规则结果',
    AI_EMAIL_INVALID_FALLBACK_TO_RULE: 'AI 邮箱格式不可信，已回退规则结果',
    AI_EDUCATION_TECH_TEXT_FILTERED: '教育经历中的技术描述已过滤',
    AI_SKILLS_NON_TECH_TEXT_FILTERED: '技能列表中的非技术文本已过滤',
    AI_DUPLICATE_TEXT_REMOVED: '重复结构化内容已移除',
    AI_OTHERS_ASSIGNED_TEXT_REMOVED: '其他内容中已归类文本已移除',
    AI_SUMMARY_SKILL_LIST_FILTERED: '自我评价中的技能堆叠已过滤',
    INVALID_CONTENT_FILTERED: '无效序号或空内容已过滤',
    AI_INVALID_CONTENT_FILTERED: 'AI 输出中的无效序号或符号内容已过滤',
    AI_OTHERS_TOO_MANY_FILTERED: '其他内容过多，已限制展示数量',
    AI_SECTION_CONFLICT: 'AI 章节归类与规则章节冲突，已按置信度策略处理',
  }

  return warningMap[warning] ?? warning
}

const resolveParseModeText = (parseMode: string | null | undefined) => {
  const modeMap: Record<string, string> = {
    FAST: 'FAST 快速预览',
    BALANCED: 'BALANCED 默认平衡',
    ACCURATE: 'ACCURATE 高精度',
  }

  return parseMode ? (modeMap[parseMode] ?? parseMode) : '-'
}

const resolveDebugStatusType = (status: string | null | undefined) => {
  if (status === 'CONFIRMED') {
    return 'success'
  }

  if (status === 'REJECTED') {
    return 'danger'
  }

  if (status === 'LOW_CONFIDENCE') {
    return 'warning'
  }

  return 'info'
}

const resolveDisplayName = (value: string | null | undefined) => {
  const normalized = value?.trim() ?? ''
  return isReliableName(normalized) ? normalized : '未识别'
}

const isReliableName = (value: string) => {
  if (!value || value.length < 2 || value.length > 8) {
    return false
  }

  if (/[0-9@.:：/\\|,，;；]/.test(value)) {
    return false
  }

  const invalidNamePattern = /(姓名|个人简历|简历|求职|岗位|电话|邮箱|手机|学校|学院|大学|专业|项目|经历|技能|教育|证书|奖项|自我评价|参加项目描述|本人|先生|女士)/
  return !invalidNamePattern.test(value)
}

const resolveAnalysisStatusText = (status: string | null | undefined) => {
  const statusMap: Record<string, string> = {
    PENDING: '待分析',
    SUCCESS: '分析成功',
    FAILED: '分析失败',
  }

  return status ? (statusMap[status] ?? status) : '未分析'
}

const resolveAnalysisStatusType = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return 'success'
  }

  if (status === 'FAILED') {
    return 'danger'
  }

  return 'info'
}

const selectResume = (resume: ResumeListItem) => {
  activeResume.value = resume
  parseResult.value = null
  aiAnalysis.value = null
  activePanel.value = null
  activeDetailTab.value = 'overview'
}

const openResumeOverview = (resume: ResumeListItem) => {
  selectResume(resume)
}

const selectResumeFromRoute = () => {
  const resumeId = Number(route.query.resumeId)
  if (!Number.isFinite(resumeId)) {
    return
  }

  const resume = resumes.value.find((item) => item.id === resumeId)
  if (resume) {
    selectResume(resume)
  }
}

const parseRouteNumericId = (value: unknown) => {
  const rawValue = Array.isArray(value) ? value[0] : value

  if (typeof rawValue !== 'string' || rawValue.trim() === '') {
    return null
  }

  const parsed = Number(rawValue)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

const resolveAsyncTaskStatusText = (status: string | null | undefined) => {
  const statusMap: Record<string, string> = {
    PENDING: '待执行',
    RUNNING: '执行中',
    SUCCESS: '已完成',
    FAILED: '执行失败',
    CANCELLED: '已取消',
  }

  return status ? (statusMap[status] ?? status) : '-'
}

const resolveAsyncTaskStatusType = (status: string | null | undefined) => {
  if (status === 'SUCCESS') {
    return 'success'
  }

  if (status === 'FAILED' || status === 'CANCELLED') {
    return 'danger'
  }

  if (status === 'RUNNING') {
    return 'warning'
  }

  return 'info'
}

const stopAsyncTaskProgressTimer = () => {
  if (asyncTaskProgressTimer.value !== null) {
    window.clearInterval(asyncTaskProgressTimer.value)
    asyncTaskProgressTimer.value = null
  }
}

const resolveProgressStep = (current: number) => {
  if (current < 35) {
    return 3
  }
  if (current < 70) {
    return 2
  }
  return 1
}

const tickDisplayedAsyncTaskProgress = () => {
  const task = activeAsyncTask.value
  if (!task || !isActiveAsyncTaskRunning.value) {
    stopAsyncTaskProgressTimer()
    return
  }

  displayedAsyncTaskProgress.value = Math.min(
    displayedAsyncTaskProgress.value + resolveProgressStep(displayedAsyncTaskProgress.value),
    ASYNC_TASK_PROGRESS_CAP,
  )
}

const startAsyncTaskProgressTimer = () => {
  stopAsyncTaskProgressTimer()
  asyncTaskProgressTimer.value = window.setInterval(tickDisplayedAsyncTaskProgress, ASYNC_TASK_PROGRESS_INTERVAL_MS)
}

const syncDisplayedAsyncTaskProgress = (task: AsyncTaskVO | null) => {
  if (!task) {
    displayedAsyncTaskProgress.value = 0
    return
  }

  if (task.status === 'SUCCESS') {
    stopAsyncTaskProgressTimer()
    displayedAsyncTaskProgress.value = 100
    return
  }

  if (task.status === 'FAILED' || task.status === 'CANCELLED') {
    stopAsyncTaskProgressTimer()
    return
  }

  startAsyncTaskProgressTimer()
}

const stopActiveAsyncTaskPolling = () => {
  activeAsyncTaskPolling.value?.stop()
  activeAsyncTaskPolling.value = null
  stopAsyncTaskProgressTimer()
}

const clearAsyncTaskState = () => {
  stopActiveAsyncTaskPolling()
  activeAsyncTask.value = null
  activeAsyncTaskResumeId.value = null
  asyncTaskError.value = null
  asyncTaskTimedOut.value = false
  displayedAsyncTaskProgress.value = 0
}

const startResumeTaskPolling = (taskId: number, resumeId: number | null, initialTask?: AsyncTaskVO) => {
  stopActiveAsyncTaskPolling()
  activeAsyncTask.value = initialTask ?? null
  activeAsyncTaskResumeId.value = resumeId
  asyncTaskError.value = null
  asyncTaskTimedOut.value = false
  displayedAsyncTaskProgress.value = 0
  syncDisplayedAsyncTaskProgress(activeAsyncTask.value)

  activeAsyncTaskPolling.value = startAsyncTaskPolling({
    taskId,
    timeoutMs: RESUME_ASYNC_TASK_TIMEOUT_MS,
    onUpdate: (task) => {
      activeAsyncTask.value = task
      syncDisplayedAsyncTaskProgress(task)
    },
    onSuccess: async (task) => {
      activeAsyncTask.value = task
      syncDisplayedAsyncTaskProgress(task)
      ElMessage.success(task.resultSummary || '任务已完成')

      if (resumeId) {
        const resume = resumes.value.find((item) => item.id === resumeId)
        if (resume) {
          if (task.taskType === 'RESUME_DIAGNOSIS') {
            await loadAiAnalysis(resume)
          } else if (task.taskType === 'RESUME_PARSE') {
            await loadParseResult(resume)
          }
        }
      }
    },
    onFailed: (task) => {
      activeAsyncTask.value = task
      syncDisplayedAsyncTaskProgress(task)
      asyncTaskError.value = task.errorMessage || task.message || '任务执行失败'
      ElMessage.error(asyncTaskError.value)
    },
    onCancelled: (task) => {
      activeAsyncTask.value = task
      syncDisplayedAsyncTaskProgress(task)
      asyncTaskError.value = task.message || '任务已取消'
      ElMessage.warning(asyncTaskError.value)
    },
    onTimeout: (task) => {
      if (task) {
        activeAsyncTask.value = task
        syncDisplayedAsyncTaskProgress(task)
      }
      stopAsyncTaskProgressTimer()
      asyncTaskTimedOut.value = true
      asyncTaskError.value = '任务仍在后台执行，请稍后刷新查看结果'
      ElMessage.warning(asyncTaskError.value)
    },
    onError: (error) => {
      stopAsyncTaskProgressTimer()
      asyncTaskError.value = error instanceof Error ? error.message : '任务状态查询失败'
      ElMessage.error(asyncTaskError.value)
    },
  })
}

const startRouteAsyncTaskPolling = () => {
  const taskId = parseRouteNumericId(route.query.taskId)
  if (!taskId) {
    return
  }

  startResumeTaskPolling(taskId, parseRouteNumericId(route.query.resumeId))
}

const loadParseResult = async (resume: ResumeListItem) => {
  selectResume(resume)
  activePanel.value = 'parse'
  activeDetailTab.value = 'parse'
  loadingParseResult.value = true

  try {
    parseResult.value = await getResumeParseResult(resume.id)
    await loadExistingAiAnalysis(resume.id)
  } catch (error) {
    parseResult.value = null
    aiAnalysis.value = null
    ElMessage.warning(error instanceof Error ? error.message : '获取解析结果失败')
  } finally {
    loadingParseResult.value = false
  }
}

const handleParse = async (resume: ResumeListItem) => {
  selectResume(resume)
  activePanel.value = 'parse'
  activeDetailTab.value = 'parse'
  parsingResumeId.value = resume.id

  try {
    const task = await submitResumeParseTask(resume.id, { parseMode: selectedParseMode.value })
    aiAnalysis.value = null
    startResumeTaskPolling(task.taskId, resume.id, task)
    ElMessage.success('简历解析任务已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交解析任务失败')
  } finally {
    parsingResumeId.value = null
  }
}

const loadAiAnalysis = async (resume: ResumeListItem) => {
  if (activeResume.value?.id !== resume.id) {
    selectResume(resume)
  }

  activePanel.value = 'analysis'
  activeDetailTab.value = 'analysis'
  loadingAiAnalysis.value = true

  try {
    aiAnalysis.value = await getResumeAiAnalysis(resume.id)
  } catch (error) {
    aiAnalysis.value = null
    ElMessage.warning(error instanceof Error ? error.message : '获取简历诊断结果失败')
  } finally {
    loadingAiAnalysis.value = false
  }
}

const loadExistingAiAnalysis = async (resumeId: number) => {
  loadingAiAnalysis.value = true

  try {
    aiAnalysis.value = await getResumeAiAnalysis(resumeId)
  } catch (error) {
    aiAnalysis.value = null
  } finally {
    loadingAiAnalysis.value = false
  }
}

const handleAiAnalysis = async (resume: ResumeListItem) => {
  if (activeResume.value?.id !== resume.id) {
    selectResume(resume)
  }

  activePanel.value = 'analysis'
  activeDetailTab.value = 'analysis'

  if (parseResult.value && parseResult.value.parseStatus !== 'SUCCESS') {
    ElMessage.warning('请先完成简历解析')
    return
  }

  analyzingResumeId.value = resume.id

  try {
    const task = await submitResumeDiagnosisTask(resume.id)
    startResumeTaskPolling(task.taskId, resume.id, task)
    ElMessage.success('简历诊断任务已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交简历诊断任务失败')
  } finally {
    analyzingResumeId.value = null
  }
}

const retryActiveAsyncTask = async () => {
  if (!activeAsyncTaskResumeId.value) {
    return
  }

  const resume = resumes.value.find((item) => item.id === activeAsyncTaskResumeId.value)
  if (!resume) {
    ElMessage.warning('未找到可重试的简历记录')
    return
  }

  const taskType = activeAsyncTask.value?.taskType
  clearAsyncTaskState()

  if (taskType === 'RESUME_PARSE') {
    await handleParse(resume)
    return
  }

  if (taskType === 'RESUME_DIAGNOSIS') {
    await handleAiAnalysis(resume)
  }
}

const handleEmbedding = async (resume: ResumeListItem) => {
  embeddingResumeId.value = resume.id

  try {
    const task = await submitResumeEmbeddingTask(resume.id)
    startResumeTaskPolling(task.taskId, resume.id, task)
    ElMessage.success('简历向量生成任务已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交简历向量生成任务失败')
  } finally {
    embeddingResumeId.value = null
  }
}

const handleDelete = async (resume: ResumeListItem) => {
  if (isRowBusy(resume.id)) {
    return
  }

  try {
    await ElMessageBox.confirm(
      h('div', { class: 'resume-delete-confirm' }, [
        h('p', { class: 'resume-delete-confirm-title' }, `确认删除「${resume.originalFilename}」吗？`),
        h('p', { class: 'resume-delete-confirm-desc' }, '删除后不可恢复，以下关联内容也会一起删除：'),
        h('ul', { class: 'resume-delete-confirm-list' }, [
          h('li', '简历解析结果和完整原文记录'),
          h('li', '简历诊断结果和 AI 历史中的相关记录'),
          h('li', '匹配分析、岗位优化建议和局部改写记录'),
          h('li', '已生成的向量索引和上传文件'),
        ]),
      ]),
      '删除简历',
      {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
    })
  } catch (error) {
    return
  }

  deletingResumeId.value = resume.id

  try {
    await deleteResume(resume.id)
    resumes.value = resumes.value.filter((item) => item.id !== resume.id)
    saveResumeOrder()

    if (activeResume.value?.id === resume.id) {
      activeResume.value = null
      parseResult.value = null
      aiAnalysis.value = null
      activePanel.value = null
      activeDetailTab.value = 'overview'
    }

    await loadResumes()
    ElMessage.success('删除成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  } finally {
    deletingResumeId.value = null
  }
}

const hasStructuredList = (items: string[] | null | undefined) => {
  return Array.isArray(items) && items.length > 0
}

const hasSections = (sections: ResumeTextSection[]) => {
  return sections.length > 0
}

const handleConfirmParseResult = () => {
  if (!parseResult.value?.resumeId) {
    return
  }

  const nextConfirmedIds = new Set(confirmedParseResultIds.value)
  nextConfirmedIds.add(parseResult.value.resumeId)
  confirmedParseResultIds.value = nextConfirmedIds
  ElMessage.success('已确认当前结构化结果')
}

const scrollToUpload = () => {
  uploadPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const resolveResumeCardParseStatus = (resume: ResumeListItem) => {
  if (parsingResumeId.value === resume.id || (parseTaskActive.value && activeAsyncTaskResumeId.value === resume.id)) {
    return 'PROCESSING'
  }

  if (activeResume.value?.id === resume.id && parseResult.value?.parseStatus) {
    return parseResult.value.parseStatus
  }

  return 'PENDING'
}

const resolveResumeCardAnalysisStatus = (resume: ResumeListItem) => {
  if (analyzingResumeId.value === resume.id || (diagnosisTaskActive.value && activeAsyncTaskResumeId.value === resume.id)) {
    return 'PROCESSING'
  }

  if (activeResume.value?.id === resume.id && aiAnalysis.value?.analysisStatus) {
    return aiAnalysis.value.analysisStatus
  }

  return 'PENDING'
}

const toggleSetValue = (source: Set<string>, id: string) => {
  const next = new Set(source)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  return next
}

const toggleCardDetails = (target: 'experience' | 'project', id: string) => {
  if (target === 'experience') {
    expandedExperienceCards.value = toggleSetValue(expandedExperienceCards.value, id)
  } else {
    expandedProjectCards.value = toggleSetValue(expandedProjectCards.value, id)
  }
}

const toggleCardSource = (target: 'experience' | 'project', id: string) => {
  if (target === 'experience') {
    expandedExperienceSourceCards.value = toggleSetValue(expandedExperienceSourceCards.value, id)
  } else {
    expandedProjectSourceCards.value = toggleSetValue(expandedProjectSourceCards.value, id)
  }
}

const isExperienceDetailsExpanded = (id: string) => expandedExperienceCards.value.has(id)
const isProjectDetailsExpanded = (id: string) => expandedProjectCards.value.has(id)
const isExperienceSourceExpanded = (id: string) => expandedExperienceSourceCards.value.has(id)
const isProjectSourceExpanded = (id: string) => expandedProjectSourceCards.value.has(id)
const isSkillGroupExpanded = (id: string) => expandedSkillGroups.value.has(id)

const toggleSkillGroupExpanded = (id: string) => {
  const next = new Set(expandedSkillGroups.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedSkillGroups.value = next
}

onMounted(async () => {
  await loadResumes()
  startRouteAsyncTaskPolling()
})

watch(resumes, async () => {
  await nextTick()
  bindResumeTableDragHandlers()
})

onUnmounted(() => {
  stopActiveAsyncTaskPolling()
})
</script>

<template>
  <section class="resume-page">
    <section class="resume-shell">
      <PageHeader
        eyebrow="我的简历"
        title="管理简历资产、结构化结果和简历诊断"
        description="本页只处理简历自身的上传、解析和质量诊断；岗位匹配和岗位优化建议进入“匹配与优化”页面。"
      >
        <template #actions>
          <el-button type="primary" @click="scrollToUpload">上传新简历</el-button>
        </template>
      </PageHeader>

      <section class="resume-flow-panel">
        <div class="resume-flow-header">
          <div>
            <h2 class="resume-section-title">简历处理流程</h2>
            <p class="resume-section-subtitle">按“选择文件 -> 上传 -> 解析 -> 诊断 -> 匹配”逐步推进。</p>
          </div>
          <el-tag type="info">当前页面不写回原始简历</el-tag>
        </div>
        <ProcessStepper :steps="resumeFlowSteps" />
      </section>

      <section ref="uploadPanelRef" class="resume-upload-panel">
        <div>
          <div class="resume-upload-heading">
            <div>
              <h2 class="resume-section-title">上传简历</h2>
              <p class="resume-section-subtitle">{{ selectedUploadSummary }}</p>
            </div>
            <el-tag :type="selectedUploadFiles.length ? 'success' : 'info'">
              {{ selectedUploadFiles.length ? '已选择文件' : '等待选择' }}
            </el-tag>
          </div>
          <el-upload
            v-model:file-list="uploadFiles"
            accept=".pdf,.doc,.docx"
            :auto-upload="false"
            multiple
            :limit="RESUME_UPLOAD_LIMIT"
            :on-change="handleFileChange"
            :on-exceed="handleFileExceed"
            :on-remove="handleFileRemove"
          >
            <el-button type="primary">选择简历文件</el-button>
            <template #tip>
              <div class="resume-upload-tip">支持 PDF、DOC、DOCX，单份最大 10 MB，一次最多 {{ RESUME_UPLOAD_LIMIT }} 份。</div>
            </template>
          </el-upload>

          <p class="resume-upload-tip">解析会优先使用规则结果；只有需要 AI 参与时才调用 AI，调用失败后自动降级为规则解析。</p>
          <p v-if="uploadProgressText" class="resume-upload-progress">{{ uploadProgressText }}</p>
          <el-alert
            v-if="selectedUploadFiles.length"
            class="resume-upload-selection-alert"
            :title="`准备上传 ${selectedUploadFiles.length} 份简历，上传完成后会刷新下方简历列表。`"
            type="success"
            :closable="false"
            show-icon
          />
          <div class="resume-parse-mode-control">
            <span class="resume-parse-mode-label">解析模式</span>
            <el-select v-model="selectedParseMode" size="small" class="resume-parse-mode-select">
              <el-option
                v-for="option in parseModeOptions"
                :key="option.value"
                :label="`${option.label} ${option.description}`"
                :value="option.value"
              />
            </el-select>
          </div>
        </div>

        <el-button type="success" :loading="uploading" @click="handleUpload">上传</el-button>
      </section>

      <section v-if="activeAsyncTask || asyncTaskError" class="resume-task-panel">
        <header class="resume-task-header">
          <div>
            <h2 class="resume-task-title">{{ activeAsyncTaskTitle }}</h2>
            <p class="resume-task-subtitle">
              {{ activeAsyncTask?.message || asyncTaskError || '正在获取任务状态' }}
            </p>
          </div>
          <el-space>
            <el-tag v-if="activeAsyncTask" :type="resolveAsyncTaskStatusType(activeAsyncTask.status)">
              {{ resolveAsyncTaskStatusText(activeAsyncTask.status) }}
            </el-tag>
            <el-button
              v-if="asyncTaskError && canRetryActiveTask"
              size="small"
              type="primary"
              @click="retryActiveAsyncTask"
            >
              重试
            </el-button>
            <el-button v-if="isActiveAsyncTaskRunning" size="small" @click="clearAsyncTaskState">停止轮询</el-button>
          </el-space>
        </header>
        <div class="resume-task-steps">
          <span
            v-for="stage in activeTaskStages"
            :key="stage.title"
            class="resume-task-step"
            :class="`is-${stage.status}`"
          >
            {{ stage.title }}
          </span>
        </div>
        <el-progress
          v-if="activeAsyncTask"
          :percentage="asyncTaskProgress"
          :status="activeAsyncTask.status === 'SUCCESS' ? 'success' : undefined"
        />
        <el-alert
          v-if="asyncTaskError"
          class="resume-task-alert"
          :title="asyncTaskError"
          :type="asyncTaskTimedOut ? 'warning' : 'error'"
          :closable="false"
          show-icon
        />
      </section>

      <section class="resume-workbench-grid">
        <section class="resume-list-panel">
          <header class="resume-list-header">
            <div>
              <h2 class="resume-section-title">简历资产</h2>
              <p class="resume-section-subtitle">先选择简历，再在右侧查看解析和诊断结果。</p>
            </div>
            <el-tag type="info">{{ resumes.length }} 份</el-tag>
          </header>

          <div class="resume-asset-list">
            <SkeletonBlock v-if="loading" compact title :rows="6" />

            <article
              v-else
              v-for="resume in resumes"
              :key="resume.id"
              class="resume-asset-card"
              :class="{ 'is-active': activeResume?.id === resume.id }"
            >
              <button type="button" class="resume-asset-main" @click="openResumeOverview(resume)">
                <strong>{{ resume.originalFilename }}</strong>
                <span>{{ formatDateTime(resume.createdAt) }} · {{ resume.fileType }} · {{ formatFileSize(resume.fileSize) }}</span>
              </button>
              <div class="resume-asset-status">
                <span>
                  上传
                  <StatusTag :status="resume.uploadStatus" />
                </span>
                <span>
                  解析
                  <StatusTag :status="resolveResumeCardParseStatus(resume)" />
                </span>
                <span>
                  诊断
                  <StatusTag :status="resolveResumeCardAnalysisStatus(resume)" />
                </span>
              </div>
              <div class="resume-actions">
                <el-button size="small" type="primary" plain :disabled="isRowBusy(resume.id)" @click="openResumeOverview(resume)">
                  查看
                </el-button>
                <el-button
                  size="small"
                  type="primary"
                  :disabled="isRowBusy(resume.id)"
                  :loading="parsingResumeId === resume.id"
                  @click="handleParse(resume)"
                >
                  解析
                </el-button>
                <el-button
                  size="small"
                  type="success"
                  plain
                  :disabled="isRowBusy(resume.id)"
                  :loading="analyzingResumeId === resume.id"
                  @click="handleAiAnalysis(resume)"
                >
                  诊断
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  :disabled="isRowBusy(resume.id) && deletingResumeId !== resume.id"
                  :loading="deletingResumeId === resume.id"
                  @click="handleDelete(resume)"
                >
                  删除
                </el-button>
              </div>
            </article>

            <EmptyState
              v-if="!loading && resumes.length === 0"
              title="你还没有上传简历"
              description="上传后可以进行简历解析、简历诊断和后续岗位匹配。"
              action-text="上传第一份简历"
              @action="scrollToUpload"
            />
          </div>
        </section>

        <section class="resume-detail-workspace">
          <template v-if="activeResume">
            <header class="resume-detail-topbar">
              <div>
                <h2 class="resume-section-title">{{ activeResume.originalFilename }}</h2>
                <p class="resume-section-subtitle">
                  {{ activeResume.fileType }} · {{ formatFileSize(activeResume.fileSize) }} · {{ formatDateTime(activeResume.createdAt) }}
                </p>
              </div>
              <el-tag>{{ activeResume.uploadStatus }}</el-tag>
            </header>
            <el-tabs v-model="activeDetailTab" class="resume-detail-tabs">
              <el-tab-pane label="概览" name="overview" />
              <el-tab-pane label="结构化结果" name="parse" />
              <el-tab-pane label="完整原文" name="raw" />
              <el-tab-pane label="简历诊断" name="analysis" />
            </el-tabs>
          </template>
          <EmptyState
            v-else
            title="先选择一份简历"
            description="左侧选择简历后，右侧会显示概览、结构化结果、完整原文和简历诊断。"
            action-text="上传新简历"
            @action="scrollToUpload"
          />

      <section v-if="activeResume && activeDetailTab === 'overview'" class="resume-detail-panel">
        <header class="resume-parse-header">
          <div>
            <h2 class="resume-section-title">概览</h2>
            <p class="resume-section-subtitle">文件信息和当前处理状态。</p>
          </div>
          <el-tag>{{ activeResume.uploadStatus }}</el-tag>
        </header>
        <div class="resume-overview-grid">
          <article v-for="item in resumeOverviewStats" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.note }}</small>
          </article>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文件名">{{ activeResume.originalFilename }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ activeResume.fileType }}</el-descriptions-item>
          <el-descriptions-item label="大小">{{ formatFileSize(activeResume.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ formatDateTime(activeResume.createdAt) }}</el-descriptions-item>
        </el-descriptions>
        <div class="resume-detail-actions">
          <el-button type="primary" @click="handleParse(activeResume)">开始解析</el-button>
          <el-button @click="loadParseResult(activeResume)">查看结构化结果</el-button>
          <el-button type="success" plain @click="handleAiAnalysis(activeResume)">生成简历诊断</el-button>
          <el-button @click="loadAiAnalysis(activeResume)">查看诊断</el-button>
          <el-button @click="router.push('/job-descriptions')">选择目标岗位</el-button>
        </div>
      </section>

      <section
        v-if="activeResume && parseResult && activeDetailTab === 'parse'"
        v-loading="loadingParseResult"
        class="resume-parse-panel"
      >
        <header class="resume-parse-header">
          <div>
            <h2 class="resume-section-title">结构化结果</h2>
            <p class="resume-section-subtitle">核对基础信息、技能和经历是否完整，再确认结构化结果。</p>
          </div>
          <el-space wrap>
            <div class="resume-parse-mode-control compact">
              <span class="resume-parse-mode-label">解析模式</span>
              <el-select v-model="selectedParseMode" size="small" class="resume-parse-mode-select">
                <el-option
                  v-for="option in parseModeOptions"
                  :key="option.value"
                  :label="`${option.label} ${option.description}`"
                  :value="option.value"
                />
              </el-select>
            </div>
            <el-tag v-if="parseResultConfirmed" type="success">已确认</el-tag>
            <el-tag :type="resolveParseStatusType(parseResult?.parseStatus)">
              {{ resolveParseStatusText(parseResult?.parseStatus) }}
            </el-tag>
            <el-button
              type="primary"
              :disabled="parseResult.parseStatus !== 'SUCCESS' || parseResultConfirmed"
              @click="handleConfirmParseResult"
            >
              确认结构化结果
            </el-button>
            <el-button
              :loading="parsingResumeId === activeResume.id"
              :disabled="isRowBusy(activeResume.id)"
              @click="handleParse(activeResume)"
            >
              重新解析
            </el-button>
          </el-space>
        </header>

        <div class="resume-result-content">
          <el-alert
            v-if="parseResult.parseStatus === 'FAILED'"
            :title="parseResult.errorMessage || '解析失败'"
            type="error"
            :closable="false"
            show-icon
          />

          <section class="resume-structured-section">
            <h3 class="resume-block-title">解析状态</h3>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="解析状态">
                <el-tag :type="resolveParseStatusType(parseResult.parseStatus)">
                  {{ resolveParseStatusText(parseResult.parseStatus) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="文本质量">
                <el-tag :type="resolveQualityStatusType(parseResult.textQualityStatus)">
                  {{ resolveQualityStatusText(parseResult.textQualityStatus) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="结构质量">
                <el-tag :type="resolveQualityStatusType(parseResult.parseQualityStatus)">
                  {{ resolveQualityStatusText(parseResult.parseQualityStatus) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="质量分数">{{ parseResult.parseQualityScore ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="解析模式">{{ parseModeText }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDateTime(parseResult.updatedAt || '') }}</el-descriptions-item>
              <el-descriptions-item label="文件名">{{ activeResume.originalFilename }}</el-descriptions-item>
            </el-descriptions>
          </section>

          <section
            v-if="parseResult.textQualityMessage || parseResult.parseQualityMessage || allQualityWarnings.length"
            class="resume-structured-section"
          >
            <h3 class="resume-block-title">质量提示</h3>
            <div class="resume-quality-alerts">
              <el-alert
                v-if="parseResult.textQualityMessage"
                :title="parseResult.textQualityMessage"
                :type="parseResult.textQualityStatus === 'FAILED' ? 'error' : 'warning'"
                :closable="false"
                show-icon
              />
              <el-alert
                v-if="parseResult.parseQualityMessage"
                :title="parseResult.parseQualityMessage"
                :type="parseResult.parseQualityStatus === 'FAILED' ? 'error' : 'warning'"
                :closable="false"
                show-icon
              />
              <div v-if="allQualityWarnings.length" class="resume-tag-list">
                <el-tag
                  v-for="warning in allQualityWarnings"
                  :key="warning"
                  type="warning"
                >
                  {{ resolveWarningText(warning) }}
                </el-tag>
              </div>
            </div>
          </section>

          <section class="resume-structured-section">
            <h3 class="resume-block-title">基础信息</h3>
            <el-descriptions :column="3" border>
              <el-descriptions-item
                v-for="field in displaySections.basicInfo"
                :key="field.key"
                :label="field.label"
              >
                {{ field.value }}
              </el-descriptions-item>
            </el-descriptions>
            <el-alert
              v-if="nameLowConfidenceVisible"
              class="resume-inline-alert"
              title="姓名未识别，低置信度字段不会强行填入"
              type="info"
              :closable="false"
              show-icon
            />
          </section>

          <section class="resume-display-layout">
            <article v-if="displaySections.skillGroups.length" class="resume-display-section span-full">
              <h3 class="resume-block-title">技能关键词</h3>
              <div class="resume-skill-groups">
                <div v-for="group in displaySections.skillGroups" :key="group.key" class="resume-skill-group">
                  <div class="resume-skill-group-title">{{ group.label }}</div>
                  <div class="resume-tag-list">
                    <el-tag
                      v-for="skill in isSkillGroupExpanded(group.key) ? group.skills : group.previewSkills"
                      :key="`${group.key}-${skill}`"
                      type="success"
                    >
                      {{ skill }}
                    </el-tag>
                  </div>
                  <el-button
                    v-if="group.hiddenCount > 0"
                    link
                    type="primary"
                    class="resume-inline-link"
                    @click="toggleSkillGroupExpanded(group.key)"
                  >
                    {{ isSkillGroupExpanded(group.key) ? '收起' : `展开更多（${group.hiddenCount}）` }}
                  </el-button>
                </div>
              </div>
            </article>

            <article v-if="displaySections.educationCards.length" class="resume-display-section">
              <h3 class="resume-block-title">教育经历</h3>
              <div class="resume-card-list">
                <div v-for="card in displaySections.educationCards" :key="card.id" class="resume-info-card">
                  <div class="resume-card-heading">
                    <strong>{{ card.school }}</strong>
                    <span v-if="card.timeRange">{{ card.timeRange }}</span>
                  </div>
                  <p class="resume-card-line">{{ card.degreeMajor }}</p>
                  <p v-for="extra in card.extras" :key="extra" class="resume-muted-text">{{ extra }}</p>
                </div>
              </div>
            </article>

            <article
              v-if="displaySections.experienceCards.length"
              class="resume-display-section"
            >
              <h3 class="resume-block-title">工作经历</h3>
              <div class="resume-card-list">
                <div v-for="card in displaySections.experienceCards" :key="card.id" class="resume-info-card">
                  <div class="resume-card-heading">
                    <strong>{{ card.organization }}</strong>
                    <span v-if="card.timeRange">{{ card.timeRange }}</span>
                  </div>
                  <p v-if="card.role" class="resume-card-line">{{ card.role }}</p>
                  <p v-if="card.summary" class="resume-muted-text">{{ card.summary }}</p>
                  <ul v-if="card.details.length" class="resume-compact-list">
                    <li
                      v-for="item in isExperienceDetailsExpanded(card.id) ? card.details : card.details.slice(0, 3)"
                      :key="item"
                    >
                      {{ item }}
                    </li>
                  </ul>
                  <el-button
                    v-if="card.hiddenCount > 0"
                    link
                    type="primary"
                    @click="toggleCardDetails('experience', card.id)"
                  >
                    {{ isExperienceDetailsExpanded(card.id) ? '收起详情' : `展开详情（${card.hiddenCount} 条）` }}
                  </el-button>
                  <el-button
                    v-if="card.sourceText"
                    link
                    type="primary"
                    @click="toggleCardSource('experience', card.id)"
                  >
                    {{ isExperienceSourceExpanded(card.id) ? '收起原文' : '查看原文' }}
                  </el-button>
                  <pre v-if="isExperienceSourceExpanded(card.id) && card.sourceText" class="resume-source-text">{{ card.sourceText }}</pre>
                </div>
              </div>
            </article>

            <article
              v-if="displaySections.projectCards.length"
              class="resume-display-section"
            >
              <h3 class="resume-block-title">项目经历</h3>
              <div class="resume-card-list">
                <div
                  v-for="card in projectsExpanded ? displaySections.projectCards : displaySections.projectPreviewCards"
                  :key="card.id"
                  class="resume-info-card"
                >
                  <div class="resume-card-heading">
                    <strong>{{ card.name }}</strong>
                  </div>
                  <p v-if="card.summary" class="resume-muted-text">{{ card.summary }}</p>
                  <div v-if="card.techStack.length" class="resume-tag-list compact">
                    <el-tag v-for="skill in card.techStack" :key="`${card.id}-${skill}`" size="small">{{ skill }}</el-tag>
                  </div>
                  <p v-if="card.role" class="resume-card-line">{{ card.role }}</p>
                  <ul v-if="card.responsibilities.length" class="resume-compact-list">
                    <li
                      v-for="item in isProjectDetailsExpanded(card.id) ? card.responsibilities : card.responsibilities.slice(0, 3)"
                      :key="item"
                    >
                      {{ item }}
                    </li>
                  </ul>
                  <el-button
                    v-if="card.hiddenCount > 0"
                    link
                    type="primary"
                    @click="toggleCardDetails('project', card.id)"
                  >
                    {{ isProjectDetailsExpanded(card.id) ? '收起详情' : `展开详情（${card.hiddenCount} 条）` }}
                  </el-button>
                  <el-button
                    v-if="card.sourceText"
                    link
                    type="primary"
                    @click="toggleCardSource('project', card.id)"
                  >
                    {{ isProjectSourceExpanded(card.id) ? '收起原文' : '查看原文' }}
                  </el-button>
                  <pre v-if="isProjectSourceExpanded(card.id) && card.sourceText" class="resume-source-text">{{ card.sourceText }}</pre>
                </div>
                <el-button
                  v-if="displaySections.projectHiddenCount > 0"
                  link
                  type="primary"
                  @click="projectsExpanded = !projectsExpanded"
                >
                  {{ projectsExpanded ? '收起项目' : `查看更多项目（${displaySections.projectHiddenCount} 个）` }}
                </el-button>
              </div>
            </article>

            <article v-if="displaySections.internshipCards.length" class="resume-display-section">
              <h3 class="resume-block-title">实习经历</h3>
              <div class="resume-card-list">
                <div v-for="card in displaySections.internshipCards" :key="card.id" class="resume-info-card">
                  <div class="resume-card-heading">
                    <strong>{{ card.organization }}</strong>
                    <span v-if="card.timeRange">{{ card.timeRange }}</span>
                  </div>
                  <p v-if="card.role" class="resume-card-line">{{ card.role }}</p>
                  <p v-if="card.summary" class="resume-muted-text">{{ card.summary }}</p>
                  <ul v-if="card.details.length" class="resume-compact-list">
                    <li
                      v-for="item in isExperienceDetailsExpanded(card.id) ? card.details : card.details.slice(0, 3)"
                      :key="item"
                    >
                      {{ item }}
                    </li>
                  </ul>
                  <el-button
                    v-if="card.hiddenCount > 0"
                    link
                    type="primary"
                    @click="toggleCardDetails('experience', card.id)"
                  >
                    {{ isExperienceDetailsExpanded(card.id) ? '收起详情' : `展开详情（${card.hiddenCount} 条）` }}
                  </el-button>
                  <el-button
                    v-if="card.sourceText"
                    link
                    type="primary"
                    @click="toggleCardSource('experience', card.id)"
                  >
                    {{ isExperienceSourceExpanded(card.id) ? '收起原文' : '查看原文' }}
                  </el-button>
                  <pre v-if="isExperienceSourceExpanded(card.id) && card.sourceText" class="resume-source-text">{{ card.sourceText }}</pre>
                </div>
              </div>
            </article>

            <article v-if="displaySections.campusCards.length" class="resume-display-section">
              <h3 class="resume-block-title">校园 / 实践经历</h3>
              <div class="resume-card-list">
                <div v-for="card in displaySections.campusCards" :key="card.id" class="resume-info-card">
                  <div class="resume-card-heading">
                    <strong>{{ card.organization }}</strong>
                    <span v-if="card.timeRange">{{ card.timeRange }}</span>
                  </div>
                  <p v-if="card.summary" class="resume-muted-text">{{ card.summary }}</p>
                  <ul v-if="card.details.length" class="resume-compact-list">
                    <li
                      v-for="item in isExperienceDetailsExpanded(card.id) ? card.details : card.details.slice(0, 3)"
                      :key="item"
                    >
                      {{ item }}
                    </li>
                  </ul>
                  <el-button
                    v-if="card.hiddenCount > 0"
                    link
                    type="primary"
                    @click="toggleCardDetails('experience', card.id)"
                  >
                    {{ isExperienceDetailsExpanded(card.id) ? '收起详情' : `展开详情（${card.hiddenCount} 条）` }}
                  </el-button>
                  <el-button
                    v-if="card.sourceText"
                    link
                    type="primary"
                    @click="toggleCardSource('experience', card.id)"
                  >
                    {{ isExperienceSourceExpanded(card.id) ? '收起原文' : '查看原文' }}
                  </el-button>
                  <pre v-if="isExperienceSourceExpanded(card.id) && card.sourceText" class="resume-source-text">{{ card.sourceText }}</pre>
                </div>
              </div>
            </article>

            <article v-if="displaySections.achievementCards.length" class="resume-display-section">
              <h3 class="resume-block-title">获奖 / 成果</h3>
              <div class="resume-card-list">
                <div v-for="card in displaySections.achievementCards" :key="card.id" class="resume-info-card compact">
                  <strong>{{ card.title }}</strong>
                  <p v-if="card.meta" class="resume-muted-text">{{ card.meta }}</p>
                </div>
              </div>
            </article>

            <article v-if="displaySections.certificateTags.length" class="resume-display-section">
              <h3 class="resume-block-title">证书</h3>
              <div class="resume-tag-list">
                <el-tag v-for="certificate in displaySections.certificateTags" :key="certificate">{{ certificate }}</el-tag>
              </div>
            </article>

            <article v-if="displaySections.summaryCard" class="resume-display-section span-full">
              <h3 class="resume-block-title">自我评价</h3>
              <p class="resume-summary-text">
                {{ summaryExpanded ? displaySections.summaryCard.fullText : displaySections.summaryCard.preview }}
              </p>
              <el-button
                v-if="displaySections.summaryCard.expandable"
                link
                type="primary"
                @click="summaryExpanded = !summaryExpanded"
              >
                {{ summaryExpanded ? '收起' : '展开' }}
              </el-button>
            </article>

            <article v-if="displaySections.pendingItems.length" class="resume-display-section span-full">
              <div class="resume-section-title-row">
                <h3 class="resume-block-title">待确认内容（{{ displaySections.pendingItems.length }} 条）</h3>
                <el-button v-if="otherHiddenCount > 0" link type="primary" @click="othersExpanded = !othersExpanded">
                  {{ othersExpanded ? '收起' : '展开全部' }}
                </el-button>
              </div>
              <ul class="resume-compact-list">
                <li
                  v-for="item in othersExpanded ? displaySections.pendingItems : displaySections.pendingPreviewItems"
                  :key="item"
                >
                  {{ item }}
                </li>
              </ul>
            </article>
          </section>
        </div>

      </section>

      <EmptyState
        v-if="activeResume && activeDetailTab === 'parse' && !parseResult"
        title="还没有结构化结果"
        description="发起解析后，系统会展示基础信息、技能和结构化经历。"
        action-text="开始解析"
        @action="handleParse(activeResume)"
      />

      <section
        v-if="activeResume && activeDetailTab === 'raw'"
        class="resume-raw-panel"
      >
        <header class="resume-parse-header">
          <div>
            <h2 class="resume-section-title">完整原文</h2>
            <p class="resume-section-subtitle">这里展示系统整理后的简历全文，方便你核对是否有内容遗漏。</p>
          </div>
          <el-space wrap>
            <el-tag v-if="parseResult" :type="resolveParseStatusType(parseResult.parseStatus)">
              {{ resolveParseStatusText(parseResult.parseStatus) }}
            </el-tag>
            <el-button
              :disabled="!cleanedResumeFullText"
              @click="copyFullResumeText"
            >
              复制全文
            </el-button>
            <el-button
              :loading="parsingResumeId === activeResume.id"
              :disabled="isRowBusy(activeResume.id)"
              @click="handleParse(activeResume)"
            >
              重新解析
            </el-button>
          </el-space>
        </header>

        <pre v-if="cleanedResumeFullText" class="resume-full-text">{{ cleanedResumeFullText }}</pre>
        <EmptyState
          v-else
          title="还没有完整原文"
          description="完成简历解析后，这里会展示整理后的简历全文。"
          action-text="开始解析"
          @action="handleParse(activeResume)"
        />
      </section>

      <section
        v-if="activeResume && aiAnalysis && activeDetailTab === 'analysis'"
        v-loading="loadingAiAnalysis"
        class="resume-ai-section"
      >
        <header class="resume-ai-header">
          <div>
            <h3 class="resume-block-title">简历诊断</h3>
            <p class="resume-ai-meta">
              更新时间：{{ formatDateTime(aiAnalysis.updatedAt || '') }}
            </p>
          </div>
          <el-tag :type="resolveAnalysisStatusType(aiAnalysis.analysisStatus)">
            {{ resolveAnalysisStatusText(aiAnalysis.analysisStatus) }}
          </el-tag>
        </header>

        <el-alert
          v-if="aiAnalysis.analysisStatus === 'FAILED'"
          :title="aiAnalysis.errorMessage || '简历诊断失败'"
          type="error"
          :closable="false"
          show-icon
        />

        <template v-else>
          <el-alert
            title="简历诊断仅供参考，涉及经历、技能、证书、奖项和量化结果的内容需要你确认后再使用。"
            type="warning"
            :closable="false"
            show-icon
            class="resume-ai-warning"
          />

          <section class="resume-ai-overview">
            <article class="resume-ai-score-card" :class="`is-${aiAnalysisScoreSummary.level}`">
              <span class="resume-ai-score-value">{{ aiAnalysis.score ?? '-' }}</span>
              <span class="resume-ai-score-label">综合评分</span>
              <strong>{{ aiAnalysisScoreSummary.label }}</strong>
              <p>{{ aiAnalysisScoreSummary.description }}</p>
            </article>

            <article class="resume-ai-summary-card">
              <div class="resume-ai-summary-header">
                <div>
                  <h3 class="resume-block-title">AI 诊断摘要</h3>
                  <p class="resume-muted-text">诊断只分析简历自身质量，不判断具体岗位匹配度。</p>
                </div>
                <el-tag :type="resolveAnalysisStatusType(aiAnalysis.analysisStatus)">
                  {{ resolveAnalysisStatusText(aiAnalysis.analysisStatus) }}
                </el-tag>
              </div>
              <div class="resume-ai-meta-grid">
                <span>
                  <strong>{{ aiAnalysis.strengths?.length ?? 0 }}</strong>
                  优势
                </span>
                <span>
                  <strong>{{ aiAnalysis.problems?.length ?? 0 }}</strong>
                  问题
                </span>
                <span>
                  <strong>{{ aiAnalysis.suggestionsSummary?.length ?? 0 }}</strong>
                  建议
                </span>
              </div>
              <el-descriptions :column="1" border class="resume-score-detail">
                <el-descriptions-item label="更新时间">{{ formatDateTime(aiAnalysis.updatedAt || '') }}</el-descriptions-item>
              </el-descriptions>
            </article>
          </section>

          <section v-if="aiAnalysisResultCards.length" class="resume-ai-result-grid">
            <article
              v-for="card in aiAnalysisResultCards"
              :key="card.key"
              class="resume-ai-result-card"
              :class="`is-${card.tone}`"
            >
              <div class="resume-ai-result-card-header">
                <h3 class="resume-block-title">{{ card.title }}</h3>
                <el-tag size="small" :type="card.tone">{{ card.items.length }} 条</el-tag>
              </div>
              <ol v-if="hasStructuredList(card.items)" class="resume-ai-result-list">
                <li v-for="item in card.items" :key="item">{{ item }}</li>
              </ol>
            </article>
          </section>

          <section class="resume-ai-next-panel">
            <h3 class="resume-block-title">下一步</h3>
            <p>先确认诊断里的真实问题，再选择或新增目标岗位进入匹配分析；岗位相关优化建议会在匹配成功后生成。</p>
            <el-space wrap>
              <el-button @click="router.push('/job-descriptions')">选择目标岗位</el-button>
              <el-button type="primary" @click="router.push('/ai-job-matches')">进入匹配与优化</el-button>
            </el-space>
          </section>
        </template>
      </section>

      <EmptyState
        v-if="activeResume && activeDetailTab === 'analysis' && !aiAnalysis"
        title="还没有简历诊断"
        description="简历诊断只分析简历自身质量，不判断具体岗位匹配度。"
        action-text="生成简历诊断"
        @action="handleAiAnalysis(activeResume)"
      />
        </section>
      </section>
    </section>
  </section>
</template>

<style scoped>
.resume-page {
  min-height: 0;
  padding: 0;
  background: transparent;
}

.resume-shell {
  display: grid;
  gap: 18px;
  width: 100%;
}

.resume-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.resume-title {
  margin: 0;
  color: var(--app-color-text);
  font-size: 28px;
  font-weight: 700;
}

.resume-subtitle {
  margin: 8px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 15px;
  line-height: 1.7;
}

.resume-flow-panel {
  display: grid;
  gap: 18px;
  margin-bottom: 20px;
  padding: 24px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.resume-list-panel {
  padding: 24px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.resume-workbench-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.35fr) minmax(0, 0.65fr);
  gap: 18px;
  align-items: start;
}

.resume-list-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.resume-asset-list {
  display: grid;
  gap: 12px;
  min-height: 240px;
}

.resume-asset-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.resume-asset-card.is-active {
  border-color: rgba(37, 111, 108, 0.38);
  background: var(--app-color-primary-soft);
}

.resume-asset-main {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 0;
  border: 0;
  color: var(--app-color-text);
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.resume-asset-main strong {
  overflow: hidden;
  font-size: 14px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-asset-main span {
  overflow: hidden;
  color: var(--app-color-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-asset-status {
  display: grid;
  gap: 8px;
}

.resume-asset-status span {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--app-color-text-secondary);
  font-size: 12px;
}

.resume-detail-workspace {
  min-width: 0;
}

.resume-detail-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 20px 22px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.resume-detail-topbar > div {
  min-width: 0;
}

.resume-detail-topbar .resume-section-title,
.resume-detail-topbar .resume-section-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-detail-tabs {
  margin-bottom: 16px;
  padding: 0 6px;
}

.resume-overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.resume-overview-grid article {
  display: grid;
  gap: 8px;
  min-height: 108px;
  align-content: space-between;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.resume-overview-grid span,
.resume-overview-grid small {
  overflow: hidden;
  color: var(--app-color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-overview-grid strong {
  overflow: hidden;
  color: var(--app-color-text);
  font-size: 22px;
  line-height: 1.15;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-flow-header,
.resume-upload-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.resume-flow-steps {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.resume-flow-step {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  min-height: 96px;
  padding: 14px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.resume-flow-step.is-done {
  border-color: rgba(63, 143, 104, 0.35);
  background: #edf7f1;
}

.resume-flow-step.is-current {
  border-color: rgba(37, 111, 108, 0.42);
  background: var(--app-color-primary-soft);
}

.resume-flow-step.is-failed {
  border-color: rgba(184, 92, 92, 0.45);
  background: #fbefef;
}

.resume-flow-index {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 999px;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  font-weight: 700;
  background: #eee9e1;
}

.resume-flow-step.is-done .resume-flow-index {
  color: #fff;
  background: var(--app-color-success);
}

.resume-flow-step.is-current .resume-flow-index {
  color: #fff;
  background: var(--app-color-primary);
}

.resume-flow-step.is-failed .resume-flow-index {
  color: #fff;
  background: var(--app-color-danger);
}

.resume-flow-step strong {
  display: block;
  color: var(--app-color-text);
  font-size: 14px;
}

.resume-flow-step span:last-child {
  display: block;
  margin-top: 4px;
  color: var(--app-color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.resume-upload-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  padding: 24px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.resume-upload-tip {
  margin-top: 8px;
  color: var(--app-color-text-secondary);
  font-size: 13px;
}

.resume-upload-progress {
  margin: 10px 0 0;
  color: var(--app-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.resume-upload-selection-alert {
  margin-top: 12px;
}

.resume-task-panel {
  display: grid;
  gap: 14px;
  margin-bottom: 20px;
  padding: 20px 24px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.resume-task-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.resume-task-title {
  margin: 0;
  color: var(--app-color-text);
  font-size: 17px;
  font-weight: 700;
}

.resume-task-subtitle {
  margin: 6px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.resume-task-alert {
  margin-top: 2px;
}

.resume-task-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.resume-task-step {
  padding: 6px 10px;
  border: 1px solid var(--app-color-border);
  border-radius: 999px;
  color: var(--app-color-text-secondary);
  font-size: 12px;
  font-weight: 700;
  background: var(--app-color-surface-soft);
}

.resume-task-step.is-done {
  border-color: rgba(63, 143, 104, 0.35);
  color: var(--app-color-success);
  background: #edf7f1;
}

.resume-task-step.is-current {
  border-color: rgba(37, 111, 108, 0.42);
  color: var(--app-color-primary);
  background: var(--app-color-primary-soft);
}

.resume-task-step.is-failed {
  border-color: rgba(184, 92, 92, 0.45);
  color: var(--app-color-danger);
  background: #fbefef;
}

.resume-parse-mode-control {
  display: flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  margin-top: 12px;
}

.resume-parse-mode-control.compact {
  margin-top: 0;
}

.resume-parse-mode-label {
  color: var(--app-color-text);
  font-size: 13px;
  white-space: nowrap;
}

.resume-parse-mode-select {
  width: 180px;
}

.resume-table {
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
}

.resume-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

:deep(.resume-row-dragging) {
  opacity: 0.55;
}

:deep(.resume-row-drop-target td) {
  background: var(--app-color-primary-soft) !important;
}

.resume-detail-panel {
  margin-top: 24px;
  padding: 28px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.resume-detail-workspace .resume-detail-panel,
.resume-detail-workspace .resume-parse-panel,
.resume-detail-workspace .resume-raw-panel,
.resume-detail-workspace .resume-ai-section,
.resume-detail-workspace > .ui-empty-state {
  margin-top: 0;
}

.resume-detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.resume-parse-panel,
.resume-raw-panel {
  margin-top: 24px;
  padding: 28px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.resume-full-text {
  max-height: 620px;
  margin: 0;
  overflow: auto;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 16px;
  color: var(--app-color-text);
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--app-color-surface-soft);
}

.resume-parse-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.resume-section-title {
  margin: 0;
  color: var(--app-color-text);
  font-size: 20px;
  font-weight: 700;
}

.resume-section-subtitle {
  margin: 6px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 14px;
}

.resume-structured-section {
  margin-top: 20px;
}

.resume-result-content {
  display: block;
}

.resume-ai-section {
  margin-top: 24px;
  padding: 28px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface);
  box-shadow: var(--app-shadow-card);
}

.resume-ai-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.resume-ai-meta {
  margin: 0;
  color: var(--app-color-text-secondary);
  font-size: 13px;
}

.resume-ai-warning {
  margin-bottom: 16px;
}

.resume-ai-overview {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
  margin-top: 20px;
  align-items: stretch;
}

.resume-ai-score-card {
  display: grid;
  align-content: center;
  justify-items: center;
  min-height: 220px;
  padding: 24px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface-soft);
  text-align: center;
}

.resume-ai-score-card.is-strong {
  border-color: rgba(63, 143, 104, 0.35);
  background: #edf7f1;
}

.resume-ai-score-card.is-normal {
  border-color: rgba(37, 111, 108, 0.35);
  background: var(--app-color-primary-soft);
}

.resume-ai-score-card.is-weak {
  border-color: rgba(201, 138, 46, 0.4);
  background: #fbf3e5;
}

.resume-ai-score-value {
  color: var(--app-color-primary);
  font-size: 54px;
  font-weight: 700;
  line-height: 1;
}

.resume-ai-score-label {
  margin-top: 8px;
  color: var(--app-color-text-secondary);
  font-size: 13px;
}

.resume-ai-score-card strong {
  margin-top: 14px;
  color: var(--app-color-text);
  font-size: 18px;
}

.resume-ai-score-card p {
  margin: 8px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.resume-ai-summary-card {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface-soft);
}

.resume-ai-summary-header,
.resume-ai-result-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.resume-ai-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.resume-ai-meta-grid span {
  display: grid;
  gap: 4px;
  min-height: 74px;
  align-content: center;
  padding: 12px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  background: var(--app-color-surface);
}

.resume-ai-meta-grid strong {
  color: var(--app-color-text);
  font-size: 24px;
  line-height: 1;
}

.resume-score-detail {
  min-width: 0;
}

.resume-ai-result-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 20px;
}

.resume-ai-result-card {
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-surface-soft);
}

.resume-ai-result-card.is-success {
  border-color: rgba(63, 143, 104, 0.32);
}

.resume-ai-result-card.is-danger {
  border-color: rgba(184, 92, 92, 0.32);
}

.resume-ai-result-card.is-warning {
  border-color: rgba(201, 138, 46, 0.34);
}

.resume-ai-result-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.resume-ai-result-list li {
  position: relative;
  padding: 12px 12px 12px 34px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  color: var(--app-color-text);
  line-height: 1.7;
  background: var(--app-color-surface);
}

.resume-ai-result-list li::before {
  position: absolute;
  top: 14px;
  left: 12px;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  background: var(--app-color-primary);
  content: '';
}

.resume-ai-result-card.is-danger .resume-ai-result-list li::before {
  background: var(--app-color-danger);
}

.resume-ai-result-card.is-warning .resume-ai-result-list li::before {
  background: var(--app-color-warning);
}

.resume-ai-next-panel {
  display: grid;
  gap: 12px;
  margin-top: 20px;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 18px;
  background: var(--app-color-primary-soft);
}

.resume-ai-next-panel p {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.7;
}

.resume-display-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px;
  margin-top: 24px;
}

.resume-display-section {
  min-width: 0;
}

.resume-display-section.span-full {
  grid-column: 1 / -1;
}

.resume-card-list {
  display: grid;
  gap: 12px;
}

.resume-info-card {
  min-width: 0;
  padding: 16px;
  border: 0;
  border-left: 3px solid var(--app-color-primary);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.resume-info-card.compact {
  padding: 14px 16px;
}

.resume-card-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--app-color-text);
  line-height: 1.5;
}

.resume-card-heading span {
  flex: 0 0 auto;
  color: var(--app-color-text-secondary);
  font-size: 13px;
}

.resume-card-placeholder {
  color: var(--app-color-text-secondary);
  font-weight: 600;
}

.resume-inline-link {
  margin-top: 4px;
  padding: 0;
}

.resume-card-line {
  margin: 0 0 8px;
  color: var(--app-color-text);
  font-size: 14px;
  line-height: 1.6;
}

.resume-compact-list {
  margin: 10px 0 0;
  padding-left: 18px;
  color: var(--app-color-text);
  line-height: 1.7;
}

.resume-source-text {
  max-height: 240px;
  margin: 10px 0 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 12px;
  border-radius: 14px;
  color: var(--app-color-text);
  font-size: 13px;
  line-height: 1.7;
  background: var(--app-color-primary-soft);
}

.resume-summary-text {
  margin: 0;
  color: var(--app-color-text);
  line-height: 1.8;
  white-space: pre-wrap;
}

.resume-skill-groups {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
}

.resume-skill-group {
  min-width: 0;
}

.resume-skill-group-title {
  margin-bottom: 8px;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.resume-tag-list.compact {
  margin-top: 10px;
}

.resume-section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.resume-section-summary {
  min-width: 0;
}

.resume-block-title {
  margin: 0 0 12px;
  color: var(--app-color-text);
  font-size: 15px;
  font-weight: 700;
}

.resume-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.resume-quality-alerts {
  display: grid;
  gap: 12px;
}

.resume-inline-alert {
  margin-top: 12px;
}

.resume-text-list {
  min-height: 88px;
  margin: 0;
  padding: 14px 16px 14px 28px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  color: var(--app-color-text);
  line-height: 1.7;
}

.resume-section-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.resume-section-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  background: var(--app-color-surface-soft);
}

.resume-section-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--app-color-text);
}

.resume-section-lines {
  min-height: 0;
}

.resume-debug-tabs {
  margin-top: 16px;
}

.resume-debug-title {
  margin: 18px 0 10px;
  color: var(--app-color-text);
  font-size: 14px;
  font-weight: 700;
}

.resume-muted-text {
  margin: 8px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.resume-empty-section-summary {
  margin: 14px 0 0;
  color: var(--app-color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.resume-raw-text {
  max-height: 360px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 16px;
  border: 1px solid var(--app-color-border);
  border-radius: 14px;
  color: var(--app-color-text);
  font-family:
    ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    monospace;
  font-size: 13px;
  line-height: 1.7;
  background: var(--app-color-surface-soft);
}

:global(.resume-delete-confirm) {
  display: grid;
  gap: 10px;
  color: var(--app-color-text);
  line-height: 1.6;
}

:global(.resume-delete-confirm-title) {
  margin: 0;
  color: var(--app-color-text);
  font-weight: 700;
}

:global(.resume-delete-confirm-desc) {
  margin: 0;
  color: var(--app-color-text-secondary);
}

:global(.resume-delete-confirm-list) {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
  color: var(--app-color-text-secondary);
}

@media (max-width: 640px) {
  .resume-header,
  .resume-flow-header,
  .resume-flow-steps,
  .resume-upload-panel,
  .resume-upload-heading,
  .resume-task-header,
  .resume-parse-header {
    align-items: stretch;
    flex-direction: column;
  }

  .resume-flow-steps {
    grid-template-columns: 1fr;
  }

  .resume-workbench-grid,
  .resume-overview-grid {
    grid-template-columns: 1fr;
  }

  .resume-detail-topbar {
    align-items: stretch;
    flex-direction: column;
  }

  .resume-actions {
    flex-direction: column;
  }

  .resume-ai-header,
  .resume-ai-overview {
    grid-template-columns: 1fr;
    align-items: stretch;
    flex-direction: column;
  }

  .resume-ai-meta-grid,
  .resume-ai-result-grid {
    grid-template-columns: 1fr;
  }

  .resume-display-layout,
  .resume-skill-groups {
    grid-template-columns: 1fr;
  }

  .resume-section-list {
    grid-template-columns: 1fr;
  }
}
</style>
