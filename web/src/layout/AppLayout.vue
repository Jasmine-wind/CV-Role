<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'

const route = useRoute()

const contentWidthClass = computed(() => {
  const layoutWidth = route.meta.layoutWidth || 'default'
  return `is-${layoutWidth}`
})

// 窄屏导航作为可键盘操作的 Drawer：关闭时不留在 tab 顺序，打开时焦点进入导航。
const menuOpen = ref(false)
const isNarrowScreen = ref(false)
let narrowMediaQuery: MediaQueryList | null = null

const closeMenu = () => {
  menuOpen.value = false
}

const toggleMenu = () => {
  if (isNarrowScreen.value) {
    menuOpen.value = !menuOpen.value
  }
}

const handleNarrowChange = (event: MediaQueryListEvent) => {
  isNarrowScreen.value = event.matches
  if (!event.matches) closeMenu()
}

watch(
  () => route.fullPath,
  () => {
    closeMenu()
  },
)

watch(menuOpen, (open) => {
  if (!isNarrowScreen.value) return
  void nextTick().then(() => {
    const selector = open
      ? '.app-sidebar.is-open .app-sidebar-link'
      : '.app-topbar-menu-button'
    document.querySelector<HTMLElement>(selector)?.focus()
  })
})

onMounted(() => {
  narrowMediaQuery = window.matchMedia('(max-width: 960px)')
  isNarrowScreen.value = narrowMediaQuery.matches
  narrowMediaQuery.addEventListener('change', handleNarrowChange)
})

onBeforeUnmount(() => {
  narrowMediaQuery?.removeEventListener('change', handleNarrowChange)
})
</script>

<template>
  <div class="app-shell" @keydown.esc="closeMenu">
    <AppSidebar :open="menuOpen" :drawer="isNarrowScreen" @navigate="closeMenu" />
    <button
      v-if="menuOpen && isNarrowScreen"
      type="button"
      class="app-shell-overlay"
      aria-label="关闭导航菜单"
      @click="closeMenu"
    />
    <div class="app-page" :inert="menuOpen && isNarrowScreen">
      <AppTopbar :menu-open="menuOpen" @toggle-menu="toggleMenu" />
      <main class="app-content" :class="contentWidthClass">
        <slot />
      </main>
    </div>
  </div>
</template>
