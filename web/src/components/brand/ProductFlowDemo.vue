<script setup lang="ts">
import { computed, ref, watch } from 'vue'

export type ProductFlowStageId = 'resume-job' | 'evidence' | 'gap' | 'rewrite' | 'preview'
export type ProductFlowSceneId = ProductFlowStageId | 'requirement'
export type ProductFlowLandingStage = 'resume-job' | 'requirement' | 'evidence' | 'gap' | 'confirm-export'

const props = withDefaults(
  defineProps<{
    compact?: boolean
    activeStage?: ProductFlowStageId
    presentationStage?: ProductFlowSceneId
    /** Landing-only continuous document demo. Auth keeps the default presentation. */
    landing?: boolean
    landingStage?: ProductFlowLandingStage
  }>(),
  {
    compact: false,
    activeStage: undefined,
    presentationStage: undefined,
    landing: false,
    landingStage: 'resume-job',
  },
)

const renderedStage = computed<ProductFlowSceneId | undefined>(
  () => props.presentationStage ?? props.activeStage,
)

const compactStages = [
  { label: '我的简历', value: '林然 · 3 年工作经验', state: 'source' },
  { label: '目标岗位', value: '一份真实岗位 JD', state: 'target' },
  { label: '岗位要求', value: '逐条拆解要求', state: 'requirement' },
  { label: '简历证据', value: '回看材料中的相关表达', state: 'evidence' },
  { label: '当前差距', value: '在事实边界内调整表达', state: 'gap' },
  { label: '用户确认', value: '采纳或继续手动编辑', state: 'apply' },
  { label: '简历预览', value: '确认后导出 PDF', state: 'preview' },
]

const storyStages: { id: ProductFlowSceneId; label: string }[] = [
  { id: 'resume-job', label: '简历与岗位' },
  { id: 'requirement', label: '岗位要求' },
  { id: 'evidence', label: '证据核对' },
  { id: 'gap', label: '差距与建议' },
  { id: 'preview', label: '确认与导出' },
]

const rewriteApplied = ref(false)

watch(
  () => [props.activeStage, props.presentationStage],
  () => {
    rewriteApplied.value = false
  },
)
</script>

