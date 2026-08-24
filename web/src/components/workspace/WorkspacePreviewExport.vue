<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ErrorState from '@/components/common/ErrorState.vue'
import {
  deleteWorkspaceArtifact,
  downloadArtifactPdf,
  exportWorkspacePdf,
  listWorkspaceArtifacts,
  previewWorkspacePdf,
} from '@/api/workspace'
import type {
  ExportArtifact,
  ExportPreflight,
  ResumeTemplateId,
  WorkspaceSaveStatus,
} from '@/types/workspace'

const props = defineProps<{
  optimizationTaskId: number
  /** 最近一次成功保存的服务端内容版本号；null 表示内容尚未就绪。 */
  revision: number | null
  status: WorkspaceSaveStatus
}>()

/** revision 过期（其它端修改了内容）时通知工作区同步服务端版本。 */
const emit = defineEmits<{ stale: [] }>()

const TEMPLATE_OPTIONS: { value: ResumeTemplateId; label: string }[] = [
  { value: 'classic', label: '经典' },
  { value: 'modern', label: '现代' },
  { value: 'minimal', label: '简洁' },
]

const TEMPLATE_LABELS: Record<ResumeTemplateId, string> = {
  classic: '经典',
  modern: '现代',
  minimal: '简洁',
}

const templateId = ref<ResumeTemplateId>('classic')
const previewLoading = ref(false)
const exporting = ref(false)
const previewDialogVisible = ref(false)
const previewUrl = ref<string | null>(null)
/** 记录当前预览对应的 (task, revision, template)，任一变化即视为失效。 */
const previewKey = ref('')
const previewReceipt = ref<string | null>(null)
const previewPreflight = ref<ExportPreflight | null>(null)
let previewRequestSequence = 0
const artifacts = ref<ExportArtifact[]>([])
const artifactsLoading = ref(false)
const artifactsLoadError = ref<string | null>(null)
const downloadingId = ref<number | null>(null)

type OperationFailure = {
  operation: 'preview' | 'export' | 'download' | 'delete'
  message: string
  artifact?: ExportArtifact
}

const operationFailure = ref<OperationFailure | null>(null)

// 只有最近一次保存成功后，才允许用服务端内容生成 Preview / Export；
// dirty / saving / failed / conflict 时前端草稿一律不得进入渲染链路。
const canOperate = computed(() => props.status === 'saved' && props.revision !== null)
const currentPreviewKey = computed(
  () => `${props.optimizationTaskId}:${props.revision}:${templateId.value}`,
)
const canExport = computed(
  () =>
    canOperate.value &&
    previewKey.value === currentPreviewKey.value &&
    previewReceipt.value !== null,
)

const preflightMessages = computed(() => {
  const result = previewPreflight.value
  if (!result) return []
  const messages = [`编译成功，实际 PDF：${result.pageCount} 页`]
  if (result.missingContact) messages.push('缺少联系方式，请返回编辑器补充')
  if (result.pageLimitExceeded) messages.push('超过建议的 2 页，请检查内容取舍')
  if (result.overflowDetected) messages.push('检测到文字超出页面边界，请调整内容后重新预览')
  if (messages.length === 1) messages.push('联系方式、页数与排版边界均未发现问题')
  return messages
})

const operateHint = computed(() => {
  switch (props.status) {
    case 'dirty':
    case 'saving':
      return '正在保存，保存完成后可预览或导出'
    case 'failed':
      return '保存失败，请先重试保存'
    case 'conflict':
      return '存在保存冲突，请先处理冲突'
    default:
      return ''
  }
})

const revokePreviewUrl = () => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = null
  }
  previewDialogVisible.value = false
}

const invalidatePreview = () => {
  previewRequestSequence += 1
  revokePreviewUrl()
  previewKey.value = ''
  previewReceipt.value = null
  previewPreflight.value = null
  if (operationFailure.value?.operation === 'preview' || operationFailure.value?.operation === 'export') {
    operationFailure.value = null
  }
}

