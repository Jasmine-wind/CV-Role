<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import {
  deleteResume,
  getResumeList,
  getResumeReview,
  requestResumePreparation,
  resolveResumeReview,
  updateResumeDisplayName,
  uploadResume,
} from '@/api/resume'
import type { ResumeReviewResolveRequest, ResumeReviewUnresolvedItem } from '@/api/resume'
import { startAsyncTaskPolling } from '@/utils/asyncTaskPolling'
import type { AsyncTaskPollingController } from '@/utils/asyncTaskPolling'
import type { AsyncTaskVO } from '@/types/task'
import type { ResumeListItem } from '@/types/resume'
import type { ResumeDocument } from '@/types/resume-document'
import ResumeReviewWorkspace from '@/components/resume/review/ResumeReviewWorkspace.vue'
import ResumeSourcePreview from '@/components/resume/ResumeSourcePreview.vue'
import {
  REQUIRED_RESUME_CONTACT_TYPE_OPTIONS,
  RESUME_CONTACT_TYPE_OPTIONS,
} from '@/components/resume/resumeContactPresentation'
import {
  getInitialReviewItemId,
  selectReviewItemAfterResolve,
} from './resumeReviewPresentation'
import type {
  ReviewDraftContact,
  ReviewDraftEntry,
  ReviewDraftFragment,
  ReviewItemState,
} from './resumeReviewPresentation'
import {
  canRetryResumePreparation,
  formatResumeDate,
  formatResumeFileSize,
  getResumeLibraryStatus,
  getResumeLibrarySummary,
} from './resumeLibraryPresentation'

const router = useRouter()

const MAX_FILE_SIZE = 10 * 1024 * 1024
const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx']
const PREPARATION_TIMEOUT_MS = 5 * 60 * 1000