<template>
  <div
    class="product-flow-demo"
    :class="[
      landing ? `is-landing-stage-${landingStage}` : `is-scene-${renderedStage ?? 'default'}`,
      {
        'is-compact': compact,
        'is-story-compact': compact && activeStage,
        'is-landing-demo': landing,
      },
    ]"
  >
    <template v-if="landing">
      <div class="landing-review-workspace">
        <article class="landing-review-resume" :class="`is-${landingStage}`" aria-label="林然的当前简历">
          <header class="landing-demo-paper-header">
            <div>
              <span>{{ landingStage === 'confirm-export' ? '岗位定向版本 · 已确认' : '原始简历 · 当前版本' }}</span>
              <h3>林然</h3>
              <p>项目推进 · 数据分析 · 跨团队协作</p>
            </div>
            <div class="landing-demo-paper-contact">
              <span>林然 · 3 年</span>
              <span>上海</span>
            </div>
          </header>
          <div class="landing-demo-paper-rule" />
          <section class="landing-demo-paper-section">
            <header><strong>工作经历</strong><span>项目推进</span></header>
            <div class="landing-demo-entry">
              <div class="landing-demo-entry-heading"><strong>某互联网公司</strong><span>项目运营 / 数据分析</span></div>
              <Transition name="landing-demo-copy" mode="out-in">
                <p :key="landingStage === 'confirm-export' ? 'confirmed' : 'original'" :class="{ 'is-evidence': landingStage === 'evidence' }">
                  <mark v-if="landingStage === 'evidence'">负责核心项目的规划、协作与交付，推动多个关键节点按期落地。</mark>
                  <template v-else-if="landingStage === 'confirm-export'">负责项目关键数据整理与分析，根据结果推动后续方案调整。</template>
                  <template v-else>负责核心项目的规划、协作与交付，推动多个关键节点按期落地。</template>
                </p>
              </Transition>
              <p>整理业务数据并跟进异常，与产品、工程团队协作推进方案。</p>
            </div>
          </section>
          <section class="landing-demo-paper-section landing-demo-evidence-block">
            <header><strong>项目 / 证据</strong><span>{{ landingStage === 'gap' ? '需要澄清' : '当前材料' }}</span></header>
            <p>梳理现有流程与协作节点，推动方案稳定执行。</p>
          </section>
          <footer class="landing-demo-paper-skills"><span>技能</span><p>数据分析 · 项目推进 · 跨团队协作</p></footer>
        </article>

        <aside class="landing-review-margin" aria-label="当前审阅内容">
          <Transition name="landing-demo-annotation" mode="out-in">
            <div v-if="landingStage === 'resume-job'" key="job" class="landing-review-content landing-demo-job">
              <span class="landing-demo-annotation-label">目标岗位</span><strong>数据分析 / 商业分析</strong><span>某互联网公司</span>
              <p>岗位信息已经和当前简历放在同一个工作区。</p>
            </div>
            <div v-else-if="landingStage === 'requirement'" key="requirement" class="landing-review-content landing-demo-requirement">
              <span class="landing-demo-annotation-label">岗位要求</span>
              <ol><li>有相关项目经验</li><li class="is-selected">能独立推进复杂任务</li><li>具备数据分析能力</li><li>有良好的跨团队协作能力</li></ol>
            </div>
            <div v-else-if="landingStage === 'evidence'" key="evidence" class="landing-review-content landing-demo-evidence">
              <span class="landing-demo-annotation-label">证据核对</span><strong>能独立推进复杂任务</strong>
              <blockquote>“负责核心项目的规划、协作与交付，推动多个关键节点按期落地。”</blockquote>
              <span class="landing-demo-annotation-source">来自当前简历原文</span>
            </div>
            <div v-else-if="landingStage === 'gap'" key="gap" class="landing-review-content landing-demo-gap">
              <span class="landing-demo-annotation-label">生成建议</span><strong>具备数据分析能力</strong>
              <div class="landing-demo-diff"><div><span>原句</span><p>整理业务数据并跟进异常。</p></div><div class="is-suggested"><span>建议</span><p>负责项目关键数据整理与分析，根据结果推动后续方案调整。</p><small>更贴近岗位要求，但没有新增经历。</small></div><div class="is-empty"><span>不建议添加</span><p>“独立搭建数据仓库”</p><small>当前简历中没有对应证据。</small></div></div>
            </div>
            <div v-else key="confirm" class="landing-review-content landing-demo-confirm">
              <span class="landing-demo-annotation-label">确认与导出</span><strong>✓ 已确认</strong><p>建议已经写回当前岗位版本。</p><span>可以导出 PDF</span>
            </div>
          </Transition>
        </aside>
      </div>
    </template>

    <template v-else-if="compact && !activeStage">
      <div class="product-flow-heading">
        <div class="product-flow-heading-line">
          <h2>一段真实的岗位定向过程</h2>
          <span class="product-flow-label">产品演示</span>
        </div>
        <p>从已有材料开始，逐步看清要求、证据和下一步。</p>
      </div>

      <ol class="product-flow-list" aria-label="岗位定向过程">
        <li v-for="(stage, index) in compactStages" :key="stage.label" class="product-flow-stage">
          <span class="product-flow-node" :class="`is-${stage.state}`" aria-hidden="true" />
          <div class="product-flow-copy">
            <strong>{{ stage.label }}</strong>
            <span>{{ stage.value }}</span>
          </div>
          <span v-if="index < compactStages.length - 1" class="product-flow-arrow" aria-hidden="true" />
        </li>
      </ol>
    </template>

    <template v-else>
      <div class="product-flow-heading">
        <div class="product-flow-heading-line">
          <h2>岗位定向，逐步确认</h2>
          <span v-if="!compact" class="product-flow-label">产品演示</span>
        </div>
        <p v-if="!compact">同一份简历，经过要求、证据、编辑，直到可以交付。</p>
      </div>

      <ol v-if="!compact" class="product-flow-story-rail" aria-label="岗位定向阶段">
        <li
          v-for="stage in storyStages"
          :key="stage.id"
          :class="{ 'is-active': renderedStage === stage.id }"
        >
          <span class="product-flow-story-node" aria-hidden="true" />
          <span>{{ stage.label }}</span>
        </li>
      </ol>

      <section class="demo-scene" :aria-label="renderedStage || '岗位定向阶段'">
        <div class="demo-scene-heading">
          <span v-if="renderedStage === 'resume-job'">我的简历</span>
          <span v-else-if="renderedStage === 'requirement'">岗位要求</span>
          <span v-else-if="renderedStage === 'evidence'">简历证据</span>
          <span v-else-if="renderedStage === 'gap'">当前差距</span>
          <span v-else-if="renderedStage === 'rewrite'">修改建议 / 确认</span>
          <span v-else>简历预览 / 导出</span>
          <strong v-if="renderedStage === 'resume-job'">先从已有材料开始</strong>
          <strong v-else-if="renderedStage === 'requirement'">先把岗位要求拆成可以核对的事项</strong>
          <strong v-else-if="renderedStage === 'evidence'">要求和简历原文放在一起核对</strong>
          <strong v-else-if="renderedStage === 'gap'">有证据就用，没有证据就停下来</strong>
          <strong v-else-if="renderedStage === 'rewrite'">AI 提建议，最终决定仍然由你做</strong>
          <strong v-else>确认后，直接得到可投递的 PDF</strong>
        </div>

        <div class="demo-product-object" :class="`is-${renderedStage}`">
          <article class="demo-resume-paper" aria-label="林然的岗位定向简历">
            <div class="demo-paper-topline">
              <span>岗位定向版本</span>
              <span>林然 · 3 年工作经验</span>
            </div>
            <h3>林然</h3>
            <p class="demo-paper-role">项目推进 · 数据分析 · 跨团队协作</p>
            <div class="demo-paper-section">
              <span>工作经历</span>
              <i /><i />
              <p
                v-if="renderedStage === 'preview'"
                class="is-highlighted"
              >
                负责项目关键数据整理与分析，根据结果推动后续方案调整。
              </p>
              <p v-else>
                负责核心项目的规划、协作与交付，推动多个关键节点按期落地。
              </p>
              <i /><i />
            </div>
            <div class="demo-paper-section">
              <span>项目经历</span>
              <i /><i /><i />
            </div>
            <span
              v-if="renderedStage === 'requirement' || renderedStage === 'evidence' || renderedStage === 'gap' || renderedStage === 'rewrite'"
              class="demo-paper-marker"
              aria-hidden="true"
            />
          </article>

          <Transition name="demo-stage" mode="out-in">
            <aside v-if="renderedStage === 'resume-job'" key="job" class="demo-context demo-job-context">
              <span class="demo-context-kicker">目标岗位</span>
              <strong>岗位定向上下文</strong>
              <p>项目经验 · 独立推进 · 分析与协作</p>
              <span class="demo-context-line">附着到当前简历</span>
            </aside>

            <aside v-else-if="renderedStage === 'requirement'" key="requirement" class="demo-context demo-requirement-context">
              <span class="demo-context-kicker">岗位要求</span>
              <div class="demo-context-list">
                <div><span>01</span><strong>有相关项目经验</strong></div>
                <div class="is-selected"><span>02</span><strong>能独立推进复杂任务</strong></div>
                <div><span>03</span><strong>具备数据分析能力</strong></div>
                <div><span>04</span><strong>有良好的跨团队协作能力</strong></div>
              </div>
            </aside>

            <aside v-else-if="renderedStage === 'evidence'" key="evidence" class="demo-context demo-evidence-context">
              <span class="demo-context-kicker">证据核对</span>
              <strong>能独立推进复杂任务</strong>
              <p>“负责核心项目的规划、协作与交付，推动多个关键节点按期落地。”</p>
              <span class="demo-evidence-underline" aria-hidden="true" />
            </aside>

            <aside v-else-if="renderedStage === 'gap'" key="gap" class="demo-context demo-gap-context">
              <span class="demo-context-kicker">当前材料的差距</span>
              <strong>具备数据分析能力</strong>
              <p>部分支持 · 简历未说明具体做法和结果</p>
              <div class="demo-gap-callout">
                <span>当前材料未体现</span>
                <strong>问题解决能力</strong>
                <small>不代表你没有这项能力</small>
              </div>
            </aside>

            <aside v-else-if="renderedStage === 'rewrite'" key="rewrite" class="demo-context demo-suggestion-context">
              <span class="demo-context-kicker">修改建议</span>
              <div class="demo-suggestion-copy">
                <span>建议版本</span>
                <p>负责项目关键数据整理与分析，根据结果推动后续方案调整</p>
              </div>
              <div class="demo-confirm-row">
                <small>基于已有事实，让作用表达得更清楚。</small>
                <el-button
                  v-if="!rewriteApplied"
                  size="small"
                  type="primary"
                  @click="rewriteApplied = true"
                >
                  采纳建议
                </el-button>
                <span v-else class="demo-confirmed">已进入编辑草稿</span>
              </div>
            </aside>

            <aside v-else key="preview" class="demo-context demo-preview-context">
              <span class="demo-context-kicker">确认与导出</span>
              <div class="demo-confirmed-copy">
                <span>已确认的修改</span>
                <strong>负责项目关键数据整理与分析，根据结果推动后续方案调整。</strong>
              </div>
              <p>页面结构与联系方式检查通过。</p>
              <span class="demo-export-line">✓ 可以导出 PDF</span>
            </aside>
          </Transition>
        </div>

        <p v-if="renderedStage === 'resume-job'" class="demo-scene-note">不是先猜一个分数，而是先看清这个岗位真正需要什么。</p>
        <p v-else-if="renderedStage === 'requirement'" class="demo-scene-note">先确定要求，再判断材料。</p>
        <p v-else-if="renderedStage === 'evidence'" class="demo-scene-note">证据只来自当前材料，相关表达都能直接回看。</p>
        <p v-else-if="renderedStage === 'gap'" class="demo-scene-note">缺口只描述当前材料，不替用户判断现实能力，也不会自动写入简历。</p>
        <p v-else-if="renderedStage === 'rewrite'" class="demo-scene-note">每一处修改都先由你确认，再进入编辑草稿。</p>
        <p v-else class="demo-scene-note">保存后才能导出，最终版本始终由你确认。</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.product-flow-demo {
  display: grid;
  gap: 24px;
  min-height: 560px;
  padding: 32px 0;
  border-top: 1px solid var(--app-border-strong);
  border-bottom: 1px solid var(--app-border-strong);
}

