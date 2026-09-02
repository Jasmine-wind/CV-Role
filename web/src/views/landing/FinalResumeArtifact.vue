<template>
  <div class="final-resume-artifact">
    <div class="final-artifact-stack">
      <div class="final-artifact-original" aria-hidden="true">
        <span>原始版本</span>
        <i /><i /><i /><i />
      </div>
      <article class="final-artifact-sheet">
        <header>
          <div>
            <span class="final-artifact-status">岗位定向版本 · 已确认</span>
            <h3>林然</h3>
            <p>项目推进 · 数据分析 · 跨团队协作</p>
          </div>
          <strong>✓ 已确认</strong>
        </header>
        <div class="final-artifact-rule" />
        <section>
          <header><h4>工作经历</h4><span>项目推进</span></header>
          <div class="final-artifact-entry">
            <strong>某互联网公司</strong>
            <span>项目运营 / 数据分析</span>
            <p>• 负责项目关键数据整理与分析，根据结果推动后续方案调整。</p>
            <p>• 梳理业务数据并推进异常处理，与产品、工程团队协作完成交付。</p>
          </div>
        </section>
        <section class="final-artifact-project">
          <header><h4>项目经历</h4><span>已保留原文</span></header>
          <p>• 梳理现有流程与协作节点，推动方案从讨论进入稳定执行。</p>
        </section>
        <footer class="final-artifact-results">
          <div><strong>3</strong><span>处表达调整</span></div>
          <div><strong>2</strong><span>处保留原文</span></div>
          <div><strong>0</strong><span>处无依据新增</span></div>
        </footer>
      </article>
    </div>
    <p class="final-artifact-disclaimer">演示结果 · 示例内容，不代表真实用户数据或效果承诺</p>
    <p class="final-artifact-export">可以导出 PDF</p>
  </div>
</template>

<style scoped>
.final-resume-artifact {
  position: relative;
  width: min(100%, 620px);
  padding-bottom: 4px;
}

.final-artifact-stack {
  position: relative;
  min-height: 510px;
}

.final-artifact-original,
.final-artifact-sheet {
  position: absolute;
  box-sizing: border-box;
  border: 1px solid var(--app-border-strong);
  background: var(--app-document);
}

.final-artifact-original {
  top: 28px;
  right: 8%;
  bottom: 14px;
  left: 4%;
  padding: 30px;
  color: var(--app-text-muted);
  transform: rotate(-1.2deg);
}

.final-artifact-original span {
  display: block;
  margin-bottom: 32px;
  font-family: var(--app-font-mono);
  font-size: 10px;
}

.final-artifact-original i {
  display: block;
  width: 76%;
  height: 5px;
  margin: 16px 0;
  background: var(--app-border);
}

.final-artifact-original i:nth-of-type(2) { width: 58%; }
.final-artifact-original i:nth-of-type(3) { width: 84%; }
.final-artifact-original i:nth-of-type(4) { width: 62%; }

.final-artifact-sheet {
  z-index: 1;
  top: 0;
  right: 0;
  bottom: 0;
  left: 8%;
  display: grid;
  align-content: start;
  gap: 17px;
  padding: 30px 34px 24px;
  box-shadow: 0 8px 24px rgba(15, 14, 12, 0.18), 0 2px 5px rgba(15, 14, 12, 0.08);
}

.final-artifact-sheet > header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.final-artifact-sheet > header > div {
  display: grid;
  gap: 7px;
}

.final-artifact-status,
.final-artifact-sheet > header > strong {
  color: var(--app-primary-active);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.final-artifact-sheet > header > strong {
  color: var(--app-accent-hover);
  white-space: nowrap;
}

.final-artifact-sheet h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 34px;
  line-height: 1;
  letter-spacing: -0.04em;
}

.final-artifact-sheet > header p,
.final-artifact-sheet section header span,
.final-artifact-entry > span,
.final-artifact-project > p,
.final-artifact-entry p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 11px;
  line-height: 1.65;
}

.final-artifact-rule,
.final-artifact-sheet section header {
  border-bottom: 1px solid var(--app-border);
}

.final-artifact-rule { height: 1px; }

.final-artifact-sheet section {
  display: grid;
  gap: 9px;
}

.final-artifact-sheet section header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 7px;
}

.final-artifact-sheet h4 {
  margin: 0;
  color: var(--app-text);
  font-size: 13px;
}

.final-artifact-entry {
  display: grid;
  gap: 4px;
}

.final-artifact-entry > strong {
  color: var(--app-text);
  font-size: 13px;
}

.final-artifact-entry p,
.final-artifact-project > p {
  color: var(--app-text);
}

.final-artifact-results {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: auto;
  padding-top: 15px;
  border-top: 1px solid var(--app-border-strong);
}

.final-artifact-results div {
  display: grid;
  gap: 4px;
  padding-left: 14px;
}

.final-artifact-results div + div {
  border-left: 1px solid var(--app-border);
}

.final-artifact-results strong {
  color: var(--app-primary);
  font-size: 23px;
  line-height: 1;
}

.final-artifact-results span {
  color: var(--app-text-muted);
  font-size: 10px;
}

.final-artifact-disclaimer {
  margin: 14px 0 0 8%;
  color: #aaa39a;
  font-size: 10px;
  line-height: 1.5;
}

.final-artifact-export {
  margin: 9px 0 0 8%;
  color: #c5d0c1;
  font-family: var(--app-font-mono);
  font-size: 11px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .final-resume-artifact { width: 100%; }
  .final-artifact-stack { min-height: 410px; }
  .final-artifact-original { display: none; }
  .final-artifact-sheet { left: 0; padding: 23px 20px 19px; }
  .final-artifact-sheet h3 { font-size: 29px; }
  .final-artifact-disclaimer,
  .final-artifact-export { margin-left: 0; }
}

@media (max-width: 520px) {
  .final-artifact-stack { min-height: 390px; }
  .final-artifact-sheet { gap: 13px; }
  .final-artifact-sheet > header > strong { font-size: 9px; }
  .final-artifact-results div { padding-left: 9px; }
  .final-artifact-results strong { font-size: 20px; }
  .final-artifact-results span { font-size: 9px; }
}

@media (prefers-reduced-motion: reduce) {
  .final-artifact-original { transform: none; }
}
</style>
