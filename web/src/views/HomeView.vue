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
const preparationTaskIds = ref<Record<number, number>>({})
const preparationMessages = ref<Record<number, string>>({})
const hasJobDirectionInsight = ref(false)
let analysisPolling: AsyncTaskPollingController | null = null
const preparationPolling = new Map<number, AsyncTaskPollingController>()

const selectedResume = computed(
  () => resumes.value.find((item) => item.id === selectedResumeId.value) ?? null,
)
const otherResumes = computed(() =>
  resumes.value.filter((item) => item.id !== selectedResumeId.value),
)
const resumeStatusLabel = (resume: ResumeListItem | null) => {
  if (!resume) return '请选择简历'
  if (resume.qualityStatus === 'PENDING') return '正在准备'
  if (resume.qualityStatus === 'NEEDS_REVIEW') return '需要确认'
  if (resume.parseStatus === 'SUCCESS' && resume.canonicalReady === false) return '需要重新解析'
  if (resume.parseStatus === 'SUCCESS' && resume.qualityStatus === 'READY') return '已准备好'
  if (resume.parseStatus === 'FAILED' || resume.qualityStatus === 'FAILED') return '准备失败'
  return '等待准备'
}
const selectedResumeStatus = computed(() => resumeStatusLabel(selectedResume.value))
const preparationTaskId = computed(() =>
  selectedResumeId.value == null
    ? null
    : (preparationTaskIds.value[selectedResumeId.value] ?? null),
)
const preparationMessage = computed(() =>
  selectedResumeId.value == null
    ? null
    : (preparationMessages.value[selectedResumeId.value] ?? null),
)
const selectedResumeReady = computed(
  () =>
    selectedResume.value?.parseStatus === 'SUCCESS' &&
    selectedResume.value.qualityStatus === 'READY' &&
    selectedResume.value.canonicalReady !== false,
)
const analysisRunning = computed(() => {
  const status = analysisTask.value?.status
  return Boolean(activeAnalysis.value && (!status || status === 'PENDING' || status === 'RUNNING'))
})
const canStart = computed(() =>
  Boolean(
    selectedResumeId.value &&
    jobDescription.value.trim() &&
    selectedResumeReady.value &&
    !analysisRunning.value &&
    !startingAnalysis.value &&
    !preparationTaskId.value,
  ),
)
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
    } else if (
      !selectedResumeId.value ||
      !resumes.value.some((item) => item.id === selectedResumeId.value)
    ) {
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

const clearPreparationState = (resumeId: number) => {
  const nextTaskIds = { ...preparationTaskIds.value }
  delete nextTaskIds[resumeId]
  preparationTaskIds.value = nextTaskIds
  const nextMessages = { ...preparationMessages.value }
  delete nextMessages[resumeId]
  preparationMessages.value = nextMessages
  preparationPolling.delete(resumeId)
}

const startPreparationPolling = (resumeId: number, taskId: number) => {
  preparationPolling.get(resumeId)?.stop()
  preparationTaskIds.value = { ...preparationTaskIds.value, [resumeId]: taskId }
  preparationMessages.value = { ...preparationMessages.value, [resumeId]: '正在读取简历内容' }
  const controller = startAsyncTaskPolling({
    taskId,
    timeoutMs: 5 * 60 * 1000,
    onUpdate: (task) => {
      preparationMessages.value = {
        ...preparationMessages.value,
        [resumeId]: task.message || '正在准备简历',
      }
    },
    onSuccess: async () => {
      clearPreparationState(resumeId)
      await loadResumes(resumeId)
      ElMessage.success('简历已准备好，可以开始分析')
    },
    onFailed: async (task) => {
      clearPreparationState(resumeId)
      await loadResumes(resumeId)
      ElMessage.error(task.errorMessage || '未能读取简历，请前往“我的简历”重试')
    },
    onTimeout: () => {
      clearPreparationState(resumeId)
      ElMessage.warning('简历仍在后台准备，请稍后再开始分析')
    },
    onError: (error) => {
      clearPreparationState(resumeId)
      ElMessage.error(error instanceof Error ? error.message : '获取简历状态失败')
    },
  })
  preparationPolling.set(resumeId, controller)
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
    if (
      stored.taskId &&
      stored.optimizationTaskId &&
      stored.sourceResumeVersionId &&
      stored.targetResumeVersionId &&
      stored.jobTargetId
    ) {
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
  const pendingResumes = resumes.value.filter(
    (resume) => resume.parseStatus === 'PENDING' || resume.qualityStatus === 'PENDING',
  )
  await Promise.all(
    pendingResumes.map(async (resume) => {
      try {
        const task = await requestResumePreparation(resume.id)
        startPreparationPolling(resume.id, task.taskId)
      } catch {
        // The resume page exposes an explicit retry when the background task cannot be resumed.
      }
    }),
  )
  restoreActiveAnalysis()
  void loadInsightAvailability()
})

onUnmounted(() => {
  analysisPolling?.stop()
  preparationPolling.forEach((controller) => controller.stop())
  preparationPolling.clear()
})
</script>

<template>
  <section class="home-page">
    <PageHeader
      title="开始岗位优化"
      description="选择一份真实简历，提供目标岗位 JD，系统会完成准备并给出逐条可核对的分析结果。"
    />

    <SkeletonBlock v-if="loading && !resumes.length" title :rows="5" />

    <ErrorState
      v-else-if="loadFailed"
      title="简历列表加载失败"
      description="暂时无法读取你的简历列表，这不影响已保存的简历。"
      action-text="重新加载"
      @action="loadResumes()"
    />

    <section v-else class="home-composer" aria-label="岗位优化输入">
      <div class="home-composer-grid">
        <section class="home-source-column" aria-labelledby="home-source-title">
          <header class="home-section-heading">
            <span class="home-section-label">RESUME SOURCE</span>
            <h3 id="home-source-title">我的简历</h3>
            <p>选择一份真实材料作为本次岗位分析的基础。</p>
          </header>

          <div v-if="selectedResume" class="home-source-current">
            <span class="home-source-indicator" aria-hidden="true" />
            <div>
              <strong>{{ selectedResume.originalFilename }}</strong>
              <span>{{ selectedResume.fileType }} · {{ selectedResumeStatus }}</span>
            </div>
          </div>

          <div
            v-if="otherResumes.length"
            class="home-resume-options"
            role="listbox"
            aria-label="选择其他简历"
          >
            <span class="home-options-label">其他简历</span>
            <button
              v-for="resume in otherResumes"
              :key="resume.id"
              type="button"
              class="home-resume-option"
              :class="{ 'is-selected': selectedResumeId === resume.id }"
              :aria-selected="selectedResumeId === resume.id"
              :disabled="analysisRunning"
              role="option"
              @click="selectedResumeId = resume.id"
            >
              <span class="home-option-dot" aria-hidden="true" />
              <span class="home-option-copy">
                <strong>{{ resume.originalFilename }}</strong>
                <small>{{ resume.fileType }} · {{ resumeStatusLabel(resume) }}</small>
              </span>
              <span v-if="selectedResumeId === resume.id" class="home-option-check">当前</span>
            </button>
          </div>

          <button
            v-if="resumes.length"
            type="button"
            class="home-upload-trigger"
            :disabled="analysisRunning"
            @click="uploadRowVisible = !uploadRowVisible"
          >
            <span aria-hidden="true">＋</span>
            {{ uploadRowVisible ? '收起上传' : '上传另一份简历' }}
          </button>

          <div v-if="!resumes.length || uploadRowVisible" class="home-inline-upload">
            <p v-if="!resumes.length">先上传一份真实简历。上传后系统会自动读取内容。</p>
            <label class="home-file-picker">
              <span>{{ selectedFile?.name || '选择 PDF、DOC 或 DOCX' }}</span>
              <input
                ref="fileInput"
                data-testid="home-resume-upload"
                type="file"
                accept=".pdf,.doc,.docx"
                @change="handleFileChange"
              />
            </label>
            <el-button
              type="primary"
              :loading="uploading"
              :disabled="!selectedFile"
              @click="handleUpload"
            >
              上传简历
            </el-button>
          </div>

          <div v-if="preparationTaskId" class="home-analysis-state is-running" role="status">
            <span class="home-state-dot" />
            <div>
              <strong>正在准备简历</strong>
              <p>{{ preparationMessage }}</p>
            </div>
          </div>
        </section>

        <section class="home-target-column" aria-labelledby="home-target-title">
          <header class="home-section-heading">
            <span class="home-section-label">TARGET JOB</span>
            <h3 id="home-target-title">目标岗位 JD</h3>
            <p>粘贴完整岗位描述，包含职责、要求和加分项。</p>
          </header>

          <div v-if="resumes.length" class="home-jd-field">
            <el-input
              id="home-jd"
              v-model="jobDescription"
              type="textarea"
              :rows="11"
              maxlength="10000"
              show-word-limit
              resize="vertical"
              placeholder="粘贴完整的岗位描述，包括职责、要求和加分项……"
              :disabled="analysisRunning"
            />
            <p class="home-field-boundary">分析只依据你提供的简历材料，不会为匹配岗位编造经历。</p>
          </div>
          <p v-else class="home-target-empty">上传并准备一份简历后，再提供目标岗位 JD。</p>

          <div
            v-if="analysisError && !activeAnalysis"
            class="home-analysis-state is-error"
            role="alert"
          >
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

          <div
            v-else-if="activeAnalysis"
            class="home-analysis-state"
            :class="analysisError ? 'is-error' : 'is-running'"
            role="status"
          >
            <span class="home-state-dot" />
            <div>
              <strong>{{ analysisError ? '岗位分析没有完成' : '正在分析岗位' }}</strong>
              <p v-if="analysisError">
                {{ analysisError }}
                <template v-if="!analysisTimedOut"
                  >你的简历和目标岗位信息已保存，可以直接重试，无需重新填写。</template
                >
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
        </section>
      </div>

      <footer v-if="resumes.length" class="home-task-bar">
        <div class="home-task-boundary">
          <span class="home-state-dot is-static" aria-hidden="true" />
          <span>基于真实简历材料分析，系统不会为了匹配岗位编造经历。</span>
        </div>
        <div class="home-start-actions-buttons">
          <el-button
            v-if="selectedResume?.qualityStatus === 'NEEDS_REVIEW'"
            plain
            type="warning"
            @click="router.push('/resumes')"
          >
            先确认简历
          </el-button>
          <el-button
            data-testid="home-start-analysis"
            type="primary"
            size="large"
            :loading="startingAnalysis || analysisRunning"
            :disabled="!canStart"
            @click="handleStartAnalysis"
          >
            开始分析
          </el-button>
        </div>
      </footer>
    </section>

    <section v-if="!loadFailed && resumes.length" class="home-library-link">
      <div>
        <strong>简历库</strong>
        <span>已有 {{ resumes.length }} 份简历，可随时用于新的岗位分析。</span>
      </div>
      <el-button text type="primary" @click="router.push('/resumes')">管理简历 →</el-button>
    </section>

    <section v-if="hasJobDirectionInsight" class="home-insight-row">
      <div>
        <strong>岗位方向洞察</strong>
        <span>基于这份简历的历史岗位分析，查看重复出现的岗位要求。</span>
      </div>
      <el-button text type="primary" @click="router.push('/job-direction-insights')"
        >查看 →</el-button
      >
    </section>
  </section>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: var(--app-section-spacing);
}

