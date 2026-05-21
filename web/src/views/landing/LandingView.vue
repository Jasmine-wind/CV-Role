<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const visibleSectionIds = ref(new Set(['hero']))

let revealObserver: IntersectionObserver | null = null

const markSectionVisible = (sectionId: string) => {
  const next = new Set(visibleSectionIds.value)
  next.add(sectionId)
  visibleSectionIds.value = next
}

const isSectionVisible = (sectionId: string) => visibleSectionIds.value.has(sectionId)

const scrollToWorkflow = () => {
  document.getElementById('workflow')?.scrollIntoView({ behavior: 'smooth' })
}

const flowSteps = [
  {
    title: '上传简历',
    description: '建立第一份可分析的简历资产。',
  },
  {
    title: '解析简历',
    description: '提取技能、经历和项目内容。',
  },
  {
    title: '添加目标岗位',
    description: '粘贴真实 JD，生成岗位要求画像。',
  },
  {
    title: '匹配分析',
    description: '看清强匹配、弱匹配和缺失项。',
  },
  {
    title: '优化建议',
    description: '按优先级处理最关键差距。',
  },
  {
    title: '局部改写',
    description: '只优化你选择的真实简历片段。',
  },
]

const aiCapabilities = [
  {
    title: '简历解析',
    description: '提取基础信息、技能、项目和经历。',
  },
  {
    title: '简历诊断',
    description: '发现结构、表达和完整度问题。',
  },
  {
    title: '岗位解析',
    description: '抽取职责、技能、关键词和经验要求。',
  },
  {
    title: '匹配分析',
    description: '展示强匹配、弱匹配和缺失项。',
  },
  {
    title: '优化建议',
    description: '给出优先级、依据和注意事项。',
  },
  {
    title: '局部改写',
    description: '只改写用户确认的真实内容片段。',
  },
  {
    title: 'AI 历史回看',
    description: '回看所有已保存的结构化结果。',
  },
]

onMounted(() => {
  const root = document.querySelector('.landing-page')
  const sections = document.querySelectorAll<HTMLElement>('[data-landing-section]')

  revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      const sectionId = (entry.target as HTMLElement).dataset.landingSection
      if (entry.isIntersecting && sectionId) {
        markSectionVisible(sectionId)
      }
    })
  }, {
    root,
    threshold: 0.34,
    rootMargin: '0px 0px -10% 0px',
  })

  sections.forEach((section) => revealObserver?.observe(section))
})

onUnmounted(() => {
  revealObserver?.disconnect()
})
</script>

