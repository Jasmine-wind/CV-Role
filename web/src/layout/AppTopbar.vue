<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageTitle = computed(() => route.meta.title || '工作台')
</script>

<template>
  <header class="app-topbar">
    <div>
      <p class="app-topbar-kicker">CV Role</p>
      <h1>{{ pageTitle }}</h1>
    </div>

    <span class="app-topbar-note">用真实经历，为每一个岗位准备更合适的简历</span>
  </header>
</template>
