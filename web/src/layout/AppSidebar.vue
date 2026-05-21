<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const primaryNavItems = [
  {
    label: '工作台',
    route: '/app',
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

const auxiliaryNavItems = [
  {
    label: '岗位库',
    route: '/jobs',
    hint: '预置参考',
  },
]

const displayName = computed(() => {
  const user = authStore.currentUser

  return user?.nickname || user?.username || '已登录用户'
})

const userEmail = computed(() => authStore.currentUser?.email || '当前账号')

const handleLogout = () => {
  authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(() => {
  if (authStore.isAuthenticated && !authStore.currentUser) {
    authStore.fetchMe().catch(() => undefined)
  }
})
</script>

<template>
  <aside class="app-sidebar">
    <div class="app-sidebar-main">
      <div class="app-sidebar-brand">
        <span class="app-sidebar-mark">AI</span>
        <div>
          <strong>简历优化</strong>
          <small>Resume Workspace</small>
        </div>
      </div>

      <div class="app-sidebar-menu">
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

        <section class="app-sidebar-section">
          <p>辅助入口</p>
          <nav class="app-sidebar-nav is-secondary" aria-label="辅助导航">
            <RouterLink
              v-for="item in auxiliaryNavItems"
              :key="item.route"
              :to="item.route"
              class="app-sidebar-link"
            >
              <span>{{ item.label }}</span>
              <small>{{ item.hint }}</small>
            </RouterLink>
          </nav>
        </section>
      </div>
    </div>

    <footer class="app-sidebar-user">
      <div>
        <strong>{{ displayName }}</strong>
        <small>{{ userEmail }}</small>
      </div>
      <el-button plain type="danger" size="small" @click="handleLogout">退出</el-button>
    </footer>
  </aside>
</template>
