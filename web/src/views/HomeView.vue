<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import { retryJobAnalysis, startJobAnalysis } from '@/api/job-analysis'
import { getJobDirectionInsights } from '@/api/job-direction-insight'
import { getResumeList, requestResumePreparation, uploadResume } from '@/api/resume'
import { startAsyncTaskPolling } from '@/utils/asyncTaskPolling'
import type { AsyncTaskPollingController } from '@/utils/asyncTaskPolling'
import type { ActiveJobAnalysis, JobAnalysisStartResult } from '@/types/job-analysis'
import type { AsyncTaskVO } from '@/types/task'
import type { ResumeListItem } from '@/types/resume'

const ACTIVE_ANALYSIS_STORAGE_KEY = 'cv-role:active-job-analysis'
const MAX_FILE_SIZE = 10 * 1024 * 1024
const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx']
const ANALYSIS_TIMEOUT_MS = 8 * 60 * 1000

const router = useRouter()
const resumes = ref<ResumeListItem[]>([])
const selectedResumeId = ref<number | null>(null)
const jobDescription = ref('')
const loading = ref(false)
const loadFailed = ref(false)
const uploading = ref(false)
const uploadRowVisible = ref(false)
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const activeAnalysis = ref<JobAnalysisStartResult | null>(null)
const analysisTask = ref<AsyncTaskVO | null>(null)
const analysisError = ref<string | null>(null)
const analysisTimedOut = ref(false)
const startingAnalysis = ref(false)
const preparationTaskId = ref<number | null>(null)
const preparationMessage = ref<string | null>(null)
const hasJobDirectionInsight = ref(false)
let analysisPolling: AsyncTaskPollingController | null = null
let preparationPolling: AsyncTaskPollingController | null = null

const selectedResume = computed(() => resumes.value.find((item) => item.id === selectedResumeId.value) ?? null)
const analysisRunning = computed(() => {
  const status = analysisTask.value?.status
  return Boolean(activeAnalysis.value && (!status || status === 'PENDING' || status === 'RUNNING'))
})
const canStart = computed(() => Boolean(
  selectedResumeId.value
  && jobDescription.value.trim()
  && !analysisRunning.value
  && !startingAnalysis.value
  && !preparationTaskId.value,
))
const currentStage = computed(() => analysisTask.value?.message || '正在保存你的简历和目标岗位')

const loadInsightAvailability = async () => {
  try {
    hasJobDirectionInsight.value = (await getJobDirectionInsights()).cohorts.length > 0
  } catch {
    // Insight is optional long-term value. Its read failure must never block the single-JD start flow.
    hasJobDirectionInsight.value = false
  }
}

