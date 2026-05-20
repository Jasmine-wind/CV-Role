<script setup lang="ts">
defineProps<{
  title: string
  items?: string[]
  emptyText?: string
  tone?: 'default' | 'success' | 'warning' | 'danger' | 'info'
}>()
</script>

<template>
  <article class="ai-result-block" :class="tone ? `is-${tone}` : 'is-default'">
    <h3>{{ title }}</h3>
    <ul v-if="items?.length">
      <li v-for="item in items" :key="item">{{ item }}</li>
    </ul>
    <p v-else>{{ emptyText || '暂无内容' }}</p>
  </article>
</template>
