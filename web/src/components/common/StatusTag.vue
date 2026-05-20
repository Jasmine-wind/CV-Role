<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status?: string | null
}>()

const normalized = computed(() => props.status || '-')

const tagType = computed(() => {
  if (['SUCCESS', 'DONE', 'COMPLETED', 'ACCEPTED'].includes(normalized.value)) {
    return 'success'
  }

  if (['FAILED', 'ERROR', 'REJECTED'].includes(normalized.value)) {
    return 'danger'
  }

  if (['PENDING', 'PROCESSING', 'RUNNING'].includes(normalized.value)) {
    return 'warning'
  }

  return 'info'
})

const label = computed(() => {
  const map: Record<string, string> = {
    SUCCESS: '成功',
    DONE: '完成',
    COMPLETED: '完成',
    FAILED: '失败',
    ERROR: '异常',
    PENDING: '处理中',
    PROCESSING: '处理中',
    RUNNING: '运行中',
    ACCEPTED: '已采纳',
    REJECTED: '已拒绝',
  }

  return map[normalized.value] ?? normalized.value
})
</script>

<template>
  <el-tag :type="tagType">{{ label }}</el-tag>
</template>
