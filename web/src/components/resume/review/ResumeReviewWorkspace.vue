<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import ResumeReviewActionBar from './ResumeReviewActionBar.vue'
import ResumeReviewCandidateEditor from './ResumeReviewCandidateEditor.vue'
import ResumeReviewProgress from './ResumeReviewProgress.vue'
import type { ReviewItemState } from '@/views/resume/resumeReviewPresentation'
import { getReviewCandidatePresentation } from '@/views/resume/resumeReviewPresentation'

const props = defineProps<{
  filename: string
  loading: boolean
  loadError: string | null
  actionError: string | null
  qualityStatus: string | null
  reviewName: string
  reviewNameMissing: boolean
  items: ReviewItemState[]
  activeItemId: string | null
  resolvingItemId: string | null
  contactTypeOptions: Array<{ value: string; label: string }>
  requiredContactTypeOptions: Array<{ value: string; label: string }>
}>()

const emit = defineEmits<{
  close: []
  retryLoad: []
  retryAction: []
  uploadReplacement: []
  backToLibrary: []
  selectItem: [itemId: string]
  previousItem: []
  nextItem: []
  accept: []
  reject: []
  'update:reviewName': [value: string]
  'update:state': [state: ReviewItemState]
}>()

const heading = ref<HTMLElement | null>(null)
const activeState = computed(() => props.items.find((item) => item.item.id === props.activeItemId) ?? null)
const activeIndex = computed(() => props.items.findIndex((item) => item.item.id === props.activeItemId))
const activePresentation = computed(() => activeState.value ? getReviewCandidatePresentation(activeState.value) : null)
const isBusy = computed(() => Boolean(props.resolvingItemId))

const focusHeading = async () => {
  await nextTick()
  if (window.matchMedia('(max-width: 900px)').matches) {
    heading.value?.scrollIntoView({ behavior: 'auto', block: 'start' })
  }
  heading.value?.focus({ preventScroll: true })
}

watch(
  () => [props.loading, props.loadError] as const,
  ([loading]) => {
    if (!loading) void focusHeading()
  },
  { flush: 'post' },
)

const isTextInput = (target: EventTarget | null) => {
  if (!(target instanceof HTMLElement)) return false
  return target.matches('input, textarea, select, [contenteditable="true"]')
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    if (!isTextInput(event.target)) {
      event.preventDefault()
      emit('close')
    }
    return
  }
  if (isTextInput(event.target) || isBusy.value || !activeState.value) return
  if (event.key === 'ArrowLeft' && activeIndex.value > 0) {
    event.preventDefault()
    emit('previousItem')
  }
  if (event.key === 'ArrowRight' && activeIndex.value < props.items.length - 1) {
    event.preventDefault()
    emit('nextItem')
  }
}
</script>

