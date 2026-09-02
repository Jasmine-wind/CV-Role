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
const uploadOpen = ref(false)
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
const reviewResume = computed(
  () => resumes.value.find((resume) => resume.id === reviewResumeId.value) ?? null,
)
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
      description="管理用于岗位优化的真实基础简历。上传后系统会自动读取并准备内容。"
    >
      <template #actions>
        <el-button type="primary" @click="uploadOpen = !uploadOpen">
          {{ uploadOpen ? '收起上传' : '+ 上传简历' }}
        </el-button>
      </template>
    </PageHeader>

    <section v-if="uploadOpen" class="resume-upload-row" aria-label="上传简历">
      <div class="resume-upload-copy">
        <strong>上传一份新的真实简历</strong>
        <span>{{ selectedFileText }}</span>
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

    <section v-else-if="resumes.length" class="resume-library-shell">
      <div class="resume-library-main">
      <header class="resume-library-heading">
        <div>
          <h2>已保存的简历</h2>
          <p>选择简历并粘贴岗位 JD，即可在首页开始分析。</p>
        </div>
        <strong>{{ resumes.length }} 份</strong>
      </header>

      <div class="resume-library-column-head" aria-hidden="true">
        <span>文件</span>
        <span>状态</span>
        <span>上传时间</span>
        <span>操作</span>
      </div>
      <div class="resume-library-list" role="list">
      <article v-for="resume in resumes" :key="resume.id" class="resume-library-row" role="listitem">
        <div class="resume-simple-file">
          <span>{{ resume.fileType }}</span>
          <div>
            <strong>{{ resume.originalFilename }}</strong>
            <small>{{ formatFileSize(resume.fileSize) }}</small>
          </div>
        </div>
        <div class="resume-library-status">
          <span class="resume-status-dot" :class="`is-${statusFor(resume).tone}`" aria-hidden="true" />
          <span>{{ statusFor(resume).label }}</span>
        </div>
        <time class="resume-library-date" :datetime="resume.createdAt">
          {{ resume.createdAt.slice(0, 10) }}
        </time>
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
            {{ reviewResumeId === resume.id ? '收起确认' : '确认 →' }}
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

      </article>
      </div>
      </div>

      <aside v-if="reviewResumeId && reviewResume" class="resume-review-inspector">
        <header class="resume-review-header">
          <div>
            <span class="resume-review-label">CONTEXTUAL REVIEW</span>
            <h2>需要确认</h2>
            <p>{{ reviewResume.originalFilename }}</p>
          </div>
          <button type="button" class="resume-review-close" @click="closeReview">收起</button>
        </header>
        <p v-if="reviewLoading" class="resume-review-loading">正在读取待确认内容…</p>
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
                @click="acceptReviewItem(reviewResume.id, state)"
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
                @click="deleteReviewItem(reviewResume.id, state)"
              >
                删除
              </el-button>
            </div>
          </article>
        </template>
      </aside>
    </section>

    <EmptyState
      v-else
      title="还没有简历"
      description="上传一份真实简历，系统会自动准备后续岗位分析需要的内容。"
    />
  </section>
</template>

<style scoped>
.resume-library-page {
  display: grid;
  gap: var(--app-section-spacing);
}

.resume-upload-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: var(--app-space-4);
  align-items: center;
  border-top: 1px solid var(--app-border);
  border-bottom: 1px solid var(--app-border);
  padding: var(--app-space-4) 0;
}

.resume-upload-copy {
  display: grid;
  gap: var(--app-space-1);
  min-width: 0;
}

.resume-upload-copy strong {
  color: var(--app-text);
  font-size: var(--app-font-size-md);
}

.resume-upload-copy span,
.resume-library-heading p {
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.resume-file-picker {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  max-width: 260px;
  min-width: 0;
  padding: 0 var(--app-space-4);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  color: var(--app-text);
  font-size: var(--app-font-size-sm);
  font-weight: 700;
  cursor: pointer;
  background: var(--app-surface);
}

.resume-file-picker span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.resume-library-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.36fr);
  gap: var(--app-space-8);
  border-top: 1px solid var(--app-border-strong);
  padding-top: var(--app-space-5);
}

.resume-library-main {
  min-width: 0;
}

.resume-library-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-4);
  padding-bottom: var(--app-space-5);
}

.resume-library-heading h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
  line-height: var(--app-line-height-tight);
}

