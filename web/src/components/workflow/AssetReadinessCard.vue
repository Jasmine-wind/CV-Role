<script setup lang="ts">
interface AssetReadinessItem {
  label: string
  value: string | number
  status: 'ready' | 'pending' | 'warning'
  description?: string
}

defineProps<{
  title?: string
  items: AssetReadinessItem[]
}>()
</script>

<template>
  <article class="workflow-readiness-card">
    <header>
      <span>{{ title || '资产状态' }}</span>
    </header>
    <div class="workflow-readiness-list">
      <div
        v-for="item in items"
        :key="item.label"
        class="workflow-readiness-item"
        :class="`is-${item.status}`"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small v-if="item.description">{{ item.description }}</small>
      </div>
    </div>
  </article>
</template>
