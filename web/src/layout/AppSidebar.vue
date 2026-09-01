<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

defineProps<{
  /** 窄屏 Drawer 展开状态；桌面端始终可见，忽略该值。 */
  open?: boolean
  /** 仅窄屏 Drawer 关闭时从辅助技术与 tab 顺序中移除导航。 */
  drawer?: boolean
}>()

const emit = defineEmits<{
  navigate: []
}>()

const authStore = useAuthStore()

const primaryNavItems = [
  {
    label: '首页',
    route: '/app',
  },
  {
    label: '我的简历',
    route: '/resumes',
  },
]

onMounted(() => {
  if (authStore.isAuthenticated && !authStore.currentUser) {
    authStore.fetchMe().catch(() => undefined)
  }
})
</script>

<template>
  <aside
    id="app-primary-navigation"
    class="app-sidebar"
    :class="{ 'is-open': open }"
    :aria-hidden="drawer && !open"
    :inert="drawer && !open"
  >
    <div class="app-sidebar-brand">
      <span class="app-sidebar-mark">CV</span>
      <div>
        <strong>简历优化</strong>
        <small>为目标岗位准备简历</small>
      </div>
    </div>

    <nav class="app-sidebar-nav" aria-label="主导航">
      <RouterLink
        v-for="item in primaryNavItems"
        :key="item.route"
        :to="item.route"
        class="app-sidebar-link"
        @click="emit('navigate')"
      >
        {{ item.label }}
      </RouterLink>
    </nav>

  </aside>
</template>
