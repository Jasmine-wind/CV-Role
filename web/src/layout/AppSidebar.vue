<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const primaryNavItems = [
  {
    label: '首页',
    route: '/app',
    hint: '分析新岗位',
  },
  {
    label: '我的简历',
    route: '/resumes',
    hint: '真实经历',
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
        <span class="app-sidebar-mark">CV</span>
        <div>
          <strong>简历优化</strong>
          <small>CV Role</small>
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

      </div>
    </div>

    <footer class="app-sidebar-user">
      <RouterLink to="/settings/ai-provider" class="app-sidebar-settings">
        AI Provider 设置
      </RouterLink>
      <div>
        <strong>{{ displayName }}</strong>
        <small>{{ userEmail }}</small>
      </div>
      <el-button plain type="danger" size="small" @click="handleLogout">退出</el-button>
    </footer>
  </aside>
</template>