.product-flow-heading {
  display: grid;
  gap: 8px;
}

.product-flow-heading-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--app-space-4);
}

.product-flow-label {
  flex: 0 0 auto;
  color: var(--app-primary);
  font-family: var(--app-font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.product-flow-heading h2 {
  margin: 0;
  color: var(--app-text);
  font-size: clamp(20px, 2vw, 28px);
  line-height: 1.25;
  letter-spacing: -0.02em;
}

.product-flow-heading p {
  max-width: 48ch;
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.product-flow-list {
  display: grid;
  align-content: center;
  gap: 0;
  max-width: 500px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.product-flow-stage {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 48px;
}

.product-flow-node,
.product-flow-story-node {
  z-index: 1;
  width: 12px;
  height: 12px;
  flex: 0 0 12px;
  border: 2px solid var(--app-primary);
  border-radius: 50%;
  background: var(--app-bg);
}

.product-flow-node.is-source,
.product-flow-node.is-evidence,
.product-flow-node.is-preview {
  border-color: var(--app-accent);
  background: var(--app-accent);
}

.product-flow-node.is-target,
.product-flow-node.is-requirement,
.product-flow-node.is-gap,
.product-flow-node.is-apply {
  background: var(--app-primary-soft);
}

.product-flow-copy {
  display: grid;
  gap: 2px;
}

.product-flow-copy strong {
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.35;
}

.product-flow-copy span {
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.product-flow-arrow {
  position: absolute;
  top: 34px;
  left: 5px;
  width: 2px;
  height: 14px;
  background: var(--app-border-strong);
}

.product-flow-story-rail {
  display: flex;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.product-flow-story-rail li {
  position: relative;
  display: grid;
  flex: 1;
  gap: 8px;
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  line-height: 1.4;
}

.product-flow-story-rail li::after {
  position: absolute;
  top: 5px;
  right: 0;
  left: 12px;
  height: 1px;
  background: var(--app-border);
  content: '';
}

.product-flow-story-rail li:last-child::after {
  display: none;
}

.product-flow-story-rail li.is-active {
  color: var(--app-primary-active);
}

.product-flow-story-rail li.is-active .product-flow-story-node {
  border-color: var(--app-primary);
  background: var(--app-primary);
}

.product-flow-story-node {
  position: relative;
  width: 11px;
  height: 11px;
  border-width: 1px;
  background: var(--app-surface);
}

.product-flow-demo.is-scene-requirement .product-flow-story-rail li:not(.is-active) > span:last-child,
.product-flow-demo.is-scene-evidence .product-flow-story-rail li:not(.is-active) > span:last-child,
.product-flow-demo.is-scene-gap .product-flow-story-rail li:not(.is-active) > span:last-child,
.product-flow-demo.is-scene-preview .product-flow-story-rail li:not(.is-active) > span:last-child {
  width: 0;
  overflow: hidden;
  color: transparent;
  white-space: nowrap;
}

.product-flow-demo.is-scene-requirement .product-flow-story-rail,
.product-flow-demo.is-scene-evidence .product-flow-story-rail,
.product-flow-demo.is-scene-gap .product-flow-story-rail,
.product-flow-demo.is-scene-preview .product-flow-story-rail {
  gap: 4px;
}

.product-flow-demo.is-scene-requirement .product-flow-story-rail li,
.product-flow-demo.is-scene-evidence .product-flow-story-rail li,
.product-flow-demo.is-scene-gap .product-flow-story-rail li,
.product-flow-demo.is-scene-preview .product-flow-story-rail li {
  gap: 4px;
}

.demo-scene {
  display: grid;
  align-content: center;
  gap: 20px;
  min-height: 330px;
}

.demo-product-object {
  --paper-width: 74%;
  --paper-min-height: 390px;
  --paper-offset-x: 0px;
  --paper-offset-y: 0px;
  --context-width: 38%;
  --context-offset-x: 0px;
  --context-offset-y: 58px;
  position: relative;
  min-height: clamp(420px, 52vh, 540px);
}

.demo-resume-paper {
  position: relative;
  z-index: 2;
  display: grid;
  align-content: start;
  gap: 10px;
  width: var(--paper-width);
  min-height: var(--paper-min-height);
  box-sizing: border-box;
  padding: 24px 28px;
  border: 1px solid var(--app-border-strong);
  border-radius: 2px;
  background: var(--app-document);
  box-shadow: var(--app-shadow-page);
  left: var(--paper-offset-x);
  top: var(--paper-offset-y);
  transition: opacity 320ms ease, transform 420ms cubic-bezier(0.22, 1, 0.36, 1);
}

.demo-paper-topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--app-text-muted);
  font-size: 10px;
}

.demo-resume-paper h3 {
  margin: 8px 0 0;
  color: var(--app-text);
  font-size: 28px;
  letter-spacing: -0.03em;
}

.demo-paper-role,
.demo-paper-section p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.demo-paper-section {
  display: grid;
  gap: 7px;
  margin-top: 12px;
}

.demo-paper-section > span {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.demo-paper-section i {
  display: block;
  width: 88%;
  height: 4px;
  background: var(--app-border);
}

.demo-paper-section i:nth-of-type(2) {
  width: 70%;
}

.demo-paper-section i:nth-of-type(3) {
  width: 78%;
}

.demo-paper-section p {
  max-width: 38ch;
  color: var(--app-text);
}

.demo-paper-section p.is-highlighted {
  text-decoration: underline;
  text-decoration-color: var(--app-primary);
  text-decoration-thickness: 3px;
  text-underline-offset: 4px;
}

.demo-paper-marker {
  position: absolute;
  top: 104px;
  right: -1px;
  width: 26px;
  height: 12px;
  border-top: 1px solid var(--app-primary);
  border-bottom: 1px solid var(--app-primary);
  background: var(--app-primary-soft);
}

.demo-context {
  position: absolute;
  z-index: 3;
  top: var(--context-offset-y);
  right: var(--context-offset-x);
  display: grid;
  gap: 9px;
  width: var(--context-width);
  box-sizing: border-box;
  padding: 14px 0 16px 20px;
  border-left: 1px solid var(--app-primary);
  transition: opacity 320ms ease, transform 420ms cubic-bezier(0.22, 1, 0.36, 1);
}

.demo-context-kicker {
  color: var(--app-primary-active);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.demo-context strong {
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.45;
}

.demo-context p,
.demo-context small,
.demo-context-line {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.65;
}

.demo-context-line,
.demo-export-line {
  padding-top: 9px;
  border-top: 1px solid var(--app-border);
  color: var(--app-text-muted);
  font-size: 11px;
}

.demo-product-object.is-resume-job {
  --paper-width: 80%;
  --paper-min-height: 420px;
  --paper-offset-y: 18px;
  --context-width: 38%;
  --context-offset-y: 48px;
}

.demo-product-object.is-requirement {
  --paper-width: 74%;
  --paper-min-height: 400px;
  --paper-offset-y: 28px;
  --context-width: 45%;
  --context-offset-x: clamp(-34px, -2.4vw, -20px);
  --context-offset-y: 18px;
}

.demo-product-object.is-evidence {
  --paper-width: 84%;
  --paper-min-height: 450px;
  --paper-offset-x: clamp(10px, 1.4vw, 24px);
  --context-width: 36%;
  --context-offset-x: clamp(-58px, -4vw, -32px);
  --context-offset-y: 90px;
}

.demo-product-object.is-gap {
  --paper-width: 74%;
  --paper-min-height: 410px;
  --paper-offset-y: 42px;
  --context-width: 42%;
  --context-offset-x: clamp(-30px, -2vw, -16px);
  --context-offset-y: 42px;
}

.demo-product-object.is-rewrite {
  --paper-width: 68%;
  --paper-min-height: 420px;
  --context-width: 47%;
  --context-offset-x: clamp(-20px, -1.4vw, -10px);
  --context-offset-y: 62px;
}

.demo-product-object.is-preview {
  --paper-width: 88%;
  --paper-min-height: 470px;
  --paper-offset-x: clamp(14px, 2vw, 34px);
  --context-width: 31%;
  --context-offset-x: clamp(14px, 2vw, 28px);
  --context-offset-y: 138px;
}

.demo-product-object.is-preview .demo-resume-paper,
.demo-product-object.is-preview .demo-context {
  transition-duration: 560ms;
}

.demo-context-list {
  display: grid;
}

.demo-context-list > div {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
  padding: 8px 0;
  border-bottom: 1px solid var(--app-border);
}

.demo-context-list > div > span {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
}

.demo-context-list > div > strong {
  font-size: 12px;
}

.demo-context-list > div.is-selected {
  position: relative;
  color: var(--app-primary-active);
}

.demo-context-list > div.is-selected::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: -21px;
  width: 3px;
  background: var(--app-primary);
  content: '';
}

.demo-evidence-context p {
  color: var(--app-text);
}

.demo-evidence-underline {
  display: block;
  width: 76%;
  height: 3px;
  background: var(--app-primary);
}

.demo-gap-context > strong {
  color: var(--app-primary-active);
}

.demo-gap-callout {
  display: grid;
  gap: 4px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border);
}

.demo-gap-callout span,
.demo-gap-callout small {
  color: var(--app-text-muted);
  font-size: 11px;
}

.demo-gap-callout strong {
  font-size: 13px;
}

.demo-suggestion-copy {
  display: grid;
  gap: 6px;
  padding: 11px 0;
  border-top: 1px solid var(--app-primary-subtle);
  border-bottom: 1px solid var(--app-border);
}

.demo-suggestion-copy > span {
  color: var(--app-primary-active);
  font-size: 11px;
  font-weight: 700;
}

.demo-suggestion-copy p {
  color: var(--app-text);
}

.demo-confirmed-copy {
  display: grid;
  gap: 6px;
  padding: 11px 0;
  border-top: 1px solid var(--app-primary-subtle);
  border-bottom: 1px solid var(--app-border);
}

.demo-confirmed-copy > span {
  color: var(--app-accent-hover);
  font-size: 11px;
  font-weight: 700;
}

.demo-confirmed-copy > strong {
  font-size: 13px;
}

.demo-preview-context > strong {
  color: var(--app-accent-hover);
}

.demo-scene-heading {
  display: grid;
  gap: 6px;
}

.demo-scene-heading span,
.demo-surface-label {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.demo-scene-heading strong {
  color: var(--app-text);
  font-size: 20px;
  line-height: 1.35;
}

.demo-split-row,
.demo-preview-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--app-space-3);
}

.demo-surface,
.demo-requirement-row,
.demo-evidence-row,
.demo-gap-row,
.demo-diff-row,
.demo-preview-inspector {
  border: 1px solid var(--app-border);
  background: var(--app-surface);
}

.demo-surface {
  display: grid;
  gap: 7px;
  min-height: 150px;
  padding: 16px;
}

.demo-surface.is-target {
  border-color: var(--app-primary-subtle);
  background: var(--app-primary-soft);
}

.demo-surface strong,
.demo-gap-row strong,
.demo-requirement-row strong {
  color: var(--app-text);
  font-size: 15px;
}

.demo-surface small,
.demo-surface p,
.demo-scene-note,
.demo-gap-status,
.demo-preview-inspector p,
.demo-confirm-row small {
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.demo-surface p {
  margin: 0;
}

.demo-document-lines {
  display: grid;
  gap: 5px;
  margin-top: auto;
}

.demo-document-lines i,
.demo-preview-paper i {
  display: block;
  height: 4px;
  background: var(--app-border);
}

.demo-document-lines i:nth-child(1) {
  width: 88%;
}

.demo-document-lines i:nth-child(2) {
  width: 72%;
}

.demo-document-lines i:nth-child(3) {
  width: 80%;
}

.demo-scene-note {
  margin: 0;
}

.demo-requirement-list {
  display: grid;
  gap: 8px;
}

.demo-requirement-row,
.demo-evidence-row {
  display: grid;
  gap: 8px;
  padding: 15px 16px;
}

.demo-requirement-list .demo-requirement-row {
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
}

.demo-requirement-row.is-selected {
  border-color: var(--app-primary-subtle);
  background: var(--app-primary-soft);
}

.demo-requirement-row span,
.demo-evidence-row > span,
.demo-diff-row > span {
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 700;
}

.demo-requirement-row strong {
  font-weight: 700;
}

.demo-evidence-row {
  border-color: var(--app-primary-subtle);
  background: var(--app-primary-soft);
}

.demo-evidence-row p {
  margin: 0;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.7;
}

.demo-evidence-row mark {
  padding: 1px 3px;
  color: var(--app-text);
  background: #e2c1b5;
}

.demo-gap-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 16px;
}

.demo-gap-row > div {
  display: grid;
  gap: 6px;
}

.demo-gap-row.is-empty {
  background: var(--app-surface-soft);
}

.demo-gap-status {
  max-width: 26ch;
  text-align: right;
}

.demo-diff {
  display: grid;
  gap: 8px;
}

.demo-diff-row {
  display: grid;
  gap: 6px;
  padding: 13px 16px;
}

.demo-diff-row p {
  margin: 0;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.6;
}

.demo-diff-row.is-original {
  background: var(--app-surface-soft);
}

.demo-diff-row.is-suggestion,
.demo-diff-row.is-applied {
  border-color: var(--app-primary-subtle);
  background: var(--app-primary-soft);
}

.demo-diff-row.is-suggestion > span {
  color: var(--app-primary-active);
}

.demo-diff-row.is-applied > span,
.demo-confirmed {
  color: var(--app-accent);
  font-weight: 700;
}

.demo-confirm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.demo-confirm-row small {
  max-width: 34ch;
}

.demo-confirmed-edit {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 16px;
  border: 1px solid var(--app-primary-subtle);
  background: var(--app-primary-soft);
}

.demo-confirmed-edit > div {
  display: grid;
  gap: 6px;
}

.demo-confirmed-edit p {
  margin: 0;
  color: var(--app-text);
  font-size: 13px;
  line-height: 1.6;
}

.demo-preview-paper {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 190px;
  padding: 18px;
  border: 1px solid var(--app-border-strong);
  background: var(--app-document);
  box-shadow: var(--app-shadow-page);
}

.demo-preview-paper span {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.demo-preview-paper strong {
  font-size: 19px;
}

.demo-preview-paper i {
  width: 86%;
  margin-top: 7px;
}

.demo-preview-paper i:nth-of-type(2) {
  width: 70%;
}

.demo-preview-paper i:nth-of-type(3) {
  width: 78%;
}

.demo-preview-paper i:nth-of-type(4) {
  width: 62%;
}

.demo-preview-paper small {
  margin-top: auto;
  color: var(--app-text-muted);
  font-size: 11px;
}

.demo-preview-inspector {
  display: grid;
  align-content: start;
  gap: 9px;
  min-height: 190px;
  padding: 18px;
}

.demo-preview-inspector strong {
  color: var(--app-accent-hover);
  font-size: 16px;
}

.demo-preview-inspector p {
  margin: 0;
}

.demo-export-line {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--app-border-soft);
  color: var(--app-primary-active);
  font-size: 13px;
  font-weight: 700;
}

.demo-stage-enter-active,
.demo-stage-leave-active,
.demo-copy-enter-active,
.demo-copy-leave-active {
  transition: opacity 280ms ease, transform 280ms ease;
}

.demo-stage-enter-from,
.demo-stage-leave-to,
.demo-copy-enter-from,
.demo-copy-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.product-flow-demo.is-compact {
  min-height: 0;
  gap: 24px;
  padding: 0;
  border: 0;
}

.product-flow-demo.is-compact .product-flow-heading h2 {
  font-size: 20px;
}

.product-flow-demo.is-compact .product-flow-list {
  max-width: none;
}

.product-flow-demo.is-story-compact {
  display: block;
  min-height: 0;
}

.product-flow-demo.is-story-compact .product-flow-heading {
  display: none;
}

.product-flow-demo.is-story-compact .demo-scene {
  min-height: 0;
  gap: 14px;
}

.product-flow-demo.is-story-compact .demo-scene-heading strong {
  font-size: 16px;
}

/* Landing variant: one small document object, with only the current state changing. */
.product-flow-demo.is-landing-demo {
  display: grid;
  gap: 18px;
  min-height: 0;
  padding: 0;
  border: 0;
}

.landing-demo-canvas {
  position: relative;
  min-height: 520px;
  padding: 22px 0 28px;
}

.landing-demo-paper {
  position: relative;
  z-index: 1;
  display: grid;
  align-content: start;
  gap: 16px;
  width: min(74%, 620px);
  min-height: 470px;
  margin-left: 42px;
  padding: 28px 34px 30px;
  box-sizing: border-box;
  border: 1px solid var(--app-border-strong);
  background: var(--app-document);
  box-shadow: var(--app-shadow-page);
  transition: transform 440ms cubic-bezier(0.22, 1, 0.36, 1), opacity 320ms ease;
}

.landing-demo-paper.is-requirement {
  opacity: 0.94;
}

.landing-demo-paper.is-evidence {
  transform: scale(1.04);
  transform-origin: left center;
}

.landing-demo-paper.is-gap {
  transform: scale(1.02);
  transform-origin: left center;
}

.landing-demo-paper.is-confirm-export {
  transform: scale(1.05);
  transform-origin: left center;
}

.landing-demo-paper-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}

.landing-demo-paper-header > div:first-child {
  display: grid;
  gap: 7px;
}

.landing-demo-paper-header > div:first-child > span,
.landing-demo-paper-contact,
.landing-demo-paper-section header span,
.landing-demo-paper-skills > span {
  color: var(--app-text-muted);
  font-size: 10px;
}

.landing-demo-paper-header > div:first-child > span {
  color: var(--app-primary-active);
  font-family: var(--app-font-mono);
  font-weight: 700;
  letter-spacing: 0.05em;
}

.landing-demo-paper-header h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 31px;
  line-height: 1;
  letter-spacing: -0.045em;
}

.landing-demo-paper-header p,
.landing-demo-entry-heading span,
.landing-demo-evidence-block > p,
.landing-demo-paper-skills p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.landing-demo-paper-contact {
  display: grid;
  align-content: end;
  justify-items: end;
  gap: 3px;
  text-align: right;
}

.landing-demo-paper-rule {
  height: 1px;
  background: var(--app-border-strong);
}

.landing-demo-paper-section {
  display: grid;
  gap: 10px;
}

.landing-demo-paper-section header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 7px;
  border-bottom: 1px solid var(--app-border);
}

