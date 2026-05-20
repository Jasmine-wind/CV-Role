<script setup lang="ts">
defineProps<{
  warnings?: string[]
  title?: string
}>()
</script>

<template>
  <section v-if="warnings?.length" class="ai-warning-list">
    <strong>{{ title || '风险提示' }}</strong>
    <p v-for="item in warnings" :key="item">{{ item }}</p>
  </section>
</template>
