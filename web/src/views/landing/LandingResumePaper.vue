<script setup lang="ts">
import { computed } from 'vue'

export type LandingResumeStage =
  | 'initial'
  | 'resume-job'
  | 'requirement'
  | 'evidence'
  | 'gap'
  | 'confirm-export'
  | 'final'

const props = defineProps<{
  stage: LandingResumeStage
}>()

const changedBullet = '负责项目关键数据整理与分析，根据结果推动后续方案调整。'
const originalBullet = '负责核心项目的规划、协作与交付，推动多个关键节点按期落地。'

const isConfirmed = computed(() => props.stage === 'confirm-export' || props.stage === 'final')
const isEvidence = computed(() => props.stage === 'evidence')
const isFinal = computed(() => props.stage === 'final')
const featuredBullet = computed(() => (isConfirmed.value ? changedBullet : originalBullet))
</script>

<template>
  <article class="resume-paper" :class="[`is-${stage}`, { 'is-confirmed': isConfirmed }]" aria-label="林然的简历">
    <header class="resume-header">
      <div>
        <p class="resume-status">{{ isFinal ? '岗位定向版本 · 已确认' : '原始简历 · 当前版本' }}</p>
        <h2>林然</h2>
        <p class="resume-role">项目推进 · 数据分析 · 跨团队协作</p>
      </div>
      <div class="resume-contact" aria-label="联系方式">
        <span>linran@example.com</span>
        <span>138 0000 0000</span>
        <span>上海</span>
      </div>
    </header>

    <div class="resume-rule" />

    <section class="resume-section">
      <header class="resume-section-header">
        <h3>工作经历</h3>
        <span>项目推进 · 数据分析</span>
      </header>
      <article class="resume-entry">
        <div class="resume-entry-heading">
          <div>
            <h4>某互联网公司</h4>
            <p>项目运营 / 数据分析</p>
          </div>
          <time>2022.06 — 至今</time>
        </div>
        <ul>
          <li>
            <Transition name="resume-copy" mode="out-in">
              <span :key="featuredBullet" :class="{ 'is-evidence': isEvidence }">
                <mark v-if="isEvidence">负责核心项目的规划、协作与交付，推动多个关键节点按期落地。</mark>
                <template v-else>{{ featuredBullet }}</template>
              </span>
            </Transition>
          </li>
          <li>整理业务数据并跟进异常，与产品、工程团队协作推进方案。</li>
        </ul>
      </article>
    </section>

    <section class="resume-section">
      <header class="resume-section-header">
        <h3>项目经历</h3>
        <span>独立推进 · 跨团队协作</span>
      </header>
      <article class="resume-entry">
        <div class="resume-entry-heading">
          <div>
            <h4>业务流程优化项目</h4>
            <p>项目负责人</p>
          </div>
          <time>2023.03 — 2023.11</time>
        </div>
        <ul>
          <li>梳理现有流程与协作节点，推动方案从讨论进入稳定执行。</li>
        </ul>
      </article>
    </section>

    <section class="resume-section resume-skills-section">
      <header class="resume-section-header">
        <h3>技能</h3>
        <span>当前已确认</span>
      </header>
      <ul class="resume-skills" aria-label="当前简历技能">
        <li>数据分析</li>
        <li>项目推进</li>
        <li>跨团队协作</li>
        <li>Excel</li>
      </ul>
    </section>

    <span class="resume-page-mark" aria-hidden="true">{{ isFinal ? 'FINAL · 01' : 'CURRENT · 01' }}</span>
  </article>
</template>

<style scoped>
.resume-paper {
  position: relative;
  display: grid;
  align-content: start;
  gap: 18px;
  width: min(100%, 700px);
  min-height: 620px;
  box-sizing: border-box;
  padding: 34px 44px 36px;
  border: 1px solid var(--app-border-strong);
  background: var(--app-document);
  box-shadow: var(--app-shadow-page);
  transition: transform 520ms cubic-bezier(0.22, 1, 0.36, 1), box-shadow 520ms ease;
}

.resume-paper.is-confirm-export,
.resume-paper.is-final {
  transform: scale(1.04);
}

.resume-paper.is-final {
  box-shadow: 0 3px 18px rgba(37, 35, 31, 0.12), 0 1px 2px rgba(37, 35, 31, 0.06);
}

.resume-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
}

.resume-status,
.resume-role,
.resume-contact,
.resume-section-header span,
.resume-entry-heading p,
.resume-entry-heading time,
.resume-page-mark {
  color: var(--app-text-muted);
  font-size: 11px;
  line-height: 1.5;
}

.resume-status {
  margin: 0 0 12px;
  color: var(--app-primary-active);
  font-family: var(--app-font-mono);
  font-weight: 700;
  letter-spacing: 0.06em;
}

.resume-paper.is-final .resume-status {
  color: var(--app-accent-hover);
}

.resume-header h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 38px;
  line-height: 1;
  letter-spacing: -0.045em;
}

.resume-role {
  margin: 10px 0 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.resume-contact {
  display: grid;
  align-content: end;
  justify-items: end;
  gap: 3px;
  text-align: right;
}

.resume-rule {
  height: 1px;
  background: var(--app-border-strong);
}

.resume-section {
  display: grid;
  gap: 10px;
}

.resume-section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 7px;
  border-bottom: 1px solid var(--app-border);
}

.resume-section-header h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 750;
}

.resume-entry {
  display: grid;
  gap: 7px;
}

.resume-entry-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.resume-entry-heading h4 {
  margin: 0;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 700;
}

.resume-entry-heading p,
.resume-entry-heading time {
  margin: 3px 0 0;
}

.resume-entry-heading time {
  flex: 0 0 auto;
  text-align: right;
}

.resume-entry ul {
  display: grid;
  gap: 5px;
  margin: 0;
  padding-left: 17px;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.65;
}

.resume-entry li::marker {
  color: var(--app-primary);
}

.resume-entry li > span {
  display: inline;
}

.resume-entry mark {
  padding: 1px 2px;
  color: inherit;
  background: var(--app-primary-soft);
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
}

.resume-copy-enter-active,
.resume-copy-leave-active {
  transition: opacity 320ms ease, transform 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

.resume-copy-enter-from,
.resume-copy-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

.resume-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin: 0;
  padding: 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  list-style: none;
}

.resume-skills li::before {
  margin-right: 6px;
  color: var(--app-primary);
  content: '·';
}

.resume-paper.is-final .resume-skills li::before {
  color: var(--app-accent);
}

.resume-page-mark {
  position: absolute;
  right: 30px;
  bottom: 16px;
  color: var(--app-border-strong);
  font-family: var(--app-font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

@media (max-width: 900px) {
  .resume-paper {
    width: 100%;
    min-height: 0;
    padding: 26px 24px 36px;
  }

  .resume-paper.is-confirm-export,
  .resume-paper.is-final {
    transform: none;
  }
}

@media (max-width: 520px) {
  .resume-paper {
    gap: 16px;
    padding: 22px 18px 32px;
  }

  .resume-header {
    display: block;
  }

  .resume-header h2 {
    font-size: 32px;
  }

  .resume-contact {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-start;
    gap: 4px 12px;
    margin-top: 12px;
    text-align: left;
  }

  .resume-entry-heading {
    display: block;
  }

  .resume-entry-heading time {
    display: block;
    text-align: left;
  }

  .resume-section-header span {
    max-width: 14ch;
    text-align: right;
  }
}

@media (prefers-reduced-motion: reduce) {
  .resume-paper,
  .resume-copy-enter-active,
  .resume-copy-leave-active {
    transition: none;
  }
}
</style>
