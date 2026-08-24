<script setup lang="ts">
import { computed, ref } from 'vue'
import { diffText } from '@/utils/diffText'

const props = defineProps<{
  mode: 'composing' | 'requesting' | 'ready' | 'stale' | 'rejected' | 'error'
  originalText: string
  suggestedText?: string | null
  reason?: string | null
  rejectMessage?: string | null
  errorMessage?: string | null
}>()

const emit = defineEmits<{
  apply: []
  reject: []
  regenerate: []
  cancel: []
  submitCustom: [instruction: string]
}>()

const diffSegments = computed(() => {
  if (!props.suggestedText) return []
  return diffText(props.originalText, props.suggestedText)
})

const customInstruction = ref('')

const submitCustom = () => {
  const instruction = customInstruction.value.trim()
  if (!instruction) return
  customInstruction.value = ''
  emit('submitCustom', instruction)
}
</script>

<template>
  <div :class="['bullet-suggestion', `is-${mode}`]">
    <template v-if="mode === 'composing'">
      <p class="suggestion-title">自定义要求</p>
      <el-input
        v-model="customInstruction"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 4 }"
        :maxlength="500"
        placeholder="描述本次改写要求，例如：更突出后端职责。AI 不能新增事实。"
        aria-label="自定义改写要求"
      />
      <div class="suggestion-actions">
        <el-button size="small" type="primary" :disabled="!customInstruction.trim()" @click="submitCustom">
          生成建议
        </el-button>
        <el-button size="small" @click="emit('cancel')">取消</el-button>
      </div>
    </template>

    <template v-else-if="mode === 'requesting'">
      <p class="suggestion-title">正在生成岗位定向改写建议…</p>
      <p class="suggestion-note">生成期间继续编辑这条要点会使建议失效。</p>
    </template>

    <template v-else-if="mode === 'ready' || mode === 'stale'">
      <p class="suggestion-title">优化建议</p>
      <div class="suggestion-diff" aria-label="改写差异">
        <span
          v-for="(segment, index) in diffSegments"
          :key="index"
          :class="`diff-segment is-${segment.type}`"
        >{{ segment.text }}</span>
      </div>
      <p class="suggestion-diff-legend">
        <span class="diff-legend-item is-added">新增表达</span>
        <span class="diff-legend-item is-removed">原表达</span>
      </p>
      <p v-if="reason" class="suggestion-reason">修改原因：{{ reason }}</p>
      <p v-if="mode === 'stale'" class="suggestion-stale">
        内容或版本已变化，这条建议已失效，不能采纳。可以重新生成或关闭。
      </p>
      <div class="suggestion-actions">
        <el-button
          v-if="mode === 'ready'"
          size="small"
          type="primary"
          :disabled="!suggestedText"
          @click="emit('apply')"
        >
          采纳
        </el-button>
        <el-button size="small" @click="emit('regenerate')">重新生成</el-button>
        <el-button size="small" text @click="emit('reject')">{{ mode === 'stale' ? '关闭' : '拒绝' }}</el-button>
      </div>
    </template>

    <template v-else-if="mode === 'rejected'">
      <p class="suggestion-title">本次建议未通过事实校验</p>
      <p class="suggestion-note">{{ rejectMessage ?? 'AI 改写引入了原文没有的事实，已被拒绝。' }}</p>
      <div class="suggestion-actions">
        <el-button size="small" @click="emit('reject')">关闭</el-button>
        <el-button size="small" @click="emit('regenerate')">重新生成</el-button>
      </div>
    </template>

    <template v-else>
      <p class="suggestion-title">建议生成失败</p>
      <p class="suggestion-note">{{ errorMessage ?? 'AI 服务暂时不可用，请稍后重试。' }}</p>
      <div class="suggestion-actions">
        <el-button size="small" @click="emit('reject')">关闭</el-button>
        <el-button size="small" @click="emit('regenerate')">重试</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.bullet-suggestion {
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: var(--app-radius-sm);
  background: var(--app-primary-soft);
  padding: 12px;
  display: grid;
  gap: 8px;
}

.bullet-suggestion.is-rejected,
.bullet-suggestion.is-error {
  border-color: var(--el-color-warning-light-7);
  background: var(--app-warning-soft);
}

.suggestion-title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text);
}

.suggestion-diff {
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}

.diff-segment.is-added {
  background: var(--el-color-success-light-8);
  color: var(--el-color-success-dark-2);
}

.diff-segment.is-removed {
  background: var(--el-color-danger-light-8);
  color: var(--el-color-danger);
  text-decoration: line-through;
}

.suggestion-diff-legend {
  display: flex;
  gap: 12px;
  margin: 0;
}

.diff-legend-item {
  font-size: 12px;
  padding: 0 6px;
  border-radius: 4px;
}

.diff-legend-item.is-added {
  background: var(--el-color-success-light-8);
  color: var(--el-color-success-dark-2);
}

.diff-legend-item.is-removed {
  background: var(--el-color-danger-light-8);
  color: var(--el-color-danger);
  text-decoration: line-through;
}

.suggestion-reason,
.suggestion-note,
.suggestion-stale {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-secondary);
}

.suggestion-stale {
  color: var(--el-color-warning-dark-2);
  font-weight: 600;
}

.suggestion-actions {
  display: flex;
  gap: 8px;
}
</style>
