<script setup lang="ts">
defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="AI 结果详情"
    size="640px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <slot />
  </el-drawer>
</template>
