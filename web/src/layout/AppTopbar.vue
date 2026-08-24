<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

defineProps<{
  menuOpen?: boolean
}>()

const emit = defineEmits<{
  toggleMenu: []
}>()

const router = useRouter()
const authStore = useAuthStore()

const displayName = computed(() => {
  const user = authStore.currentUser
  return user?.nickname || user?.username || '已登录用户'
})

const userEmail = computed(() => authStore.currentUser?.email || '当前账号')

const handleAccountCommand = (command: string) => {
  if (command === 'settings') {
    router.push('/settings/ai-provider')
    return
  }
  if (command === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}
</script>

<template>
  <header class="app-topbar">
    <div class="app-topbar-left">
      <el-button
        class="app-topbar-menu-button"
        text
        aria-label="打开导航菜单"
        aria-controls="app-primary-navigation"
        :aria-expanded="menuOpen"
        @click="emit('toggleMenu')"
      >
        菜单
      </el-button>
      <RouterLink to="/app" class="app-topbar-brand">
        <span class="app-sidebar-mark">CV</span>
        简历优化
      </RouterLink>
    </div>

    <div class="app-topbar-account">
      <el-dropdown trigger="click" @command="handleAccountCommand">
        <button type="button" class="app-account-trigger" aria-label="账号菜单">
          <strong>{{ displayName }}</strong>
          <small>{{ userEmail }}</small>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="settings">AI 设置</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>