<template>
  <aside
    class="resume-review-workspace"
    tabindex="-1"
    aria-labelledby="resume-review-workspace-title"
    @keydown="handleKeydown"
  >
    <header class="resume-review-header">
      <div class="resume-review-header-copy">
        <span class="resume-review-label">内容审阅</span>
        <h2 id="resume-review-workspace-title" ref="heading" tabindex="-1">内容确认</h2>
        <p :title="props.filename">{{ props.filename }}</p>
        <span class="resume-review-header-description">
          系统只把无法安全判断的内容交给你确认。确认后的内容，才能用于岗位分析与导出。
        </span>
      </div>
      <button type="button" class="resume-review-close" @click="emit('close')">收起</button>
    </header>

    <div v-if="props.loading" class="resume-review-loading-state" role="status" aria-live="polite">
      <span class="resume-review-loading-line is-wide" />
      <span class="resume-review-loading-line" />
      <span class="resume-review-loading-field" />
      <span class="resume-review-loading-field is-short" />
      <strong>正在读取待确认内容</strong>
    </div>

    <div v-else-if="props.loadError" class="resume-review-error-state" role="alert">
      <strong>暂时无法读取待确认内容。</strong>
      <p>{{ props.loadError }}</p>
      <div class="resume-review-error-actions">
        <button type="button" class="resume-review-retry" @click="emit('retryLoad')">重新读取</button>
        <button type="button" class="resume-review-text-action" @click="emit('backToLibrary')">返回简历库</button>
      </div>
    </div>

    <div v-else-if="!props.items.length && props.qualityStatus !== 'READY'" class="resume-review-recovery" role="status">
      <span class="resume-review-recovery-mark" aria-hidden="true">!</span>
      <div>
        <strong>当前没有可安全确认的候选内容</strong>
        <p>系统识别到这份简历仍需处理，但没有足够明确的片段让你安全裁决。继续猜测可能会把错误内容写入简历，因此本次没有自动补全。</p>
        <small>建议上传一个排版更清晰的版本后重新准备。</small>
      </div>
      <div class="resume-review-recovery-actions">
        <button type="button" class="resume-review-accept" @click="emit('uploadReplacement')">上传更清晰的版本</button>
        <button type="button" class="resume-review-text-action" @click="emit('backToLibrary')">返回简历库</button>
      </div>
    </div>

    <div v-else-if="!props.items.length" class="resume-review-complete" role="status" aria-live="polite">
      <strong>确认完成</strong>
      <p>这份简历已可用于岗位分析。</p>
    </div>

    <template v-else-if="activeState && activePresentation">
      <ResumeReviewProgress
        :items="props.items"
        :active-item-id="props.activeItemId"
        :disabled="isBusy"
        @select="emit('selectItem', $event)"
      />
      <ResumeReviewCandidateEditor
        :state="activeState"
        :review-name="props.reviewName"
        :review-name-missing="props.reviewNameMissing"
        :contact-type-options="props.contactTypeOptions"
        :required-contact-type-options="props.requiredContactTypeOptions"
        @update:review-name="emit('update:reviewName', $event)"
        @update:state="emit('update:state', $event)"
      />
      <ResumeReviewActionBar
        :primary-action="activePresentation.primaryAction"
        :can-delete="activePresentation.canDelete"
        :busy="isBusy"
        :action-error="props.actionError"
        @accept="emit('accept')"
        @reject="emit('reject')"
        @retry="emit('retryAction')"
      />
    </template>
  </aside>
</template>

<style>
.resume-review-workspace {
  display: grid;
  align-content: start;
  gap: var(--app-space-6);
  min-width: 0;
  min-height: 560px;
  scroll-margin-top: calc(var(--app-shell-header-height) + var(--app-space-4));
  padding: var(--app-space-1) 0 var(--app-space-8);
  outline: none;
}

.resume-review-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-6);
  border-bottom: 1px solid var(--app-border-strong);
  padding-bottom: var(--app-space-5);
}

.resume-review-header-copy {
  display: grid;
  min-width: 0;
  gap: var(--app-space-1);
}

