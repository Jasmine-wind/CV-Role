<script setup lang="ts">
import type { ResumeListItem } from '@/types/resume'

defineProps<{
  resumes: ResumeListItem[]
  activeResumeId?: number | null
}>()

const emit = defineEmits<{
  select: [resume: ResumeListItem]
}>()
</script>

<template>
  <section class="resume-list-panel">
    <button
      v-for="resume in resumes"
      :key="resume.id"
      type="button"
      class="resume-list-item"
      :class="{ 'is-active': activeResumeId === resume.id }"
      @click="emit('select', resume)"
    >
      <strong>{{ resume.originalFilename }}</strong>
      <span>{{ resume.fileType }} · {{ resume.uploadStatus }}</span>
    </button>
    <el-empty v-if="!resumes.length" description="暂无简历" :image-size="80" />
  </section>
</template>