<template>
  <main class="landing-page">
    <header class="landing-nav">
      <RouterLink to="/" class="landing-brand">
        <span>AI</span>
        <strong>简历优化</strong>
      </RouterLink>
      <nav>
        <a href="#workflow">核心流程</a>
        <a href="#ai">AI 能力</a>
        <a href="#preview">界面预览</a>
      </nav>
      <div class="landing-nav-actions">
        <el-button @click="router.push('/login')">登录</el-button>
        <el-button type="primary" @click="router.push('/login?redirect=/app')">进入工作台</el-button>
      </div>
    </header>

    <section
      class="landing-snap-section landing-hero-section"
      :class="{ 'is-visible': isSectionVisible('hero') }"
      data-landing-section="hero"
    >
      <div class="landing-section-inner landing-hero">
        <div class="landing-hero-copy">
          <p class="landing-eyebrow">AI Resume Optimization Workspace</p>
          <h1>AI 简历优化与岗位匹配工作台</h1>
          <p>
            从简历解析、岗位匹配到优化建议，帮助你看清简历与目标岗位之间的真实差距。
          </p>
          <div class="landing-hero-actions">
            <el-button type="primary" size="large" @click="router.push('/login?redirect=/app')">进入工作台</el-button>
            <el-button size="large" @click="scrollToWorkflow">查看使用流程</el-button>
          </div>
        </div>

        <aside class="landing-hero-panel" aria-label="产品流程预览">
          <div class="landing-panel-header">
            <span>下一步</span>
            <el-tag type="primary">匹配分析</el-tag>
          </div>
          <h2>选择已解析简历和目标岗位，生成匹配报告。</h2>
          <div class="landing-metric-grid">
            <span>
              <strong>3</strong>
              简历资产
            </span>
            <span>
              <strong>2</strong>
              目标岗位
            </span>
            <span>
              <strong>86</strong>
              匹配分
            </span>
          </div>
        </aside>
      </div>
    </section>

    <section
      id="workflow"
      class="landing-snap-section"
      :class="{ 'is-visible': isSectionVisible('workflow') }"
      data-landing-section="workflow"
    >
      <div class="landing-section-inner">
        <div class="landing-section-heading">
          <p class="landing-eyebrow">Workflow</p>
          <h2>一条清晰的求职优化流程</h2>
        </div>
        <div class="landing-flow">
          <article
            v-for="(step, index) in flowSteps"
            :key="step.title"
            :style="{ '--landing-item-index': String(index + 1) }"
          >
            <span>{{ index + 1 }}</span>
            <strong>{{ step.title }}</strong>
            <p>{{ step.description }}</p>
          </article>
        </div>
      </div>
    </section>

    <section
      id="ai"
      class="landing-snap-section"
      :class="{ 'is-visible': isSectionVisible('ai') }"
      data-landing-section="ai"
    >
      <div class="landing-section-inner">
        <div class="landing-section-heading">
          <p class="landing-eyebrow">AI Capability</p>
          <h2>AI 不替你编简历，只给出可验证的优化依据</h2>
        </div>
        <div class="landing-feature-grid">
          <article
            v-for="(item, index) in aiCapabilities"
            :key="item.title"
            class="landing-card"
            :style="{ '--landing-item-index': String(index + 1) }"
          >
            <h3>{{ item.title }}</h3>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </div>
    </section>

    <section
      id="preview"
      class="landing-snap-section"
      :class="{ 'is-visible': isSectionVisible('preview') }"
      data-landing-section="preview"
    >
      <div class="landing-section-inner landing-preview-layout">
        <div class="landing-section-heading">
          <p class="landing-eyebrow">Preview</p>
          <h2>像 SaaS 工具一样管理你的求职材料</h2>
        </div>
        <div class="landing-product-preview" aria-label="工作台界面预览">
          <aside>
            <span>工作台</span>
            <span>我的简历</span>
            <span>目标岗位</span>
            <span>匹配与优化</span>
            <span>AI 历史</span>
          </aside>
          <section>
            <div class="landing-preview-header">
              <strong>继续完成岗位匹配流程</strong>
              <el-button type="primary">开始匹配</el-button>
            </div>
            <div class="landing-preview-grid">
              <article>
                <small>当前状态</small>
                <strong>简历和岗位已就绪</strong>
                <p>下一步生成匹配报告。</p>
              </article>
              <article>
                <small>AI 建议</small>
                <strong>补强项目经历证据</strong>
                <p>优先补充与岗位相关的后端接口、性能优化和协作经历。</p>
              </article>
            </div>
          </section>
        </div>
      </div>
    </section>

    <section
      class="landing-snap-section landing-final-section"
      :class="{ 'is-visible': isSectionVisible('start') }"
      data-landing-section="start"
    >
      <div class="landing-section-inner">
        <p class="landing-eyebrow">Start</p>
        <h2>准备开始一次完整的简历优化演示</h2>
        <p>上传简历、添加目标岗位，再用 AI 报告找到下一步优化方向。</p>
        <div class="landing-hero-actions">
          <el-button type="primary" size="large" @click="router.push('/login?redirect=/app')">立即体验</el-button>
          <el-button size="large" @click="router.push('/login')">登录账号</el-button>
        </div>
      </div>
    </section>
  </main>
</template>
