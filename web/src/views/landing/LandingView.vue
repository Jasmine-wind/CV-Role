<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProductFlowDemo, { type ProductFlowStageId } from '@/components/brand/ProductFlowDemo.vue'

const router = useRouter()

const storyStages: {
  id: ProductFlowStageId
  index: string
  label: string
  title: string
  description: string
}[] = [
  {
    id: 'resume-job',
    index: '01',
    label: 'RESUME × TARGET JOB',
    title: '先看清这个岗位真正需要什么。',
    description: '从一份已经确认的简历和一份真实 JD 开始，不先猜一个分数。',
  },
  {
    id: 'evidence',
    index: '02',
    label: 'REQUIREMENT → EVIDENCE',
    title: '再找到简历里真正支持它的证据。',
    description: '岗位要求与简历原文逐条对应，相关表达在哪里，可以直接回看。',
  },
  {
    id: 'gap',
    index: '03',
    label: 'GAP',
    title: '有证据就用，没有证据就明确停下来。',
    description: '当前材料不足，只说明这份简历还没有写出支持它的证据，不代表你没有这项能力。',
  },
  {
    id: 'rewrite',
    index: '04',
    label: 'SUGGESTED EDIT / CONFIRM',
    title: 'AI 提建议，最终决定仍然由你做。',
    description: '每一次修改都能看到原文、建议和原因。只有你确认后，内容才会进入编辑草稿。',
  },
  {
    id: 'preview',
    index: '05',
    label: 'PREVIEW / EXPORT',
    title: '确认后，直接得到可投递的 PDF。',
    description: '保存岗位版本，预览最终排版，通过检查后导出一份可以直接投递的文件。',
  },
]

const activeStage = ref<ProductFlowStageId>('resume-job')
const storySectionElements = ref<HTMLElement[]>([])
let storyObserver: IntersectionObserver | null = null

const setStorySection = (element: unknown) => {
  if (element instanceof HTMLElement && !storySectionElements.value.includes(element)) {
    storySectionElements.value.push(element)
  }
}

const updateActiveStage = () => {
  const focusLine = window.innerHeight * 0.45
  const focusedSection = storySectionElements.value
    .map((section) => {
      const rect = section.getBoundingClientRect()
      return {
        section,
        distance: Math.abs(rect.top + rect.height / 2 - focusLine),
        inViewport: rect.bottom > focusLine && rect.top < focusLine,
      }
    })
    .filter((item) => item.inViewport)
    .sort((left, right) => left.distance - right.distance)[0]

  const stage = focusedSection?.section.getAttribute('data-stage') as ProductFlowStageId | null
  if (stage && storyStages.some((item) => item.id === stage)) {
    activeStage.value = stage
  }
}

const observeStorySections = () => {
  storyObserver?.disconnect()
  storySectionElements.value.forEach((section) => storyObserver?.observe(section))
}

onMounted(async () => {
  await nextTick()
  if ('IntersectionObserver' in window) {
    storyObserver = new IntersectionObserver(updateActiveStage, {
      rootMargin: '-28% 0px -45% 0px',
      threshold: [0, 0.25, 0.5, 0.75],
    })
    observeStorySections()
    window.addEventListener('resize', observeStorySections)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', observeStorySections)
  storyObserver?.disconnect()
})
</script>

<template>
  <main class="landing-page">
    <header class="landing-nav">
      <RouterLink to="/" class="landing-brand" aria-label="简历优化首页">
        <span>CV</span>
        <strong>简历优化</strong>
      </RouterLink>
      <div class="landing-nav-actions">
        <el-button text @click="router.push('/login')">登录</el-button>
        <el-button type="primary" @click="router.push('/register')">开始优化</el-button>
      </div>
    </header>

    <section class="landing-hero">
      <div class="landing-hero-copy">
        <h1><span>为每一个岗位，</span><span>认真调整一次简历。</span></h1>
        <p class="landing-hero-description">
          基于真实经历找到岗位要求、证据和差距，再优化表达。不编造经历，重要决定始终留给你。
        </p>
        <div class="landing-hero-actions">
          <el-button type="primary" size="large" @click="router.push('/register')">
            开始优化
          </el-button>
          <el-button text size="large" @click="router.push('/login')">已有账号？登录</el-button>
        </div>
        <p class="landing-hero-note">第一次使用只需要两项输入：我的简历 + 目标岗位 JD。</p>
      </div>
    </section>

    <section class="landing-story" aria-label="岗位定向产品过程">
      <div class="landing-story-demo">
        <div class="landing-story-demo-sticky">
          <ProductFlowDemo :active-stage="activeStage" />
        </div>
      </div>

      <div class="landing-story-sections">
        <article
          v-for="stage in storyStages"
          :key="stage.id"
          :ref="setStorySection"
          class="landing-story-section"
          :data-stage="stage.id"
        >
          <header class="landing-story-section-header">
            <span class="landing-story-index">{{ stage.index }}</span>
            <span>{{ stage.label }}</span>
          </header>
          <h2>{{ stage.title }}</h2>
          <p>{{ stage.description }}</p>
          <ProductFlowDemo
            class="landing-mobile-demo"
            compact
            :active-stage="stage.id"
          />
        </article>
      </div>
    </section>

    <section class="landing-final-cta">
      <div>
        <h2>让最值得被看见的部分，更适合这个岗位。</h2>
        <p>你的经历没有改变，只是表达变得更清楚、更有针对性。</p>
      </div>
      <el-button type="primary" size="large" @click="router.push('/register')">
        开始优化简历
      </el-button>
    </section>

    <footer class="landing-foot">从真实材料出发，得到一份你愿意投递的简历。</footer>
  </main>
</template>

<style scoped>
.landing-page {
  min-height: 100vh;
  color: var(--app-text);
  background: var(--app-bg);
}

.landing-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-6);
  min-height: var(--app-shell-header-height);
  padding: var(--app-space-2) max(var(--app-content-gutter), calc((100% - var(--app-landing-max-width)) / 2));
  border-bottom: 1px solid var(--app-border);
  background: var(--app-surface);
}