.resume-review-label,
.resume-review-candidate-kicker {
  color: var(--app-primary);
  font-family: var(--app-font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.resume-review-header h2 {
  margin: 0;
  scroll-margin-top: calc(var(--app-shell-header-height) + var(--app-space-4));
  color: var(--app-text);
  font-size: 26px;
  line-height: var(--app-line-height-tight);
}

.resume-review-header p {
  overflow: hidden;
  max-width: 40ch;
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-review-header-description {
  max-width: 62ch;
  margin-top: var(--app-space-3);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.resume-review-close,
.resume-review-text-action,
.resume-review-retry {
  border: 0;
  border-bottom: 1px solid var(--app-border-strong);
  padding: 4px 0;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: var(--app-font-size-sm);
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.resume-review-close:hover,
.resume-review-close:focus-visible,
.resume-review-text-action:hover,
.resume-review-text-action:focus-visible,
.resume-review-retry:hover,
.resume-review-retry:focus-visible {
  color: var(--app-primary-active);
  border-color: var(--app-primary);
}

.resume-review-progress {
  display: grid;
  gap: var(--app-space-3);
}

.resume-review-progress-track {
  height: 2px;
  overflow: hidden;
  background: var(--app-border);
}

.resume-review-progress-track span {
  display: block;
  height: 100%;
  background: var(--app-primary);
}

.resume-review-progress-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--app-space-4);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
}

.resume-review-progress-meta span:first-child {
  color: var(--app-text);
  font-weight: 700;
}

.resume-review-progress-items {
  display: flex;
  min-width: 0;
  gap: var(--app-space-2);
  overflow-x: auto;
  padding: 2px 2px 5px;
  scrollbar-color: var(--app-scroll-thumb) transparent;
  scrollbar-width: thin;
}

.resume-review-progress-item {
  min-width: 34px;
  flex: 0 0 auto;
  min-height: 30px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  color: var(--app-text-secondary);
  font-family: var(--app-font-mono);
  font-size: var(--app-font-size-xs);
  background: var(--app-surface);
  cursor: pointer;
}

.resume-review-progress-item:hover,
.resume-review-progress-item:focus-visible,
.resume-review-progress-item.is-active {
  border-color: var(--app-primary);
  color: var(--app-primary-active);
  background: var(--app-primary-soft);
}

.resume-review-progress-item:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.resume-review-candidate {
  display: grid;
  gap: var(--app-space-5);
}

.resume-review-candidate-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-4);
}

.resume-review-candidate-header h3 {
  margin: var(--app-space-1) 0 0;
  color: var(--app-text);
  font-size: 21px;
  line-height: var(--app-line-height-tight);
}

.resume-review-candidate-kind {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-xs);
}

.resume-review-reason {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.resume-review-name,
.resume-review-form-grid,
.resume-review-entry-form,
.resume-review-field-grid {
  display: grid;
  gap: var(--app-space-2);
}

.resume-review-name {
  border-top: 1px solid var(--app-border);
  padding-top: var(--app-space-4);
}

.resume-review-name label,
.resume-review-field-grid label,
.resume-review-form-grid label {
  color: var(--app-text);
  font-size: var(--app-font-size-sm);
  font-weight: 700;
}

.resume-review-name small {
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
}

.resume-review-field-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--app-space-4);
}

.resume-review-name input,
.resume-review-form-grid input,
.resume-review-form-grid textarea,
.resume-review-form-grid select,
.resume-review-field-grid input,
.resume-review-field-grid textarea {
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 10px 12px;
  color: var(--app-text);
  font: inherit;
  font-size: var(--app-font-size-sm);
  background: var(--app-surface);
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.resume-review-name input:focus,
.resume-review-form-grid input:focus,
.resume-review-form-grid textarea:focus,
.resume-review-form-grid select:focus,
.resume-review-field-grid input:focus,
.resume-review-field-grid textarea:focus {
  border-color: var(--app-primary);
  outline: none;
  box-shadow: 0 0 0 2px var(--app-primary-subtle);
}

.resume-review-form-grid textarea,
.resume-review-field-grid textarea {
  min-height: 108px;
  resize: vertical;
}

.resume-review-entry-preview {
  display: grid;
  gap: var(--app-space-2);
  border-top: 1px solid var(--app-border);
  padding-top: var(--app-space-4);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.resume-review-entry-preview strong {
  color: var(--app-text);
}

.resume-review-entry-preview p {
  margin: 0;
}

.resume-review-action-area {
  display: grid;
  gap: var(--app-space-4);
  border-top: 1px solid var(--app-border-strong);
  padding-top: var(--app-space-5);
}

.resume-review-action-error {
  display: grid;
  gap: var(--app-space-1);
  border-left: 2px solid var(--app-danger);
  padding-left: var(--app-space-3);
  color: var(--app-danger);
  font-size: var(--app-font-size-xs);
  line-height: var(--app-line-height-body);
}

.resume-review-action-error button {
  justify-self: start;
  border: 0;
  border-bottom: 1px solid currentColor;
  padding: 2px 0;
  color: inherit;
  font: inherit;
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.resume-review-action-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--app-space-6);
}

.resume-review-action-note {
  display: grid;
  gap: var(--app-space-1);
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-xs);
  line-height: var(--app-line-height-body);
}