.home-composer {
  display: grid;
  border-top: 1px solid var(--app-border-strong);
  border-bottom: 1px solid var(--app-border-strong);
  background: var(--app-surface);
}

.home-composer-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.38fr) minmax(0, 0.62fr);
}

.home-source-column,
.home-target-column {
  display: grid;
  align-content: start;
  gap: var(--app-space-5);
  min-width: 0;
  padding: var(--app-space-8);
}

.home-source-column {
  border-right: 1px solid var(--app-border-strong);
}

.home-section-heading {
  display: grid;
  gap: var(--app-space-1);
}

.home-section-label {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: var(--app-font-size-xs);
  font-weight: 700;
  letter-spacing: 0.06em;
}

.home-section-heading h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
  line-height: var(--app-line-height-tight);
}

.home-section-heading p,
.home-field-boundary,
.home-target-empty {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.home-source-current {
  display: flex;
  gap: var(--app-space-3);
  align-items: flex-start;
  padding-bottom: var(--app-space-4);
  border-bottom: 1px solid var(--app-border);
}

.home-source-indicator,
.home-option-dot {
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--app-success);
}

.home-source-indicator {
  width: 8px;
  height: 8px;
  margin-top: 6px;
}

.home-source-current div,
.home-option-copy {
  display: grid;
  gap: var(--app-space-1);
  min-width: 0;
}

