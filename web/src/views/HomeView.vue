<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const displayName = computed(() => {
  const user = authStore.currentUser

  return user?.nickname || user?.username || '已登录用户'
})

const handleFetchMe = async () => {
  try {
    await authStore.fetchMe()
    ElMessage.success('当前用户信息已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取当前用户失败')
  }
}

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
  <main class="home-page">
    <section class="home-shell">
      <header class="home-header">
        <div>
          <h1 class="home-title">AI 简历优化匹配系统</h1>
          <p class="home-subtitle">当前处于 v0.1 前端认证页面搭建阶段。</p>
        </div>
        <el-space v-if="!authStore.isAuthenticated">
          <el-button @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </el-space>
        <el-space v-else>
          <el-button @click="router.push('/resumes')">我的简历</el-button>
          <el-button type="primary" @click="router.push('/jobs')">岗位列表</el-button>
          <el-button :loading="authStore.loading" @click="handleFetchMe">刷新用户</el-button>
          <el-button type="danger" plain @click="handleLogout">退出</el-button>
        </el-space>
      </header>

      <el-card class="home-card" shadow="never">
        <h2 class="home-title">基础首页</h2>
        <template v-if="authStore.isAuthenticated">
          <p class="home-subtitle">当前登录用户：{{ displayName }}</p>
          <ul class="home-list">
            <li>Token 已保存到 localStorage，刷新页面后仍可恢复。</li>
            <li>点击“刷新用户”会使用当前 Token 请求 /api/users/me。</li>
            <li>点击“退出”会清除 Token 和当前用户信息。</li>
          </ul>
        </template>
        <template v-else>
          <ul class="home-list">
            <li>本页用于确认登录后的基础入口页面。</li>
            <li>当前没有检测到 Token，请先登录。</li>
            <li>后续小闭环会继续接入 Axios 和路由守卫。</li>
          </ul>
        </template>
      </el-card>
    </section>
  </main>
</template>
