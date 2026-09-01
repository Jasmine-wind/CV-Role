<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { getOptimizationAnalysisResult } from '@/api/job-analysis'
import ErrorState from '@/components/common/ErrorState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import ResumeEditor from '@/components/workspace/ResumeEditor.vue'
import WorkspacePreviewExport from '@/components/workspace/WorkspacePreviewExport.vue'
import WorkspaceSuggestions from '@/components/workspace/WorkspaceSuggestions.vue'
import type { OptimizationAnalysisResult } from '@/types/job-analysis'
import { useBulletSuggest } from '@/utils/useBulletSuggest'
import { useWorkspaceEditor } from '@/utils/useWorkspaceEditor'

const props = defineProps<{
  optimizationTaskId: number
}>()

const parsePositiveId = (value: unknown): number | null => {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

const route = useRoute()
const editor = useWorkspaceEditor(props.optimizationTaskId)
const bulletSuggest = useBulletSuggest(props.optimizationTaskId, editor)

const analysisResult = ref<OptimizationAnalysisResult | null>(null)
const analysisLoading = ref(false)
const analysisError = ref<string | null>(null)

const restoring = ref(false)
const previewPreparing = ref(false)
const previewComponentMounted = ref(false)
const workspaceMode = ref<'edit' | 'preview'>('edit')
const initialRequirementId = parsePositiveId(route.query.requirement)
const inspectorOpen = ref(initialRequirementId !== null)
const mobilePanel = ref<'editor' | 'suggestions'>(
  initialRequirementId !== null ? 'suggestions' : 'editor',
)
const selectedRequirementId = ref<number | null>(initialRequirementId)

const initialNarrow = () =>
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(max-width: 720px)').matches
const isNarrowScreen = ref(initialNarrow())
let narrowMediaQuery: MediaQueryList | null = null

const handleNarrowChange = (event: MediaQueryListEvent) => {
  isNarrowScreen.value = event.matches
  if (!event.matches) mobilePanel.value = 'editor'
}

const jobTitle = computed(() => analysisResult.value?.jobTitle ?? '简历编辑')
const suggestionCount = computed(() => {
  const analysis = analysisResult.value?.evidenceAnalysis
  return analysis ? analysis.partialEvidenceCount + analysis.noEvidenceCount : 0
})

// 岗位定向改写只对拥有正式证据分析的任务开放；旧版兼容任务由服务端 fail closed 兜底。
const suggestEnabled = computed(() => analysisResult.value?.analysisMode === 'EVIDENCE')

const saveStatusText = computed(() => {
  switch (editor.status.value) {
    case 'dirty':
      return '未保存'
    case 'saving':
      return '正在保存'
    case 'saved':
      return '已保存'
    case 'failed':
      return '保存失败'
    case 'conflict':
      return '存在冲突'
    default:
      return ''
  }
})

const saveStatusClass = computed(() => `save-status is-${editor.status.value}`)

// 未保存 / saving / failed / conflict 时不允许发起 Suggest，避免候选绑定到未落库内容。
const suggestLocked = computed(() => editor.status.value !== 'saved')

const loadAnalysis = async () => {
  analysisLoading.value = true
  analysisError.value = null
  try {
    analysisResult.value = await getOptimizationAnalysisResult(props.optimizationTaskId)
  } catch (error) {
    analysisError.value = error instanceof Error ? error.message : '优化建议加载失败'
  } finally {
    analysisLoading.value = false
  }
}

const handleEditorChange = (document: Parameters<typeof editor.applyDocument>[0]) => {
  editor.applyDocument(document)
}

const confirmRestore = async () => {
  if (restoring.value || editor.revision.value === null) return
  try {
    await ElMessageBox.confirm(
      '将用本次优化开始前的简历内容覆盖当前编辑版本，并保存为新的版本。是否继续？',
      '恢复本次优化前版本',
      { confirmButtonText: '恢复', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  restoring.value = true
  try {
    const outcome = await editor.restorePreOptimization()
    if (outcome === 'saved') {
      ElMessage.success('已恢复到本次优化前的版本')
    } else if (outcome === 'conflict') {
      ElMessage.warning('恢复未生效：线上已有更新，请先处理保存冲突')
    } else {
      ElMessage.error('恢复失败，请稍后重试')
    }
  } finally {
    restoring.value = false
  }
}

const handleOverwrite = async () => {
  try {
    await editor.overwriteWithLocalDraft()
    if (editor.status.value === 'saved') {
      ElMessage.success('已用当前编辑内容覆盖保存')
    } else if (editor.status.value === 'conflict') {
      ElMessage.warning('仍然与线上内容冲突，请重新选择处理方式')
    } else {
      ElMessage.error(editor.saveError.value ?? '保存失败，请稍后重试')
    }
  } catch {
    ElMessage.error('保存失败，请稍后重试')
  }
}

const handleAdoptServer = async () => {
  try {
    await editor.adoptServerVersion()
    ElMessage.success('已加载线上最新版本，本地草稿已被替换')
  } catch {
    ElMessage.error('加载线上版本失败，请稍后重试')
  }
}

const handleRetry = () => {
  void editor.retrySave()
}

const openPreviewMode = async () => {
  if (previewPreparing.value || workspaceMode.value === 'preview') return
  previewPreparing.value = true
  try {
    const ready = await editor.ensurePersistedForRender()
    if (!ready) {
      ElMessage.warning('请先完成当前简历保存或冲突处理')
      return
    }
    previewComponentMounted.value = true
    workspaceMode.value = 'preview'
  } finally {
    previewPreparing.value = false
  }
}

const openInspector = () => {
  inspectorOpen.value = true
  if (isNarrowScreen.value) mobilePanel.value = 'suggestions'
}

const closeInspector = () => {
  inspectorOpen.value = false
  if (isNarrowScreen.value) mobilePanel.value = 'editor'
}

const routeRequirementId = computed(() => parsePositiveId(route.query.requirement))
watch(routeRequirementId, (requirementId) => {
  selectedRequirementId.value = requirementId
  if (requirementId) {
    inspectorOpen.value = true
    if (isNarrowScreen.value) mobilePanel.value = 'suggestions'
  } else {
    inspectorOpen.value = false
    mobilePanel.value = 'editor'
  }
})

// Preview / Export 发现服务端 revision 已变化：同步线上最新版本，杜绝静默渲染旧内容。
const handlePreviewStale = async () => {
  // Preview / Export 的过期响应绝不能覆盖已出现的本地草稿。
  if (editor.hasUnsavedChanges.value) {
    ElMessage.warning('当前草稿仍在，未自动替换。请先完成保存或处理冲突后重新预览。')
    return
  }
  try {
    await editor.adoptServerVersion()
  } catch {
    ElMessage.error('同步线上版本失败，请刷新页面')
  }
}

const beforeUnloadHandler = (event: BeforeUnloadEvent) => {
  if (editor.hasUnsavedChanges.value) {
    event.preventDefault()
    // 部分浏览器（如 Safari）只认 returnValue，不设则不弹确认框。
    event.returnValue = ''
  }
}

onMounted(() => {
  void editor.load()
  void loadAnalysis()
  window.addEventListener('beforeunload', beforeUnloadHandler)
  if (typeof window.matchMedia === 'function') {
    narrowMediaQuery = window.matchMedia('(max-width: 720px)')
    narrowMediaQuery.addEventListener('change', handleNarrowChange)
  }
})

onBeforeUnmount(() => {
  bulletSuggest.dispose()
  editor.dispose()
  window.removeEventListener('beforeunload', beforeUnloadHandler)
  narrowMediaQuery?.removeEventListener('change', handleNarrowChange)
})

const confirmDiscardUnsavedChanges = async () => {
  if (!editor.hasUnsavedChanges.value) return true
  try {
    await ElMessageBox.confirm(
      '当前简历还有未保存的修改，离开后这些修改不会保留。是否离开？',
      '离开工作区',
      { confirmButtonText: '离开', cancelButtonText: '继续编辑', type: 'warning' },
    )
    return true
  } catch {
    return false
  }
}

// 普通离开和 /workspace/A → /workspace/B 参数切换都必须显式处理未保存内容。
onBeforeRouteLeave(confirmDiscardUnsavedChanges)
onBeforeRouteUpdate(confirmDiscardUnsavedChanges)
</script>

<template>
  <section class="workspace-panel" :class="{ 'is-preview-mode': workspaceMode === 'preview' }">
    <header class="workspace-header">
      <div class="workspace-title">
        <h1>{{ jobTitle }}</h1>
        <span :class="saveStatusClass" role="status">{{ saveStatusText }}</span>
      </div>
      <div class="workspace-toolbar">
        <div class="workspace-mode-switch" role="tablist" aria-label="工作区模式">
          <button
            type="button"
            role="tab"
            :aria-selected="workspaceMode === 'edit'"
            :class="{ 'is-active': workspaceMode === 'edit' }"
            @click="workspaceMode = 'edit'"
          >
            编辑
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="workspaceMode === 'preview'"
            :class="{ 'is-active': workspaceMode === 'preview' }"
            :disabled="previewPreparing"
            @click="openPreviewMode"
          >
            {{ previewPreparing ? '准备预览…' : '预览' }}
          </button>
        </div>
        <template v-if="workspaceMode === 'edit'">
          <el-button :disabled="!editor.canUndo.value" @click="editor.undo()">撤销</el-button>
          <el-button :disabled="!editor.canRedo.value" @click="editor.redo()">重做</el-button>
          <el-button class="inspector-open-button" @click="openInspector">
            优化建议<span v-if="suggestionCount" class="toolbar-count">{{ suggestionCount }}</span>
          </el-button>
          <details class="workspace-more">
            <summary>更多</summary>
            <div class="workspace-more-menu">
              <button
                type="button"
                :disabled="editor.status.value === 'saving'"
                @click="confirmRestore"
              >
                {{ restoring ? '正在恢复…' : '恢复优化前版本' }}
              </button>
            </div>
          </details>
        </template>
      </div>
    </header>

    <div v-if="editor.status.value === 'conflict'" class="workspace-conflict" role="alert">
      <p>线上已有更新，你的本地修改尚未保存。请选择保留哪一份内容：</p>
      <div>
        <el-button type="primary" @click="handleOverwrite">保留我的草稿</el-button>
        <el-button @click="handleAdoptServer">使用线上版本</el-button>
      </div>
    </div>

    <div v-else-if="editor.status.value === 'failed'" class="workspace-failed" role="alert">
      <p>{{ editor.saveError.value ?? '保存失败' }}。草稿仍在，不会丢失。</p>
      <el-button type="primary" @click="handleRetry">重试保存</el-button>
    </div>

    <SkeletonBlock v-if="editor.loading.value" title :rows="10" />
    <ErrorState
      v-else-if="editor.loadError.value"
      title="暂时无法打开工作区"
      :description="editor.loadError.value"
      action-text="返回分析结果"
      @action="$router.push(`/job-analysis/${optimizationTaskId}`)"
    />

    <template v-else-if="editor.draft.value">
      <div v-if="workspaceMode === 'edit'" class="workspace-edit-mode">
        <div
          v-if="isNarrowScreen"
          class="workspace-mobile-switch"
          role="tablist"
          aria-label="编辑与建议"
        >
          <button
            type="button"
            role="tab"
            :aria-selected="mobilePanel === 'editor'"
            :class="{ 'is-active': mobilePanel === 'editor' }"
            @click="mobilePanel = 'editor'"
          >
            编辑简历
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="mobilePanel === 'suggestions'"
            :class="{ 'is-active': mobilePanel === 'suggestions' }"
            @click="openInspector"
          >
            优化建议<span v-if="suggestionCount" class="toolbar-count">{{ suggestionCount }}</span>
          </button>
        </div>
        <div class="workspace-body" :class="{ 'is-inspector-open': inspectorOpen }">
          <div v-if="!isNarrowScreen || mobilePanel === 'editor'" class="workspace-editor-column">
            <ResumeEditor
              :document="editor.draft.value"
              :suggest="bulletSuggest"
              :suggest-enabled="suggestEnabled"
              :suggest-locked="suggestLocked"
              @change="handleEditorChange"
            />
          </div>
          <WorkspaceSuggestions
            v-if="
              (!isNarrowScreen && inspectorOpen) ||
              (isNarrowScreen && mobilePanel === 'suggestions')
            "
            :result="analysisResult"
            :loading="analysisLoading"
            :error="analysisError"
            :selected-requirement-id="selectedRequirementId"
            @retry-load="loadAnalysis"
            @close="closeInspector"
          />
        </div>
      </div>

      <div
        v-if="previewComponentMounted"
        v-show="workspaceMode === 'preview'"
        class="workspace-preview-mode"
      >
        <!-- 任务 ID 作为 key：任务切换时预览 / 导出状态整体重建，旧凭证不会跨任务残留。 -->
        <WorkspacePreviewExport
          :key="optimizationTaskId"
          :optimization-task-id="optimizationTaskId"
          :revision="editor.revision.value"
          :status="editor.status.value"
          @stale="handlePreviewStale"
        />
      </div>
    </template>

    <ErrorState
      v-else
      title="简历内容暂不可用"
      description="请返回分析结果后重试。"
      action-text="返回分析结果"
      @action="$router.push(`/job-analysis/${optimizationTaskId}`)"
    />
  </section>
</template>

<style scoped>
.workspace-panel {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-width: 0;
}

.workspace-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.workspace-title h1 {
  overflow: hidden;
  margin: 0;
  color: var(--app-text);
  font-size: 22px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.workspace-mode-switch,
.workspace-mobile-switch {
  display: inline-flex;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 2px;
  background: var(--app-surface-soft);
}

.workspace-mode-switch button,
.workspace-mobile-switch button {
  min-height: 30px;
  border: 0;
  border-radius: 5px;
  padding: 0 12px;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.workspace-mode-switch button:hover,
.workspace-mode-switch button:focus-visible,
.workspace-mobile-switch button:hover,
.workspace-mobile-switch button:focus-visible {
  color: var(--app-text);
}

.workspace-mode-switch button.is-active,
.workspace-mobile-switch button.is-active {
  color: var(--app-primary);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-card);
}

.workspace-mode-switch button:disabled {
  color: var(--app-text-muted);
  cursor: wait;
}

.toolbar-count {
  display: inline-grid;
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  place-items: center;
  border-radius: 50%;
  color: var(--app-primary);
  font-size: 11px;
  background: var(--app-primary-soft);
}

.save-status {
  flex: 0 0 auto;
  color: var(--app-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.save-status.is-saved {
  color: var(--app-text-muted);
}

.save-status.is-dirty,
.save-status.is-saving {
  color: var(--app-primary);
  font-weight: 700;
}

.save-status.is-failed,
.save-status.is-conflict {
  color: var(--app-danger);
  font-weight: 700;
}

.save-status.is-conflict {
  color: var(--app-warning);
}

.workspace-more,
.workspace-more summary {
  position: relative;
}

.workspace-more summary {
  list-style: none;
  min-height: 32px;
  padding: 7px 10px;
  border-radius: var(--app-radius-md);
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.workspace-more summary::-webkit-details-marker {
  display: none;
}

.workspace-more summary:hover,
.workspace-more summary:focus-visible {
  color: var(--app-text);
  background: var(--app-bg-soft);
}

.workspace-more-menu {
  position: absolute;
  z-index: 5;
  top: calc(100% + 6px);
  right: 0;
  display: grid;
  min-width: 170px;
  gap: 2px;
  padding: 5px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-soft);
}

.workspace-more-menu button {
  border: 0;
  padding: 8px 9px;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: 12px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.workspace-more-menu button:hover,
.workspace-more-menu button:focus-visible {
  color: var(--app-text);
  background: var(--app-surface-soft);
}

.workspace-more-menu button:disabled {
  color: var(--app-text-muted);
  cursor: wait;
}

.workspace-conflict,
.workspace-failed {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  border-radius: var(--app-radius-md);
  padding: 14px 16px;
}

.workspace-conflict {
  border: 1px solid var(--el-color-warning-light-7);
  background: var(--el-color-warning-light-9);
}

.workspace-failed {
  border: 1px solid var(--el-color-danger-light-7);
  background: var(--el-color-danger-light-9);
}

.workspace-conflict p,
.workspace-failed p {
  margin: 0;
  color: var(--app-text);
  font-size: 13px;
  line-height: 1.6;
}

.workspace-edit-mode,
.workspace-preview-mode {
  min-width: 0;
}

.workspace-mobile-switch {
  display: none;
  margin-bottom: 16px;
}

.workspace-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 28px;
  align-items: start;
  min-width: 0;
}

.workspace-body.is-inspector-open {
  grid-template-columns: minmax(0, 1fr) minmax(300px, 320px);
}

.workspace-editor-column {
  min-width: 0;
}

@media (max-width: 1040px) and (min-width: 721px) {
  .workspace-body.is-inspector-open {
    grid-template-columns: minmax(0, 1fr) minmax(260px, 300px);
    gap: 20px;
  }
}

@media (max-width: 720px) {
  .workspace-panel {
    gap: 16px;
  }

  .workspace-header {
    display: grid;
    align-items: start;
    gap: 12px;
  }

  .workspace-title h1 {
    font-size: 20px;
  }

  .workspace-toolbar {
    justify-content: flex-start;
  }

  .workspace-toolbar .el-button {
    min-height: 32px;
  }

  .workspace-mobile-switch {
    display: inline-flex;
    width: 100%;
  }

  .workspace-mobile-switch button {
    width: 50%;
    min-height: 40px;
  }

  .workspace-toolbar .el-button {
    min-height: 40px;
  }

  .workspace-mode-switch button {
    min-height: 40px;
  }

  .workspace-body,
  .workspace-body.is-inspector-open {
    display: block;
  }
}
</style>