type ApiErrorLike = Error & { code?: number }

const isStaleError = (error: unknown): error is ApiErrorLike =>
  typeof error === 'object' &&
  error !== null &&
  'message' in error &&
  (error as ApiErrorLike).code === 409

const handleStale = (message: string) => {
  invalidatePreview()
  ElMessage.warning(message)
  emit('stale')
}

const isCurrentRequest = (
  requestSequence: number,
  taskAtRequest: number,
  revisionAtRequest: number,
  templateAtRequest: ResumeTemplateId,
) =>
  requestSequence === previewRequestSequence
  && props.status === 'saved'
  && props.optimizationTaskId === taskAtRequest
  && props.revision === revisionAtRequest
  && templateId.value === templateAtRequest

const handlePreview = async () => {
  if (!canOperate.value || previewLoading.value || props.revision === null) return
  operationFailure.value = null
  previewLoading.value = true
  const taskAtRequest = props.optimizationTaskId
  const revisionAtRequest = props.revision
  const templateAtRequest = templateId.value
  const requestSequence = ++previewRequestSequence
  try {
    const preview = await previewWorkspacePdf(taskAtRequest, templateAtRequest, revisionAtRequest)
    if (!isCurrentRequest(requestSequence, taskAtRequest, revisionAtRequest, templateAtRequest)
      || preview.contentRevision !== revisionAtRequest) {
      return
    }
    revokePreviewUrl()
    previewUrl.value = URL.createObjectURL(new Blob([preview.blob], { type: 'application/pdf' }))
    previewKey.value = `${taskAtRequest}:${revisionAtRequest}:${templateAtRequest}`
    previewReceipt.value = preview.previewReceipt
    previewPreflight.value = preview.preflight
    previewDialogVisible.value = true
  } catch (error) {
    if (!isCurrentRequest(requestSequence, taskAtRequest, revisionAtRequest, templateAtRequest)) {
      return
    }
    const message = error instanceof Error ? error.message : '预览生成失败，请稍后重试'
    if (isStaleError(error)) {
      handleStale(message)
    } else {
      operationFailure.value = { operation: 'preview', message }
      ElMessage.error(message)
    }
  } finally {
    previewLoading.value = false
  }
}

const triggerBrowserDownload = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

const refreshArtifacts = async () => {
  artifactsLoading.value = true
  artifactsLoadError.value = null
  try {
    artifacts.value = await listWorkspaceArtifacts(props.optimizationTaskId)
  } catch (error) {
    artifactsLoadError.value = error instanceof Error ? error.message : '暂时无法读取导出记录'
  } finally {
    artifactsLoading.value = false
  }
}

const handleExport = async () => {
  if (
    !canExport.value ||
    exporting.value ||
    props.revision === null ||
    previewReceipt.value === null
  ) return
  operationFailure.value = null
  exporting.value = true
  const taskAtRequest = props.optimizationTaskId
  const revisionAtRequest = props.revision
  const templateAtRequest = templateId.value
  const receiptAtRequest = previewReceipt.value
  const requestSequence = previewRequestSequence
  const requestIsCurrent = () =>
    isCurrentRequest(requestSequence, taskAtRequest, revisionAtRequest, templateAtRequest)
    && previewKey.value === `${taskAtRequest}:${revisionAtRequest}:${templateAtRequest}`
    && previewReceipt.value === receiptAtRequest
  let exportedArtifact: ExportArtifact | null = null
  try {
    exportedArtifact = await exportWorkspacePdf(taskAtRequest, {
      templateId: templateAtRequest,
      expectedRevision: revisionAtRequest,
      previewReceipt: receiptAtRequest,
    })
    await refreshArtifacts()
    // 导出请求在本地编辑、任务、模板或预览凭证变化后完成时，绝不自动下载旧版本。
    if (!requestIsCurrent()) return
    const blob = await downloadArtifactPdf(exportedArtifact.id)
    if (!requestIsCurrent()) return
    triggerBrowserDownload(blob, exportedArtifact.fileName)
    ElMessage.success('PDF 导出成功')
  } catch (error) {
    if (!requestIsCurrent()) {
      return
    }
    const message = error instanceof Error ? error.message : '导出失败，请稍后重试'
    if (!exportedArtifact && isStaleError(error)) {
      handleStale(message)
    } else {
      operationFailure.value = exportedArtifact
        ? { operation: 'download', message, artifact: exportedArtifact }
        : { operation: 'export', message }
      ElMessage.error(message)
    }
  } finally {
    exporting.value = false
  }
}

