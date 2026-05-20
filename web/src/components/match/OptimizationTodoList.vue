<script setup lang="ts">
defineProps<{
  items?: string[]
  title?: string
}>()
</script>

<template>
  <section class="match-todo-list">
    <h3>{{ title || '下一步清单' }}</h3>
    <p v-for="item in items" :key="item">{{ item }}</p>
    <el-empty v-if="!items?.length" description="暂无下一步清单" :image-size="64" />
  </section>
</template>