const resumes = ref<ResumeListItem[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const uploading = ref(false)
const uploadOpen = ref(false)
const deletingId = ref<number | null>(null)
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const uploadPanel = ref<HTMLElement | null>(null)
const activeTasks = ref<Record<number, AsyncTaskVO>>({})
const pollingControllers = new Map<number, AsyncTaskPollingController>()

const selectedFileType = computed(() => selectedFile.value?.name.split('.').pop()?.toUpperCase() || '')
const librarySummary = computed(() => getResumeLibrarySummary(resumes.value, activeTasks.value))

const statusFor = (resume: ResumeListItem) =>
  getResumeLibraryStatus(resume, activeTasks.value[resume.id])

const displayNameFor = (resume: ResumeListItem) => {
  if (resume.displayName?.trim()) return resume.displayName.trim()
  return resume.originalFilename.replace(/\.[^.]+$/u, '') || '未命名简历'
}

const canOpenSource = (resume: ResumeListItem) => statusFor(resume).kind === 'ready'

const startOptimizationFromPreview = () => {
  const resumeId = sourcePreviewId.value
  if (resumeId === null) return
  void router.push({ path: '/app', query: { resumeId: String(resumeId) } })
}

const clearSelectedFile = () => {
  selectedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}

const closeUpload = () => {
  if (uploading.value) return
  uploadOpen.value = false
  clearSelectedFile()
}

const toggleUpload = () => {
  if (uploadOpen.value) {
    closeUpload()
    return
  }
  uploadOpen.value = true
}

const focusUploadTitle = () => {
  const title = document.querySelector('#resume-upload-title') as HTMLElement | null
  title?.focus()
}

const openUploadFromEmpty = async () => {
  uploadOpen.value = true
  await nextTick()
  uploadPanel.value?.scrollIntoView({ behavior: 'auto', block: 'start' })
  focusUploadTitle()
}

const openUploadFromReview = async () => {
  await closeReview(false)
  uploadOpen.value = true
  await nextTick()
  uploadPanel.value?.scrollIntoView({ behavior: 'auto', block: 'start' })
  focusUploadTitle()
}

/** Slice A 确认面板：只展示 canonical 层候选内容，不暴露解析内部结构。 */
const reviewResumeId = ref<number | null>(null)
const reviewResume = computed(
  () => resumes.value.find((resume) => resume.id === reviewResumeId.value) ?? null,
)
const reviewLoading = ref(false)
const reviewLoadError = ref<string | null>(null)
const reviewActionError = ref<string | null>(null)
const reviewQualityStatus = ref<string | null>(null)
const reviewName = ref('')
const reviewNameMissing = ref(false)
const reviewItems = ref<ReviewItemState[]>([])
const activeReviewItemId = ref<string | null>(null)
const resolvingItemId = ref<string | null>(null)
const reviewTrigger = ref<HTMLElement | null>(null)
const lastReviewAction = ref<{ resumeId: number; itemId: string; action: 'ACCEPT' | 'DELETE' } | null>(null)

const sourcePreviewId = ref<number | null>(null)
const sourcePreviewDocument = ref<ResumeDocument | null>(null)
const sourcePreviewLoading = ref(false)
const sourcePreviewError = ref<string | null>(null)
const sourcePreviewTrigger = ref<HTMLElement | null>(null)
const displayNameEditingId = ref<number | null>(null)
const displayNameDraft = ref('')
const displayNameSavingId = ref<number | null>(null)
const sourcePreviewResume = computed(
  () => resumes.value.find((resume) => resume.id === sourcePreviewId.value) ?? null,
)

const parseDraft = <T,>(draft: string): T => {
  try {
    return JSON.parse(draft) as T
  } catch {
    return {} as T
  }
}

const buildReviewItems = (review: Awaited<ReturnType<typeof getResumeReview>>) => {
  const items = review.unresolvedItems
    ? (JSON.parse(review.unresolvedItems) as ResumeReviewUnresolvedItem[])
    : []
  return items.map((item) => {
    const contact = parseDraft<ReviewDraftContact>(item.canonicalDraft)
    const entry = parseDraft<ReviewDraftEntry>(item.canonicalDraft)
    const fragment = parseDraft<ReviewDraftFragment>(item.canonicalDraft)
    entry.bullets = Array.isArray(entry.bullets)
      ? entry.bullets.map((bullet, index) => ({
          id: bullet.id || `review-${item.id}-${index}`,
          text: bullet.text ?? '',
        }))
      : []
    return {
      item,
      contact: {
        type: contact.type ?? 'OTHER',
        label: contact.label ?? null,
        value: contact.value ?? '',
      },
      entry,
      text: fragment.text ?? '',
    }
  })
}

const updateReviewState = (
  review: Awaited<ReturnType<typeof getResumeReview>>,
  resolvedItemId?: string,
) => {
  reviewQualityStatus.value = review.qualityStatus
  const document = parseDraft<Partial<ResumeDocument>>(review.canonicalDocument ?? '{}')
  reviewName.value = document.basics?.name?.trim() ?? ''
  reviewNameMissing.value = !reviewName.value
  const previousItems = reviewItems.value
  const nextItems = buildReviewItems(review)
  reviewItems.value = nextItems
  activeReviewItemId.value = resolvedItemId
    ? selectReviewItemAfterResolve({
        previousItems,
        nextItems,
        resolvedItemId,
        previousActiveItemId: activeReviewItemId.value,
      })
    : (nextItems.some((item) => item.item.id === activeReviewItemId.value)
        ? activeReviewItemId.value
        : getInitialReviewItemId(nextItems))
}

const loadReview = async (resumeId: number, reset = false) => {
  reviewLoading.value = true
  reviewLoadError.value = null
  if (reset) {
    reviewItems.value = []
    activeReviewItemId.value = null
  }
  try {
    const review = await getResumeReview(resumeId)
    updateReviewState(review)
  } catch (error) {
    reviewLoadError.value = error instanceof Error ? error.message : '获取待确认内容失败'
    ElMessage.error(reviewLoadError.value)
  } finally {
    reviewLoading.value = false
  }
}

const openReview = async (resumeId: number) => {
  if (sourcePreviewId.value !== null) await closeSourcePreview(false)
  reviewTrigger.value = document.activeElement instanceof HTMLElement ? document.activeElement : null
  reviewResumeId.value = resumeId
  reviewLoadError.value = null
  reviewActionError.value = null
  reviewQualityStatus.value = null
  reviewName.value = ''
  reviewNameMissing.value = false
  await nextTick()
  if (window.matchMedia('(max-width: 900px)').matches) {
    document.querySelector('.resume-review-workspace')?.scrollIntoView({ behavior: 'auto', block: 'start' })
  }
  await loadReview(resumeId, true)
}

const focusReviewRow = (resumeId: number) => {
  const row = document.querySelector(`[data-resume-row="${resumeId}"]`) as HTMLElement | null
  row?.focus()
}

const clearReviewState = () => {
  reviewResumeId.value = null
  reviewItems.value = []
  activeReviewItemId.value = null
  reviewLoadError.value = null
  reviewActionError.value = null
  reviewQualityStatus.value = null
  reviewName.value = ''
  reviewNameMissing.value = false
  resolvingItemId.value = null
  lastReviewAction.value = null
}

const closeReview = async (restoreFocus = true) => {
  const resumeId = reviewResumeId.value
  const trigger = reviewTrigger.value
  clearReviewState()
  reviewTrigger.value = null
  if (restoreFocus) {
    await nextTick()
    if (trigger?.isConnected) trigger.focus()
    else if (resumeId !== null) focusReviewRow(resumeId)
  }
}

const readCanonicalDocument = (value: string | null) => {
  if (!value) return null
  try {
    return JSON.parse(value) as ResumeDocument
  } catch {
    return null
  }
}

const loadSourcePreview = async (resumeId: number) => {
  sourcePreviewLoading.value = true
  sourcePreviewError.value = null
  try {
    const review = await getResumeReview(resumeId)
    sourcePreviewDocument.value = readCanonicalDocument(review.canonicalDocument)
    if (!sourcePreviewDocument.value) sourcePreviewError.value = '已确认简历内容暂不可用，请重新准备这份简历。'
  } catch (error) {
    sourcePreviewError.value = error instanceof Error ? error.message : '获取简历预览失败'
  } finally {
    sourcePreviewLoading.value = false
  }
}

const openSourcePreview = async (resume: ResumeListItem) => {
  if (!canOpenSource(resume)) return
  sourcePreviewTrigger.value = document.activeElement instanceof HTMLElement ? document.activeElement : null
  if (reviewResumeId.value !== null) await closeReview(false)
  displayNameEditingId.value = null
  sourcePreviewId.value = resume.id
  sourcePreviewDocument.value = null
  await nextTick()
  if (window.matchMedia('(max-width: 900px)').matches) {
    document.querySelector('.resume-source-preview')?.scrollIntoView({ behavior: 'auto', block: 'start' })
  }
  await loadSourcePreview(resume.id)
}

const closeSourcePreview = async (restoreFocus = true) => {
  const resumeId = sourcePreviewId.value
  const trigger = sourcePreviewTrigger.value
  sourcePreviewId.value = null
  sourcePreviewDocument.value = null
  sourcePreviewError.value = null
  sourcePreviewTrigger.value = null
  if (restoreFocus) {
    await nextTick()
    if (trigger?.isConnected) trigger.focus()
    else if (resumeId !== null) focusReviewRow(resumeId)
  }
}

const startDisplayNameEdit = (resume: ResumeListItem) => {
  displayNameEditingId.value = resume.id
  displayNameDraft.value = displayNameFor(resume)
}

const cancelDisplayNameEdit = () => {
  displayNameEditingId.value = null
  displayNameDraft.value = ''
}

const saveDisplayName = async (resume: ResumeListItem) => {
  const displayName = displayNameDraft.value.trim()
  if (!displayName || displayNameSavingId.value === resume.id) return
  displayNameSavingId.value = resume.id
  try {
    const updated = await updateResumeDisplayName(resume.id, { displayName })
    const index = resumes.value.findIndex((item) => item.id === resume.id)
    const current = resumes.value[index]
    if (current) resumes.value[index] = { ...current, displayName: updated.displayName }
    cancelDisplayNameEdit()
    ElMessage.success('简历名称已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历名称更新失败')
  } finally {
    displayNameSavingId.value = null
  }
}

const finishReview = async (resumeId: number) => {
  await closeReview(false)
  await loadResumes()
  await nextTick()
  focusReviewRow(resumeId)
}

const applyReview = async (resumeId: number, payload: ResumeReviewResolveRequest) => {
  resolvingItemId.value = payload.itemId
  reviewActionError.value = null
  try {
    const review = await resolveResumeReview(resumeId, {
      ...payload,
      ...(reviewName.value.trim() ? { name: reviewName.value.trim() } : {}),
    })
    lastReviewAction.value = null
    updateReviewState(review, payload.itemId)
    if (review.qualityStatus === 'READY' && reviewItems.value.length === 0) {
      ElMessage.success('确认完成，简历已可用于岗位分析')
      await finishReview(resumeId)
    }
  } catch (error) {
    reviewActionError.value = error instanceof Error ? error.message : '确认操作失败'
    ElMessage.error(reviewActionError.value)
  } finally {
    resolvingItemId.value = null
  }
}

const editableEntryPayload = (entry: ReviewDraftEntry): ResumeReviewResolveRequest['entry'] => {
  const fields = { ...entry }
  delete fields.kind
  return fields
}

const acceptReviewItem = (resumeId: number, state: ReviewItemState) => {
  lastReviewAction.value = { resumeId, itemId: state.item.id, action: 'ACCEPT' }
  if (state.item.kind === 'CONTACT_CANDIDATE' || state.item.kind === 'REQUIRED_CONTACT_CANDIDATE') {
    void applyReview(resumeId, {
      itemId: state.item.id,
      action: 'ACCEPT',
      contactType: state.contact.type,
      contactValue: state.contact.value,
    })
    return
  }
  if (state.item.kind === 'NAME_CANDIDATE') {
    void applyReview(resumeId, {
      itemId: state.item.id,
      action: 'ACCEPT',
      name: state.text,
    })
    return
  }
  if (state.item.kind === 'TEXT_FRAGMENT') {
    void applyReview(resumeId, {
      itemId: state.item.id,
      action: 'ACCEPT',
      text: state.text,
    })
    return
  }
  if (state.item.kind === 'ENTRY_CANDIDATE') {
    void applyReview(resumeId, {
      itemId: state.item.id,
      action: 'ACCEPT',
      entry: editableEntryPayload(state.entry),
    })
    return
  }
  void applyReview(resumeId, { itemId: state.item.id, action: 'ACCEPT' })
}

const deleteReviewItem = (resumeId: number, state: ReviewItemState) => {
  lastReviewAction.value = { resumeId, itemId: state.item.id, action: 'DELETE' }
  void applyReview(resumeId, { itemId: state.item.id, action: 'DELETE' })
}

const retryReviewAction = () => {
  const action = lastReviewAction.value
  const state = reviewItems.value.find((item) => item.item.id === action?.itemId)
  if (!action || !state || resolvingItemId.value) return
  if (action.action === 'DELETE') deleteReviewItem(action.resumeId, state)
  else acceptReviewItem(action.resumeId, state)
}

const selectReviewItem = (itemId: string) => {
  if (resolvingItemId.value || !reviewItems.value.some((item) => item.item.id === itemId)) return
  activeReviewItemId.value = itemId
  reviewActionError.value = null
}

const moveReviewItem = (offset: number) => {
  if (resolvingItemId.value) return
  const index = reviewItems.value.findIndex((item) => item.item.id === activeReviewItemId.value)
  const nextIndex = Math.min(Math.max(index + offset, 0), reviewItems.value.length - 1)
  const next = reviewItems.value[nextIndex]
  if (next) selectReviewItem(next.item.id)
}

const updateReviewItem = (nextState: ReviewItemState) => {
  const index = reviewItems.value.findIndex((item) => item.item.id === nextState.item.id)
  if (index >= 0) reviewItems.value[index] = nextState
}

const acceptActiveReviewItem = () => {
  const state = reviewItems.value.find((item) => item.item.id === activeReviewItemId.value)
  if (reviewResumeId.value !== null && state && !resolvingItemId.value) {
    acceptReviewItem(reviewResumeId.value, state)
  }
}

const deleteActiveReviewItem = () => {
  const state = reviewItems.value.find((item) => item.item.id === activeReviewItemId.value)
  if (reviewResumeId.value !== null && state && !resolvingItemId.value) {
    deleteReviewItem(reviewResumeId.value, state)
  }
}

const loadResumes = async () => {
  loading.value = true
  loadFailed.value = false
  try {
    resumes.value = await getResumeList()
  } catch {
    // 区分真正的加载失败与空数据：失败提供重试，而不是当成没有简历。
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

const setActiveTask = (resumeId: number, task: AsyncTaskVO | null) => {
  const next = { ...activeTasks.value }
  if (task) {
    next[resumeId] = task
  } else {
    delete next[resumeId]
  }
  activeTasks.value = next
}

const startPreparationPolling = (resumeId: number, taskId: number, initialTask?: AsyncTaskVO) => {
  pollingControllers.get(resumeId)?.stop()
  if (initialTask) {
    setActiveTask(resumeId, initialTask)
  }

  const controller = startAsyncTaskPolling({
    taskId,
    timeoutMs: PREPARATION_TIMEOUT_MS,
    onUpdate: (task) => setActiveTask(resumeId, task),
    onSuccess: async () => {
      pollingControllers.delete(resumeId)
      setActiveTask(resumeId, null)
      await loadResumes()
      ElMessage.success('简历已准备好')
    },
    onFailed: async (task) => {
      pollingControllers.delete(resumeId)
      setActiveTask(resumeId, null)
      await loadResumes()
      ElMessage.error(task.errorMessage || '简历准备失败，可稍后重试')
    },
    onCancelled: async () => {
      pollingControllers.delete(resumeId)
      setActiveTask(resumeId, null)
      await loadResumes()
    },
    onTimeout: async () => {
      pollingControllers.delete(resumeId)
      setActiveTask(resumeId, null)
      await loadResumes()
      ElMessage.warning('简历仍在后台准备，可稍后回来查看')
    },
    onError: (error) => {
      pollingControllers.delete(resumeId)
      setActiveTask(resumeId, null)
      ElMessage.error(error instanceof Error ? error.message : '获取简历准备状态失败')
    },
  })
  pollingControllers.set(resumeId, controller)
}

const prepareResume = async (resume: ResumeListItem, silent = false) => {
  if (activeTasks.value[resume.id]) {
    return true
  }
  try {
    const task = await requestResumePreparation(resume.id)
    startPreparationPolling(resume.id, task.taskId, task)
    if (!silent) {
      ElMessage.success('正在后台准备简历')
    }
    return true
  } catch (error) {
    if (!silent) {
      ElMessage.error(error instanceof Error ? error.message : '简历准备启动失败')
    }
    return false
  }
}

const validateFile = (file: File) => {
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
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

const handleFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  selectedFile.value = file && validateFile(file) ? file : null
  if (!selectedFile.value) {
    input.value = ''
  }
}

const handleUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择简历文件')
    return
  }

  uploading.value = true
  try {
    const uploaded = await uploadResume(selectedFile.value)
    selectedFile.value = null
    if (fileInput.value) {
      fileInput.value.value = ''
    }
    await loadResumes()
    uploadOpen.value = false
    let preparationStarted = Boolean(uploaded.preparationTaskId)
    if (uploaded.preparationTaskId) {
      startPreparationPolling(uploaded.id, uploaded.preparationTaskId)
    } else {
      const uploadedResume = resumes.value.find((resume) => resume.id === uploaded.id)
      preparationStarted = uploadedResume ? await prepareResume(uploadedResume, true) : false
    }
    if (preparationStarted) {
      ElMessage.success('上传成功，正在后台准备简历')
    } else {
      ElMessage.warning('简历已上传，但暂时无法准备，请点击“重试”')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历上传失败')
  } finally {
    uploading.value = false
  }
}

const handleDelete = async (resume: ResumeListItem) => {
  try {
    await ElMessageBox.confirm(
      `确认删除「${displayNameFor(resume)}」吗？相关分析结果也会删除。`,
      '删除简历',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deletingId.value = resume.id
  try {
    pollingControllers.get(resume.id)?.stop()
    pollingControllers.delete(resume.id)
    await deleteResume(resume.id)
    await loadResumes()
    ElMessage.success('简历已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除简历失败')
  } finally {
    deletingId.value = null
  }
}

onMounted(async () => {
  await loadResumes()
  for (const resume of resumes.value.filter(
    (item) => item.parseStatus === 'PENDING' || item.qualityStatus === 'PENDING',
  )) {
    void prepareResume(resume, true)
  }
})

onUnmounted(() => {
  pollingControllers.forEach((controller) => controller.stop())
  pollingControllers.clear()
})
</script>

<template>
  <section class="resume-page resume-library-page">
    <PageHeader
      title="我的简历"
      description="保存用于岗位定向的真实材料。上传后系统会自动准备；无法确定的内容会交给你确认。"
    >
      <template #actions>
        <el-button
          :plain="uploadOpen"
          :type="uploadOpen ? 'default' : 'primary'"
          :aria-expanded="uploadOpen"
          aria-controls="resume-upload-panel"
          @click="toggleUpload"
        >
          {{ uploadOpen ? '取消上传' : '上传简历' }}
        </el-button>
      </template>
    </PageHeader>

    <section
      v-if="uploadOpen"
      id="resume-upload-panel"
      ref="uploadPanel"
      class="resume-upload-panel"
      aria-labelledby="resume-upload-title"
    >
      <header class="resume-upload-header">
        <div>
          <span class="resume-section-label">新增简历</span>
          <h2 id="resume-upload-title" tabindex="-1">上传一份真实简历</h2>
          <p>
            支持 PDF、DOC、DOCX，单份最大 10 MB。上传后系统会自动准备；无法确认的内容会交给你核对。
          </p>
        </div>
        <button type="button" class="resume-upload-cancel" :disabled="uploading" @click="closeUpload">
          取消上传
        </button>
      </header>

      <label class="resume-file-picker" :class="{ 'has-file': selectedFile }">
        <span class="resume-file-picker-label">{{ selectedFile ? '重新选择' : '选择简历文件' }}</span>
        <strong v-if="selectedFile" :title="selectedFile.name">{{ selectedFile.name }}</strong>
        <strong v-else>点击选择一份简历</strong>
        <small>
          {{ selectedFile ? `${selectedFileType} · ${formatResumeFileSize(selectedFile.size)}` : 'PDF、DOC、DOCX · 最大 10 MB' }}
        </small>
        <input
          ref="fileInput"
          type="file"
          accept=".pdf,.doc,.docx"
          :disabled="uploading"
          @change="handleFileChange"
        />
      </label>

      <div v-if="selectedFile" class="resume-selected-file" aria-live="polite">
        <span>文件已准备上传</span>
        <button type="button" :disabled="uploading" @click="clearSelectedFile">移除</button>
      </div>

      <div class="resume-upload-actions">
        <button type="button" class="resume-upload-secondary" :disabled="uploading" @click="closeUpload">
          取消
        </button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!selectedFile"
          @click="handleUpload"
        >
          {{ uploading ? '正在上传…' : '上传并准备' }}
        </el-button>
      </div>
    </section>

    <div v-if="loading && resumes.length" class="resume-library-refreshing" role="status">
      <span class="resume-loading-indicator" aria-hidden="true" /> 正在更新简历库…
    </div>

    <section v-if="loading && !resumes.length" class="resume-library-loading" aria-label="正在加载简历列表">
      <div class="resume-library-loading-heading"><SkeletonBlock title :rows="1" compact /></div>
      <div v-for="index in 4" :key="index" class="resume-library-loading-row">
        <SkeletonBlock :rows="2" compact />
      </div>
    </section>

    <ErrorState
      v-else-if="loadFailed"
      title="简历列表加载失败"
      description="暂时无法读取你的简历列表，这不影响已保存的简历。"
      action-text="重新加载"
      @action="loadResumes()"
    />

    <section
      v-else-if="resumes.length"
      class="resume-library-shell"
      :class="{ 'has-review-inspector': reviewResumeId && reviewResume, 'has-source-preview': sourcePreviewId && sourcePreviewResume }"
    >
      <div class="resume-library-main">
        <header class="resume-library-heading">
          <p class="resume-library-summary" aria-live="polite">共 {{ librarySummary.total }} 份</p>
        </header>

        <div class="resume-library-column-head" role="row" aria-hidden="true">
          <span>简历文件</span>
          <span>当前状态</span>
          <span>添加时间</span>
          <span>操作</span>
        </div>
        <div class="resume-library-list" role="list" aria-label="简历库">
          <article
            v-for="resume in resumes"
            :key="resume.id"
            class="resume-library-row"
            :class="{ 'is-reviewing': reviewResumeId === resume.id }"
            :data-resume-row="resume.id"
            role="listitem"
            tabindex="-1"
            :aria-current="reviewResumeId === resume.id ? 'true' : undefined"
          >
            <div class="resume-simple-file">
              <span class="resume-file-type" aria-hidden="true">{{ resume.fileType }}</span>
              <div>
                <template v-if="displayNameEditingId === resume.id">
                  <input
                    v-model="displayNameDraft"
                    class="resume-name-input"
                    maxlength="255"
                    aria-label="简历管理名称"
                    @keydown.enter.prevent="saveDisplayName(resume)"
                    @keydown.esc="cancelDisplayNameEdit"
                  />
                  <span class="resume-name-edit-actions">
                    <button
                      type="button"
                      :disabled="displayNameSavingId === resume.id"
                      @click="saveDisplayName(resume)"
                    >保存</button>
                    <button type="button" :disabled="displayNameSavingId === resume.id" @click="cancelDisplayNameEdit">取消</button>
                  </span>
                </template>
                <template v-else>
                  <div class="resume-name-line">
                    <button
                      type="button"
                      class="resume-source-trigger"
                      :disabled="!canOpenSource(resume)"
                      :title="canOpenSource(resume) ? `查看${displayNameFor(resume)}的已确认内容` : statusFor(resume).description"
                      @click="openSourcePreview(resume)"
                    >
                      <strong>{{ displayNameFor(resume) }}</strong>
                      <small>原始文件：{{ resume.originalFilename }} · {{ formatResumeFileSize(resume.fileSize) }}</small>
                    </button>
                    <button
                      type="button"
                      class="resume-rename-button"
                      :aria-label="`重命名 ${displayNameFor(resume)}`"
                      @click="startDisplayNameEdit(resume)"
                    >重命名</button>
                  </div>
                </template>
              </div>
            </div>
            <div class="resume-library-status">
              <div class="resume-status-line">
                <span class="resume-status-dot" :class="`is-${statusFor(resume).tone}`" aria-hidden="true" />
                <strong>{{ reviewResumeId === resume.id ? '正在确认' : statusFor(resume).label }}</strong>
              </div>
              <p :title="reviewResumeId === resume.id ? '当前确认工作区已打开。' : statusFor(resume).description">
                {{ reviewResumeId === resume.id ? '当前确认工作区已打开。' : statusFor(resume).description }}
              </p>
            </div>
            <time class="resume-library-date" :datetime="resume.createdAt">
              {{ formatResumeDate(resume.createdAt) }}
            </time>
            <div class="resume-simple-actions">
              <el-button
                v-if="statusFor(resume).primaryAction === 'review' && reviewResumeId !== resume.id"
                type="warning"
                plain
                :aria-label="`确认 ${resume.originalFilename} 的内容`"
                @click="openReview(resume.id)"
              >
                确认内容 →
              </el-button>
              <el-button
                v-if="canRetryResumePreparation(resume, activeTasks[resume.id])"
                plain
                :aria-label="`${statusFor(resume).primaryAction === 'prepare' ? '重新准备' : '重试'} ${resume.originalFilename}`"
                @click="prepareResume(resume)"
              >
                {{ statusFor(resume).primaryAction === 'prepare' ? '重新准备' : '重试' }}
              </el-button>
              <el-button
                class="resume-delete-button"
                type="danger"
                text
                :aria-label="`删除 ${displayNameFor(resume)}`"
                :loading="deletingId === resume.id"
                :disabled="!statusFor(resume).canDelete || Boolean(activeTasks[resume.id])"
                @click="handleDelete(resume)"
              >
                删除
              </el-button>
            </div>
          </article>
        </div>
      </div>

      <ResumeSourcePreview
        v-if="sourcePreviewId && sourcePreviewResume"
        class="resume-source-preview-inspector"
        :document="sourcePreviewDocument"
        :display-name="displayNameFor(sourcePreviewResume)"
        :filename="sourcePreviewResume.originalFilename"
        :loading="sourcePreviewLoading"
        :error="sourcePreviewError"
        @close="closeSourcePreview()"
        @retry="loadSourcePreview(sourcePreviewResume.id)"
        @start-optimization="startOptimizationFromPreview"
      />

      <ResumeReviewWorkspace
        v-if="reviewResumeId && reviewResume"
        class="resume-review-inspector"
        :filename="reviewResume.originalFilename"
        :loading="reviewLoading"
        :load-error="reviewLoadError"
        :action-error="reviewActionError"
        :quality-status="reviewQualityStatus"
        :review-name="reviewName"
        :review-name-missing="reviewNameMissing"
        :items="reviewItems"
        :active-item-id="activeReviewItemId"
        :resolving-item-id="resolvingItemId"
        :contact-type-options="RESUME_CONTACT_TYPE_OPTIONS"
        :required-contact-type-options="REQUIRED_RESUME_CONTACT_TYPE_OPTIONS"
        @close="closeReview()"
        @retry-load="reviewResumeId && loadReview(reviewResumeId)"
        @retry-action="retryReviewAction"
        @upload-replacement="openUploadFromReview"
        @back-to-library="closeReview()"
        @select-item="selectReviewItem"
        @previous-item="moveReviewItem(-1)"
        @next-item="moveReviewItem(1)"
        @accept="acceptActiveReviewItem"
        @reject="deleteActiveReviewItem"
        @update:review-name="reviewName = $event"
        @update:state="updateReviewItem"
      />
    </section>

    <EmptyState
      v-else
      title="还没有简历"
      description="上传一份真实简历。系统会先准备内容，无法确定的部分会交给你确认。"
      action-text="上传第一份简历"
      @action="openUploadFromEmpty"
    />
  </section>
</template>

<style scoped>
.resume-library-page {
  display: grid;
  gap: var(--app-section-spacing);
}

.resume-section-label {
  display: block;
  margin-bottom: var(--app-space-2);
  color: var(--app-primary);
  font-family: var(--app-font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.resume-upload-panel {
  display: grid;
  gap: var(--app-space-5);
  border: 1px solid var(--app-border-strong);
  border-radius: var(--app-radius-lg);
  padding: var(--app-space-6);
  background: var(--app-surface);
}

.resume-upload-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-6);
}

.resume-upload-header h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
  line-height: var(--app-line-height-tight);
}

.resume-upload-header p {
  max-width: 72ch;
  margin: var(--app-space-2) 0 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.resume-upload-cancel,
.resume-upload-secondary,
.resume-selected-file button {
  border: 0;
  border-bottom: 1px solid var(--app-border-strong);
  padding: 4px 0;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: var(--app-font-size-sm);
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.resume-upload-cancel:hover,
.resume-upload-cancel:focus-visible,
.resume-upload-secondary:hover,
.resume-upload-secondary:focus-visible,
.resume-selected-file button:hover,
.resume-selected-file button:focus-visible {
  color: var(--app-primary-active);
  border-color: var(--app-primary);
}

.resume-upload-cancel:disabled,
.resume-upload-secondary:disabled,
.resume-selected-file button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.resume-file-picker {
  position: relative;
  display: grid;
  min-height: 92px;
  align-content: center;
  gap: var(--app-space-1);
  padding: var(--app-space-4) var(--app-space-5);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  color: var(--app-text);
  cursor: pointer;
  background: var(--app-surface-soft);
  transition: border-color 160ms ease, background-color 160ms ease;
}

.resume-file-picker:hover,
.resume-file-picker.has-file {
  border-color: var(--app-primary-subtle);
  background: var(--app-primary-soft);
}

.resume-file-picker:focus-within {
  outline: 2px solid var(--app-primary);
  outline-offset: 3px;
}

.resume-file-picker-label {
  color: var(--app-primary);
  font-size: var(--app-font-size-xs);
  font-weight: 700;
}

.resume-file-picker strong {
  overflow: hidden;
  max-width: 100%;
  color: var(--app-text);
  font-size: var(--app-font-size-md);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-file-picker small {
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
}

.resume-file-picker input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  cursor: pointer;
  opacity: 0;
}

.resume-selected-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
}

.resume-upload-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--app-space-5);
}

.resume-library-refreshing {
  display: flex;
  align-items: center;
  gap: var(--app-space-2);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
}

.resume-loading-indicator {
  width: 9px;
  height: 9px;
  border: 1px solid var(--app-border-strong);
  border-top-color: var(--app-primary);
  border-radius: 50%;
  animation: resume-spin 700ms linear infinite;
}

.resume-library-loading {
  display: grid;
  gap: var(--app-space-3);
}

.resume-library-loading-heading {
  max-width: 320px;
}

.resume-library-loading-row {
  min-height: 74px;
  border-bottom: 1px solid var(--app-border);
  padding: var(--app-space-4) var(--app-space-3);
}

.resume-library-shell {
  display: block;
  border-top: 1px solid var(--app-border-strong);
  padding-top: var(--app-space-5);
}

.resume-library-shell.has-review-inspector,
.resume-library-shell.has-source-preview {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 0.42fr);
  gap: var(--app-space-8);
}

.resume-library-main {
  min-width: 0;
}

.resume-library-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-6);
  padding-bottom: var(--app-space-5);
}

