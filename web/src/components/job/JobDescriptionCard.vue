<script setup lang="ts">
import { computed } from 'vue'
import type { JobDescriptionDetail, JobDescriptionStructuredContent } from '@/types/job-description'
import StatusTag from '@/components/common/StatusTag.vue'

const props = defineProps<{
  record: JobDescriptionDetail
}>()

const emit = defineEmits<{
  detail: [record: JobDescriptionDetail]
  parse: [record: JobDescriptionDetail]
  match: [record: JobDescriptionDetail]
  delete: [record: JobDescriptionDetail]
}>()

const sourceText = (sourceType: string | null) => {
  if (sourceType === 'PRESET') {
    return '系统预置参考'
  }
  if (sourceType === 'CRAWLED') {
    return '外部采集'
  }
  return '用户粘贴 JD'
}

const structuredContent = computed<JobDescriptionStructuredContent | null>(() => {
  if (!props.record.structuredContent) {
    return null
  }

  try {
    const parsed = JSON.parse(props.record.structuredContent) as Partial<JobDescriptionStructuredContent>
    return {
      jobTitle: parsed.jobTitle || '',
      requiredSkills: Array.isArray(parsed.requiredSkills) ? parsed.requiredSkills : [],
      bonusSkills: Array.isArray(parsed.bonusSkills) ? parsed.bonusSkills : [],
      experienceSignals: Array.isArray(parsed.experienceSignals) ? parsed.experienceSignals : [],
      responsibilities: Array.isArray(parsed.responsibilities) ? parsed.responsibilities : [],
      keywords: Array.isArray(parsed.keywords) ? parsed.keywords : [],
      summary: parsed.summary || '',
    }
  } catch {
    return null
  }
})

const coreSkills = computed(() => {
  return [
    ...(structuredContent.value?.requiredSkills ?? []),
    ...(structuredContent.value?.bonusSkills ?? []),
    ...(structuredContent.value?.keywords ?? []),
  ].filter((item, index, items) => item && items.indexOf(item) === index).slice(0, 6)
})

const formatDateTime = (value: string | null) => {
  if (!value) {
    return '-'
  }

  return value.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <article class="job-description-card">
    <header>
      <div>
        <h3>{{ record.title }}</h3>
        <p>{{ sourceText(record.sourceType) }}</p>
      </div>
      <StatusTag :status="record.parseStatus" />
    </header>
    <p class="job-description-card-text">{{ structuredContent?.summary || record.rawText.slice(0, 120) }}{{ !structuredContent?.summary && record.rawText.length > 120 ? '...' : '' }}</p>
    <div class="job-description-card-meta">
      <span>创建时间：{{ formatDateTime(record.createdAt) }}</span>
      <span>{{ record.parseStatus === 'SUCCESS' ? '可进入匹配分析' : '解析后可匹配' }}</span>
    </div>
    <div class="job-description-card-skills">
      <el-tag v-for="skill in coreSkills" :key="skill" size="small">{{ skill }}</el-tag>
      <span v-if="!coreSkills.length">解析后显示核心技能</span>
    </div>
    <footer>
      <el-button @click="emit('detail', record)">查看详情</el-button>
      <el-button
        :disabled="record.parseStatus === 'SUCCESS'"
        @click="emit('parse', record)"
      >
        解析
      </el-button>
      <el-button
        type="primary"
        :disabled="record.parseStatus !== 'SUCCESS'"
        @click="emit('match', record)"
      >
        开始匹配
      </el-button>
      <el-button type="danger" plain @click="emit('delete', record)">删除</el-button>
    </footer>
  </article>
</template>
