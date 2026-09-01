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
const previewUrl = ref<string | null>(null)
/** 记录当前预览对应的 (task, revision, template)，任一变化即视为失效。 */
const previewKey = ref('')
const previewReceipt = ref<string | null>(null)
const previewPreflight = ref<ExportPreflight | null>(null)
let previewRequestSequence = 0
const artifacts = ref<ExportArtifact[]>([])
const artifactsLoading = ref(false)
const artifactsLoaded = ref(false)
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
const canOperate = computed(
  () => props.status === 'saved' && props.revision !== null && props.revision > 0,
)
const currentPreviewKey = computed(
  () => `${props.optimizationTaskId}:${props.revision}:${templateId.value}`,
)
const canExport = computed(
  () =>
    canOperate.value &&
    previewKey.value === currentPreviewKey.value &&
    previewReceipt.value !== null &&
    previewPreflight.value !== null &&
    !previewPreflight.value.missingContact &&
    !previewPreflight.value.overflowDetected &&
    !previewPreflight.value.orphanFinalPage &&
    !previewPreflight.value.readabilityTooSmall &&
    !previewPreflight.value.needsReview,
)

const blockingPreflightMessages = computed(() => {
  const result = previewPreflight.value
  if (!result) return []
  const messages: string[] = []
  if (result.missingContact) messages.push('缺少可用联系方式，需要补充电话或邮箱')
  if (result.overflowDetected) messages.push('检测到文字超出页面边界，需要调整内容或编辑器字段')
  if (result.orphanFinalPage) messages.push('末页内容过少，需要调整内容分页')
  if (result.readabilityTooSmall) messages.push('部分字号低于可读下限，需要调整内容')
  if (result.needsReview) messages.push('简历内容仍需确认，完成确认后才能导出')
  return messages
})

const advisoryPreflightMessages = computed(() => {
  const result = previewPreflight.value
  if (!result || !result.pageLimitExceeded) return []
  return ['当前 PDF 超过建议的 2 页，可以导出，但建议检查内容取舍']
})

const allPreflightMessages = computed(() => [
  ...blockingPreflightMessages.value,
  ...advisoryPreflightMessages.value,
])

const preflightStatusLabel = computed(() => {
  if (!previewPreflight.value) return '尚未检查'
  if (blockingPreflightMessages.value.length) return '需要处理后才能导出'
  if (advisoryPreflightMessages.value.length) return '可以导出，建议检查页数'
  return '可以导出'
})

const preflightStatusClass = computed(() => {
  if (!previewPreflight.value) return 'is-pending'
  if (blockingPreflightMessages.value.length) return 'is-blocked'
  if (advisoryPreflightMessages.value.length) return 'is-advisory'
  return 'is-ready'
})

