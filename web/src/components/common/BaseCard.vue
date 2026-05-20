<script setup lang="ts">
defineProps<{
  title?: string
  subtitle?: string
  tone?: 'default' | 'primary' | 'success' | 'warning' | 'danger'
}>()
</script>

<template>
  <article class="ui-card" :class="tone ? `is-${tone}` : 'is-default'">
    <header v-if="title || subtitle || $slots.actions" class="ui-card-header">
      <div>
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <slot name="actions" />
    </header>
    <slot />
  </article>
</template>