const loadResumes = async (preferredResumeId?: number) => {
  loading.value = true
  loadFailed.value = false
  try {
    resumes.value = await getResumeList()
    if (preferredResumeId && resumes.value.some((item) => item.id === preferredResumeId)) {
      selectedResumeId.value = preferredResumeId
    } else if (!selectedResumeId.value || !resumes.value.some((item) => item.id === selectedResumeId.value)) {
      selectedResumeId.value = resumes.value[0]?.id ?? null
    }
  } catch {
    // 区分真正的加载失败与空数据：失败提供重试，而不是当成没有简历。
    loadFailed.value = true
  } finally {
    loading.value = false
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

const startPreparationPolling = (resumeId: number, taskId: number) => {
  preparationPolling?.stop()
  preparationTaskId.value = taskId
  preparationMessage.value = '正在读取简历内容'
  preparationPolling = startAsyncTaskPolling({
    taskId,
    timeoutMs: 5 * 60 * 1000,
    onUpdate: (task) => {
      preparationMessage.value = task.message || '正在准备简历'
    },
    onSuccess: async () => {
      preparationTaskId.value = null
      preparationMessage.value = null
      await loadResumes(resumeId)
      ElMessage.success('简历已准备好，可以开始分析')
    },
    onFailed: async (task) => {
      preparationTaskId.value = null
      preparationMessage.value = null
      await loadResumes(resumeId)
      ElMessage.error(task.errorMessage || '未能读取简历，请前往“我的简历”重试')
    },
    onTimeout: () => {
      preparationTaskId.value = null
      preparationMessage.value = null
      ElMessage.warning('简历仍在后台准备，请稍后再开始分析')
    },
    onError: (error) => {
      preparationTaskId.value = null
      preparationMessage.value = null
      ElMessage.error(error instanceof Error ? error.message : '获取简历状态失败')
    },
  })
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
    uploadRowVisible.value = false
    await loadResumes(uploaded.id)
    if (uploaded.preparationTaskId) {
      startPreparationPolling(uploaded.id, uploaded.preparationTaskId)
    } else {
      try {
        const task = await requestResumePreparation(uploaded.id)
        startPreparationPolling(uploaded.id, task.taskId)
      } catch {
        ElMessage.warning('简历已上传；如果暂时无法准备，开始分析时系统会自动重试')
      }
    }
    ElMessage.success('简历上传成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '简历上传失败')
  } finally {
    uploading.value = false
  }
}

const saveActiveAnalysis = (analysis: JobAnalysisStartResult) => {
  const stored: ActiveJobAnalysis = {
    ...analysis,
    startedAt: new Date().toISOString(),
  }
  window.sessionStorage.setItem(ACTIVE_ANALYSIS_STORAGE_KEY, JSON.stringify(stored))
}

const clearActiveAnalysis = () => {
  window.sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY)
  activeAnalysis.value = null
  analysisTask.value = null
}

const openAnalysisResult = (analysis: JobAnalysisStartResult) => {
  clearActiveAnalysis()
  router.push({
    name: 'job-analysis',
    params: {
      optimizationTaskId: String(analysis.optimizationTaskId),
    },
  })
}

const startAnalysisPolling = (analysis: JobAnalysisStartResult) => {
  analysisPolling?.stop()
  activeAnalysis.value = analysis
  analysisTask.value = null
  analysisError.value = null
  analysisTimedOut.value = false

  analysisPolling = startAsyncTaskPolling({
    taskId: analysis.taskId,
    timeoutMs: ANALYSIS_TIMEOUT_MS,
    onUpdate: (task) => {
      analysisTask.value = task
    },
    onSuccess: () => openAnalysisResult(analysis),
    onFailed: (task) => {
      analysisTask.value = task
      analysisTimedOut.value = false
      analysisError.value = task.errorMessage || '岗位分析没有完成。'
    },
    onCancelled: () => {
      analysisTimedOut.value = false
      analysisError.value = '岗位分析已取消，可以重新开始。'
    },
    onTimeout: () => {
      analysisTimedOut.value = true
      analysisError.value = '岗位分析仍在后台进行，你可以继续等待或稍后回到首页查看。'
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : '获取岗位分析状态失败'
      if (message.includes('任务不存在') || message.includes('无权限')) {
        clearActiveAnalysis()
        analysisError.value = null
        ElMessage.warning('上次岗位分析已不可用，请重新开始')
        return
      }
      analysisTimedOut.value = true
      analysisError.value = message
    },
  })
}

const handleStartAnalysis = async () => {
  if (!canStart.value || !selectedResumeId.value) {
    return
  }

  analysisError.value = null
  analysisTimedOut.value = false
  startingAnalysis.value = true
  try {
    const analysis = await startJobAnalysis({
      resumeId: selectedResumeId.value,
      jobDescription: jobDescription.value.trim(),
    })
    saveActiveAnalysis(analysis)
    startAnalysisPolling(analysis)
  } catch (error) {
    analysisError.value = error instanceof Error ? error.message : '岗位分析启动失败'
  } finally {
    startingAnalysis.value = false
  }
}

const retryAnalysis = async () => {
  const analysis = activeAnalysis.value
  if (!analysis || startingAnalysis.value) {
    return
  }

  startingAnalysis.value = true
  analysisError.value = null
  analysisTimedOut.value = false
  try {
    const retried = await retryJobAnalysis(analysis.optimizationTaskId)
    saveActiveAnalysis(retried)
    startAnalysisPolling(retried)
  } catch (error) {
    analysisError.value = error instanceof Error ? error.message : '岗位分析重试失败'
  } finally {
    startingAnalysis.value = false
  }
}

const continueWaiting = () => {
  if (!activeAnalysis.value) {
    return
  }
  startAnalysisPolling(activeAnalysis.value)
}

const restoreActiveAnalysis = () => {
  const raw = window.sessionStorage.getItem(ACTIVE_ANALYSIS_STORAGE_KEY)
  if (!raw) {
    return
  }
  try {
    const stored = JSON.parse(raw) as ActiveJobAnalysis
    if (stored.taskId && stored.optimizationTaskId && stored.sourceResumeVersionId && stored.targetResumeVersionId && stored.jobTargetId) {
      startAnalysisPolling(stored)
      return
    }
  } catch {
    // Invalid local state should not block a new analysis.
  }
  window.sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY)
}

