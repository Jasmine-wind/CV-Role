<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { getOptimizationAnalysisResult } from '@/api/job-analysis'
import ErrorState from '@/components/common/ErrorState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import ResumeEditor from '@/components/workspace/ResumeEditor.vue'
import WorkspacePreviewExport from '@/components/workspace/WorkspacePreviewExport.vue'
import WorkspaceRequirements from '@/components/workspace/WorkspaceRequirements.vue'
import WorkspaceSuggestions from '@/components/workspace/WorkspaceSuggestions.vue'
import TaskHeader from '@/components/task/TaskHeader.vue'
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
const router = useRouter()
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
const inspectorOpen = ref(true)
const mobilePanel = ref<'editor' | 'suggestions'>(
  initialRequirementId !== null ? 'suggestions' : 'editor',
)
const selectedRequirementId = ref<number | null>(initialRequirementId)

// At 1120px, compact columns are 245px + 340px, leaving 535px for the Resume stage.
// Below that threshold, the workspace becomes focused instead of preserving an unusable tri-column grid.
const WORKSPACE_FOCUSED_MEDIA_QUERY = '(max-width: 1119px)'
const initialNarrow = () =>
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia(WORKSPACE_FOCUSED_MEDIA_QUERY).matches
const isNarrowScreen = ref(initialNarrow())
let narrowMediaQuery: MediaQueryList | null = null

const handleNarrowChange = (event: MediaQueryListEvent) => {
  isNarrowScreen.value = event.matches
  if (!event.matches) mobilePanel.value = 'editor'
}

const jobTitle = computed(() => analysisResult.value?.jobTitle ?? '简历编辑')
const resumeName = computed(() => analysisResult.value?.resumeName ?? '当前简历')
const requirements = computed(() => analysisResult.value?.evidenceAnalysis?.requirements ?? [])
const effectiveSelectedRequirementId = computed(() => {
  const requested = selectedRequirementId.value
  if (requested && requirements.value.some((item) => item.evidenceRequirementId === requested)) {
    return requested
  }
  return requirements.value[0]?.evidenceRequirementId ?? null
})
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

const goToAnalysis = () => {
  void router.push({
    path: `/job-analysis/${props.optimizationTaskId}`,
    ...(effectiveSelectedRequirementId.value
      ? { query: { requirement: String(effectiveSelectedRequirementId.value) } }
      : {}),
  })
}

const selectRequirement = (requirementId: number) => {
  selectedRequirementId.value = requirementId
  inspectorOpen.value = true
  if (isNarrowScreen.value) mobilePanel.value = 'suggestions'
  void router.replace({
    query: { ...route.query, requirement: String(requirementId) },
  })
}

const setReviewStep = (step: 'match' | 'evidence' | 'edit' | 'preview') => {
  if (step === 'match' || step === 'evidence') {
    void router.push({
      path: `/job-analysis/${props.optimizationTaskId}`,
      ...(effectiveSelectedRequirementId.value
        ? { query: { requirement: String(effectiveSelectedRequirementId.value) } }
        : {}),
    })
    return
  }
  if (step === 'preview') {
    void openPreviewMode()
    return
  }
  workspaceMode.value = 'edit'
  if (isNarrowScreen.value) mobilePanel.value = 'editor'
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
  }
})

// Preview / Export 发现服务端 revision 已变化：同步线上最新版本，杜绝静默渲染旧内容。
const handlePreviewStale = async () => {
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
    event.returnValue = ''
  }
}

