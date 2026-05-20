<script setup lang="ts">
const primaryNavItems = [
  {
    label: '工作台',
    route: '/',
    hint: '当前进度',
  },
  {
    label: '我的简历',
    route: '/resumes',
    hint: '简历资产',
  },
  {
    label: '目标岗位',
    route: '/job-descriptions',
    hint: '真实 JD',
  },
  {
    label: '匹配与优化',
    route: '/ai-job-matches',
    hint: 'AI 主流程',
  },
  {
    label: 'AI 历史',
    route: '/history',
    hint: '结果回看',
  },
]
</script>

<template>
  <aside class="app-sidebar">
    <div class="app-sidebar-brand">
      <span class="app-sidebar-mark">AI</span>
      <div>
        <strong>简历优化</strong>
        <small>Resume Workspace</small>
      </div>
    </div>

    <nav class="app-sidebar-nav" aria-label="主导航">
      <RouterLink
        v-for="item in primaryNavItems"
        :key="item.route"
        :to="item.route"
        class="app-sidebar-link"
      >
        <span>{{ item.label }}</span>
        <small>{{ item.hint }}</small>
      </RouterLink>
    </nav>
  </aside>
</template>