onMounted(async () => {
  await loadResumes()
  restoreActiveAnalysis()
  void loadInsightAvailability()
})

onUnmounted(() => {
  analysisPolling?.stop()
  preparationPolling?.stop()
})
</script>

<template>
  <section class="home-page">
    <PageHeader
      title="开始岗位分析"
      description="选择一份简历，粘贴目标岗位 JD，系统会完成其余准备并给出逐条核对的分析结果。"
    />

    <SkeletonBlock v-if="loading && !resumes.length" title :rows="5" />

    <ErrorState
      v-else-if="loadFailed"
      title="简历列表加载失败"
      description="暂时无法读取你的简历列表，这不影响已保存的简历。"
      action-text="重新加载"
      @action="loadResumes()"
    />

    <section v-else class="home-start-card app-card">
      <div class="home-start-field">
        <div class="home-field-head">
          <label for="home-resume">我的简历</label>
          <el-button
            v-if="resumes.length"
            text
            type="primary"
            size="small"
            :disabled="analysisRunning"
            @click="uploadRowVisible = !uploadRowVisible"
          >
            {{ uploadRowVisible ? '收起' : '上传另一份' }}
          </el-button>
        </div>

        <el-select
          v-if="resumes.length"
          id="home-resume"
          v-model="selectedResumeId"
          class="home-resume-select"
          placeholder="选择简历"
          :disabled="analysisRunning"
        >
          <el-option
            v-for="resume in resumes"
            :key="resume.id"
            :label="resume.originalFilename"
            :value="resume.id"
          >
            <span>{{ resume.originalFilename }}</span>
            <small>{{ resume.parseStatus === 'SUCCESS' ? '已准备好' : '系统将自动准备' }}</small>
          </el-option>
        </el-select>

        <div v-if="!resumes.length || uploadRowVisible" class="home-inline-upload">
          <p v-if="!resumes.length">先上传一份真实简历。上传后系统会自动读取内容。</p>
          <label class="home-file-picker">
            <span>{{ selectedFile?.name || '选择简历文件' }}</span>
            <input ref="fileInput" data-testid="home-resume-upload" type="file" accept=".pdf,.doc,.docx" @change="handleFileChange" />
          </label>
          <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="handleUpload">
            上传简历
          </el-button>
        </div>
      </div>

      <div v-if="resumes.length" class="home-start-field">
        <label for="home-jd">目标岗位 JD</label>
        <el-input
          id="home-jd"
          v-model="jobDescription"
          type="textarea"
          :rows="10"
          maxlength="10000"
          show-word-limit
          resize="vertical"
          placeholder="粘贴完整的岗位描述，包括职责、要求和加分项……"
          :disabled="analysisRunning"
        />
      </div>

      <div v-if="preparationTaskId" class="home-analysis-state is-running" role="status">
        <span class="home-state-dot" />
        <div>
          <strong>正在准备简历</strong>
          <p>{{ preparationMessage }}</p>
        </div>
      </div>

      <div v-if="analysisError && !activeAnalysis" class="home-analysis-state is-error" role="alert">
        <span class="home-state-dot" />
        <div>
          <strong>岗位分析没有开始</strong>
          <p>{{ analysisError }}</p>
          <p>当前选择和岗位 JD 仍保留在本页，可以直接重试。</p>
          <div class="home-state-actions">
            <el-button
              type="primary"
              plain
              :loading="startingAnalysis"
              @click="handleStartAnalysis"
            >
              重新开始分析
            </el-button>
          </div>
        </div>
      </div>

      <div v-else-if="activeAnalysis" class="home-analysis-state" :class="analysisError ? 'is-error' : 'is-running'" role="status">
        <span class="home-state-dot" />
        <div>
          <strong>{{ analysisError ? '岗位分析没有完成' : '正在分析岗位' }}</strong>
          <p v-if="analysisError">
            {{ analysisError }}
            <template v-if="!analysisTimedOut">你的简历和目标岗位信息已保存，可以直接重试，无需重新填写。</template>
          </p>
          <p v-else>{{ currentStage }}</p>
          <div v-if="analysisError" class="home-state-actions">
            <el-button
              v-if="analysisTimedOut"
              plain
              :loading="startingAnalysis"
              @click="continueWaiting()"
            >
              继续等待
            </el-button>
            <el-button
              v-else
              type="primary"
              plain
              :loading="startingAnalysis"
              @click="retryAnalysis()"
            >
              重试分析
            </el-button>
          </div>
        </div>
      </div>

      <footer v-if="resumes.length" class="home-start-actions">
        <div>
          <strong>{{ selectedResume?.originalFilename || '请选择简历' }}</strong>
          <small>系统不会为了匹配岗位编造你的经历。</small>
        </div>
        <el-button data-testid="home-start-analysis" type="primary" size="large" :loading="startingAnalysis || analysisRunning" :disabled="!canStart" @click="handleStartAnalysis">
          开始分析
        </el-button>
      </footer>
    </section>

    <section v-if="!loadFailed && resumes.length" class="home-resume-summary">
      <header>
        <div>
          <h2>我的简历</h2>
          <p>已有 {{ resumes.length }} 份简历，可以随时用于新的岗位分析。</p>
        </div>
        <el-button text type="primary" @click="router.push('/resumes')">管理简历</el-button>
      </header>
      <div class="home-resume-chips">
        <button
          v-for="resume in resumes.slice(0, 4)"
          :key="resume.id"
          type="button"
          :class="{ 'is-selected': selectedResumeId === resume.id }"
          :disabled="analysisRunning"
          @click="selectedResumeId = resume.id"
        >
          <strong>{{ resume.originalFilename }}</strong>
          <small>{{ resume.parseStatus === 'SUCCESS' ? '已准备好' : '分析时自动准备' }}</small>
        </button>
      </div>
    </section>

    <section v-if="hasJobDirectionInsight" class="home-insight-card app-card">
      <div>
        <h2>岗位方向洞察</h2>
        <p>近期岗位分析已积累出可参考的共同要求；它不会改变当前的单岗位分析结果。</p>
      </div>
      <el-button type="primary" plain @click="router.push('/job-direction-insights')">查看方向洞察</el-button>
    </section>
  </section>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: 24px;
}