.home-source-current strong,
.home-option-copy strong {
  overflow: hidden;
  color: var(--app-text);
  font-size: var(--app-font-size-md);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-source-current span,
.home-option-copy small {
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
}

.home-resume-options {
  display: grid;
  border-top: 1px solid var(--app-border-soft);
}

.home-options-label {
  padding: var(--app-space-3) 0 var(--app-space-1);
  color: var(--app-text-muted);
  font-size: var(--app-font-size-xs);
  font-weight: 700;
}

.home-resume-option {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  gap: var(--app-space-3);
  align-items: center;
  width: 100%;
  min-width: 0;
  border: 0;
  border-bottom: 1px solid var(--app-border-soft);
  padding: var(--app-space-3) 0;
  color: var(--app-text);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.home-resume-option:hover,
.home-resume-option:focus-visible {
  color: var(--app-text);
  background: var(--app-surface-soft);
}

.home-resume-option.is-selected {
  border-bottom-color: var(--app-primary-subtle);
  box-shadow: inset 3px 0 var(--app-primary);
  padding-left: var(--app-space-3);
  background: var(--app-primary-soft);
}

.home-option-dot {
  width: 6px;
  height: 6px;
  background: var(--app-border-strong);
}

.home-resume-option.is-selected .home-option-dot {
  background: var(--app-primary);
}

.home-option-check {
  color: var(--app-primary-active);
  font-size: var(--app-font-size-xs);
  font-weight: 700;
}

.home-upload-trigger {
  justify-self: start;
  border: 0;
  padding: 0;
  color: var(--app-primary-active);
  font: inherit;
  font-size: var(--app-font-size-sm);
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.home-upload-trigger:hover,
.home-upload-trigger:focus-visible {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.home-upload-trigger:disabled {
  color: var(--app-text-muted);
  cursor: not-allowed;
}

.home-inline-upload {
  display: grid;
  gap: var(--app-space-3);
  padding: var(--app-space-4);
  border: 1px dashed var(--app-border-strong);
  background: var(--app-surface-soft);
}

.home-inline-upload p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.home-file-picker {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  min-width: 0;
  padding: 0 var(--app-space-3);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  color: var(--app-text);
  font-size: var(--app-font-size-sm);
  font-weight: 600;
  cursor: pointer;
  background: var(--app-surface);
}

.home-file-picker span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.home-jd-field {
  display: grid;
  gap: var(--app-space-3);
  min-width: 0;
}

.home-jd-field :deep(.el-textarea__inner) {
  min-height: 250px !important;
  padding: var(--app-space-4);
  color: var(--app-text);
  font-size: var(--app-font-size-md);
  line-height: var(--app-line-height-body);
  background: var(--app-surface);
}

.home-field-boundary {
  font-size: var(--app-font-size-xs);
}

.home-target-empty {
  display: grid;
  min-height: 160px;
  place-items: center;
  border: 1px dashed var(--app-border);
  padding: var(--app-space-5);
  text-align: center;
  background: var(--app-surface-soft);
}

.home-analysis-state {
  display: flex;
  gap: var(--app-space-3);
  align-items: flex-start;
  padding: var(--app-space-4);
  border-top: 1px solid var(--app-border);
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
  flex: 0 0 auto;
  background: var(--app-primary);
}

.home-state-dot.is-static {
  background: var(--app-success);
}

.home-analysis-state.is-running .home-state-dot:not(.is-static) {
  animation: home-pulse 1.4s ease-in-out infinite;
}

.home-analysis-state.is-error .home-state-dot {
  background: var(--app-danger);
  animation: none;
}

.home-analysis-state > div {
  display: grid;
  gap: var(--app-space-2);
}

.home-analysis-state strong {
  color: var(--app-text);
  font-size: var(--app-font-size-sm);
}

.home-analysis-state p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  line-height: var(--app-line-height-body);
}

.home-state-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-2);
  margin-top: var(--app-space-1);
}

.home-task-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-5);
  padding: var(--app-space-4) var(--app-space-8);
  border-top: 1px solid var(--app-border-strong);
  background: var(--app-surface-soft);
}