.resume-library-heading h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 22px;
  line-height: var(--app-line-height-tight);
}

.resume-library-heading p {
  max-width: 72ch;
  margin: var(--app-space-2) 0 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.resume-library-summary {
  flex: 0 0 auto;
  margin: var(--app-space-1) 0 0;
  color: var(--app-text-muted) !important;
  font-size: var(--app-font-size-sm) !important;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.resume-library-column-head,
.resume-library-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.7fr) minmax(230px, 1.2fr) minmax(130px, 0.65fr) minmax(170px, auto);
  gap: var(--app-space-4);
  align-items: center;
}

.resume-library-column-head {
  padding: 0 var(--app-space-3) var(--app-space-2);
  color: var(--app-text-muted);
  font-size: var(--app-font-size-xs);
  font-weight: 700;
}

.resume-library-list {
  border-top: 1px solid var(--app-border-strong);
}

.resume-library-row {
  position: relative;
  min-height: 78px;
  padding: var(--app-space-4) var(--app-space-3);
  border-bottom: 1px solid var(--app-border);
  transition: background-color 160ms ease;
}

.resume-library-row:hover,
.resume-library-row.is-reviewing {
  background: var(--app-surface-soft);
}

.resume-library-row.is-reviewing {
  box-shadow: inset 3px 0 var(--app-primary);
}