.home-start-card {
  display: grid;
  gap: 22px;
  padding: 24px;
}

.home-start-field {
  display: grid;
  gap: 10px;
}

.home-field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.home-start-field > label,
.home-field-head > label {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
}

.home-resume-select {
  width: 100%;
}

.home-inline-upload {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 16px;
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
}

.home-inline-upload p {
  flex: 1 1 100%;
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 14px;
}

.home-file-picker {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  padding: 0 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  background: var(--app-surface);
}

.home-file-picker input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.home-file-picker:focus-within {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}

.home-start-actions,
.home-resume-summary > header,
.home-insight-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.home-start-actions > div {
  display: grid;
  gap: 4px;
}

.home-start-actions small,
.home-resume-summary p,
.home-analysis-state p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.home-analysis-state {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
}

.home-analysis-state.is-error {
  border-color: var(--el-color-danger-light-7);
  background: var(--app-danger-soft);
}

.home-state-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 999px;
  background: var(--app-primary);
}

.home-analysis-state.is-running .home-state-dot {
  animation: home-pulse 1.4s ease-in-out infinite;
}

.home-analysis-state.is-error .home-state-dot {
  background: var(--app-danger);
  animation: none;
}

.home-analysis-state > div {
  display: grid;
  gap: 6px;
}

.home-state-actions {
  display: flex;
  gap: 10px;
  margin-top: 2px;
}

.home-resume-summary {
  display: grid;
  gap: 14px;
}

.home-resume-summary h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.home-insight-card {
  padding: 18px 20px;
}

.home-insight-card > div {
  display: grid;
  gap: 5px;
}

.home-insight-card h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.home-insight-card p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.home-resume-chips {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 12px;
}

.home-resume-chips button {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
  background: var(--app-surface);
}

.home-resume-chips button.is-selected {
  border-color: var(--app-primary);
  background: var(--app-primary-soft);
}

.home-resume-chips strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-resume-chips small {
  color: var(--app-text-secondary);
}

@keyframes home-pulse {
  0%, 100% { opacity: 0.4; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

@media (max-width: 760px) {
  .home-start-actions,
  .home-resume-summary > header,
  .home-insight-card {
    flex-direction: column;
    align-items: stretch;
  }

  .home-start-actions .el-button {
    width: 100%;
  }
}
</style>
