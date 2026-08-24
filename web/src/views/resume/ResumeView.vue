<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import { deleteResume, getResumeList, requestResumePreparation, uploadResume } from '@/api/resume'
import { startAsyncTaskPolling } from '@/utils/asyncTaskPolling'
import type { AsyncTaskPollingController } from '@/utils/asyncTaskPolling'
import type { AsyncTaskVO } from '@/types/task'
import type { ResumeListItem } from '@/types/resume'

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

const formatDate = (value: string) => value.replace('T', ' ').slice(0, 16)

const statusFor = (resume: ResumeListItem) => {
  const task = activeTasks.value[resume.id]
  if (task?.status === 'PENDING' || task?.status === 'RUNNING') {
    return {
      label: task.message || '正在准备简历',
      tone: 'warning' as const,
    }
  }
  if (resume.parseStatus === 'SUCCESS') {
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
    await ElMessageBox.confirm(`确认删除「${resume.originalFilename}」吗？相关分析结果也会删除。`, '删除简历', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
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
  for (const resume of resumes.value.filter((item) => item.parseStatus === 'PENDING')) {
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
        <input
          ref="fileInput"
          type="file"
          accept=".pdf,.doc,.docx"
          @change="handleFileChange"
        />
      </label>
      <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="handleUpload">
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
            <small>{{ formatFileSize(resume.fileSize) }} · {{ formatDate(resume.createdAt) }}</small>
          </div>
        </div>
        <el-tag :type="statusFor(resume).tone" effect="light">{{ statusFor(resume).label }}</el-tag>
        <p v-if="resume.parseStatus === 'FAILED'" class="resume-simple-error">
          {{ resume.parseErrorMessage || '未能读取这份简历，请重试。' }}
        </p>
        <div class="resume-simple-actions">
          <el-button
            v-if="resume.parseStatus !== 'SUCCESS' && !activeTasks[resume.id]"
            plain
            @click="prepareResume(resume)"
          >
            重试
          </el-button>
          <el-button
            type="danger"
            text
            :loading="deletingId === resume.id"
            :disabled="Boolean(activeTasks[resume.id])"
            @click="handleDelete(resume)"
          >
            删除
          </el-button>
        </div>
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