.landing-demo-paper-section header strong,
.landing-demo-entry-heading > strong {
  color: var(--app-text);
  font-size: 13px;
}

.landing-demo-entry {
  display: grid;
  gap: 9px;
}

.landing-demo-entry-heading {
  display: grid;
  gap: 2px;
}

.landing-demo-entry p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.landing-demo-entry p.is-evidence,
.landing-demo-entry p.is-evidence mark {
  color: var(--app-text);
}

.landing-demo-entry mark {
  padding: 1px 2px;
  background: var(--app-primary-soft);
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
  animation: landing-demo-highlight 360ms ease both;
}

@keyframes landing-demo-highlight {
  from { clip-path: inset(0 100% 0 0); }
  to { clip-path: inset(0 0 0 0); }
}

.landing-demo-evidence-block > p {
  max-width: 42ch;
}

.landing-demo-paper-skills {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding-top: 2px;
}

.landing-demo-paper-skills p {
  color: var(--app-text);
}

.landing-demo-annotation-label {
  color: var(--app-primary-active);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.landing-demo-annotation > strong {
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.4;
}

.landing-demo-annotation p,
.landing-demo-annotation blockquote {
  margin: 0;
}

.landing-demo-annotation blockquote {
  color: var(--app-text);
  font-size: 13px;
}

.landing-demo-annotation-source,
.landing-demo-job > span:not(.landing-demo-annotation-label),
.landing-demo-confirm p,
.landing-demo-gap small,
.landing-demo-gap > div > span {
  color: var(--app-text-muted);
}

.landing-demo-requirement ol {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.landing-demo-requirement li {
  padding: 8px 0;
  border-bottom: 1px solid color-mix(in srgb, var(--app-border) 68%, transparent);
}

.landing-demo-requirement li.is-selected {
  color: var(--app-text);
  font-weight: 700;
}

.landing-demo-requirement li.is-selected::before {
  margin-right: 7px;
  color: var(--app-primary);
  content: '●';
  font-size: 8px;
  vertical-align: 2px;
}

.landing-demo-gap > strong {
  color: var(--app-primary-active);
}

.landing-demo-gap > div {
  display: grid;
  gap: 2px;
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px solid color-mix(in srgb, var(--app-border) 68%, transparent);
}

.landing-demo-gap > div > strong {
  color: var(--app-text);
  font-size: 13px;
}

.landing-demo-diff {
  display: grid;
  gap: 6px;
  margin-top: 3px;
}

.landing-demo-diff > div {
  display: grid;
  gap: 3px;
  padding: 7px 9px;
  border: 1px solid var(--app-border);
  background: var(--app-surface-soft);
}

.landing-demo-diff > div.is-suggested {
  border-color: var(--app-primary-subtle);
  background: var(--app-primary-soft);
}

.landing-demo-diff > div.is-empty {
  opacity: 0.78;
}

.landing-demo-diff span {
  color: var(--app-text-muted);
  font-family: var(--app-font-mono);
  font-size: 9px;
}

.landing-demo-diff p,
.landing-demo-diff small {
  margin: 0;
  color: var(--app-text);
  font-size: 11px;
  line-height: 1.5;
}

.landing-demo-diff small {
  color: var(--app-text-muted);
  font-size: 9px;
}

.landing-demo-confirm > strong,
.landing-demo-confirm > span:last-child {
  color: var(--app-accent-hover);
}

.landing-demo-confirm > span:last-child {
  padding-top: 10px;
  border-top: 1px solid color-mix(in srgb, var(--app-border) 68%, transparent);
  font-size: 11px;
  font-weight: 700;
}

.landing-demo-copy-enter-active,
.landing-demo-copy-leave-active {
  transition: opacity 320ms ease, transform 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

.landing-demo-copy-enter-from,
.landing-demo-copy-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

.landing-demo-annotation-enter-active,
.landing-demo-annotation-leave-active {
  transition: opacity 320ms ease, transform 360ms cubic-bezier(0.22, 1, 0.36, 1);
}

.landing-demo-annotation-enter-from,
.landing-demo-annotation-leave-to {
  opacity: 0;
  transform: translateX(14px);
}

/* Landing review workspace: the resume and its margin are one document surface. */
.landing-review-workspace {
  display: grid;
  grid-template-columns: minmax(0, 68%) minmax(0, 32%);
  min-height: 500px;
  overflow: hidden;
  border: 1px solid var(--app-border-strong);
  background: var(--app-document);
  box-shadow: var(--app-shadow-page);
}

.landing-review-resume {
  position: relative;
  display: grid;
  align-content: start;
  gap: 16px;
  min-width: 0;
  padding: 28px 32px 30px;
  background: var(--app-document);
}

.landing-review-margin {
  min-width: 0;
  overflow: hidden;
  border-left: 1px solid var(--app-border);
  background: color-mix(in srgb, var(--app-document) 96%, var(--app-bg-soft));
}

.landing-review-content {
  display: grid;
  align-content: start;
  gap: 8px;
  height: 100%;
  box-sizing: border-box;
  padding: 28px 22px;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.65;
}

.landing-review-content > strong {
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.45;
}

.landing-review-content > p,
.landing-review-content blockquote { margin: 0; }
.landing-review-content blockquote { color: var(--app-text); font-size: 13px; }
.landing-demo-annotation-label { color: var(--app-primary-active); font-family: var(--app-font-mono); font-size: 10px; font-weight: 700; letter-spacing: 0.05em; }
.landing-demo-annotation-source,
.landing-demo-job > span:not(.landing-demo-annotation-label),
.landing-demo-confirm p,
.landing-demo-gap small { color: var(--app-text-muted); }

.landing-demo-requirement ol { display: grid; gap: 0; margin: 4px 0 0; padding: 0; list-style: none; }
.landing-demo-requirement li { padding: 8px 8px; border-bottom: 1px solid var(--app-border); }
.landing-demo-requirement li.is-selected { color: var(--app-text); font-weight: 700; background: var(--app-primary-soft); }
.landing-demo-requirement li.is-selected::before { margin-right: 7px; color: var(--app-primary); content: '●'; font-size: 8px; vertical-align: 2px; }

.landing-demo-gap > strong { color: var(--app-primary-active); }
.landing-demo-diff { display: grid; gap: 0; margin-top: 3px; }
.landing-demo-diff > div { display: grid; gap: 3px; padding: 9px 0; border: 0; border-top: 1px solid var(--app-border); background: transparent; }
.landing-demo-diff > div.is-suggested { padding-right: 8px; padding-left: 8px; border-top-color: var(--app-primary-subtle); background: var(--app-primary-soft); }
.landing-demo-diff > div.is-empty { padding-right: 8px; padding-left: 8px; background: var(--app-surface-soft); }
.landing-demo-diff span { color: var(--app-text-muted); font-family: var(--app-font-mono); font-size: 9px; }
.landing-demo-diff p,
.landing-demo-diff small { margin: 0; color: var(--app-text); font-size: 11px; line-height: 1.5; }
.landing-demo-diff small { color: var(--app-text-muted); font-size: 9px; }

.landing-demo-confirm > strong,
.landing-demo-confirm > span:last-child { color: var(--app-accent-hover); }
.landing-demo-confirm > span:last-child { margin-top: 3px; padding-top: 10px; border-top: 1px solid var(--app-border); font-size: 11px; font-weight: 700; }
.landing-review-resume.is-evidence .landing-demo-entry { position: relative; }
.landing-review-resume.is-evidence .landing-demo-entry::after { position: absolute; top: 31px; right: -32px; width: 32px; height: 1px; background: var(--app-primary-subtle); content: ''; }

@media (max-width: 900px) {
  .product-flow-demo.is-landing-demo { gap: 16px; }
  .landing-review-workspace { display: block; min-height: 0; max-height: 390px; }
  .landing-review-resume { max-height: 230px; overflow: hidden; padding: 22px 20px 24px; }
  .landing-review-margin { max-height: 155px; overflow: auto; border-top: 1px solid var(--app-border); border-left: 0; }
  .landing-review-content { height: auto; padding: 16px 18px 18px; }
  .landing-review-resume.is-evidence .landing-demo-entry::after { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .demo-resume-paper,
  .demo-context,
  .landing-demo-paper {
    transition: none;
  }

  .landing-demo-paper mark {
    animation: none;
  }

  .landing-demo-copy-enter-active,
  .landing-demo-copy-leave-active,
  .landing-demo-annotation-enter-active,
  .landing-demo-annotation-leave-active {
    transition: none;
  }

  .demo-stage-enter-active,
  .demo-stage-leave-active,
  .demo-copy-enter-active,
  .demo-copy-leave-active {
    transition: opacity 80ms linear;
  }

  .demo-stage-enter-from,
  .demo-stage-leave-to,
  .demo-copy-enter-from,
  .demo-copy-leave-to {
    transform: none;
  }
}

@media (max-width: 640px) {
  .product-flow-demo {
    min-height: 0;
    gap: 24px;
    padding: 24px 0;
  }

  .demo-product-object {
    display: grid;
    gap: 20px;
    min-height: 0;
  }

  .demo-resume-paper {
    width: 100% !important;
    min-height: 300px;
    padding: 22px;
    left: 0;
    top: 0;
  }

  .demo-context {
    position: relative;
    top: auto !important;
    right: auto;
    width: auto !important;
    padding: 16px 0 0;
    border-top: 1px solid var(--app-primary);
    border-left: 0;
  }

  .demo-context-list > div.is-selected::before {
    top: 0;
    bottom: 0;
    left: 0;
    width: 3px;
  }

  .demo-context-list > div.is-selected {
    padding-left: 10px;
  }

  .product-flow-stage {
    min-height: 44px;
  }

  .product-flow-heading-line {
    align-items: flex-start;
    flex-direction: column-reverse;
    gap: 6px;
  }

  .product-flow-story-rail {
    display: none;
  }

  .demo-split-row,
  .demo-preview-row {
    grid-template-columns: 1fr;
  }

  .demo-gap-row,
  .demo-confirm-row,
  .demo-confirmed-edit {
    align-items: flex-start;
    flex-direction: column;
  }

  .demo-gap-status {
    max-width: none;
    text-align: left;
  }

  .demo-confirm-row .el-button {
    width: 100%;
  }
}
</style>
