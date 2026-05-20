<script setup lang="ts">
import type { ResumeListItem } from '@/types/resume'

defineProps<{
  modelValue: boolean
  resume: ResumeListItem | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="简历详情"
    size="720px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <slot>
      <el-empty v-if="!resume" description="请选择简历" :image-size="80" />
      <section v-else class="resume-detail-drawer-body">
        <h3>{{ resume.originalFilename }}</h3>
        <p>{{ resume.fileType }} · {{ resume.uploadStatus }}</p>
      </section>
    </slot>
  </el-drawer>
</template>
