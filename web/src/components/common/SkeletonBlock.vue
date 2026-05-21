<script setup lang="ts">
withDefaults(defineProps<{
  rows?: number
  title?: boolean
  compact?: boolean
}>(), {
  rows: 3,
  title: false,
  compact: false,
})
</script>

<template>
  <div class="ui-skeleton-block" :class="{ 'is-compact': compact }">
    <span v-if="title" class="is-title" />
    <span
      v-for="index in rows"
      :key="index"
      :class="{ 'is-short': index === rows }"
    />
  </div>
</template>