const handleDownloadArtifact = async (artifact: ExportArtifact) => {
  if (downloadingId.value !== null) return
  operationFailure.value = null
  downloadingId.value = artifact.id
  try {
    const blob = await downloadArtifactPdf(artifact.id)
    triggerBrowserDownload(blob, artifact.fileName)
  } catch (error) {
    const message = error instanceof Error ? error.message : '下载失败，请稍后重试'
    operationFailure.value = { operation: 'download', message, artifact }
    ElMessage.error(message)
  } finally {
    downloadingId.value = null
  }
}

const handleDeleteArtifact = async (artifact: ExportArtifact) => {
  try {
    await ElMessageBox.confirm('删除后该 PDF 不可恢复，是否继续？', '删除导出文件', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  operationFailure.value = null
  try {
    await deleteWorkspaceArtifact(artifact.id)
    await refreshArtifacts()
    ElMessage.success('已删除导出文件')
  } catch (error) {
    await refreshArtifacts()
    const message = error instanceof Error ? error.message : '删除失败，请稍后重试'
    operationFailure.value = { operation: 'delete', message, artifact }
    ElMessage.error(message)
  }
}

const operationFailureTitle = computed(() => {
  switch (operationFailure.value?.operation) {
    case 'preview':
      return 'PDF 预览未生成'
    case 'export':
      return 'PDF 导出失败'
    case 'download':
      return 'PDF 下载失败'
    case 'delete':
      return '删除导出文件失败'
    default:
      return '操作失败'
  }
})

const operationFailureActionText = computed(() => {
  switch (operationFailure.value?.operation) {
    case 'preview':
      return '重新预览'
    case 'export':
      return '重新导出'
    case 'download':
      return '重新下载'
    case 'delete':
      return '重试删除'
    default:
      return '重试'
  }
})

const retryFailedOperation = () => {
  const failure = operationFailure.value
  if (!failure) return
  if (failure.operation === 'preview') {
    void handlePreview()
  } else if (failure.operation === 'export') {
    void handleExport()
  } else if (failure.operation === 'download' && failure.artifact) {
    void handleDownloadArtifact(failure.artifact)
  } else if (failure.operation === 'delete' && failure.artifact) {
    void handleDeleteArtifact(failure.artifact)
  }
}

const formatFileSize = (size: number) => {
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(size / 1024))} KB`
}

const formatCreatedAt = (value: string) => value.replace('T', ' ').slice(0, 16)

// 状态、revision、模板或任务变化时立即使旧预览及仍在途的响应失效。
watch(
  () => [props.status, props.revision, templateId.value, props.optimizationTaskId],
  () => {
    if (props.status !== 'saved' || previewKey.value !== currentPreviewKey.value) {
      invalidatePreview()
    }
  },
)

onMounted(() => {
  void refreshArtifacts()
})

onBeforeUnmount(() => {
  invalidatePreview()
})
</script>

<template>
  <div class="preview-export">
    <section class="preview-export-controls">
      <div class="control-row">
        <span class="control-label">模板</span>
        <el-radio-group v-model="templateId" size="small">
          <el-radio-button v-for="option in TEMPLATE_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="control-row">
        <el-button
          type="primary"
          :loading="previewLoading"
          :disabled="!canOperate"
          @click="handlePreview"
        >
          预览 PDF
        </el-button>
        <el-button :loading="exporting" :disabled="!canExport" @click="handleExport">
          导出 PDF
        </el-button>
      </div>
      <p v-if="!canOperate" class="operate-hint">{{ operateHint }}</p>
      <ErrorState
        v-if="operationFailure"
        compact
        :title="operationFailureTitle"
        :description="operationFailure.message"
        :action-text="operationFailureActionText"
        @action="retryFailedOperation"
      />
      <div v-if="previewPreflight" class="preflight-result" role="status">
        <strong>导出前检查</strong>
        <ul>
          <li v-for="message in preflightMessages" :key="message">{{ message }}</li>
        </ul>
        <small>以上问题不会自动修改内容；告警不阻止导出，编译失败会阻止导出。</small>
      </div>
      <p class="preview-note">
        预览与导出均使用最近一次成功保存的内容；未保存的编辑和未采纳的 AI 建议不会进入 PDF。修改内容或模板后必须重新预览。
      </p>
    </section>

    <section class="artifact-list">
      <div class="artifact-header">
        <h3>已导出文件</h3>
        <el-button text size="small" :loading="artifactsLoading" @click="refreshArtifacts">刷新</el-button>
      </div>
      <p v-if="artifactsLoading && artifacts.length === 0" class="artifact-loading">正在读取导出记录…</p>
      <ErrorState
        v-else-if="artifactsLoadError"
        compact
        title="已导出文件加载失败"
        :description="artifactsLoadError"
        action-text="重新加载"
        @action="refreshArtifacts"
      />
      <p v-else-if="artifacts.length === 0" class="artifact-empty">还没有导出记录。</p>
      <ul v-else>
        <li v-for="artifact in artifacts" :key="artifact.id">
          <div class="artifact-info">
            <span class="artifact-name">{{ artifact.fileName }}</span>
            <span class="artifact-meta">
              {{ TEMPLATE_LABELS[artifact.templateId] }} · {{ artifact.pageCount }} 页 ·
              {{ formatFileSize(artifact.fileSize) }} · {{ formatCreatedAt(artifact.createdAt) }}
            </span>
          </div>
          <div class="artifact-actions">
            <el-button
              size="small"
              :loading="downloadingId === artifact.id"
              :disabled="artifact.status !== 'READY'"
              @click="handleDownloadArtifact(artifact)"
            >
              下载
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDeleteArtifact(artifact)">
              {{ artifact.status === 'DELETE_PENDING' ? '重试删除' : '删除' }}
            </el-button>
          </div>
        </li>
      </ul>
    </section>

    <el-dialog
      v-model="previewDialogVisible"
      title="PDF 预览"
      width="72%"
      top="5vh"
      destroy-on-close
      @closed="revokePreviewUrl"
    >
      <iframe
        v-if="previewUrl"
        :src="previewUrl"
        class="preview-frame"
        title="简历 PDF 预览"
      />
    </el-dialog>
  </div>
</template>

<style scoped>
.preview-export {
  display: grid;
  gap: 20px;
}

.preview-export-controls {
  display: grid;
  gap: 12px;
}

.control-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.control-label {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.operate-hint {
  margin: 0;
  color: var(--el-color-warning);
  font-size: 12px;
}

.preflight-result {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  font-size: 12px;
}

.preflight-result ul {
  margin: 6px 0;
  padding-left: 18px;
}

.preview-note {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 12px;
}

.artifact-list {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 14px;
}

.artifact-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.artifact-header h3 {
  margin: 0;
  font-size: 14px;
  color: var(--app-navy);
}

.artifact-empty,
.artifact-loading {
  margin: 10px 0 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.artifact-list ul {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 10px;
}

.artifact-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
}

.artifact-info {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.artifact-name {
  font-size: 13px;
  color: var(--app-text);
  word-break: break-all;
}

.artifact-meta {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.artifact-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.preview-frame {
  width: 100%;
  height: 72vh;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
</style>