const operateHint = computed(() => {
  if (props.status === 'saved' && (props.revision === null || props.revision <= 0)) {
    return '请先完成一次保存，再生成预览'
  }
  switch (props.status) {
    case 'dirty':
    case 'saving':
      return '正在保存，保存完成后可生成预览'
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
}

const invalidatePreview = () => {
  previewRequestSequence += 1
  revokePreviewUrl()
  previewKey.value = ''
  previewReceipt.value = null
  previewPreflight.value = null
  if (
    operationFailure.value?.operation === 'preview' ||
    operationFailure.value?.operation === 'export'
  ) {
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
  requestSequence === previewRequestSequence &&
  props.status === 'saved' &&
  props.optimizationTaskId === taskAtRequest &&
  props.revision === revisionAtRequest &&
  templateId.value === templateAtRequest

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
    if (
      !isCurrentRequest(requestSequence, taskAtRequest, revisionAtRequest, templateAtRequest) ||
      preview.contentRevision !== revisionAtRequest
    ) {
      return
    }
    revokePreviewUrl()
    previewUrl.value = URL.createObjectURL(new Blob([preview.blob], { type: 'application/pdf' }))
    previewKey.value = `${taskAtRequest}:${revisionAtRequest}:${templateAtRequest}`
    previewReceipt.value = preview.previewReceipt
    previewPreflight.value = preview.preflight
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
    artifactsLoaded.value = true
  } catch (error) {
    artifactsLoadError.value = error instanceof Error ? error.message : '暂时无法读取导出记录'
  } finally {
    artifactsLoading.value = false
  }
}

const handleHistoryToggle = (event: Event) => {
  const open = (event.target as HTMLDetailsElement).open
  if (open && !artifactsLoaded.value && !artifactsLoading.value) {
    void refreshArtifacts()
  }
}

const handleExport = async () => {
  if (
    !canExport.value ||
    exporting.value ||
    props.revision === null ||
    previewReceipt.value === null
  )
    return
  operationFailure.value = null
  exporting.value = true
  const taskAtRequest = props.optimizationTaskId
  const revisionAtRequest = props.revision
  const templateAtRequest = templateId.value
  const receiptAtRequest = previewReceipt.value
  const requestSequence = previewRequestSequence
  const requestIsCurrent = () =>
    isCurrentRequest(requestSequence, taskAtRequest, revisionAtRequest, templateAtRequest) &&
    previewKey.value === `${taskAtRequest}:${revisionAtRequest}:${templateAtRequest}` &&
    previewReceipt.value === receiptAtRequest
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
    if (!requestIsCurrent()) return
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
  if (failure.operation === 'preview') void handlePreview()
  else if (failure.operation === 'export') void handleExport()
  else if (failure.operation === 'download' && failure.artifact)
    void handleDownloadArtifact(failure.artifact)
  else if (failure.operation === 'delete' && failure.artifact)
    void handleDeleteArtifact(failure.artifact)
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
  // 进入 Preview mode 即生成一次预览；操作栏仍保留“重新预览”供模板切换后使用。
  if (canOperate.value) void handlePreview()
})

onBeforeUnmount(() => {
  invalidatePreview()
})
</script>

<template>
  <div class="preview-export">
    <header class="preview-toolbar">
      <div class="preview-template-control">
        <span class="control-label">模板</span>
        <el-radio-group v-model="templateId" size="small" aria-label="选择简历模板">
          <el-radio-button
            v-for="option in TEMPLATE_OPTIONS"
            :key="option.value"
            :value="option.value"
            :data-testid="`preview-template-${option.value}`"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="preview-toolbar-actions">
        <span v-if="previewPreflight" class="preview-page-count"
          >{{ previewPreflight.pageCount }} 页</span
        >
        <el-button
          size="small"
          :loading="previewLoading"
          :disabled="!canOperate"
          @click="handlePreview"
        >
          {{ previewUrl ? '重新预览' : '生成预览' }}
        </el-button>
        <el-button type="primary" :loading="exporting" :disabled="!canExport" @click="handleExport">
          导出 PDF
        </el-button>
      </div>
    </header>

    <p v-if="!canOperate" class="operate-hint">{{ operateHint }}</p>
    <p v-else-if="!previewPreflight && !previewLoading" class="preview-first-hint">
      先生成一次预览，系统会检查联系方式、页数和排版边界后再允许导出。
    </p>

    <ErrorState
      v-if="operationFailure"
      compact
      :title="operationFailureTitle"
      :description="operationFailure.message"
      :action-text="operationFailureActionText"
      @action="retryFailedOperation"
    />

    <div
      v-if="previewPreflight"
      class="preflight-status"
      :class="preflightStatusClass"
      role="status"
    >
      <div class="preflight-status-heading">
        <strong>{{ preflightStatusLabel }}</strong>
      </div>
      <p v-if="blockingPreflightMessages.length" class="preflight-blocked-copy">
        处理以下问题后才能导出：{{ blockingPreflightMessages[0] }}
      </p>
      <details v-if="allPreflightMessages.length" class="preflight-details">
        <summary>查看检查</summary>
        <ul>
          <li v-for="message in allPreflightMessages" :key="message">{{ message }}</li>
        </ul>
      </details>
    </div>

    <div class="preview-layout">
      <section
        class="preview-document"
        aria-label="PDF 文档预览"
        :aria-busy="previewLoading"
      >
        <div v-if="previewLoading" class="preview-placeholder" role="status">
          <strong>正在生成预览…</strong>
          <span>使用最近一次成功保存的简历内容。</span>
        </div>
        <iframe
          v-else-if="previewUrl"
          :src="previewUrl"
          class="preview-frame"
          title="简历 PDF 预览"
        />
        <div v-else class="preview-placeholder">
          <strong>预览将在这里显示</strong>
          <span>生成预览后，可以在导出前检查最终文档。</span>
        </div>
      </section>

      <aside class="preview-supporting-content">
        <p class="preview-note">
          PDF
          使用最近一次成功保存的内容；未保存的编辑和未采纳的建议不会进入文档。修改内容或模板后需要重新预览。
        </p>

        <details class="export-history" @toggle="handleHistoryToggle">
          <summary>导出记录</summary>
          <div class="export-history-content">
            <p v-if="artifactsLoading && artifacts.length === 0" class="artifact-status">
              正在读取导出记录…
            </p>
            <ErrorState
              v-else-if="artifactsLoadError"
              compact
              title="导出记录加载失败"
              :description="artifactsLoadError"
              action-text="重新加载"
              @action="refreshArtifacts"
            />
            <p v-else-if="artifacts.length === 0" class="artifact-status">还没有导出记录。</p>
            <ul v-else class="artifact-list">
              <li v-for="artifact in artifacts" :key="artifact.id">
                <div class="artifact-info">
                  <span class="artifact-name">{{ artifact.fileName }}</span>
                  <span class="artifact-meta">
                    {{ TEMPLATE_LABELS[artifact.templateId] }} · {{ artifact.pageCount }} 页 ·
                    {{ formatCreatedAt(artifact.createdAt) }}
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
                  <el-button
                    size="small"
                    type="danger"
                    plain
                    @click="handleDeleteArtifact(artifact)"
                  >
                    {{ artifact.status === 'DELETE_PENDING' ? '重试删除' : '删除' }}
                  </el-button>
                </div>
              </li>
            </ul>
          </div>
        </details>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.preview-export {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--app-border);
}

