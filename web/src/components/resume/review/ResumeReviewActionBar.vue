<script setup lang="ts">
const props = defineProps<{
  primaryAction: string
  canDelete: boolean
  busy: boolean
  actionError: string | null
}>()

const emit = defineEmits<{
  accept: []
  reject: []
  retry: []
}>()
</script>

<template>
  <div class="resume-review-action-area">
    <div v-if="props.actionError" class="resume-review-action-error" role="alert">
      <strong>本次确认没有保存，请重试。</strong>
      <span>{{ props.actionError }}</span>
      <button type="button" :disabled="props.busy" @click="emit('retry')">重试当前操作</button>
    </div>
    <div class="resume-review-action-bar">
      <div class="resume-review-action-note">
        <span>确认后才会写入准备好的简历内容。</span>
        <small v-if="props.canDelete">“不加入简历”不会删除原始文件。</small>
      </div>
      <div class="resume-review-action-buttons">
        <button
          v-if="props.canDelete"
          type="button"
          class="resume-review-reject"
          :disabled="props.busy"
          @click="emit('reject')"
        >
          不加入简历
        </button>
        <button
          type="button"
          class="resume-review-accept"
          :disabled="props.busy"
          @click="emit('accept')"
        >
          {{ props.busy ? '正在保存…' : props.primaryAction }}
        </button>
      </div>
    </div>
  </div>
</template>
