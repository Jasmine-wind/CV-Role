<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
  { value: 'classic', label: 'Classic 经典' },
  { value: 'modern', label: 'Modern 现代' },
  { value: 'minimal', label: 'Minimal 简洁' },
]

const templateId = ref<ResumeTemplateId>('classic')
const previewLoading = ref(false)
const exporting = ref(false)
const previewDialogVisible = ref(false)
const previewUrl = ref<string | null>(null)
/** 记录当前预览对应的 (task, revision, template)，任一变化即视为失效。 */
const previewKey = ref('')
const previewReceipt = ref<string | null>(null)
const previewPreflight = ref<ExportPreflight | null>(null)
const previewTemplateVersion = ref<string | null>(null)
const previewRendererVersion = ref<string | null>(null)
let previewRequestSequence = 0
const artifacts = ref<ExportArtifact[]>([])
const artifactsLoading = ref(false)
const downloadingId = ref<number | null>(null)

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
  const messages = [`实际 PDF：${result.pageCount} 页`]
  if (result.missingContact) messages.push('缺少联系方式，请返回编辑器补充')
  if (result.pageLimitExceeded) messages.push('超过建议的 2 页，请检查内容取舍')
  if (result.overflowDetected) messages.push('检测到文字超出页面边界，请调整内容后重新预览')
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
  previewTemplateVersion.value = null
  previewRendererVersion.value = null
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

const handlePreview = async () => {
  if (!canOperate.value || previewLoading.value || props.revision === null) return
  previewLoading.value = true
  const taskAtRequest = props.optimizationTaskId
  const revisionAtRequest = props.revision
  const templateAtRequest = templateId.value
  const requestSequence = ++previewRequestSequence
  try {
    const preview = await previewWorkspacePdf(taskAtRequest, templateAtRequest, revisionAtRequest)
    if (
      requestSequence !== previewRequestSequence ||
      props.status !== 'saved' ||
      props.optimizationTaskId !== taskAtRequest ||
      props.revision !== revisionAtRequest ||
      preview.contentRevision !== revisionAtRequest ||
      templateId.value !== templateAtRequest
    ) {
      return
    }
    revokePreviewUrl()
    previewUrl.value = URL.createObjectURL(new Blob([preview.blob], { type: 'application/pdf' }))
    previewKey.value = `${taskAtRequest}:${revisionAtRequest}:${templateAtRequest}`
    previewReceipt.value = preview.previewReceipt
    previewPreflight.value = preview.preflight
    previewTemplateVersion.value = preview.templateVersion
    previewRendererVersion.value = preview.rendererVersion
    previewDialogVisible.value = true
  } catch (error) {
    if (isStaleError(error)) {
      handleStale(error.message)
    } else {
      ElMessage.error(error instanceof Error ? error.message : '预览生成失败，请稍后重试')
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
  try {
    artifacts.value = await listWorkspaceArtifacts(props.optimizationTaskId)
  } catch {
    artifacts.value = []
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
  exporting.value = true
  try {
    const artifact = await exportWorkspacePdf(props.optimizationTaskId, {
      templateId: templateId.value,
      expectedRevision: props.revision,
      previewReceipt: previewReceipt.value,
    })
    const blob = await downloadArtifactPdf(artifact.id)
    triggerBrowserDownload(blob, artifact.fileName)
    await refreshArtifacts()
    ElMessage.success('PDF 导出成功')
  } catch (error) {
    if (isStaleError(error)) {
      handleStale(error.message)
    } else {
      ElMessage.error(error instanceof Error ? error.message : '导出失败，请稍后重试')
    }
  } finally {
    exporting.value = false
  }
}

const handleDownloadArtifact = async (artifact: ExportArtifact) => {
  if (downloadingId.value !== null) return
  downloadingId.value = artifact.id
  try {
    const blob = await downloadArtifactPdf(artifact.id)
    triggerBrowserDownload(blob, artifact.fileName)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '下载失败，请稍后重试')
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
  try {
    await deleteWorkspaceArtifact(artifact.id)
    await refreshArtifacts()
    ElMessage.success('已删除导出文件')
  } catch (error) {
    await refreshArtifacts()
    ElMessage.error(error instanceof Error ? error.message : '删除失败，请稍后重试')
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
      <div v-if="previewPreflight" class="preflight-result" role="status">
        <strong>导出前检查</strong>
        <ul>
          <li v-for="message in preflightMessages" :key="message">{{ message }}</li>
        </ul>
        <small>模板 v{{ previewTemplateVersion }} · {{ previewRendererVersion }}</small>
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
      <p v-if="artifacts.length === 0" class="artifact-empty">还没有导出记录。</p>
      <ul v-else>
        <li v-for="artifact in artifacts" :key="artifact.id">
          <div class="artifact-info">
            <span class="artifact-name">{{ artifact.fileName }}</span>
            <span class="artifact-meta">
              {{ artifact.templateId }} · revision {{ artifact.contentRevision }} ·
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

.artifact-empty {
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
