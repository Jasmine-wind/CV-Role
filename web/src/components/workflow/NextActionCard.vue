<script setup lang="ts">
defineProps<{
  title: string
  description: string
  actionText: string
  tag?: string
}>()

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <article class="workflow-next-card">
    <div>
      <span class="workflow-next-tag">{{ tag || '当前最有效动作' }}</span>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
    <el-button type="primary" size="large" @click="emit('action')">{{ actionText }}</el-button>
  </article>
</template>
