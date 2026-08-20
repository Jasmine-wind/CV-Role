<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ErrorState from '@/components/common/ErrorState.vue'
import WorkspacePanel from '@/components/workspace/WorkspacePanel.vue'

const route = useRoute()
const router = useRouter()

const optimizationTaskId = computed(() => {
  const raw = route.params.optimizationTaskId
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})
</script>

<template>
  <!-- 以任务 ID 作为 key：切换任务时整个面板重建，杜绝跨任务状态串写 -->
  <WorkspacePanel
    v-if="optimizationTaskId"
    :key="optimizationTaskId"
    :optimization-task-id="optimizationTaskId"
  />
  <ErrorState
    v-else
    title="工作区地址无效"
    description="请从岗位分析结果页重新进入编辑。"
    action-text="返回首页"
    @action="router.push('/app')"
  />
</template>
