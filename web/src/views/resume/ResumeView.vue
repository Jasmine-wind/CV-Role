<script setup lang="ts">
import type { UploadFile, UploadProps, UploadUserFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
const parserVersionText = computed(() => parseMeta.value?.parserVersion || structuredContent.value?.parserVersion || '-')
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
const aiParseSummary = computed(() => {
  const meta = parseMeta.value
  const aiStatus = meta?.aiStatus || 'DISABLED'
  const used = aiStatus === 'USED' || aiStatus === 'FALLBACK' || Boolean(meta?.aiUsed)
  const applied = aiStatus === 'USED'
  const downgraded = aiStatus === 'FALLBACK'
  const totalDurationMs = meta?.totalParseDurationMs ?? structuredContent.value?.totalParseDurationMs ?? null
  const aiDurationMs = sumDurations(
    meta?.aiSectionClassifyDurationMs ?? structuredContent.value?.aiSectionClassifyDurationMs,
    meta?.aiStructuredParseDurationMs ?? structuredContent.value?.aiStructuredParseDurationMs,
  )
  const cacheHit = Boolean(meta?.aiCacheHit)
  const showCache = aiStatus === 'USED' || aiStatus === 'FALLBACK'
  const reason = aiStatus === 'SKIPPED'
    ? resolveAiSkippedReason(meta?.aiSkippedReason)
    : aiStatus === 'FALLBACK'
      ? (meta?.aiFallbackReason || '-')
      : aiStatus === 'DISABLED'
        ? 'AI 解析未启用'
        : '-'

  return {
    status: aiStatus,
    used,
    applied,
    downgraded,
    text: resolveAiStatusText(aiStatus),
    appliedText: applied ? '已应用' : '未应用',
    downgradeText: downgraded ? 'AI 失败后已降级' : '未发生 AI 失败降级',
    reason,
    fallbackOccurred: downgraded,
    fallbackOccurredText: downgraded ? '是' : '否',
    totalDurationText: formatDuration(totalDurationMs),
    aiDurationText: formatDuration(aiDurationMs),
    cacheText: showCache ? (cacheHit ? '命中缓存' : '未命中缓存') : 'AI 未调用，不读写缓存',
    cacheHit,
    showCache,
  }
})
const displayModelSummary = computed(() => {
  const meta = displaySections.value.debugInfo.displayMeta
  const generatedBy = meta?.generatedBy || (structuredContent.value?.displayModel ? 'AI' : 'RULE')
  const fallback = Boolean(meta?.aiDisplayFallback)
  const aiUsed = Boolean(meta?.aiDisplayUsed)
  const cacheHit = Boolean(meta?.cacheHit)
  return {
    generatedBy,
    aiUsed,
    fallback,
    text: aiUsed ? 'AI 展示优化已启用' : fallback ? '展示优化已降级' : '规则展示模型',
    tagType: fallback ? 'warning' : aiUsed ? 'success' : 'info',
    cacheText: aiUsed ? (cacheHit ? '命中缓存' : '未命中缓存') : '未调用 AI 展示优化，不读写缓存',
    durationText: formatDuration(meta?.aiDisplayDurationMs ?? null),
    errorMessage: meta?.aiDisplayErrorMessage || '',
  }
})
const parseResultConfirmed = computed(() => {
  return parseResult.value?.resumeId ? confirmedParseResultIds.value.has(parseResult.value.resumeId) : false
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

const resolveAiStatusText = (status: string | null | undefined) => {
  const map: Record<string, string> = {
    USED: 'AI 已参与解析',
    SKIPPED: 'AI 未调用',
    FALLBACK: 'AI 失败后已降级',
    DISABLED: 'AI 已关闭',
  }
  return status ? (map[status] ?? status) : 'AI 已关闭'
}

const resolveAiSkippedReason = (reason: string | null | undefined) => {
  const map: Record<string, string> = {
    ALL_BLOCKS_RULE_CONFIRMED: '规则解析已高置信度覆盖所有文本块',
    STABLE_FIELDS_RULE_CONFIRMED: '规则解析已覆盖稳定字段',
    NO_CLASSIFIABLE_BLOCKS: '没有可归类的文本块',
    NO_STRUCTURED_PARSE_BLOCKS: '没有可用于 AI 结构化解析的文本块',
    AI_BLOCK_LIMIT_EXCEEDED: '文本块过多，已跳过 AI 结构化解析',
    LEGACY_AI_NOT_APPLIED: '旧解析结果未记录详细原因',
  }
  return reason ? (map[reason] ?? reason) : '-'
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

const formatDuration = (value: number | null | undefined) => {
  if (value == null || !Number.isFinite(value)) {
    return '-'
  }

  if (value < 1000) {
    return `${Math.max(value, 0)} ms`
  }

  return `${(value / 1000).toFixed(2)} s`
}

const sumDurations = (...values: Array<number | null | undefined>) => {
  const validValues = values.filter((value): value is number => value != null && Number.isFinite(value))
  if (!validValues.length) {
    return null
  }

  return validValues.reduce((total, value) => total + value, 0)
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
    await ElMessageBox.confirm(`确认删除「${resume.originalFilename}」吗？`, '删除简历', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
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
  ElMessage.success('已确认当前解析结果')
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
  <main class="resume-page">
    <section class="resume-shell">
      <header class="resume-header">
        <div>
          <h1 class="resume-title">我的简历</h1>
          <p class="resume-subtitle">上传 PDF、DOC 或 DOCX 简历，并查看已上传记录。</p>
        </div>
        <el-space>
          <el-button @click="router.push('/')">返回工作台</el-button>
          <el-button type="primary" @click="router.push('/history')">AI 历史</el-button>
        </el-space>
      </header>

      <section class="resume-upload-panel">
        <div>
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
            <el-button v-if="isActiveAsyncTaskRunning" size="small" @click="clearAsyncTaskState">停止轮询</el-button>
          </el-space>
        </header>
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

      <el-table
        v-loading="loading"
        :data="resumes"
        class="resume-table"
        empty-text="暂无简历"
        row-key="id"
        :row-class-name="resolveResumeRowClass"
      >
        <el-table-column prop="originalFilename" label="文件名" min-width="220" />
        <el-table-column prop="fileType" label="类型" width="100" />
        <el-table-column label="大小" width="130">
          <template #default="{ row }: { row: ResumeListItem }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="uploadStatus" label="状态" width="120" />
        <el-table-column label="上传时间" width="190">
          <template #default="{ row }: { row: ResumeListItem }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="520" fixed="right">
          <template #default="{ row }: { row: ResumeListItem }">
            <div class="resume-actions">
              <el-button
                size="small"
                type="primary"
                :disabled="isRowBusy(row.id)"
                :loading="parsingResumeId === row.id"
                @click="handleParse(row)"
              >
                开始解析
              </el-button>
              <el-button
                size="small"
                :disabled="isRowBusy(row.id)"
                :loading="loadingParseResult && activeResume?.id === row.id"
                @click="loadParseResult(row)"
              >
                查看结果
              </el-button>
              <el-button
                size="small"
                type="success"
                :disabled="isRowBusy(row.id)"
                :loading="analyzingResumeId === row.id"
                @click="handleAiAnalysis(row)"
              >
                简历诊断
              </el-button>
              <el-button
                size="small"
                :disabled="isRowBusy(row.id)"
                :loading="loadingAiAnalysis && activeResume?.id === row.id"
                @click="loadAiAnalysis(row)"
              >
                查看诊断
              </el-button>
              <el-button
                size="small"
                :disabled="isRowBusy(row.id)"
                :loading="embeddingResumeId === row.id"
                @click="handleEmbedding(row)"
              >
                生成向量
              </el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="isRowBusy(row.id) && deletingResumeId !== row.id"
                :loading="deletingResumeId === row.id"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <section v-if="activeResume && activePanel === null" class="resume-detail-panel">
        <header class="resume-parse-header">
          <div>
            <h2 class="resume-section-title">简历详情</h2>
            <p class="resume-section-subtitle">{{ activeResume.originalFilename }}</p>
          </div>
          <el-tag>{{ activeResume.uploadStatus }}</el-tag>
        </header>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文件名">{{ activeResume.originalFilename }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ activeResume.fileType }}</el-descriptions-item>
          <el-descriptions-item label="大小">{{ formatFileSize(activeResume.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ formatDateTime(activeResume.createdAt) }}</el-descriptions-item>
        </el-descriptions>
        <div class="resume-detail-actions">
          <el-button type="primary" @click="loadParseResult(activeResume)">查看解析</el-button>
          <el-button type="success" @click="loadAiAnalysis(activeResume)">查看简历诊断</el-button>
          <el-button @click="router.push('/job-descriptions')">选择目标岗位</el-button>
        </div>
      </section>

      <section
        v-if="activeResume && parseResult && activePanel === 'parse'"
        v-loading="loadingParseResult"
        class="resume-parse-panel"
      >
        <header class="resume-parse-header">
          <div>
            <h2 class="resume-section-title">解析结果</h2>
            <p class="resume-section-subtitle">{{ activeResume.originalFilename }}</p>
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
              确认解析结果
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
              <el-descriptions-item label="解析器版本">{{ parserVersionText }}</el-descriptions-item>
              <el-descriptions-item label="AI 状态">
                <el-tag :type="aiParseSummary.status === 'FALLBACK' ? 'warning' : aiParseSummary.used ? 'success' : 'info'">
                  {{ aiParseSummary.text }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="AI 应用结果">
                <el-tag :type="aiParseSummary.applied ? 'success' : 'info'">{{ aiParseSummary.appliedText }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="AI 失败降级">
                <el-tag :type="aiParseSummary.downgraded ? 'warning' : 'info'">{{ aiParseSummary.downgradeText }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="fallbackOccurred">
                <el-tag :type="aiParseSummary.fallbackOccurred ? 'warning' : 'info'">
                  {{ aiParseSummary.fallbackOccurredText }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="AI 原因">
                {{ aiParseSummary.reason || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="总耗时">
                {{ aiParseSummary.totalDurationText }}
              </el-descriptions-item>
              <el-descriptions-item label="AI 总耗时">
                {{ aiParseSummary.aiDurationText }}
              </el-descriptions-item>
              <el-descriptions-item label="AI 分类耗时">
                {{ structuredContent?.aiSectionClassifyDurationMs != null ? `${structuredContent.aiSectionClassifyDurationMs} ms` : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="AI 结构化耗时">
                {{ structuredContent?.aiStructuredParseDurationMs != null ? `${structuredContent.aiStructuredParseDurationMs} ms` : '-' }}
              </el-descriptions-item>
              <el-descriptions-item v-if="aiParseSummary.showCache" label="AI 缓存">
                <el-tag :type="aiParseSummary.cacheHit ? 'success' : 'info'">
                  {{ aiParseSummary.cacheText }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-else label="AI 缓存">
                {{ aiParseSummary.cacheText }}
              </el-descriptions-item>
              <el-descriptions-item label="展示模型">
                <el-tag :type="displayModelSummary.tagType">
                  {{ displayModelSummary.text }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="展示缓存">
                {{ displayModelSummary.cacheText }}
              </el-descriptions-item>
              <el-descriptions-item label="展示耗时">
                {{ displayModelSummary.durationText }}
              </el-descriptions-item>
              <el-descriptions-item v-if="displayModelSummary.errorMessage" label="展示降级原因">
                {{ displayModelSummary.errorMessage }}
              </el-descriptions-item>
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

          <section class="resume-structured-section">
            <h3 class="resume-block-title">调试信息</h3>
            <el-collapse v-model="debugCollapseActive">
              <el-collapse-item title="查看原文" name="raw">
                <el-tabs class="resume-debug-tabs">
                  <el-tab-pane label="原始提取文本">
                    <pre class="resume-raw-text">{{ parseResult.extractedText || '-' }}</pre>
                  </el-tab-pane>
                  <el-tab-pane label="清洗后文本">
                    <pre class="resume-raw-text">{{ parseResult.cleanedText || '-' }}</pre>
                  </el-tab-pane>
                </el-tabs>
              </el-collapse-item>
              <el-collapse-item title="查看调试信息" name="debug">
                <h4 class="resume-debug-title">AI 与规则冲突</h4>
                <div v-if="aiConflictWarnings.length" class="resume-tag-list">
                  <el-tag
                    v-for="warning in aiConflictWarnings"
                    :key="warning"
                    type="warning"
                  >
                    {{ resolveWarningText(warning) }}
                  </el-tag>
                </div>
                <el-empty v-else description="暂无 AI 与规则冲突" :image-size="48" />

                <h4 class="resume-debug-title">基础信息证据</h4>
                <el-table :data="basicInfoDebugRows" size="small" border empty-text="暂无基础信息调试详情">
                  <el-table-column prop="field" label="字段" width="120" />
                  <el-table-column prop="value" label="值" width="160" />
                  <el-table-column prop="confidence" label="置信度" width="90" />
                  <el-table-column prop="source" label="来源" width="100" />
                  <el-table-column label="状态" width="120">
                    <template #default="{ row }">
                      <el-tag size="small" :type="resolveDebugStatusType(row.status)">
                        {{ row.status }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="evidence" label="证据" min-width="220" />
                  <el-table-column prop="rejectReason" label="拒绝原因" min-width="180" />
                </el-table>

                <h4 class="resume-debug-title">文本块</h4>
                <el-table :data="debugBlocks" size="small" border empty-text="暂无文本块">
                  <el-table-column prop="originalIndex" label="原始序号" width="100" />
                  <el-table-column prop="displayOrder" label="展示序号" width="100" />
                  <el-table-column prop="sourceSection" label="来源章节" width="160" />
                  <el-table-column prop="sourceSectionConfidence" label="来源置信度" width="120" />
                  <el-table-column prop="finalSection" label="最终章节" width="160" />
                  <el-table-column prop="finalSectionSource" label="最终来源" width="150" />
                  <el-table-column label="锁定" width="90">
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.sectionLocked ? 'success' : 'info'">
                        {{ row.sectionLocked ? '是' : '否' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="heading" label="标题" width="180" />
                  <el-table-column prop="text" label="文本" min-width="260" />
                </el-table>

                <h4 class="resume-debug-title">AI 分类结果</h4>
                <el-table :data="aiClassifiedBlocks" size="small" border empty-text="暂无 AI 分类结果">
                  <el-table-column prop="originalIndex" label="原始序号" width="100" />
                  <el-table-column prop="displayOrder" label="展示序号" width="100" />
                  <el-table-column prop="sourceSection" label="来源章节" width="160" />
                  <el-table-column prop="sourceSectionConfidence" label="来源置信度" width="120" />
                  <el-table-column prop="finalSection" label="最终章节" width="160" />
                  <el-table-column prop="finalSectionSource" label="最终来源" width="150" />
                  <el-table-column label="锁定" width="90">
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.sectionLocked ? 'success' : 'info'">
                        {{ row.sectionLocked ? '是' : '否' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="text" label="文本" min-width="260" />
                </el-table>

                <h4 class="resume-debug-title">原始章节</h4>
                <div v-if="rawSectionDebugSections.length" class="resume-section-list">
                  <article v-for="section in rawSectionDebugSections" :key="section.key" class="resume-section-card">
                    <div class="resume-section-card-header">
                      <strong>{{ section.title }}</strong>
                      <el-space>
                        <el-tag size="small">{{ section.normalizedSection }}</el-tag>
                        <el-tag size="small" type="info">{{ section.source }}</el-tag>
                        <el-tag size="small" type="success">{{ section.confidence }}</el-tag>
                      </el-space>
                    </div>
                    <ul v-if="hasStructuredList(section.lines)" class="resume-text-list resume-section-lines">
                      <li v-for="line in section.lines" :key="line">{{ line }}</li>
                    </ul>
                    <el-empty v-else description="暂无原始章节内容" :image-size="64" />
                  </article>
                </div>
                <el-empty v-else description="暂无原始章节" :image-size="72" />

                <h4 class="resume-debug-title">Indexed Lines</h4>
                <el-table :data="indexedLineDebugRows" size="small" border empty-text="暂无 Indexed Lines">
                  <el-table-column prop="lineId" label="lineId" width="90" />
                  <el-table-column prop="sectionHint" label="章节提示" width="150" />
                  <el-table-column prop="rawSectionId" label="rawSectionId" width="150" />
                  <el-table-column prop="sourceType" label="来源类型" width="120" />
                  <el-table-column prop="noise" label="噪声" width="80" />
                  <el-table-column prop="text" label="原文" min-width="260" />
                </el-table>

                <h4 class="resume-debug-title">章节识别</h4>
                <div v-if="hasSections(sectionResult)" class="resume-section-list">
                  <article v-for="section in sectionResult" :key="`${section.sectionType}-${section.heading}`" class="resume-section-card">
                    <div class="resume-section-card-header">
                      <strong>{{ section.heading || section.sectionType }}</strong>
                      <el-tag size="small">{{ section.sectionType }}</el-tag>
                    </div>
                    <ul v-if="hasStructuredList(section.lines)" class="resume-text-list resume-section-lines">
                      <li v-for="line in section.lines" :key="line">{{ line }}</li>
                    </ul>
                    <el-empty v-else description="暂无章节内容" :image-size="64" />
                  </article>
                </div>
                <el-empty v-else description="暂无章节识别结果" :image-size="72" />
              </el-collapse-item>
            </el-collapse>
          </section>
        </div>

      </section>

      <section
        v-if="activeResume && aiAnalysis && (activePanel === 'parse' || activePanel === 'analysis')"
        v-loading="loadingAiAnalysis"
        class="resume-ai-section"
      >
        <header class="resume-ai-header">
          <div>
            <h3 class="resume-block-title">简历诊断</h3>
            <p class="resume-ai-meta">
              {{ aiAnalysis.modelName || '-' }} · {{ formatDateTime(aiAnalysis.updatedAt || '') }}
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

          <div class="resume-score-row">
            <el-progress
              class="resume-score-progress"
              type="dashboard"
              :percentage="aiAnalysis.score ?? 0"
              :stroke-width="8"
              :width="132"
              color="#2563eb"
            />
            <el-descriptions :column="1" border class="resume-score-detail">
              <el-descriptions-item label="综合评分">{{ aiAnalysis.score ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="Prompt 版本">{{ aiAnalysis.promptVersion || '-' }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDateTime(aiAnalysis.updatedAt || '') }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <section class="resume-structured-grid">
            <div>
              <h3 class="resume-block-title">简历优势</h3>
              <ul v-if="hasStructuredList(aiAnalysis.strengths)" class="resume-text-list">
                <li v-for="item in aiAnalysis.strengths" :key="item">{{ item }}</li>
              </ul>
              <el-empty v-else description="暂无优势信息" :image-size="72" />
            </div>

            <div>
              <h3 class="resume-block-title">主要问题</h3>
              <ul v-if="hasStructuredList(aiAnalysis.problems)" class="resume-text-list">
                <li v-for="item in aiAnalysis.problems" :key="item">{{ item }}</li>
              </ul>
              <el-empty v-else description="暂无问题信息" :image-size="72" />
            </div>

            <div>
              <h3 class="resume-block-title">建议摘要</h3>
              <ul v-if="hasStructuredList(aiAnalysis.suggestionsSummary)" class="resume-text-list">
                <li v-for="item in aiAnalysis.suggestionsSummary" :key="item">{{ item }}</li>
              </ul>
              <el-empty v-else description="暂无建议摘要" :image-size="72" />
            </div>
          </section>
        </template>
      </section>
    </section>
  </main>
</template>

<style scoped>
.resume-page {
  min-height: 100vh;
  padding: 40px 28px 56px;
  background: #f4f7fb;
}

.resume-shell {
  width: min(100%, 1280px);
  margin: 0 auto;
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
  color: #111827;
  font-size: 28px;
  font-weight: 700;
}

.resume-subtitle {
  margin: 8px 0 0;
  color: #667085;
  font-size: 15px;
  line-height: 1.7;
}

.resume-upload-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  padding: 24px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.resume-upload-tip {
  margin-top: 8px;
  color: #667085;
  font-size: 13px;
}

.resume-upload-progress {
  margin: 10px 0 0;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
}

.resume-task-panel {
  display: grid;
  gap: 14px;
  margin-bottom: 20px;
  padding: 20px 24px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.resume-task-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.resume-task-title {
  margin: 0;
  color: #111827;
  font-size: 17px;
  font-weight: 700;
}

.resume-task-subtitle {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.resume-task-alert {
  margin-top: 2px;
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
  color: #344054;
  font-size: 13px;
  white-space: nowrap;
}

.resume-parse-mode-select {
  width: 180px;
}

.resume-table {
  border: 1px solid #dde5f0;
  border-radius: 8px;
}

.resume-actions {
  display: flex;
  gap: 8px;
}

:deep(.resume-row-dragging) {
  opacity: 0.55;
}

:deep(.resume-row-drop-target td) {
  background: #eef6ff !important;
}

.resume-detail-panel {
  margin-top: 24px;
  padding: 28px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
}

.resume-detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.resume-parse-panel {
  margin-top: 24px;
  padding: 28px;
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
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
  color: #111827;
  font-size: 20px;
  font-weight: 700;
}

.resume-section-subtitle {
  margin: 6px 0 0;
  color: #667085;
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
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #ffffff;
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
  color: #667085;
  font-size: 13px;
}

.resume-ai-warning {
  margin-bottom: 16px;
}

.resume-score-row {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
  margin-top: 20px;
}

.resume-score-detail {
  min-width: 0;
}

.resume-score-progress {
  display: block;
  width: 132px;
  height: 132px;
  justify-self: center;
}

.resume-score-progress :deep(.el-progress-circle) {
  width: 132px !important;
  height: 132px !important;
}

.resume-score-progress :deep(.el-progress__text) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 132px;
  height: 132px;
  margin: 0;
  top: 0;
  left: 0;
  line-height: 1;
  transform: none;
}

.resume-structured-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 20px;
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
  border-left: 3px solid #d8e3f3;
  border-radius: 8px;
  background: #f8fbff;
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
  color: #111827;
  line-height: 1.5;
}

.resume-card-heading span {
  flex: 0 0 auto;
  color: #667085;
  font-size: 13px;
}

.resume-card-placeholder {
  color: #667085;
  font-weight: 600;
}

.resume-inline-link {
  margin-top: 4px;
  padding: 0;
}

.resume-card-line {
  margin: 0 0 8px;
  color: #344054;
  font-size: 14px;
  line-height: 1.6;
}

.resume-compact-list {
  margin: 10px 0 0;
  padding-left: 18px;
  color: #344054;
  line-height: 1.7;
}

.resume-source-text {
  max-height: 240px;
  margin: 10px 0 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 12px;
  border-radius: 8px;
  color: #344054;
  font-size: 13px;
  line-height: 1.7;
  background: #eef4fb;
}

.resume-summary-text {
  margin: 0;
  color: #344054;
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
  color: #667085;
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
  color: #111827;
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
  border: 1px solid #dde5f0;
  border-radius: 8px;
  color: #344054;
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
  border: 1px solid #dde5f0;
  border-radius: 8px;
  background: #fbfdff;
}

.resume-section-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  color: #111827;
}

.resume-section-lines {
  min-height: 0;
}

.resume-debug-tabs {
  margin-top: 16px;
}

.resume-debug-title {
  margin: 18px 0 10px;
  color: #344054;
  font-size: 14px;
  font-weight: 700;
}

.resume-muted-text {
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.resume-empty-section-summary {
  margin: 14px 0 0;
  color: #667085;
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
  border: 1px solid #dde5f0;
  border-radius: 8px;
  color: #344054;
  font-family:
    ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    monospace;
  font-size: 13px;
  line-height: 1.7;
  background: #f8fafc;
}

@media (max-width: 640px) {
  .resume-header,
  .resume-upload-panel,
  .resume-task-header,
  .resume-parse-header {
    align-items: stretch;
    flex-direction: column;
  }

  .resume-actions {
    flex-direction: column;
  }

  .resume-ai-header,
  .resume-score-row {
    grid-template-columns: 1fr;
    align-items: stretch;
    flex-direction: column;
  }

  .resume-structured-grid {
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
