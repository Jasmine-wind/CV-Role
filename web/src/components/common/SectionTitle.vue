<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="ui-section-title">
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
  </div>
</template>
