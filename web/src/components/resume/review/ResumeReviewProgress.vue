<script setup lang="ts">
import type { ReviewItemState } from '@/views/resume/resumeReviewPresentation'
import { getReviewProgressLabel } from '@/views/resume/resumeReviewPresentation'

const props = defineProps<{
  items: ReviewItemState[]
  activeItemId: string | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [itemId: string]
}>()
</script>

<template>
  <nav
    v-if="props.items.length"
    class="resume-review-progress"
    aria-label="待确认内容进度"
  >
    <div class="resume-review-progress-track" aria-hidden="true">
      <span :style="{ width: `${(props.items.findIndex((item) => item.item.id === props.activeItemId) + 1) / props.items.length * 100}%` }" />
    </div>
    <div class="resume-review-progress-meta">
      <span>{{ getReviewProgressLabel(Math.max(props.items.findIndex((item) => item.item.id === props.activeItemId), 0), props.items.length) }}</span>
      <span>按顺序确认，处理后会自动更新列表</span>
    </div>
    <div class="resume-review-progress-items">
      <button
        v-for="(state, index) in props.items"
        :key="state.item.id"
        type="button"
        class="resume-review-progress-item"
        :class="{ 'is-active': state.item.id === props.activeItemId }"
        :aria-current="state.item.id === props.activeItemId ? 'step' : undefined"
        :aria-label="`${getReviewProgressLabel(index, props.items.length)}：${state.item.kind === 'ENTRY_CANDIDATE' ? '经历条目' : state.item.kind === 'TEXT_FRAGMENT' ? '未归类内容' : state.item.kind === 'NAME_CANDIDATE' ? '姓名' : '联系方式'}`"
        :disabled="props.disabled"
        @click="emit('select', state.item.id)"
      >
        {{ String(index + 1).padStart(2, '0') }}
      </button>
    </div>
  </nav>
</template>
