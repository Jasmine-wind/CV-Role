<script setup lang="ts">
defineProps<{
  title?: string
}>()
</script>

<template>
  <section class="resume-parse-result-panel">
    <h3>{{ title || '解析结果' }}</h3>
    <slot />
  </section>
</template>