.resume-simple-file {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--app-space-3);
}

.resume-file-type {
  display: inline-flex;
  flex: 0 0 auto;
  min-width: 40px;
  justify-content: center;
  border: 1px solid var(--app-border-strong);
  border-radius: var(--app-radius-sm);
  padding: 5px 4px;
  color: var(--app-text-secondary);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
}

.resume-simple-file div {
  display: grid;
  min-width: 0;
  gap: var(--app-space-1);
}

.resume-simple-file .resume-name-line {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: var(--app-space-3);
}

.resume-source-trigger {
  display: grid;
  min-width: 0;
  flex: 1 1 auto;
  gap: var(--app-space-1);
  border: 0;
  padding: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.resume-source-trigger:not(:disabled):hover strong,
.resume-source-trigger:not(:disabled):focus-visible strong {
  color: var(--app-primary-active);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.resume-source-trigger:disabled {
  cursor: not-allowed;
}

.resume-simple-file strong {
  overflow: hidden;
  color: var(--app-text);
  font-size: var(--app-font-size-md);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-simple-file small {
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  line-height: 1.45;
}

.resume-rename-button,
.resume-name-edit-actions button {
  width: max-content;
  border: 0;
  border-bottom: 1px solid var(--app-border);
  padding: 2px 0;
  color: var(--app-primary);
  font: inherit;
  font-size: 11px;
  background: transparent;
  cursor: pointer;
}

.resume-name-edit-actions button {
  color: var(--app-text-muted);
}

.resume-rename-button:hover,
.resume-rename-button:focus-visible,
.resume-name-edit-actions button:hover,
.resume-name-edit-actions button:focus-visible {
  color: var(--app-primary-active);
  border-color: var(--app-primary);
}

.resume-name-input {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--app-primary);
  border-radius: var(--app-radius-sm);
  padding: 6px 8px;
  color: var(--app-text);
  font: inherit;
  font-size: var(--app-font-size-sm);
  background: var(--app-surface);
}

.resume-name-edit-actions {
  display: flex;
  gap: var(--app-space-3);
}

.resume-name-edit-actions button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.resume-library-status {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.resume-status-line {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--app-space-2);
  color: var(--app-text);
  font-size: var(--app-font-size-sm);
}

.resume-status-line strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-library-status p {
  overflow: hidden;
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-status-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--app-border-strong);
}

.resume-status-dot.is-success {
  background: var(--app-success);
}

.resume-status-dot.is-warning {
  background: var(--app-warning);
}

.resume-status-dot.is-danger {
  background: var(--app-danger);
}

.resume-library-date {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-xs);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.resume-simple-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--app-space-2);
  min-width: 0;
}

