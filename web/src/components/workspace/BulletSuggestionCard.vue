<script setup lang="ts">
import { computed, ref } from 'vue'
import { diffText } from '@/utils/diffText'

const props = defineProps<{
  mode: 'composing' | 'requesting' | 'ready' | 'stale' | 'rejected' | 'error'
  originalText: string
  suggestedText?: string | null
  reason?: string | null
  reviewCode?: string | null
  reviewMessage?: string | null
  rejectCode?: string | null
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

const customInstruction = ref('')
const diffSegments = computed(() =>
  props.suggestedText ? diffText(props.originalText, props.suggestedText) : [],
)

const isLowValueChange = computed(() => props.rejectCode === 'LOW_VALUE_CHANGE')

const missingReasonText = '改写原因未提供，请根据差异核对内容后再决定是否采纳。'
const displayedReason = computed(() => props.reason?.trim() || missingReasonText)

const reviewAdvisories: Record<string, { title: string; message: string }> = {
  NEW_QUANTITATIVE_CLAIM: {
    title: '新增数字或量化描述',
    message: '建议包含原文未写明的数值或量化结果，请核对数值是否真实准确。',
  },
  NEW_TECHNOLOGY: {
    title: '新增技术或能力信息',
    message: '建议包含原文未写明的技术、工具或能力，请按真实经历核对。',
  },
  NEW_ENTITY: {
    title: '新增实体信息',
    message: '建议包含原文未写明的公司、产品、项目或其他实体，请按真实经历核对。',
  },
  RESPONSIBILITY_ESCALATION: {
    title: '职责或参与程度升级',
    message: '建议提高了职责或参与程度的表述，请按实际情况核对。',
  },
  NEW_ACHIEVEMENT: {
    title: '新增成果或效果描述',
    message: '建议包含原文未写明的成果、奖项或效果，请按真实经历核对。',
  },
  NEW_SCOPE_OR_TIME: {
    title: '新增或改变范围、时间',
    message: '建议加入或改变了范围、时间等信息，请核对是否准确。',
  },
  UNDETERMINED: {
    title: '变化内容需要核对',
    message: '这次改写变化较大，系统无法自动确认其中的信息，请按真实经历逐项核对。',
  },
}

const reviewAdvisory = computed(() => {
  const reviewCode = props.reviewCode
  if (!reviewCode) return null
  const mapped = reviewAdvisories[reviewCode]
  return mapped
    ? { ...mapped, message: props.reviewMessage?.trim() || mapped.message }
    : {
        title: '变化内容需要核对',
        message:
          props.reviewMessage?.trim() ||
          '建议包含原文未写明或变化较大的信息，请按真实经历核对。',
      }
})

const applySuggestion = () => emit('apply')

const submitCustom = () => {
  const instruction = customInstruction.value.trim()
  if (!instruction) return
  customInstruction.value = ''
  emit('submitCustom', instruction)
}
</script>

<template>
  <div :class="['bullet-suggestion', `is-${props.mode}`, { 'is-low-value': isLowValueChange }]">
    <template v-if="props.mode === 'composing'">
      <p class="suggestion-title">告诉 AI 这次想怎么改</p>
      <el-input
        v-model="customInstruction"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 4 }"
        :maxlength="500"
        placeholder="例如：更突出后端职责。新增信息会提示你核对。"
        aria-label="自定义改写要求"
      />
      <div class="suggestion-actions">
        <el-button
          size="small"
          type="primary"
          :disabled="!customInstruction.trim()"
          @click="submitCustom"
        >
          生成建议
        </el-button>
        <el-button size="small" @click="emit('cancel')">取消</el-button>
      </div>
    </template>

    <template v-else-if="props.mode === 'requesting'">
      <p class="suggestion-title">正在生成修改建议…</p>
      <p class="suggestion-note">生成期间继续编辑这条内容会使建议失效。</p>
    </template>

    <template v-else-if="props.mode === 'ready' || props.mode === 'stale'">
      <div class="suggestion-copy-block">
        <span class="suggestion-label">当前版本</span>
        <p>{{ props.originalText }}</p>
      </div>
      <div class="suggestion-copy-block is-proposed">
        <span class="suggestion-label">建议版本</span>
        <p>{{ props.suggestedText }}</p>
      </div>
      <div class="suggestion-copy-block is-diff">
        <span class="suggestion-label">差异</span>
        <p aria-label="当前版本与建议版本的差异">
          <span
            v-for="(segment, index) in diffSegments"
            :key="`${segment.type}-${index}`"
            :class="`diff-${segment.type}`"
          >{{ segment.text }}</span>
        </p>
      </div>
      <div class="suggestion-copy-block is-reason">
        <span class="suggestion-label">为什么这样改</span>
        <p>{{ displayedReason }}</p>
      </div>
      <p v-if="props.mode === 'ready' && reviewAdvisory" class="suggestion-review-note">
        <strong>{{ reviewAdvisory.title }}</strong>
        {{ reviewAdvisory.message }}
        <span>采纳只会将建议写入简历，不代表系统已验证内容真实性。</span>
      </p>
      <p v-if="props.mode === 'stale'" class="suggestion-stale">
        内容或版本已变化，这条建议已失效，不能采纳。可以重新生成或关闭。
      </p>
      <div class="suggestion-actions">
        <el-button
          v-if="props.mode === 'ready'"
          size="small"
          type="primary"
          :disabled="!props.suggestedText"
          @click="applySuggestion"
        >
          采纳此建议
        </el-button>
        <el-button size="small" @click="emit('regenerate')">重新生成</el-button>
        <el-button size="small" text @click="emit('reject')">{{
          props.mode === 'stale' ? '关闭' : '拒绝'
        }}</el-button>
      </div>
    </template>

    <template v-else-if="props.mode === 'rejected' && isLowValueChange">
      <p class="suggestion-title">这条内容暂时不需要改</p>
      <p class="suggestion-note">这次生成只产生了很轻微的表达变化，没有足够价值，已保留原文。</p>
      <span class="suggestion-reason-label">原因：原文已经比较清楚</span>
      <div class="suggestion-actions">
        <el-button size="small" type="primary" @click="emit('regenerate')">换个方向</el-button>
        <el-button size="small" text @click="emit('reject')">继续手工编辑</el-button>
      </div>
    </template>

    <template v-else-if="props.mode === 'rejected'">
      <p class="suggestion-title">这条建议暂时不可用</p>
      <p class="suggestion-note">
        系统无法安全处理这次 AI 输出。你可以重新生成，也可以继续手工编辑。
      </p>
      <p v-if="props.rejectMessage" class="suggestion-detail">处理说明：{{ props.rejectMessage }}</p>
      <div class="suggestion-actions">
        <el-button size="small" type="primary" @click="emit('regenerate')">重新生成建议</el-button>
        <el-button size="small" text @click="emit('reject')">继续手工编辑</el-button>
      </div>
    </template>

    <template v-else>
      <p class="suggestion-title">暂时没有生成建议</p>
      <p class="suggestion-note">{{ props.errorMessage ?? 'AI 服务暂时不可用。你可以稍后再试，也可以继续手工编辑。' }}</p>
      <div class="suggestion-actions">
        <el-button size="small" type="primary" @click="emit('regenerate')">重新生成建议</el-button>
        <el-button size="small" text @click="emit('reject')">继续手工编辑</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.bullet-suggestion {
  display: grid;
  gap: 10px;
  margin-top: 2px;
  border: 1px solid var(--app-ai-border);
  border-radius: var(--app-radius-md);
  padding: 13px;
  background: var(--app-ai-soft);
}

