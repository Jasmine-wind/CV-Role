<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const valuePoints = [
  {
    title: '岗位定向',
    description: '粘贴真实 JD，逐条核对岗位要求与当前简历中的表达。',
  },
  {
    title: '真实可控',
    description: '分析只依据你当前的简历材料，AI 不会编造经历或成果。',
  },
  {
    title: '直接得到可投递 PDF',
    description: '编辑确认后预览排版，导出为可直接投递的 PDF。',
  },
]
</script>

<template>
  <main class="landing-page">
    <header class="landing-nav">
      <RouterLink to="/" class="landing-brand">
        <span>CV</span>
        <strong>简历优化</strong>
      </RouterLink>
      <el-button text @click="router.push('/login')">登录</el-button>
    </header>

    <section class="landing-hero">
      <h1>为目标岗位准备更合适的简历</h1>
      <p>
        上传已有简历，粘贴目标岗位 JD，看清哪些要求已有体现、哪些值得完善，
        确认后直接得到可投递的 PDF。
      </p>
      <div class="landing-hero-actions">
        <el-button type="primary" size="large" @click="router.push('/register')">
          开始优化
        </el-button>
      </div>
      <small>第一次使用只需要两项输入：我的简历 + 目标岗位 JD。</small>
    </section>

    <section class="landing-values" aria-label="产品价值">
      <article v-for="item in valuePoints" :key="item.title">
        <h2>{{ item.title }}</h2>
        <p>{{ item.description }}</p>
      </article>
    </section>

    <footer class="landing-foot">分析真实可控，重要决定始终留给你。</footer>
  </main>
</template>

<style scoped>
.landing-page {
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr auto auto;
  color: var(--app-text);
  background: var(--app-bg);
}

.landing-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 60px;
  padding: 0 max(24px, calc((100% - 1080px) / 2));
  border-bottom: 1px solid var(--app-border-soft);
  background: var(--app-surface);
}

.landing-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--app-text);
  font-weight: 800;
}

.landing-brand span {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: var(--app-radius-sm);
  color: #fff;
  font-size: 12px;
  background: var(--app-primary);
}

.landing-hero {
  display: grid;
  justify-items: start;
  align-content: center;
  gap: 20px;
  width: min(100% - 48px, 760px);
  margin: 0 auto;
  padding: 96px 0 72px;
}

.landing-hero h1 {
  margin: 0;
  color: var(--app-text);
  font-size: clamp(32px, 4.6vw, 46px);
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.landing-hero > p {
  margin: 0;
  max-width: 640px;
  color: var(--app-text-secondary);
  font-size: 16px;
  line-height: 1.8;
}

.landing-hero-actions {
  display: flex;
  gap: 12px;
}

.landing-hero small {
  color: var(--app-text-muted);
  font-size: 13px;
}

.landing-values {
  width: min(100% - 48px, 1080px);
  margin: 0 auto;
  padding: 8px 0 72px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 32px;
}

.landing-values article {
  padding-top: 16px;
  border-top: 1px solid var(--app-border);
}

.landing-values h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
}

.landing-values p {
  margin: 8px 0 0;
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.landing-foot {
  padding: 20px 24px;
  border-top: 1px solid var(--app-border-soft);
  color: var(--app-text-muted);
  font-size: 13px;
  text-align: center;
}

@media (max-width: 820px) {
  .landing-hero {
    padding: 64px 0 48px;
  }

  .landing-values {
    grid-template-columns: 1fr;
    gap: 20px;
  }
}
</style>
