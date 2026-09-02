<template>
  <main class="auth-page">
    <header class="auth-topbar">
      <RouterLink to="/" class="auth-brand" aria-label="返回简历优化首页">
        <span>CV</span>
        <strong>简历优化</strong>
      </RouterLink>
      <RouterLink to="/" class="auth-home-link">返回首页</RouterLink>
    </header>

    <section class="auth-shell">
      <aside class="auth-context" aria-label="产品工作方式">
        <ProductFlowDemo compact />
      </aside>
      <section class="auth-form-panel">
        <slot />
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import ProductFlowDemo from '@/components/brand/ProductFlowDemo.vue'
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  color: var(--app-text);
  background: var(--app-bg);
}

.auth-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
  min-height: var(--app-shell-header-height);
  padding: var(--app-space-2) max(var(--app-content-gutter), calc((100% - var(--app-layout-standard-max-width)) / 2));
  border-bottom: 1px solid var(--app-border);
  background: var(--app-surface);
}

.auth-brand {
  display: inline-flex;
  align-items: center;
  gap: var(--app-space-2);
  color: var(--app-text);
  font-size: 15px;
  font-weight: 750;
}

.auth-brand span {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: var(--app-radius-md);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  background: var(--app-primary);
}

.auth-home-link {
  padding: 8px 10px;
  border-radius: var(--app-radius-sm);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  font-weight: 650;
}

.auth-home-link:hover,
.auth-home-link:focus-visible {
  color: var(--app-text);
  background: var(--app-surface-soft);
}

.auth-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 460px);
  gap: clamp(48px, 8vw, 128px);
  align-items: center;
  width: min(calc(100% - 64px), var(--app-landing-max-width));
  min-height: calc(100vh - var(--app-shell-header-height));
  margin: 0 auto;
  padding: 64px 0 80px;
}

.auth-context {
  min-width: 0;
}

.auth-form-panel {
  min-width: 0;
  padding: 8px 0;
}

@media (max-width: 820px) {
  .auth-topbar {
    padding-right: var(--app-content-gutter-narrow);
    padding-left: var(--app-content-gutter-narrow);
  }

  .auth-shell {
    grid-template-columns: 1fr;
    gap: 32px;
    width: min(calc(100% - 32px), 560px);
    min-height: auto;
    padding: 40px 0 56px;
  }

  .auth-context {
    order: 0;
  }

  .auth-form-panel {
    order: 1;
  }
}
</style>
