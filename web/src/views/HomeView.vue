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
          <p class="home-subtitle">工作台用于串联简历、目标岗位、匹配与优化、AI 历史回看。</p>
        </div>
        <el-space v-if="!authStore.isAuthenticated">
          <el-button @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </el-space>
        <el-space v-else>
          <el-button @click="router.push('/resumes')">我的简历</el-button>
          <el-button @click="router.push('/job-descriptions')">目标岗位</el-button>
          <el-button @click="router.push('/ai-job-matches')">匹配与优化</el-button>
          <el-button @click="router.push('/jobs')">岗位库</el-button>
          <el-button type="primary" @click="router.push('/history')">AI 历史</el-button>
          <el-button :loading="authStore.loading" @click="handleFetchMe">刷新用户</el-button>
          <el-button type="danger" plain @click="handleLogout">退出</el-button>
        </el-space>
      </header>

      <el-card class="home-card" shadow="never">
        <h2 class="home-title">工作台</h2>
        <template v-if="authStore.isAuthenticated">
          <p class="home-subtitle">当前登录用户：{{ displayName }}</p>
          <ul class="home-list">
            <li>先上传并解析简历，再补充目标岗位。</li>
            <li>目标岗位解析完成后，进入匹配与优化生成岗位优化建议。</li>
            <li>已生成的分析、匹配、建议和改写结果后续统一从 AI 历史回看。</li>
          </ul>
        </template>
        <template v-else>
          <ul class="home-list">
            <li>登录后进入工作台，按流程完成简历优化。</li>
            <li>当前没有检测到 Token，请先登录。</li>
          </ul>
        </template>
      </el-card>
    </section>
  </main>
</template>
