<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const pageTitle = computed(() => route.meta.title || '工作台')

const displayName = computed(() => {
  const user = authStore.currentUser

  return user?.nickname || user?.username || '已登录用户'
})

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
  <header class="app-topbar">
    <div>
      <p class="app-topbar-kicker">AI Resume Optimizer</p>
      <h1>{{ pageTitle }}</h1>
    </div>

    <div class="app-topbar-user">
      <span>{{ displayName }}</span>
      <el-button plain type="danger" @click="handleLogout">退出</el-button>
    </div>
  </header>
</template>
