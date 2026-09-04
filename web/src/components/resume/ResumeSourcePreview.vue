<script setup lang="ts">
import { nextTick, watch } from 'vue'
import type { ResumeDocument, ResumeDocumentEntry, ResumeDocumentSection } from '@/types/resume-document'

const props = withDefaults(
  defineProps<{
    document: ResumeDocument | null
    displayName: string
    filename: string
    loading?: boolean
    error?: string | null
    highlightedBulletIds?: string[]
    focusedSectionId?: string | null
    closable?: boolean
    showFilename?: boolean
    showStartAction?: boolean
  }>(),
  {
    loading: false,
    error: null,
    highlightedBulletIds: () => [],
    focusedSectionId: null,
    closable: true,
    showFilename: true,
    showStartAction: true,
  },
)

const emit = defineEmits<{
  close: []
  retry: []
  startOptimization: []
}>()

const sectionElements = new Map<string, HTMLElement>()
const setSectionRef = (sectionId: string, element: unknown) => {
  if (element instanceof HTMLElement) sectionElements.set(sectionId, element)
  else sectionElements.delete(sectionId)
}

const scrollToFocusedSection = async () => {
  await nextTick()
  const sectionId = props.focusedSectionId
  if (!sectionId) return
  sectionElements.get(sectionId)?.scrollIntoView({ behavior: 'auto', block: 'center' })
}

watch(() => [props.focusedSectionId, props.document] as const, scrollToFocusedSection, { flush: 'post' })

const sectionKindLabel = (kind: string) => {
  switch (kind) {
    case 'EXPERIENCE':
      return '工作经历'
    case 'PROJECT':
      return '项目经历'
    case 'EDUCATION':
      return '教育经历'
    case 'SKILL':
      return '技能'
    default:
      return '其他经历'
  }
}

const sectionTitle = (section: ResumeDocumentSection) => section.title.trim() || sectionKindLabel(section.kind)

const entryTitle = (entry: ResumeDocumentEntry, kind: string) => {
  if (kind === 'EDUCATION') return entry.school || entry.degree || '教育经历'
  return entry.organization || entry.role || (kind === 'SKILL' ? entry.group : '经历条目') || '经历条目'
}

const entryMeta = (entry: ResumeDocumentEntry, kind: string) => {
  const values = kind === 'EDUCATION'
    ? [entry.degree, entry.major, entry.startDate && entry.endDate ? `${entry.startDate} — ${entry.endDate}` : entry.startDate || entry.endDate]
    : [entry.role, entry.location, entry.startDate && entry.endDate ? `${entry.startDate} — ${entry.endDate}` : entry.startDate || entry.endDate]
  return values.filter((value): value is string => Boolean(value?.trim())).join(' · ')
}

const isHighlighted = (bulletId: string) => props.highlightedBulletIds.includes(bulletId)
</script>

<template>
  <aside class="resume-source-preview" aria-label="已确认简历预览">
    <header class="source-preview-header">
      <div>
        <p class="source-preview-label">已确认材料</p>
        <h2>{{ props.displayName }}</h2>
        <p v-if="props.showFilename" class="source-preview-filename" :title="props.filename">原始文件：{{ props.filename }}</p>
      </div>
      <button v-if="props.closable" type="button" class="source-preview-close" @click="emit('close')">收起</button>
    </header>

    <div v-if="props.loading" class="source-preview-state" role="status">正在读取已确认内容…</div>
    <div v-else-if="props.error" class="source-preview-state is-error" role="alert">
      <strong>暂时无法读取简历预览</strong>
      <p>{{ props.error }}</p>
      <div>
        <button type="button" class="source-preview-retry" @click="emit('retry')">重新读取</button>
      </div>
    </div>
    <div v-else-if="!props.document" class="source-preview-state" role="status">
      <strong>暂时没有可预览的已确认内容</strong>
      <p>完成简历准备后，这里会显示当前可用于岗位分析的材料。</p>
    </div>

    <div v-else class="source-preview-scroll">
      <section class="source-preview-basics">
        <h3>{{ props.document.basics.name || '未命名简历' }}</h3>
        <p v-if="props.document.basics.jobIntention">{{ props.document.basics.jobIntention }}</p>
        <div v-if="props.document.basics.contacts.length" class="source-preview-contacts">
          <span v-for="contact in props.document.basics.contacts" :key="contact.id">
            {{ contact.value }}
          </span>
        </div>
      </section>

      <section
        v-for="section in props.document.sections"
        :key="section.id"
        :ref="(element) => setSectionRef(section.id, element)"
        class="source-preview-section"
        :class="{ 'is-focused': props.focusedSectionId === section.id }"
        :data-source-section-id="section.id"
      >
        <header>
          <h3>{{ sectionTitle(section) }}</h3>
          <span>{{ section.entries.length }} 个条目</span>
        </header>
        <p v-if="!section.entries.length" class="source-preview-empty">暂无内容</p>
        <article v-for="entry in section.entries" :key="entry.id" class="source-preview-entry">
          <div class="source-preview-entry-heading">
            <div>
              <strong>{{ entryTitle(entry, section.kind) }}</strong>
              <span v-if="entryMeta(entry, section.kind)">{{ entryMeta(entry, section.kind) }}</span>
            </div>
            <span v-if="section.kind === 'SKILL' && entry.skillItems?.length" class="source-preview-skills">
              {{ entry.skillItems.join('、') }}
            </span>
          </div>
          <ul v-if="entry.bullets.length">
            <li v-for="bullet in entry.bullets" :key="bullet.id" :class="{ 'is-highlighted': isHighlighted(bullet.id) }">
              <mark v-if="isHighlighted(bullet.id)">{{ bullet.text }}</mark>
              <span v-else>{{ bullet.text }}</span>
            </li>
          </ul>
        </article>
      </section>
    </div>

    <footer v-if="props.showStartAction && props.document" class="source-preview-footer">
      <button type="button" class="source-preview-primary" @click="emit('startOptimization')">
        开始优化这份简历 <span aria-hidden="true">→</span>
      </button>
    </footer>
  </aside>
