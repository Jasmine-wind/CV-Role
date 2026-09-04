<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'

const route = useRoute()

const contentWidthClass = computed(() => {
  const layoutWidth = route.meta.layoutWidth || 'standard'
  return `is-${layoutWidth}`
})

const isTaskLayout = computed(() => route.name === 'workspace' || route.name === 'job-analysis')

// 窄屏导航作为可键盘操作的 Drawer：关闭时不留在 tab 顺序，打开时焦点进入导航。
const menuOpen = ref(false)
const isNarrowScreen = ref(false)
const shouldRestoreMenuFocus = ref(false)
const appPage = ref<HTMLElement | null>(null)
const mainContent = ref<HTMLElement | null>(null)
let narrowMediaQuery: MediaQueryList | null = null

const closeMenu = (restoreFocus = false) => {
  shouldRestoreMenuFocus.value = restoreFocus
  menuOpen.value = false
}

const toggleMenu = () => {
  if (!isNarrowScreen.value) return
  if (menuOpen.value) {
    closeMenu(true)
    return
  }
  shouldRestoreMenuFocus.value = false
  menuOpen.value = true
}

const focusMainContent = () => {
  appPage.value?.scrollTo({ top: 0, left: 0, behavior: 'auto' })
  mainContent.value?.focus({ preventScroll: true })
}

const resetRouteViewport = async () => {
  await nextTick()
  focusMainContent()
}

const handleNarrowChange = (event: MediaQueryListEvent) => {
  isNarrowScreen.value = event.matches
  if (!event.matches) closeMenu(false)
}

watch(
  () => route.fullPath,
  () => {
    closeMenu(false)
  },
)

// Query changes inside the evidence/workspace surfaces must not reset their local scroll positions.
watch(
  () => route.path,
  () => {
    void resetRouteViewport()
  },
)

watch(menuOpen, (open) => {
  if (!isNarrowScreen.value) return
  const restoreFocus = shouldRestoreMenuFocus.value
  void nextTick().then(() => {
    const selector = open
      ? '.app-sidebar.is-open .app-sidebar-link'
      : restoreFocus
        ? '.app-topbar-menu-button'
        : null
    if (selector) document.querySelector<HTMLElement>(selector)?.focus()
    shouldRestoreMenuFocus.value = false
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
  <div
    class="app-shell"
    :class="{ 'is-task-layout': isTaskLayout }"
    @keydown.esc="closeMenu(true)"
  >
    <AppSidebar
      :open="menuOpen"
      :drawer="isNarrowScreen"
      @close="closeMenu(true)"
      @navigate="closeMenu(false)"
    />
    <button
      v-if="menuOpen && isNarrowScreen"
      type="button"
      tabindex="-1"
      class="app-shell-overlay"
      aria-hidden="true"
      @click="closeMenu(true)"
    />
    <div ref="appPage" class="app-page" :inert="menuOpen && isNarrowScreen">
      <a class="app-skip-link" href="#app-main-content" @click.prevent="focusMainContent">
        跳到主要内容
      </a>
      <AppTopbar :menu-open="menuOpen" @toggle-menu="toggleMenu" />
      <main
        id="app-main-content"
        ref="mainContent"
        class="app-content"
        :class="contentWidthClass"
        tabindex="-1"
      >
        <slot />
      </main>
    </div>
  </div>
</template>
