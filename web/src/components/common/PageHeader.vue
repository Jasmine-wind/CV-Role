<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>()
</script>

<template>
  <header class="ui-page-header">
    <div>
      <p v-if="eyebrow" class="ui-page-eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="ui-page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
