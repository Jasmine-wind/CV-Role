<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { ReviewItemState } from '@/views/resume/resumeReviewPresentation'
import {
  getReviewItemNavigationPresentation,
  getReviewProgressLabel,
  groupReviewItems,
} from '@/views/resume/resumeReviewPresentation'

const props = defineProps<{
  items: ReviewItemState[]
  activeItemId: string | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [itemId: string]
}>()

const navigationGroups = () => groupReviewItems(props.items)
const activeIndex = () => props.items.findIndex((item) => item.item.id === props.activeItemId)
const activeState = () => props.items.find((item) => item.item.id === props.activeItemId) ?? props.items[0] ?? null
const indexDisclosure = ref<HTMLDetailsElement | null>(null)
const selectItem = (itemId: string) => emit('select', itemId)

const scrollActiveIndexItem = async () => {
  await nextTick()
  const active = indexDisclosure.value?.querySelector<HTMLElement>('[aria-current="step"]')
  if (typeof active?.scrollIntoView === 'function') {
    active.scrollIntoView({ behavior: 'auto', block: 'nearest' })
  }
}

const handleIndexToggle = (event: Event) => {
  if (event.target instanceof HTMLDetailsElement && event.target.open) {
    void scrollActiveIndexItem()
  }
}

watch(
  () => props.activeItemId,
  () => {
    if (indexDisclosure.value?.open) void scrollActiveIndexItem()
  },
)
</script>

<template>
  <nav
    v-if="props.items.length"
    class="resume-review-progress"
    aria-label="待确认内容"
  >
    <div class="resume-review-progress-meta">
      <span class="resume-review-progress-current">
        <strong>第 {{ activeIndex() + 1 }} 项</strong>
        <span v-if="activeState()">{{ ` · ${getReviewItemNavigationPresentation(activeState()!).groupLabel}` }}</span>
      </span>
      <span>当前还有 {{ props.items.length }} 项待确认 · 按原顺序确认</span>
    </div>

    <details ref="indexDisclosure" class="resume-review-index" @toggle="handleIndexToggle">
      <summary>
        <span>查看全部待确认内容</span>
        <span class="resume-review-index-count">{{ props.items.length }}</span>
      </summary>
      <div class="resume-review-index-items">
        <section v-for="group in navigationGroups()" :key="group.key" class="resume-review-index-group">
          <header class="resume-review-index-group-header">
            <h3>{{ group.label }}</h3>
            <span>{{ group.items.length }} 项</span>
          </header>
          <div class="resume-review-index-group-items">
            <button
              v-for="entry in group.items"
              :key="entry.state.item.id"
              type="button"
              class="resume-review-index-item"
              :class="{ 'is-active': entry.state.item.id === props.activeItemId }"
              :aria-current="entry.state.item.id === props.activeItemId ? 'step' : undefined"
              :aria-label="`${getReviewProgressLabel(entry.index, props.items.length)}：${entry.presentation.typeLabel} · ${entry.presentation.summary}`"
              :disabled="props.disabled"
              @click="selectItem(entry.state.item.id)"
            >
              <span class="resume-review-index-number">{{ String(entry.index + 1).padStart(2, '0') }}</span>
              <span class="resume-review-index-copy">
                <strong>{{ entry.presentation.typeLabel }}</strong>
                <span>{{ entry.presentation.summary }}</span>
              </span>
            </button>
          </div>
        </section>
      </div>
    </details>
  </nav>
</template>
