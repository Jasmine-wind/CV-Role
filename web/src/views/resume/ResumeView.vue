<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
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
  uploadResume,
} from '@/api/resume'
import type { ResumeReviewResolveRequest, ResumeReviewUnresolvedItem } from '@/api/resume'
import { startAsyncTaskPolling } from '@/utils/asyncTaskPolling'
import type { AsyncTaskPollingController } from '@/utils/asyncTaskPolling'
import type { AsyncTaskVO } from '@/types/task'
import type { ResumeListItem } from '@/types/resume'
import type { ResumeDocument, ResumeDocumentBullet } from '@/types/resume-document'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx']
const PREPARATION_TIMEOUT_MS = 5 * 60 * 1000

const resumes = ref<ResumeListItem[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const uploading = ref(false)
const deletingId = ref<number | null>(null)
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const activeTasks = ref<Record<number, AsyncTaskVO>>({})
const pollingControllers = new Map<number, AsyncTaskPollingController>()

const selectedFileText = computed(() => {
  if (!selectedFile.value) {
    return '支持 PDF、DOC、DOCX，单份最大 10 MB'
  }
  return `${selectedFile.value.name} · ${formatFileSize(selectedFile.value.size)}`
})

const formatFileSize = (size: number) => {
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(2)} MB`
  }
  return `${Math.max(size / 1024, 0.1).toFixed(1)} KB`
}

const statusFor = (resume: ResumeListItem) => {
  const task = activeTasks.value[resume.id]
  if (task?.status === 'PENDING' || task?.status === 'RUNNING') {
    return {
      label: task.message || '正在准备简历',
      tone: 'warning' as const,
    }
  }
  if (resume.parseStatus === 'SUCCESS') {
    if (resume.qualityStatus === 'PENDING') {
      return {
        label: '正在准备简历',
        tone: 'warning' as const,
      }
    }
    if (resume.canonicalReady === false) {
      return {
        label: '需要重新解析',
        tone: 'warning' as const,
      }
    }
    if (resume.qualityStatus === 'NEEDS_REVIEW') {
      return {
        label: '需要确认',
        tone: 'warning' as const,
      }
    }
    if (resume.qualityStatus === 'FAILED') {
      return {
        label: '无法使用',
        tone: 'danger' as const,
      }
    }
    return {
      label: '已准备好',
      tone: 'success' as const,
    }
  }
  if (resume.parseStatus === 'FAILED') {
    return {
      label: '准备失败',
      tone: 'danger' as const,
    }
  }
  return {
    label: '等待准备',
    tone: 'info' as const,
  }
}

/** Slice A 确认面板：只展示 canonical 层候选内容，不暴露解析内部结构。 */
interface ReviewDraftContact {
  type?: string
  label?: string | null
  value?: string
}

interface ReviewDraftFragment {
  text?: string
}

interface ReviewDraftEntry {
  kind?: string
  organization?: string | null
  role?: string | null
  school?: string | null
  degree?: string | null
  major?: string | null
  startDate?: string | null
  endDate?: string | null
  group?: string | null
  skillItems?: string[] | null
  bullets: ResumeDocumentBullet[]
}

interface ReviewItemState {
  item: ResumeReviewUnresolvedItem
  contact: ReviewDraftContact
  entry: ReviewDraftEntry
  text: string
}

const reviewResumeId = ref<number | null>(null)
const reviewLoading = ref(false)
const reviewQualityStatus = ref<string | null>(null)
const reviewName = ref('')
const reviewNameMissing = ref(false)
const reviewItems = ref<ReviewItemState[]>([])
const resolvingItemId = ref<string | null>(null)

const CONTACT_TYPE_OPTIONS = [
  { value: 'PHONE', label: '电话' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'WECHAT', label: '微信' },
  { value: 'QQ', label: 'QQ' },
  { value: 'LINKEDIN', label: 'LinkedIn' },
  { value: 'GITHUB', label: 'GitHub' },
  { value: 'WEBSITE', label: '个人网站' },
  { value: 'LOCATION', label: '所在地' },
  { value: 'OTHER', label: '其他' },
]
const REQUIRED_CONTACT_TYPE_OPTIONS = CONTACT_TYPE_OPTIONS.filter(
  (option) => option.value === 'PHONE' || option.value === 'EMAIL',
)

const parseDraft = <T,>(draft: string): T => {
  try {
    return JSON.parse(draft) as T
  } catch {
    return {} as T
  }
}

const entryDraftSummary = (draft: string) => {
  const entry = parseDraft<ReviewDraftEntry>(draft)
  const title = entry.organization || entry.school || entry.group || '待确认条目'
  const details = [
    entry.role,
    entry.degree,
    entry.major,
    [entry.startDate, entry.endDate].filter(Boolean).join(' - '),
    entry.skillItems?.filter(Boolean).join('、'),
  ].filter((value): value is string => Boolean(value && value.trim()))
  const bullets = (entry.bullets ?? [])
    .map((bullet) => bullet.text?.trim())
    .filter((value): value is string => Boolean(value))
  return { title, details, bullets }
}

const updateReviewState = (review: Awaited<ReturnType<typeof getResumeReview>>) => {
  reviewQualityStatus.value = review.qualityStatus
  const document = parseDraft<Partial<ResumeDocument>>(review.canonicalDocument ?? '{}')
  reviewName.value = document.basics?.name?.trim() ?? ''
  reviewNameMissing.value = !reviewName.value
  const items = review.unresolvedItems
    ? (JSON.parse(review.unresolvedItems) as ResumeReviewUnresolvedItem[])
    : []
  reviewItems.value = items.map((item) => {
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

const openReview = async (resumeId: number) => {
  reviewResumeId.value = resumeId
  reviewLoading.value = true
  reviewItems.value = []
  try {
    const review = await getResumeReview(resumeId)
    updateReviewState(review)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取待确认内容失败')
    reviewResumeId.value = null
  } finally {
    reviewLoading.value = false
  }
}

const closeReview = () => {
  reviewResumeId.value = null
  reviewItems.value = []
  reviewQualityStatus.value = null
  reviewName.value = ''
  reviewNameMissing.value = false
}

const applyReview = async (resumeId: number, payload: ResumeReviewResolveRequest) => {
  resolvingItemId.value = payload.itemId
  try {
    const review = await resolveResumeReview(resumeId, {
      ...payload,
      ...(reviewName.value.trim() ? { name: reviewName.value.trim() } : {}),
    })
    updateReviewState(review)
    if (review.qualityStatus === 'READY' && reviewItems.value.length === 0) {
      ElMessage.success('确认完成，简历已可用于岗位分析')
      closeReview()
      await loadResumes()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '确认操作失败')
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
  void applyReview(resumeId, { itemId: state.item.id, action: 'DELETE' })
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
      `确认删除「${resume.originalFilename}」吗？相关分析结果也会删除。`,
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
  <section class="resume-page resume-simple-page">
    <PageHeader
      title="我的简历"
      description="上传后系统会自动读取并准备内容，你不需要选择解析方式或执行额外步骤。"
    />

    <section class="resume-simple-upload app-card">
      <div>
        <h2>上传简历</h2>
        <p>{{ selectedFileText }}</p>
      </div>
      <label class="resume-file-picker">
        <span>{{ selectedFile ? '重新选择' : '选择文件' }}</span>
        <input ref="fileInput" type="file" accept=".pdf,.doc,.docx" @change="handleFileChange" />
      </label>
      <el-button
        type="primary"
        :loading="uploading"
        :disabled="!selectedFile"
        @click="handleUpload"
      >
        上传
      </el-button>
    </section>

    <SkeletonBlock v-if="loading && !resumes.length" title :rows="5" />

    <ErrorState
      v-else-if="loadFailed"
      title="简历列表加载失败"
      description="暂时无法读取你的简历列表，这不影响已保存的简历。"
      action-text="重新加载"
      @action="loadResumes()"
    />

    <section v-else-if="resumes.length" class="resume-simple-list">
      <header>
        <div>
          <h2>已保存的简历</h2>
          <p>选择简历并粘贴岗位 JD，即可在首页开始分析。</p>
        </div>
        <strong>{{ resumes.length }} 份</strong>
      </header>

      <article v-for="resume in resumes" :key="resume.id" class="resume-simple-item app-card">
        <div class="resume-simple-file">
          <span>{{ resume.fileType }}</span>
          <div>
            <strong>{{ resume.originalFilename }}</strong>
            <small>上传后自动准备内容</small>
          </div>
        </div>
        <el-tag :type="statusFor(resume).tone" effect="light">{{ statusFor(resume).label }}</el-tag>
        <p v-if="resume.parseStatus === 'FAILED'" class="resume-simple-error">
          {{ resume.parseErrorMessage || '未能读取这份简历，请重试。' }}
        </p>
        <p v-if="resume.qualityStatus === 'NEEDS_REVIEW'" class="resume-simple-error">
          部分内容无法自动确认，需要你核对后才能用于岗位分析与导出。
        </p>
        <p v-if="resume.canonicalReady === false" class="resume-simple-error">
          这份简历来自旧版本解析，需重新准备后才能开始新的岗位分析。
        </p>
        <div class="resume-simple-actions">
          <el-button
            v-if="resume.qualityStatus === 'NEEDS_REVIEW'"
            type="warning"
            plain
            @click="reviewResumeId === resume.id ? closeReview() : openReview(resume.id)"
          >
            {{ reviewResumeId === resume.id ? '收起确认' : '去确认' }}
          </el-button>
          <el-button
            v-if="
              (resume.parseStatus !== 'SUCCESS' ||
                resume.qualityStatus === 'FAILED' ||
                resume.canonicalReady === false) &&
              !activeTasks[resume.id]
            "
            plain
            @click="prepareResume(resume)"
          >
            重试
          </el-button>
          <details class="resume-item-more">
            <summary>更多</summary>
            <div class="resume-item-more-menu">
              <el-button
                type="danger"
                text
                :loading="deletingId === resume.id"
                :disabled="Boolean(activeTasks[resume.id])"
                @click="handleDelete(resume)"
              >
                删除简历
              </el-button>
            </div>
          </details>
        </div>

        <section v-if="reviewResumeId === resume.id" class="resume-review-panel">
          <p v-if="reviewLoading">正在读取待确认内容…</p>
          <template v-else>
            <div v-if="reviewNameMissing" class="resume-review-name">
              <label>
                <span>姓名（请确认）</span>
                <input v-model="reviewName" placeholder="请输入姓名" />
              </label>
            </div>
            <p v-if="reviewItems.length === 0 && reviewQualityStatus === 'READY'">
              已全部确认完成。
            </p>
            <p v-else-if="reviewItems.length === 0" class="resume-review-reason">
              当前没有可直接确认的候选内容，请重新准备一份排版更清晰的简历后再试。
            </p>
            <article v-for="state in reviewItems" :key="state.item.id" class="resume-review-item">
              <p class="resume-review-reason">{{ state.item.reason || '请确认以下内容' }}</p>
              <div
                v-if="
                  state.item.kind === 'CONTACT_CANDIDATE' ||
                  state.item.kind === 'REQUIRED_CONTACT_CANDIDATE'
                "
                class="resume-review-row"
              >
                <select v-model="state.contact.type" aria-label="联系方式类型">
                  <option
                    v-for="option in state.item.kind === 'REQUIRED_CONTACT_CANDIDATE'
                      ? REQUIRED_CONTACT_TYPE_OPTIONS
                      : CONTACT_TYPE_OPTIONS"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
                <input v-model="state.contact.value" placeholder="联系方式内容" />
              </div>
              <div v-else-if="state.item.kind === 'NAME_CANDIDATE'" class="resume-review-row">
                <input v-model="state.text" aria-label="确认姓名" placeholder="姓名" />
              </div>
              <div v-else-if="state.item.kind === 'TEXT_FRAGMENT'" class="resume-review-row">
                <textarea v-model="state.text" rows="2" placeholder="内容"></textarea>
              </div>
              <div
                v-else-if="state.item.kind === 'ENTRY_CANDIDATE'"
                class="resume-review-entry-form"
              >
                <input
                  v-if="state.entry.kind === 'EDUCATION'"
                  v-model="state.entry.school"
                  placeholder="学校名（必填）"
                />
                <input
                  v-else
                  v-model="state.entry.organization"
                  placeholder="公司或项目名（必填）"
                />
                <input v-model="state.entry.role" placeholder="职位或角色（可选）" />
                <input
                  v-if="state.entry.kind === 'EDUCATION'"
                  v-model="state.entry.degree"
                  placeholder="学历（可选）"
                />
                <input
                  v-if="state.entry.kind === 'EDUCATION'"
                  v-model="state.entry.major"
                  placeholder="专业（可选）"
                />
                <div class="resume-review-date-row">
                  <input v-model="state.entry.startDate" placeholder="开始时间（可选）" />
                  <input v-model="state.entry.endDate" placeholder="结束时间（可选）" />
                </div>
                <textarea
                  v-for="(bullet, index) in state.entry.bullets"
                  :key="`${state.item.id}-bullet-${index}`"
                  v-model="bullet.text"
                  rows="2"
                  placeholder="内容"
                ></textarea>
              </div>
              <div v-else class="resume-review-entry-preview">
                <strong>{{ entryDraftSummary(state.item.canonicalDraft).title }}</strong>
                <span v-if="entryDraftSummary(state.item.canonicalDraft).details.length">
                  {{ entryDraftSummary(state.item.canonicalDraft).details.join(' · ') }}
                </span>
                <p
                  v-for="bullet in entryDraftSummary(state.item.canonicalDraft).bullets"
                  :key="bullet"
                >
                  {{ bullet }}
                </p>
              </div>
              <div class="resume-review-actions">
                <el-button
                  size="small"
                  type="primary"
                  :loading="resolvingItemId === state.item.id"
                  @click="acceptReviewItem(resume.id, state)"
                >
                  接受
                </el-button>
                <el-button
                  v-if="
                    state.item.kind !== 'REQUIRED_CONTACT_CANDIDATE' &&
                    state.item.kind !== 'NAME_CANDIDATE'
                  "
                  size="small"
                  type="danger"
                  plain
                  :loading="resolvingItemId === state.item.id"
                  @click="deleteReviewItem(resume.id, state)"
                >
                  删除
                </el-button>
              </div>
            </article>
          </template>
        </section>
      </article>
    </section>

    <EmptyState
      v-else
      title="还没有简历"
      description="上传一份真实简历，系统会自动准备后续岗位分析需要的内容。"
    />
  </section>
</template>

<style scoped>
.resume-simple-page {
  display: grid;
  gap: 24px;
}

.resume-simple-upload {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 16px;
  align-items: center;
  padding: 24px;
}

.resume-simple-upload h2,
.resume-simple-list h2 {
  margin: 0;
  color: var(--app-navy);
  font-size: 20px;
}

.resume-simple-upload p,
.resume-simple-list header p {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: 14px;
}

.resume-file-picker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  color: var(--app-text);
  font-weight: 700;
  cursor: pointer;
  background: var(--app-surface);
}

.resume-file-picker input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
}

.resume-file-picker:focus-within {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}

.resume-simple-list {
  display: grid;
  gap: 12px;
}

.resume-simple-list > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.resume-simple-list > header > strong {
  color: var(--app-primary);
}

.resume-simple-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 16px;
  align-items: center;
  padding: 18px 20px;
}

.resume-simple-file {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
}

.resume-simple-file > span {
  display: grid;
  flex: 0 0 48px;
  height: 48px;
  place-items: center;
  border-radius: var(--app-radius-md);
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  background: var(--app-primary-soft);
}

.resume-simple-file div {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.resume-simple-file strong {
  overflow: hidden;
  color: var(--app-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-simple-file small {
  color: var(--app-text-secondary);
}

.resume-simple-error {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--el-color-danger);
  font-size: 13px;
}

.resume-simple-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.resume-item-more {
  position: relative;
}

.resume-item-more summary {
  list-style: none;
  padding: 7px 10px;
  border-radius: var(--app-radius-md);
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.resume-item-more summary::-webkit-details-marker {
  display: none;
}

.resume-item-more summary:hover,
.resume-item-more summary:focus-visible {
  color: var(--app-text);
  background: var(--app-bg-soft);
}

.resume-item-more-menu {
  position: absolute;
  z-index: 4;
  top: calc(100% + 6px);
  right: 0;
  min-width: 120px;
  padding: 4px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-soft);
}

.resume-item-more-menu .el-button {
  width: 100%;
  justify-content: flex-start;
}

.resume-review-panel {
  grid-column: 1 / -1;
  display: grid;
  gap: 12px;
  border-top: 1px dashed var(--app-border);
  padding-top: 14px;
}

.resume-review-item {
  display: grid;
  gap: 8px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 12px;
}

.resume-review-reason {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.resume-review-name,
.resume-review-entry-form {
  display: grid;
  gap: 8px;
}

.resume-review-name label {
  display: grid;
  gap: 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.resume-review-row,
.resume-review-date-row {
  display: grid;
  gap: 8px;
}

.resume-review-date-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.resume-review-name input,
.resume-review-row input,
.resume-review-row textarea,
.resume-review-row select,
.resume-review-entry-form input,
.resume-review-entry-form textarea {
  border: 1px solid var(--app-border);
  border-radius: 6px;
  padding: 8px 10px;
  font: inherit;
}

.resume-review-entry-preview {
  display: grid;
  gap: 4px;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.resume-review-entry-preview strong {
  color: var(--app-text);
}

.resume-review-entry-preview p {
  margin: 0;
}

.resume-review-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 760px) {
  .resume-simple-upload,
  .resume-simple-item {
    grid-template-columns: 1fr;
  }

  .resume-simple-upload .el-button,
  .resume-file-picker {
    width: 100%;
  }
}
</style>
