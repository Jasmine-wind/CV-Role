<script setup lang="ts">
defineProps<{
  score?: number | null
  label?: string
  description?: string
}>()
</script>

<template>
  <article class="match-score-card">
    <span>匹配分</span>
    <strong>{{ score ?? '-' }}</strong>
    <small>{{ label || '等待匹配分析' }}</small>
    <p v-if="description">{{ description }}</p>
  </article>
</template>
