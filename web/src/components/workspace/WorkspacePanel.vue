<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
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

const editor = useWorkspaceEditor(props.optimizationTaskId)
const bulletSuggest = useBulletSuggest(props.optimizationTaskId, editor)

const analysisResult = ref<OptimizationAnalysisResult | null>(null)
const analysisLoading = ref(false)
const analysisError = ref<string | null>(null)

const restoring = ref(false)
const previewPreparing = ref(false)
const previewDrawerVisible = ref(false)

const jobTitle = computed(() => analysisResult.value?.jobTitle ?? '简历编辑')

// 岗位定向改写只对拥有正式证据分析的任务开放；旧版兼容任务由服务端 fail closed 兜底。
const suggestEnabled = computed(() => analysisResult.value?.analysisMode === 'EVIDENCE')

const saveStatusText = computed(() => {
  switch (editor.status.value) {
    case 'dirty':
      return '有修改未保存'
    case 'saving':
      return '正在保存…'
    case 'saved':
      return '已保存'
    case 'failed':
      return '保存失败'
    case 'conflict':
      return '存在保存冲突'
    default:
      return ''
  }
})

const saveStatusClass = computed(() => `save-status is-${editor.status.value}`)

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
  if (restoring.value || editor.revision.value === null) {
    return
  }
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
      ElMessage.warning('恢复未生效：服务端已有更新的版本，请先处理保存冲突')
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
      ElMessage.warning('仍然与服务端冲突，请重新选择处理方式')
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
    ElMessage.success('已加载服务端最新版本，本地草稿已被替换')
  } catch {
    ElMessage.error('加载服务端版本失败，请稍后重试')
  }
}

const handleRetry = () => {
  void editor.retrySave()
}

const openPreviewDrawer = async () => {
  if (previewPreparing.value) return
  previewPreparing.value = true
  try {
    const ready = await editor.ensurePersistedForRender()
    if (!ready) {
      ElMessage.warning('请先完成当前简历保存或冲突处理')
      return
    }
    previewDrawerVisible.value = true
  } finally {
    previewPreparing.value = false
  }
}

// Preview / Export 发现服务端 revision 已变化：同步服务端最新版本，杜绝静默渲染旧内容。
const handlePreviewStale = async () => {
  try {
    await editor.adoptServerVersion()
  } catch {
    ElMessage.error('同步服务端版本失败，请刷新页面')
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
})

onBeforeUnmount(() => {
  bulletSuggest.dispose()
  editor.dispose()
  window.removeEventListener('beforeunload', beforeUnloadHandler)
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
  <section class="workspace-panel">
    <header class="workspace-header">
      <div class="workspace-title">
        <span>优化工作区</span>
        <h1>{{ jobTitle }}</h1>
      </div>
      <div class="workspace-toolbar">
        <span :class="saveStatusClass" role="status">{{ saveStatusText }}</span>
        <el-button :disabled="!editor.canUndo.value" @click="editor.undo()">撤销</el-button>
        <el-button :disabled="!editor.canRedo.value" @click="editor.redo()">重做</el-button>
        <el-button
          :loading="restoring"
          :disabled="editor.status.value === 'saving'"
          @click="confirmRestore"
        >
          恢复优化前版本
        </el-button>
        <el-button type="primary" :loading="previewPreparing" @click="openPreviewDrawer">
          预览 / 导出 PDF
        </el-button>
      </div>
    </header>

    <div v-if="editor.status.value === 'conflict'" class="workspace-conflict">
      <p>
        服务端已存在更新的版本，你的本地修改没有保存。请选择：用当前编辑内容覆盖，或改用服务端最新版本。
      </p>
      <div>
        <el-button type="primary" @click="handleOverwrite">用我的内容覆盖</el-button>
        <el-button @click="handleAdoptServer">使用服务端版本</el-button>
      </div>
    </div>

    <div v-else-if="editor.status.value === 'failed'" class="workspace-failed">
      <p>{{ editor.saveError.value ?? '保存失败' }}，本地草稿已保留。</p>
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

    <div v-else-if="editor.draft.value" class="workspace-body">
      <WorkspaceSuggestions
        :result="analysisResult"
        :loading="analysisLoading"
        :error="analysisError"
      />
      <ResumeEditor
        :document="editor.draft.value"
        :suggest="bulletSuggest"
        :suggest-enabled="suggestEnabled"
        @change="handleEditorChange"
      />
    </div>

    <!-- 以任务 ID 为 key：任务切换时预览 / 导出状态整体重建，旧预览不会跨任务残留 -->
    <el-drawer
      v-if="editor.draft.value"
      v-model="previewDrawerVisible"
      title="预览 / 导出 PDF"
      direction="rtl"
      size="540px"
      destroy-on-close
    >
      <WorkspacePreviewExport
        :key="optimizationTaskId"
        :optimization-task-id="optimizationTaskId"
        :revision="editor.revision.value"
        :status="editor.status.value"
        @stale="handlePreviewStale"
      />
    </el-drawer>
  </section>
</template>

<style scoped>
.workspace-panel {
  display: grid;
  gap: 18px;
}

.workspace-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.workspace-title span {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
}

.workspace-title h1 {
  margin: 4px 0 0;
  color: var(--app-navy);
  font-size: 24px;
}

.workspace-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.save-status {
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid var(--el-border-color);
  color: var(--app-text-secondary);
}

.save-status.is-saved {
  color: var(--el-color-success);
  border-color: var(--el-color-success-light-7);
}

.save-status.is-dirty,
.save-status.is-saving {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-7);
}

.save-status.is-failed {
  color: var(--el-color-danger);
  border-color: var(--el-color-danger-light-7);
}

.save-status.is-conflict {
  color: var(--el-color-warning);
  border-color: var(--el-color-warning-light-7);
}

.workspace-conflict,
.workspace-failed {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-radius: 10px;
  padding: 14px 16px;
  flex-wrap: wrap;
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

.workspace-body {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(0, 1.4fr);
  gap: 22px;
  align-items: start;
}

@media (max-width: 1000px) {
  .workspace-body {
    grid-template-columns: 1fr;
  }
}
</style>
