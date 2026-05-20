<script setup lang="ts">
defineProps<{
  score?: number | null
  summary?: string | null
}>()
</script>

<template>
  <section class="resume-diagnosis-panel">
    <span>简历诊断</span>
    <strong>{{ score ?? '-' }}</strong>
    <p>{{ summary || '暂无诊断摘要' }}</p>
    <slot />
  </section>
</template>
