<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pageTitle = computed(() => route.meta.title || '工作台')
</script>

<template>
  <header class="app-topbar">
    <div>
      <p class="app-topbar-kicker">AI Resume Optimizer</p>
      <h1>{{ pageTitle }}</h1>
    </div>

    <span class="app-topbar-note">围绕简历资产、目标岗位和 AI 结果推进优化流程</span>
  </header>
</template>