.landing-brand {
  display: inline-flex;
  align-items: center;
  gap: var(--app-space-2);
  color: var(--app-text);
  font-size: 15px;
  font-weight: 750;
}

.landing-brand span {
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

.landing-nav-actions {
  display: flex;
  align-items: center;
  gap: var(--app-space-2);
}

.landing-hero {
  display: grid;
  place-items: center;
  width: min(calc(100% - 64px), var(--app-landing-max-width));
  min-height: min(720px, calc(100vh - var(--app-shell-header-height)));
  margin: 0 auto;
  padding: 96px 0 112px;
}

.landing-hero-copy {
  display: grid;
  min-width: 0;
  justify-items: start;
  gap: var(--app-space-6);
  width: min(100%, 760px);
}

.landing-hero h1 {
  margin: 0;
  color: var(--app-text);
  font-size: clamp(38px, 4vw, 56px);
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.landing-hero h1 span {
  display: block;
  white-space: nowrap;
}

.landing-hero-description {
  max-width: 48ch;
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 17px;
  line-height: 1.8;
}

.landing-hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-3);
  align-items: center;
}

.landing-hero-note {
  margin: 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-sm);
}

.landing-story {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(420px, 0.95fr);
  gap: clamp(48px, 8vw, 128px);
  align-items: start;
  width: min(calc(100% - 64px), var(--app-landing-max-width));
  margin: 0 auto;
  padding: 32px 0 0;
  border-top: 1px solid var(--app-border-strong);
}

.landing-story-demo {
  min-width: 0;
  align-self: stretch;
}

.landing-story-demo-sticky {
  position: sticky;
  top: 32px;
  display: flex;
  min-height: calc(100vh - 64px);
  align-items: center;
}

.landing-story-demo-sticky :deep(.product-flow-demo) {
  width: 100%;
}

.landing-story-sections {
  min-width: 0;
}

.landing-story-section {
  display: grid;
  align-content: center;
  min-height: 76vh;
  padding: 80px 0;
  border-bottom: 1px solid var(--app-border);
}

.landing-story-section-header {
  display: flex;
  align-items: baseline;
  gap: var(--app-space-3);
  margin-bottom: var(--app-space-6);
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.landing-story-index {
  color: var(--app-primary);
}

.landing-story-section h2 {
  max-width: 15ch;
  margin: 0;
  color: var(--app-text);
  font-size: clamp(28px, 3.2vw, 46px);
  line-height: 1.18;
  letter-spacing: -0.035em;
}

.landing-story-section p {
  max-width: 42ch;
  margin: var(--app-space-6) 0 0;
  color: var(--app-text-secondary);
  font-size: 16px;
  line-height: 1.8;
}

.landing-mobile-demo {
  display: none;
}

.landing-final-cta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-8);
  width: min(calc(100% - 64px), var(--app-landing-max-width));
  margin: 0 auto;
  padding: 96px 0;
  border-bottom: 1px solid var(--app-border-strong);
}

.landing-final-cta h2 {
  max-width: 20ch;
  margin: 0;
  color: var(--app-text);
  font-size: clamp(24px, 3vw, 40px);
  line-height: 1.2;
  letter-spacing: -0.03em;
}

.landing-final-cta p {
  margin: var(--app-space-3) 0 0;
  color: var(--app-text-secondary);
  font-size: 15px;
  line-height: 1.7;
}

.landing-foot {
  padding: var(--app-space-5) var(--app-content-gutter);
  color: var(--app-text-muted);
  font-size: var(--app-font-size-sm);
  text-align: center;
}

@media (max-width: 900px) {
  .landing-hero {
    min-height: 0;
    padding: 80px 0 96px;
  }

  .landing-story {
    display: block;
    padding-top: 0;
  }

  .landing-story-demo {
    display: none;
  }

  .landing-story-section {
    min-height: 0;
    padding: 64px 0;
  }

  .landing-mobile-demo {
    display: block;
    margin-top: 32px;
  }

  .landing-final-cta {
    align-items: flex-start;
    flex-direction: column;
    padding: 72px 0;
  }
}

@media (max-width: 640px) {
  .landing-nav {
    padding-right: var(--app-content-gutter-narrow);
    padding-left: var(--app-content-gutter-narrow);
  }

  .landing-nav-actions .el-button--primary {
    display: none;
  }

  .landing-hero,
  .landing-story,
  .landing-final-cta {
    width: min(calc(100% - 32px), var(--app-landing-max-width));
  }

  .landing-hero {
    padding: 56px 0 72px;
  }

  .landing-hero h1 {
    font-size: clamp(32px, 10vw, 42px);
  }

  .landing-hero-description {
    font-size: 16px;
  }

  .landing-story-section {
    padding: 56px 0;
  }

  .landing-story-section h2 {
    font-size: clamp(28px, 8vw, 36px);
  }

  .landing-final-cta {
    padding: 64px 0;
  }

  .landing-final-cta .el-button {
    width: 100%;
  }

  .landing-foot {
    padding-right: var(--app-content-gutter-narrow);
    padding-left: var(--app-content-gutter-narrow);
  }
}
</style>