</template>

<style scoped>
.resume-source-preview {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background: var(--app-surface);
}

.source-preview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-4);
  border-bottom: 1px solid var(--app-border-strong);
  padding: var(--app-space-5) var(--app-space-6);
}

.source-preview-header h2,
.source-preview-basics h3,
.source-preview-section h3 {
  margin: 0;
  color: var(--app-text);
}

.source-preview-header h2 {
  font-size: 20px;
  line-height: var(--app-line-height-tight);
}

.source-preview-label,
.source-preview-filename,
.source-preview-state p,
.source-preview-empty {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  line-height: var(--app-line-height-body);
}

.source-preview-label {
  margin-bottom: var(--app-space-1);
  color: var(--app-primary);
  font-family: var(--app-font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.source-preview-filename {
  overflow: hidden;
  max-width: 34ch;
  margin-top: var(--app-space-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-preview-close,
.source-preview-retry {
  border: 0;
  border-bottom: 1px solid var(--app-border-strong);
  padding: 4px 0;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: var(--app-font-size-xs);
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.source-preview-close:hover,
.source-preview-close:focus-visible,
.source-preview-retry:hover,
.source-preview-retry:focus-visible {
  color: var(--app-primary-active);
  border-color: var(--app-primary);
}

.source-preview-footer {
  border-top: 1px solid var(--app-border-strong);
  padding: var(--app-space-4) var(--app-space-6);
  background: var(--app-surface);
}

.source-preview-primary {
  width: 100%;
  min-height: 36px;
  border: 1px solid var(--app-primary);
  border-radius: var(--app-radius-sm);
  padding: 0 var(--app-space-4);
  color: var(--app-surface);
  font: inherit;
  font-size: var(--app-font-size-sm);
  font-weight: 750;
  background: var(--app-primary);
  cursor: pointer;
  transition: background-color 140ms ease, border-color 140ms ease;
}

.source-preview-primary:hover,
.source-preview-primary:focus-visible {
  border-color: var(--app-primary-active);
  background: var(--app-primary-active);
}

.source-preview-primary:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}

.source-preview-scroll {
  min-height: 0;
  overflow: auto;
  padding: var(--app-space-6);
  overscroll-behavior-y: contain;
}

.source-preview-basics {
  display: grid;
  gap: var(--app-space-1);
  border-bottom: 1px solid var(--app-border);
  padding-bottom: var(--app-space-5);
}

.source-preview-basics h3 {
  font-size: 23px;
}

.source-preview-basics > p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
}

.source-preview-contacts {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: var(--app-space-2) var(--app-space-4);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
}

.source-preview-contacts span,
.source-preview-entry-heading strong,
.source-preview-entry-heading > div span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.source-preview-section {
  display: grid;
  gap: var(--app-space-3);
  border-bottom: 1px solid var(--app-border);
  padding: var(--app-space-5) 0;
  scroll-margin: var(--app-space-5);
}

.source-preview-section.is-focused {
  border-top: 1px solid var(--app-primary);
  background: var(--app-primary-soft);
  box-shadow: 8px 0 0 var(--app-primary-soft), -8px 0 0 var(--app-primary-soft);
}

.source-preview-section > header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--app-space-3);
}

.source-preview-section h3 {
  font-size: 16px;
}

.source-preview-section > header span {
  color: var(--app-text-muted);
  font-size: 11px;
}

.source-preview-entry {
  display: grid;
  gap: var(--app-space-2);
  border-top: 1px solid var(--app-border-soft);
  padding-top: var(--app-space-3);
}

.source-preview-entry-heading {
  display: flex;
  min-width: 0;
  justify-content: space-between;
  gap: var(--app-space-3);
}

.source-preview-entry-heading > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.source-preview-entry-heading strong {
  color: var(--app-text);
  font-size: var(--app-font-size-sm);
}

.source-preview-entry-heading span,
.source-preview-skills {
  color: var(--app-text-secondary);
  font-size: 11px;
  line-height: 1.5;
}

.source-preview-skills {
  max-width: 52%;
  text-align: right;
}

.source-preview-entry ul {
  display: grid;
  gap: var(--app-space-2);
  margin: 0;
  padding-left: 18px;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  line-height: var(--app-line-height-body);
}

.source-preview-entry li.is-highlighted {
  color: var(--app-text);
  font-weight: 650;
}

.source-preview-entry mark {
  color: inherit;
  background: var(--app-primary-subtle);
}

.source-preview-state {
  display: grid;
  align-content: center;
  gap: var(--app-space-2);
  min-height: 180px;
  padding: var(--app-space-6);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
}

.source-preview-state strong {
  color: var(--app-text);
}

.source-preview-state.is-error strong {
  color: var(--app-danger);
}

@media (max-width: 640px) {
  .source-preview-header,
  .source-preview-scroll,
  .source-preview-footer {
    padding-right: var(--app-space-4);
    padding-left: var(--app-space-4);
  }

  .source-preview-entry-heading {
    display: grid;
  }

  .source-preview-skills {
    max-width: none;
    text-align: left;
  }
}
.source-preview-section.is-focused {
  border-top-color: var(--app-border);
  background: transparent;
  box-shadow: inset 2px 0 0 var(--app-primary);
  padding-left: var(--app-space-3);
}

</style>