onMounted(() => {
  void editor.load()
  void loadAnalysis()
  window.addEventListener('beforeunload', beforeUnloadHandler)
  if (typeof window.matchMedia === 'function') {
    narrowMediaQuery = window.matchMedia(WORKSPACE_FOCUSED_MEDIA_QUERY)
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

onBeforeRouteLeave(confirmDiscardUnsavedChanges)
onBeforeRouteUpdate(confirmDiscardUnsavedChanges)
</script>

<template>
  <section
    class="workspace-panel"
    :class="{ 'is-preview-mode': workspaceMode === 'preview' }"
    aria-label="优化工作区"
  >
    <!--
      THESIS: 岗位要求不是分数，而是一条可回到简历原文的证据线；工作区拒绝 Dashboard。
      OWN-WORLD: 暖中性纸面、清晰分隔线、Slate ink 与克制 Burnt Clay 标注组成证据账本。
      STORY: 用户选择要求，核对冻结材料中的证据与缺口，在同一份简历上编辑，再预览或导出。
      FIRST VIEWPORT: shared task header and contextual editor toolbar lead into a three-column workspace where requirements, resume, and inspector remain visible together.
      FORM: Redline evidence ledger；正式项目数据、编辑状态和 API 保持唯一真实链路。
      FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
    -->
    <TaskHeader
      :job-title="jobTitle"
      :resume-name="resumeName"
      :active-step="workspaceMode === 'preview' ? 'preview' : 'edit'"
      back-label="返回证据审阅"
      :status-text="saveStatusText"
      :status-tone="saveStatusClass"
      interactive
      @back="goToAnalysis()"
      @step="setReviewStep"
    >
      <template #actions>
        <el-button v-if="workspaceMode === 'edit'" type="primary" :loading="previewPreparing" @click="openPreviewMode">
          {{ previewPreparing ? '准备预览…' : '预览 →' }}
        </el-button>
        <el-button v-else @click="setReviewStep('edit')">返回编辑</el-button>
      </template>
    </TaskHeader>

    <div v-if="workspaceMode === 'edit'" class="workspace-toolbar" aria-label="编辑工具">
      <div class="workspace-toolbar-context">
        <span>编辑工作区</span>
        <strong>{{ resumeName }}</strong>
        <span v-if="effectiveSelectedRequirementId" class="workspace-selected-requirement">
          要求 {{ requirements.findIndex((item) => item.evidenceRequirementId === effectiveSelectedRequirementId) + 1 }}：{{ requirements.find((item) => item.evidenceRequirementId === effectiveSelectedRequirementId)?.requirementText }}
        </span>
      </div>
      <div class="workspace-toolbar-actions">
        <button
          v-if="workspaceMode === 'edit'"
          type="button"
          class="toolbar-button"
          :disabled="!editor.canUndo.value"
          @click="editor.undo()"
        >
          撤销
        </button>
        <button
          v-if="workspaceMode === 'edit'"
          type="button"
          class="toolbar-button"
          :disabled="!editor.canRedo.value"
          @click="editor.redo()"
        >
          重做
        </button>
        <button
          v-if="workspaceMode === 'edit' && !inspectorOpen"
          type="button"
          class="toolbar-button toolbar-button-accent"
          @click="openInspector"
        >
          优化建议<span v-if="suggestionCount" class="toolbar-count">{{ suggestionCount }}</span>
        </button>
        <details v-if="workspaceMode === 'edit'" class="workspace-more">
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
      </div>
    </div>

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
          <details class="workspace-mobile-more">
            <summary>更多</summary>
            <div class="workspace-more-menu">
              <button type="button" :disabled="editor.status.value === 'saving'" @click="editor.undo()">
                撤销
              </button>
              <button type="button" :disabled="editor.status.value === 'saving'" @click="editor.redo()">
                重做
              </button>
              <button type="button" :disabled="editor.status.value === 'saving'" @click="confirmRestore">
                {{ restoring ? '正在恢复…' : '恢复优化前版本' }}
              </button>
            </div>
          </details>
        </div>

        <div class="workspace-layout" :class="{ 'is-inspector-closed': !inspectorOpen }">
          <WorkspaceRequirements
            :requirements="requirements"
            :selected-requirement-id="effectiveSelectedRequirementId"
            :job-title="jobTitle"
            @select="selectRequirement"
          />

          <section v-if="!isNarrowScreen || mobilePanel === 'editor'" class="resume-stage" aria-label="简历编辑器">
            <div class="resume-stage-scroll">
              <ResumeEditor
                :document="editor.draft.value"
                :suggest="bulletSuggest"
                :suggest-enabled="suggestEnabled"
                :suggest-locked="suggestLocked"
                @change="handleEditorChange"
              />
            </div>
          </section>

          <WorkspaceSuggestions
            v-if="(!isNarrowScreen && inspectorOpen) || (isNarrowScreen && mobilePanel === 'suggestions')"
            :result="analysisResult"
            :loading="analysisLoading"
            :error="analysisError"
            :selected-requirement-id="effectiveSelectedRequirementId"
            @retry-load="loadAnalysis"
            @close="closeInspector"
          />
        </div>
      </div>

      <div v-if="previewComponentMounted" v-show="workspaceMode === 'preview'" class="workspace-preview-mode">
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
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  overflow: hidden;
  color: var(--app-text);
  background: var(--app-stage);
}

.workspace-toolbar {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
  padding: 0 var(--app-content-gutter);
  background: var(--app-stage);
  border-bottom: 1px solid var(--app-border-strong);
}

.workspace-toolbar-context,
.workspace-toolbar-actions {
  display: flex;
  min-width: 0;
  align-items: center;
}

.workspace-toolbar-context {
  gap: var(--app-space-2);
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-xs);
  white-space: nowrap;
}

.workspace-toolbar-context strong {
  overflow: hidden;
  color: var(--app-text);
  font-weight: 700;
  text-overflow: ellipsis;
}

.workspace-selected-requirement {
  overflow: hidden;
  max-width: min(420px, 32vw);
  border-left: 1px solid var(--app-border-strong);
  padding-left: var(--app-space-2);
  color: var(--app-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-toolbar-actions {
  flex: 0 0 auto;
  justify-content: flex-end;
  gap: var(--app-space-2);
  flex-wrap: wrap;
}

.workspace-mobile-switch {
  display: none;
  border: 1px solid var(--app-border-strong);
  border-radius: var(--app-radius-sm);
  padding: 2px;
  background: var(--app-surface-soft);
}

.workspace-mobile-switch button {
  min-height: 28px;
  border: 0;
  border-radius: var(--app-radius-sm);
  padding: 0 var(--app-space-3);
  color: var(--app-text-secondary);
  font: inherit;
  font-size: var(--app-font-size-xs);
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.workspace-mobile-switch button:hover,
.workspace-mobile-switch button:focus-visible {
  color: var(--app-text);
}

.workspace-mobile-switch button.is-active {
  color: var(--app-text);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-card);
}

.toolbar-button {
  min-height: 30px;
  border: 1px solid transparent;
  border-radius: 3px;
  padding: 0 9px;
  color: var(--app-text-secondary);
  font-size: 11px;
  font-weight: 650;
  background: transparent;
  cursor: pointer;
}

.toolbar-button:hover:not(:disabled),
.toolbar-button:focus-visible:not(:disabled) {
  color: var(--app-text);
  border-color: var(--app-border);
  background: var(--app-surface-soft);
}

.toolbar-button:disabled {
  color: var(--app-text-muted);
  cursor: not-allowed;
  opacity: 0.55;
}

.toolbar-button-accent {
  color: var(--app-primary-active);
}

.toolbar-count {
  display: inline-grid;
  min-width: 17px;
  height: 17px;
  margin-left: 5px;
  place-items: center;
  border-radius: 50%;
  color: var(--app-primary-active);
  font-size: 10px;
  background: var(--app-primary-soft);
}

.workspace-more,
.workspace-more summary {
  position: relative;
}

.workspace-more summary {
  list-style: none;
  min-height: 30px;
  padding: 7px 9px;
  color: var(--app-text-secondary);
  font-size: 11px;
  font-weight: 650;
  cursor: pointer;
}

.workspace-more summary::-webkit-details-marker {
  display: none;
}

.workspace-more summary:hover,
.workspace-more summary:focus-visible {
  color: var(--app-text);
  background: var(--app-surface-soft);
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
  background: var(--app-bg-soft);
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
  padding: 11px 30px;
  border-bottom: 1px solid var(--app-border-strong);
}

.workspace-conflict {
  background: var(--app-warning-soft);
}

.workspace-failed {
  background: var(--app-danger-soft);
}

.workspace-conflict p,
.workspace-failed p {
  margin: 0;
  color: var(--app-text);
  font-size: 12px;
  line-height: 1.6;
}

.workspace-edit-mode,
.workspace-preview-mode {
  min-width: 0;
  min-height: 0;
  height: 100%;
  flex: 1 1 auto;
}

.workspace-mobile-switch {
  display: none;
}

.workspace-layout {
  display: grid;
  grid-template-columns:
    var(--app-workspace-requirements-width)
    minmax(0, 1fr)
    var(--app-workspace-inspector-width);
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.workspace-layout.is-inspector-closed {
  grid-template-columns: var(--app-workspace-requirements-width) minmax(0, 1fr);
}

.resume-stage {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: var(--app-stage);
}

.resume-stage-scroll {
  min-height: 0;
  overflow: auto;
  scrollbar-color: var(--app-scroll-thumb) transparent;
  scrollbar-width: thin;
}

.workspace-preview-mode {
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex: 1 1 auto;
  background: var(--app-stage);
}

.workspace-preview-mode :deep(.preview-document) {
  border-radius: var(--app-radius-md);
}

@media (max-width: 1250px) and (min-width: 1120px) {
  .workspace-layout {
    grid-template-columns:
      var(--app-workspace-requirements-width-tablet)
      minmax(0, 1fr)
      var(--app-workspace-inspector-width-tablet);
  }

  .workspace-layout.is-inspector-closed {
    grid-template-columns: var(--app-workspace-requirements-width-tablet) minmax(0, 1fr);
  }

  .workspace-toolbar {
    padding-right: var(--app-space-5);
    padding-left: var(--app-space-5);
  }
}

@media (max-width: 1119px) {
  .workspace-panel {
    height: auto;
    min-height: 100%;
    overflow: visible;
  }

  .workspace-toolbar {
    min-height: 52px;
    align-items: flex-start;
    flex-direction: column;
    gap: var(--app-space-2);
    padding: var(--app-space-3) var(--app-content-gutter-narrow);
  }

  .workspace-toolbar-context {
    width: 100%;
  }

  .workspace-selected-requirement {
    max-width: 45vw;
  }

  .workspace-toolbar-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .workspace-mobile-switch {
    display: inline-flex;
    width: calc(100% - 32px);
    margin: 8px 16px;
  }

  .workspace-mobile-switch button {
    flex: 1 1 auto;
    width: auto;
    min-height: 34px;
  }

  .workspace-layout {
    display: flex;
    height: auto;
    min-height: 0;
    overflow: visible;
    flex-direction: column;
  }

  .resume-stage {
    min-height: 700px;
  }

  .workspace-layout :deep(.workspace-inspector) {
    border-top: 1px solid var(--app-border-strong);
  }

  .workspace-preview-mode {
    height: auto;
    overflow: visible;
    padding: 0;
  }

  .workspace-conflict,
  .workspace-failed {
    align-items: flex-start;
    padding: var(--app-space-3) var(--app-content-gutter-narrow);
  }
}

@media (max-width: 640px) {
  .workspace-toolbar {
    display: none;
  }

  .workspace-mobile-switch {
    align-items: center;
    gap: 2px;
    padding: 2px;
  }

  .workspace-mobile-more {
    position: relative;
    flex: 0 0 auto;
    align-self: stretch;
  }

  .workspace-mobile-more summary {
    display: inline-flex;
    min-height: 34px;
    align-items: center;
    padding: 0 var(--app-space-2);
    color: var(--app-text-secondary);
    font-size: var(--app-font-size-xs);
    font-weight: 700;
    cursor: pointer;
    list-style: none;
  }

  .workspace-mobile-more summary::-webkit-details-marker {
    display: none;
  }

  .workspace-mobile-more[open] summary {
    color: var(--app-text);
    background: var(--app-surface);
  }

  .workspace-mobile-more .workspace-more-menu {
    right: 0;
    left: auto;
    min-width: 156px;
  }
}
</style>