.resume-delete-button {
  flex: 0 0 auto;
  color: var(--app-danger) !important;
  font-size: 12px;
  opacity: 0.72;
}

.resume-delete-button:hover,
.resume-delete-button:focus-visible {
  opacity: 1;
}

@keyframes resume-spin {
  to {
    transform: rotate(360deg);
  }
}

.resume-review-inspector,
.resume-source-preview-inspector {
  min-width: 0;
  align-self: start;
  position: sticky;
  top: calc(var(--app-shell-header-height) + var(--app-space-4));
  border-left: 1px solid var(--app-border-strong);
  padding-left: var(--app-space-8);
}

.resume-source-preview-inspector {
  overflow: hidden;
}

@media (max-width: 900px) {
  .resume-upload-header {
    flex-direction: column;
    gap: var(--app-space-3);
  }

  .resume-upload-cancel {
    align-self: flex-start;
  }

  .resume-upload-actions {
    justify-content: space-between;
  }

  .resume-library-shell,
  .resume-library-shell.has-review-inspector,
  .resume-library-shell.has-source-preview {
    display: block;
  }

  .resume-library-column-head {
    display: none;
  }

  .resume-library-row {
    grid-template-columns: 1fr;
    gap: var(--app-space-3);
    align-items: start;
    padding: var(--app-space-4) 0;
  }

  .resume-library-status p {
    white-space: normal;
  }

  .resume-library-date {
    order: 3;
  }

  .resume-simple-actions {
    justify-content: flex-start;
    order: 4;
    flex-wrap: wrap;
  }

  .resume-simple-actions .resume-delete-button {
    opacity: 0.58;
  }

  .resume-review-inspector,
  .resume-source-preview-inspector {
    position: static;
    margin-top: var(--app-space-6);
    border-top: 1px solid var(--app-border-strong);
    border-left: 0;
    padding: 0;
  }

  .resume-review-date-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .resume-upload-panel {
    padding: var(--app-space-5) var(--app-space-4);
  }

  .resume-library-heading {
    display: grid;
    gap: var(--app-space-3);
  }

  .resume-library-summary {
    white-space: normal;
  }

  .resume-upload-actions {
    padding-bottom: env(safe-area-inset-bottom);
  }
}
</style>