.resume-review-action-note small {
  color: var(--app-text-muted);
}

.resume-review-action-buttons {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--app-space-4);
}

.resume-review-reject {
  border: 0;
  border-bottom: 1px solid var(--app-border-strong);
  padding: 5px 0;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: var(--app-font-size-sm);
  font-weight: 700;
  background: transparent;
  cursor: pointer;
}

.resume-review-reject:hover,
.resume-review-reject:focus-visible {
  color: var(--app-danger);
  border-color: var(--app-danger);
}

.resume-review-accept {
  min-height: 44px;
  border: 1px solid var(--app-primary);
  border-radius: var(--app-radius-sm);
  padding: 0 18px;
  color: #fffdf8;
  font: inherit;
  font-size: var(--app-font-size-sm);
  font-weight: 700;
  background: var(--app-primary);
  cursor: pointer;
}

.resume-review-accept:hover,
.resume-review-accept:focus-visible {
  border-color: var(--app-primary-hover);
  background: var(--app-primary-hover);
}

.resume-review-accept:disabled,
.resume-review-reject:disabled,
.resume-review-text-action:disabled,
.resume-review-retry:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.resume-review-loading-state,
.resume-review-error-state,
.resume-review-recovery,
.resume-review-complete {
  display: grid;
  gap: var(--app-space-4);
}

.resume-review-loading-state {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
}

.resume-review-loading-line,
.resume-review-loading-field {
  display: block;
  height: 12px;
  border-radius: 2px;
  background: var(--app-bg-soft);
}

.resume-review-loading-line.is-wide {
  grid-column: 1 / -1;
  width: 30%;
  height: 20px;
}

.resume-review-loading-line:not(.is-wide) {
  width: 70%;
}

.resume-review-loading-field {
  grid-column: 1 / -1;
  height: 44px;
}

.resume-review-loading-field.is-short {
  width: 76%;
}

.resume-review-loading-state strong {
  grid-column: 1 / -1;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
}

.resume-review-error-state {
  border-top: 1px solid var(--app-danger);
  padding-top: var(--app-space-4);
}

.resume-review-error-state strong,
.resume-review-recovery strong,
.resume-review-complete strong {
  color: var(--app-text);
  font-size: var(--app-font-size-lg);
}

.resume-review-error-state p,
.resume-review-recovery p,
.resume-review-complete p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: var(--app-font-size-sm);
  line-height: var(--app-line-height-body);
}

.resume-review-error-actions,
.resume-review-recovery-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--app-space-5);
}

.resume-review-recovery {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  border-top: 1px solid var(--app-warning);
  padding-top: var(--app-space-5);
}

.resume-review-recovery-mark {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid var(--app-warning);
  border-radius: 50%;
  color: var(--app-warning);
  font-weight: 800;
}

.resume-review-recovery small {
  display: block;
  margin-top: var(--app-space-2);
  color: var(--app-text-muted);
  font-size: var(--app-font-size-xs);
}

.resume-review-recovery-actions {
  grid-column: 2;
}

@media (max-width: 900px) {
  .resume-review-workspace {
    min-height: 0;
    padding: var(--app-space-6) 0 var(--app-space-8);
  }

  .resume-review-action-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .resume-review-action-buttons {
    align-items: stretch;
    flex-direction: column-reverse;
  }

  .resume-review-accept {
    width: 100%;
  }

  .resume-review-reject {
    align-self: center;
  }
}

@media (max-width: 560px) {
  .resume-review-header {
    gap: var(--app-space-4);
  }

  .resume-review-header h2 {
    font-size: 22px;
  }

  .resume-review-header-description {
    font-size: var(--app-font-size-xs);
  }

  .resume-review-field-pair {
    grid-template-columns: 1fr;
  }

  .resume-review-progress-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--app-space-1);
  }
}
</style>