.bullet-suggestion.is-rejected:not(.is-low-value),
.bullet-suggestion.is-error {
  border-color: var(--el-color-warning-light-7);
  background: var(--app-warning-soft);
}

.suggestion-title,
.suggestion-note,
.suggestion-stale,
.suggestion-copy-block p {
  margin: 0;
  line-height: 1.65;
}

.suggestion-title {
  color: var(--app-ai);
  font-size: 13px;
  font-weight: 700;
}

.suggestion-note,
.suggestion-stale,
.suggestion-detail {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.suggestion-review-note {
  margin: 0;
  padding: 7px 9px;
  border-left: 2px solid var(--app-warning);
  color: var(--app-warning);
  background: var(--app-warning-soft);
  font-size: 12px;
  line-height: 1.6;
}

.suggestion-review-note strong {
  margin-right: 4px;
  font-weight: 700;
}

.suggestion-reason-label {
  color: var(--app-warning);
  font-size: 11px;
  font-weight: 700;
}

.bullet-suggestion.is-low-value .suggestion-reason-label {
  color: var(--app-text-secondary);
}

.suggestion-detail {
  margin: -3px 0 0;
  color: var(--app-text-muted);
  line-height: 1.55;
}

.suggestion-stale {
  color: var(--el-color-warning-dark-2);
  font-weight: 600;
}

.suggestion-copy-block {
  display: grid;
  gap: 5px;
  padding: 9px 10px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface);
}

.suggestion-copy-block.is-proposed {
  border-color: var(--app-ai-border);
  background: var(--app-surface);
}

.suggestion-copy-block.is-diff {
  border-color: var(--app-border);
  background: var(--app-document);
}

.suggestion-copy-block.is-reason {
  background: transparent;
}

.diff-added {
  color: var(--app-success);
  background: var(--app-success-soft);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.diff-removed {
  color: var(--app-primary-active);
  background: var(--app-primary-soft);
  text-decoration: line-through;
}

.diff-equal {
  color: var(--app-text-secondary);
}

.suggestion-label {
  color: var(--app-ai);
  font-size: 11px;
  font-weight: 700;
}

.suggestion-copy-block p {
  color: var(--app-text);
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}

.suggestion-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>
