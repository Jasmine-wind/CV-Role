<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const valuePoints = [
  {
    title: '岗位定向',
    description: '根据你粘贴的真实 JD，检查岗位要求与当前简历表达。',
  },
  {
    title: '真实可控',
    description: '分析只依据当前简历；未体现的岗位要求会提示你核对真实经历。',
  },
  {
    title: '清晰结果',
    description: '集中查看已有优势、表达检查项和当前简历尚未体现的要求。',
  },
]
</script>

<template>
  <main class="landing-page landing-simple-page">
    <header class="landing-nav">
      <RouterLink to="/" class="landing-brand">
        <span>CV</span>
        <strong>CV Role</strong>
      </RouterLink>
      <div class="landing-nav-actions">
        <el-button @click="router.push('/login')">登录</el-button>
        <el-button type="primary" @click="router.push('/register')">开始优化</el-button>
      </div>
    </header>

    <section class="landing-simple-hero">
      <div class="landing-simple-copy">
        <p class="landing-eyebrow">岗位定向简历优化</p>
        <h1>为每一个岗位，准备更合适的简历</h1>
        <p>
          上传已有简历，粘贴目标 JD，看清哪里匹配、哪里值得检查，
          以及哪些岗位要求在当前简历中还没有体现。
        </p>
        <div class="landing-hero-actions">
          <el-button type="primary" size="large" @click="router.push('/register')">开始优化</el-button>
          <el-button size="large" @click="router.push('/login')">已有账号</el-button>
        </div>
      </div>

      <aside class="landing-simple-preview app-card" aria-label="产品主流程">
        <span>只需要两项输入</span>
        <div>
          <strong>我的简历</strong>
          <small>你的真实经历与当前表达</small>
        </div>
        <div>
          <strong>目标岗位 JD</strong>
          <small>当前准备投递的岗位要求</small>
        </div>
        <p>选择简历 + 粘贴 JD → 开始分析</p>
      </aside>
    </section>

    <section class="landing-simple-values">
      <article v-for="item in valuePoints" :key="item.title" class="app-card">
        <h2>{{ item.title }}</h2>
        <p>{{ item.description }}</p>
      </article>
    </section>

    <section class="landing-simple-final">
      <h2>复杂步骤留给系统，重要决定留给你</h2>
      <p>第一次使用只需要上传简历、粘贴 JD、开始分析。</p>
      <el-button type="primary" size="large" @click="router.push('/register')">立即开始</el-button>
    </section>
  </main>
</template>

<style scoped>
.landing-simple-page {
  min-height: 100vh;
  height: auto;
  overflow: visible;
  scroll-snap-type: none;
}

.landing-simple-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.75fr);
  gap: 64px;
  width: min(100% - 48px, 1160px);
  min-height: calc(100vh - 72px);
  align-items: center;
  margin: 0 auto;
  padding: 72px 0;
}

.landing-simple-copy h1 {
  max-width: 720px;
  margin: 0;
  color: var(--app-navy);
  font-size: clamp(42px, 6vw, 68px);
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.landing-simple-copy > p:not(.landing-eyebrow) {
  max-width: 680px;
  margin: 24px 0 0;
  color: var(--app-text-secondary);
  font-size: 18px;
  line-height: 1.8;
}

.landing-simple-preview {
  display: grid;
  gap: 14px;
  padding: 28px;
}

.landing-simple-preview > span {
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 800;
}

.landing-simple-preview > div {
  display: grid;
  gap: 5px;
  padding: 18px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
}

.landing-simple-preview strong {
  color: var(--app-text);
}

.landing-simple-preview small,
.landing-simple-preview p,
.landing-simple-values p,
.landing-simple-final p {
  color: var(--app-text-secondary);
  line-height: 1.7;
}

.landing-simple-preview p {
  margin: 4px 0 0;
  color: var(--app-primary);
  font-weight: 800;
}

.landing-simple-values {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  width: min(100% - 48px, 1160px);
  margin: 0 auto;
  padding: 0 0 100px;
}

.landing-simple-values article {
  padding: 24px;
}

.landing-simple-values h2,
.landing-simple-final h2 {
  margin: 0;
  color: var(--app-navy);
}

.landing-simple-values p {
  margin: 12px 0 0;
}

.landing-simple-final {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: 84px 24px;
  text-align: center;
  background: var(--app-surface);
}

.landing-simple-final h2 {
  font-size: 32px;
}

.landing-simple-final p {
  margin: 0;
}

@media (max-width: 820px) {
  .landing-simple-hero,
  .landing-simple-values {
    grid-template-columns: 1fr;
  }

  .landing-simple-hero {
    gap: 36px;
  }
}
</style>