.resume-library-heading p {
  margin: var(--app-space-1) 0 0;
}

.resume-library-heading > strong {
  flex: 0 0 auto;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-sm);
}

.resume-library-column-head,
.resume-library-row {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(110px, 0.8fr) minmax(90px, 0.75fr) auto;
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
  padding: var(--app-space-4) var(--app-space-3);
  border-bottom: 1px solid var(--app-border);
}

.resume-library-row:hover,
.resume-library-row.is-selected {
  background: var(--app-surface-soft);
}

.resume-library-row.is-selected {
  box-shadow: inset 3px 0 var(--app-primary);
}

.resume-simple-file {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--app-space-3);
}

.resume-simple-file > span {
  display: inline-flex;
  flex: 0 0 auto;
  min-width: 34px;
  justify-content: center;
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
}

.resume-simple-file div {
  display: grid;
  min-width: 0;
  gap: var(--app-space-1);
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
}

.resume-library-status {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
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
}

.resume-library-error,
.resume-simple-error {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--app-danger);
  font-size: var(--app-font-size-xs);
  line-height: var(--app-line-height-body);
}

.resume-simple-actions,
.resume-library-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--app-space-2);
}

.resume-item-more {
  position: relative;
}

.resume-item-more summary {
  list-style: none;
  padding: 7px 10px;
  border-radius: var(--app-radius-md);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  font-weight: 700;
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

.resume-review-inspector {
  display: grid;
  align-content: start;
  gap: var(--app-space-4);
  min-width: 0;
  align-self: start;
  position: sticky;
  top: calc(var(--app-shell-header-height) + var(--app-space-4));
  border-left: 1px solid var(--app-border-strong);
  padding: var(--app-space-3) 0 var(--app-space-5) var(--app-space-6);
  background: var(--app-surface-soft);
}

.resume-review-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-3);
}

.resume-review-label {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.resume-review-header h2 {
  margin: var(--app-space-1) 0 0;
  color: var(--app-text);
  font-size: 20px;
  line-height: var(--app-line-height-tight);
}

.resume-review-header p {
  margin: var(--app-space-1) 0 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
}

.resume-review-close {
  border: 0;
  border-bottom: 1px solid var(--app-text);
  padding: 3px 0;
  color: var(--app-text);
  font: inherit;
  font-size: var(--app-font-size-xs);
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.resume-review-close:hover,
.resume-review-close:focus-visible {
  color: var(--app-primary-active);
  border-color: var(--app-primary);
}

.resume-review-loading,
.resume-review-reason,
.resume-review-entry-preview {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  line-height: var(--app-line-height-body);
}

.resume-review-item {
  display: grid;
  gap: var(--app-space-3);
  border-top: 1px solid var(--app-border);
  padding-top: var(--app-space-4);
}

.resume-review-name,
.resume-review-entry-form {
  display: grid;
  gap: var(--app-space-2);
}

.resume-review-name label {
  display: grid;
  gap: var(--app-space-1);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  font-weight: 700;
}

.resume-review-row,
.resume-review-date-row {
  display: grid;
  gap: var(--app-space-2);
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
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: var(--app-space-2) var(--app-space-3);
  color: var(--app-text);
  font: inherit;
  font-size: var(--app-font-size-sm);
  background: var(--app-surface);
}

.resume-review-entry-preview {
  display: grid;
  gap: var(--app-space-1);
}

.resume-review-entry-preview strong {
  color: var(--app-text);
}

.resume-review-entry-preview p {
  margin: 0;
}

.resume-review-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-2);
}

@media (max-width: 900px) {
  .resume-library-shell {
    display: block;
  }

  .resume-review-inspector {
    position: static;
    margin-top: var(--app-space-6);
    border-top: 1px solid var(--app-border-strong);
    border-left: 0;
    padding: var(--app-space-5) 0 0;
  }
}

@media (max-width: 760px) {
  .resume-upload-row {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .resume-file-picker,
  .resume-upload-row .el-button {
    width: 100%;
    max-width: none;
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

  .resume-library-actions {
    justify-content: flex-start;
    order: 4;
  }

  .resume-library-date {
    order: 3;
  }

  .resume-library-error,
  .resume-simple-error {
    grid-column: auto;
  }

  .resume-review-date-row {
    grid-template-columns: 1fr;
  }
}
</style>
