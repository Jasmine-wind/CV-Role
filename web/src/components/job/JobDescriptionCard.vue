<script setup lang="ts">
import type { JobDescriptionDetail } from '@/types/job-description'
import StatusTag from '@/components/common/StatusTag.vue'

defineProps<{
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
    <p class="job-description-card-text">{{ record.rawText.slice(0, 120) }}{{ record.rawText.length > 120 ? '...' : '' }}</p>
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
