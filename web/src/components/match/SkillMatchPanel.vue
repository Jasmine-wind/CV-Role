<script setup lang="ts">
interface SkillMatchItem {
  item: string
  reason?: string
}

defineProps<{
  title: string
  items?: SkillMatchItem[]
  emptyText?: string
  tone?: 'success' | 'warning' | 'danger' | 'info'
}>()
</script>

<template>
  <article class="match-skill-list" :class="tone ? `is-${tone}` : 'is-info'">
    <header>
      <h3>{{ title }}</h3>
      <el-tag size="small" :type="tone || 'info'">{{ items?.length ?? 0 }} 条</el-tag>
    </header>
    <div v-if="items?.length" class="match-skill-items">
      <p v-for="item in items" :key="`${item.item}-${item.reason}`">
        <strong>{{ item.item }}</strong>
        <span>{{ item.reason || '-' }}</span>
      </p>
    </div>
    <el-empty v-else :description="emptyText || '暂无内容'" :image-size="64" />
  </article>
</template>
