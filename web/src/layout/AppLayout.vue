<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'

const route = useRoute()

const contentWidthClass = computed(() => {
  const layoutWidth = route.meta.layoutWidth || 'default'
  return `is-${layoutWidth}`
})
</script>

<template>
  <div class="app-shell">
    <AppSidebar />
    <div class="app-page">
      <AppTopbar />
      <main class="app-content" :class="contentWidthClass">
        <slot />
      </main>
    </div>
  </div>
</template>
