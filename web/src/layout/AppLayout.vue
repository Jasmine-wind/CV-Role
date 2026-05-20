<script setup lang="ts">
import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'
</script>

<template>
  <div class="app-shell">
    <AppSidebar />
    <div class="app-page">
      <AppTopbar />
      <main class="app-content">
        <slot />
      </main>
    </div>
  </div>
</template>
