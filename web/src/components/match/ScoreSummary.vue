<script setup lang="ts">
defineProps<{
  score?: number | null
  title?: string
  conclusion?: string | null
  description?: string | null
}>()
</script>

<template>
  <article class="match-score-card">
    <span>{{ title || '匹配分' }}</span>
    <strong>{{ score ?? '-' }}</strong>
    <small>{{ conclusion || '等待匹配分析' }}</small>
    <p v-if="description">{{ description }}</p>
  </article>
</template>
