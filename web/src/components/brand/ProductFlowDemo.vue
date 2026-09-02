<script setup lang="ts">
import { ref, watch } from 'vue'

export type ProductFlowStageId = 'resume-job' | 'evidence' | 'gap' | 'rewrite' | 'preview'

const props = withDefaults(
  defineProps<{
    compact?: boolean
    activeStage?: ProductFlowStageId
  }>(),
  {
    compact: false,
    activeStage: undefined,
  },
)

const compactStages = [
  { label: '我的简历', value: '已确认的经历材料', state: 'source' },
  { label: '目标岗位', value: '粘贴一份真实 JD', state: 'target' },
  { label: '岗位要求', value: '逐条拆解要求', state: 'requirement' },
  { label: '当前证据', value: '查看简历中的相关表达', state: 'evidence' },
  { label: '建议完善', value: '在事实边界内调整表达', state: 'gap' },
  { label: '用户确认', value: '采纳或继续手动编辑', state: 'apply' },
  { label: '预览 PDF', value: '确认后直接导出', state: 'preview' },
]

const storyStages: { id: ProductFlowStageId; label: string }[] = [
  { id: 'resume-job', label: 'Resume × Target Job' },
  { id: 'evidence', label: 'Requirement → Evidence' },
  { id: 'gap', label: 'Gap' },
  { id: 'rewrite', label: 'Suggested Edit / Confirm' },
  { id: 'preview', label: 'Preview / Export' },
]

const rewriteApplied = ref(false)

watch(
  () => props.activeStage,
  () => {
    rewriteApplied.value = false
  },
)
</script>

<template>
  <div
    class="product-flow-demo"
    :class="{
      'is-compact': compact,
      'is-story-compact': compact && activeStage,
    }"
  >
    <template v-if="compact && !activeStage">
      <div class="product-flow-heading">
        <div class="product-flow-heading-line">
          <h2>一段真实的岗位定向过程</h2>
          <span class="product-flow-label">PRODUCT DEMO</span>
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
          <span v-if="!compact" class="product-flow-label">PRODUCT DEMO</span>
        </div>
        <p v-if="!compact">同一份简历，经过要求、证据、编辑，直到可以交付。</p>
      </div>

      <ol v-if="!compact" class="product-flow-story-rail" aria-label="岗位定向阶段">
        <li
          v-for="stage in storyStages"
          :key="stage.id"
          :class="{ 'is-active': activeStage === stage.id }"
        >
          <span class="product-flow-story-node" aria-hidden="true" />
          <span>{{ stage.label }}</span>
        </li>
      </ol>

      <Transition name="demo-stage" mode="out-in">
        <section :key="activeStage" class="demo-scene" :aria-label="activeStage || '岗位定向阶段'">
          <template v-if="activeStage === 'resume-job'">
            <div class="demo-scene-heading">
              <span>RESUME</span>
              <strong>先把已有材料放在手边</strong>
            </div>
            <div class="demo-split-row">
              <div class="demo-surface">
                <span class="demo-surface-label">我的简历</span>
                <strong>Java 后端简历</strong>
                <small>已确认材料 · PDF</small>
                <div class="demo-document-lines" aria-hidden="true">
                  <i /><i /><i />
                </div>
              </div>
              <div class="demo-surface is-target">
                <span class="demo-surface-label">目标岗位</span>
                <strong>Java 后端工程师</strong>
                <small>一份真实的岗位描述</small>
                <p>Spring Boot · 服务稳定性 · 数据库</p>
              </div>
            </div>
            <p class="demo-scene-note">不是先猜一个分数，而是先看清这个岗位真正需要什么。</p>
          </template>

          <template v-else-if="activeStage === 'evidence'">
            <div class="demo-scene-heading">
              <span>REQUIREMENT → EVIDENCE</span>
              <strong>要求和简历原文放在一起核对</strong>
            </div>
            <div class="demo-requirement-row">
              <span>岗位要求</span>
              <strong>使用 Spring Boot 完成后端接口开发</strong>
            </div>
            <div class="demo-evidence-row">
              <span>工作经历 · 当前材料</span>
              <p>基于 <mark>Spring Boot</mark> 完成核心业务接口开发。</p>
            </div>
            <p class="demo-scene-note">引用来自当前冻结的简历材料，证据在哪里可以直接看见。</p>
          </template>

          <template v-else-if="activeStage === 'gap'">
            <div class="demo-scene-heading">
              <span>GAP</span>
              <strong>有证据就用，没有证据就停下来</strong>
            </div>
            <div class="demo-gap-row">
              <div>
                <span class="demo-surface-label">建议完善</span>
                <strong>具备缓存设计经验</strong>
              </div>
              <span class="demo-gap-status">当前材料有相关证据，但不足以完整支持</span>
            </div>
            <div class="demo-gap-row is-empty">
              <div>
                <span class="demo-surface-label">当前材料未体现</span>
                <strong>消息队列经验</strong>
              </div>
              <span class="demo-gap-status">不代表你没有这项能力</span>
            </div>
            <p class="demo-scene-note">缺口只描述当前材料，不替用户判断现实能力，也不会自动写入简历。</p>
          </template>

          <template v-else-if="activeStage === 'rewrite'">
            <div class="demo-scene-heading">
              <span>SUGGESTED EDIT / CONFIRM</span>
              <strong>AI 提建议，最终决定仍然由你做</strong>
            </div>
            <div class="demo-diff">
              <div class="demo-diff-row is-original">
                <span>原文</span>
                <p>负责后台接口开发</p>
              </div>
              <Transition name="demo-copy" mode="out-in">
                <div v-if="!rewriteApplied" key="suggestion" class="demo-diff-row is-suggestion">
                  <span>建议版本</span>
                  <p>基于 Spring Boot 完成核心业务接口开发</p>
                </div>
                <div v-else key="applied" class="demo-diff-row is-applied">
                  <span>已采纳</span>
                  <p>基于 Spring Boot 完成核心业务接口开发</p>
                </div>
              </Transition>
            </div>
            <div class="demo-confirm-row">
              <small>为什么这样改：让已有技术表达更具体，不新增经历。</small>
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
          </template>

          <template v-else-if="activeStage === 'preview'">
            <div class="demo-scene-heading">
              <span>PREVIEW / EXPORT</span>
              <strong>确认后，直接得到可投递的 PDF</strong>
            </div>
            <div class="demo-preview-row">
              <div class="demo-preview-paper">
                <span>Java 后端工程师</span>
                <strong>Alex Chen</strong>
                <i /><i /><i /><i />
                <small>已保存的岗位优化版本</small>
              </div>
              <div class="demo-preview-inspector">
                <span class="demo-surface-label">DOCUMENT CHECK</span>
                <strong>可以导出</strong>
                <p>页面结构与联系方式检查通过。</p>
                <span class="demo-export-line">导出 PDF</span>
              </div>
            </div>
          </template>
        </section>
      </Transition>
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

.demo-scene {
  display: grid;
  align-content: center;
  gap: 20px;
  min-height: 330px;
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

.demo-requirement-row,
.demo-evidence-row {
  display: grid;
  gap: 8px;
  padding: 15px 16px;
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

@media (prefers-reduced-motion: reduce) {
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
  .demo-confirm-row {
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