.preview-template-control,
.preview-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.preview-toolbar-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.control-label,
.preview-page-count {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.preview-page-count {
  color: var(--app-text);
  font-weight: 700;
}

.operate-hint,
.preview-first-hint,
.preview-note,
.artifact-status {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.operate-hint {
  color: var(--app-warning);
  font-weight: 600;
}

.preflight-status {
  display: grid;
  gap: 7px;
  padding: 12px 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
}

.preflight-status.is-ready {
  border-color: var(--el-color-success-light-7);
  background: var(--app-success-soft);
}

.preflight-status.is-advisory {
  border-color: var(--el-color-warning-light-7);
  background: var(--app-warning-soft);
}

.preflight-status.is-blocked {
  border-color: var(--el-color-danger-light-7);
  background: var(--app-danger-soft);
}

.preflight-status-heading {
  display: flex;
  gap: 12px;
  color: var(--app-text);
  font-size: 13px;
}

.preflight-blocked-copy {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.preflight-details {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.preflight-details summary {
  width: fit-content;
  color: var(--app-primary);
  font-weight: 700;
  cursor: pointer;
}

.preflight-details ul {
  display: grid;
  gap: 4px;
  margin: 7px 0 0;
  padding-left: 18px;
  line-height: 1.6;
}

.preview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 20px;
  align-items: start;
  min-width: 0;
}

.preview-document {
  display: grid;
  min-width: 0;
  min-height: min(720px, calc(100dvh - 260px));
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  overflow: hidden;
  background: var(--app-pdf-canvas);
}

.preview-frame {
  display: block;
  width: 100%;
  height: min(900px, calc(100dvh - 250px));
  min-height: 680px;
  border: 0;
  background: var(--app-surface);
}

.preview-placeholder {
  display: grid;
  min-height: min(720px, calc(100dvh - 260px));
  place-items: center;
  align-content: center;
  gap: 8px;
  padding: 28px;
  color: var(--app-text-secondary);
  text-align: center;
  background: var(--app-surface-soft);
}

.preview-placeholder strong {
  color: var(--app-text);
  font-size: 15px;
}

.preview-placeholder span {
  font-size: 12px;
}

.preview-supporting-content {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.export-history {
  border-top: 1px solid var(--app-border);
  padding-top: 12px;
}

.export-history summary {
  width: fit-content;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.export-history-content {
  display: grid;
  gap: 10px;
  padding-top: 12px;
}

.artifact-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.artifact-list li {
  display: grid;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px solid var(--app-border-soft);
}

.artifact-info {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.artifact-name {
  overflow-wrap: anywhere;
  color: var(--app-text);
  font-size: 12px;
}

.artifact-meta {
  color: var(--app-text-secondary);
  font-size: 11px;
}

.artifact-actions {
  display: flex;
  gap: 7px;
}

@media (max-width: 900px) {
  .preview-layout {
    grid-template-columns: 1fr;
  }

  .preview-supporting-content {
    grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
    align-items: start;
  }
}

@media (max-width: 720px) {
  .preview-toolbar :deep(.el-button) {
    min-height: 40px;
  }

  .preview-template-control :deep(.el-radio-button__inner) {
    display: inline-flex;
    min-height: 40px;
    align-items: center;
    padding: 0 14px;
  }

  .preview-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .preview-template-control,
  .preview-toolbar-actions {
    width: 100%;
    justify-content: space-between;
  }

  .preview-toolbar-actions {
    justify-content: flex-start;
  }

  .preview-layout,
  .preview-supporting-content {
    display: grid;
    grid-template-columns: 1fr;
  }

  .preview-document {
    min-height: 620px;
  }

  .preview-frame {
    height: 72dvh;
    min-height: 620px;
  }

  .preview-placeholder {
    min-height: 620px;
  }
}
</style>