.home-task-boundary {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  line-height: var(--app-line-height-body);
}

.home-start-actions-buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--app-space-2);
}

.home-library-link,
.home-insight-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-5);
  border-bottom: 1px solid var(--app-border);
  padding: var(--app-space-4) 0;
}

.home-library-link > div,
.home-insight-row > div {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-2);
  align-items: baseline;
}

.home-library-link strong,
.home-insight-row strong {
  color: var(--app-text);
  font-size: var(--app-font-size-md);
}

.home-library-link span,
.home-insight-row span {
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
}

@keyframes home-pulse {
  0%,
  100% {
    opacity: 0.4;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 760px) {
  .home-composer-grid {
    grid-template-columns: 1fr;
  }

  .home-source-column {
    border-right: 0;
    border-bottom: 1px solid var(--app-border-strong);
  }

  .home-source-column,
  .home-target-column {
    padding: var(--app-space-5) var(--app-content-gutter-narrow);
  }

  .home-jd-field :deep(.el-textarea__inner) {
    min-height: 220px !important;
  }

  .home-task-bar,
  .home-library-link,
  .home-insight-row {
    align-items: stretch;
    flex-direction: column;
  }

  .home-start-actions-buttons {
    justify-content: stretch;
  }

  .home-start-actions-buttons .el-button {
    flex: 1 1 auto;
  }
}
</style>
