<script setup lang="ts">
import { computed } from 'vue'
import type { JobDescriptionStructuredContent } from '@/types/job-description'

const props = defineProps<{
  content: JobDescriptionStructuredContent | null
}>()

const skillSections = computed(() => {
  if (!props.content) {
    return []
  }

  return [
    {
      key: 'requiredSkills',
      title: '核心技能',
      tone: 'success',
      items: props.content.requiredSkills,
    },
    {
      key: 'bonusSkills',
      title: '加分技能',
      tone: 'warning',
      items: props.content.bonusSkills,
    },
    {
      key: 'keywords',
      title: '关键词',
      tone: 'info',
      items: props.content.keywords,
    },
  ].filter((section) => section.items.length > 0)
})

const textSections = computed(() => {
  if (!props.content) {
    return []
  }

  return [
    {
      key: 'responsibilities',
      title: '职责要求',
      items: props.content.responsibilities,
    },
    {
      key: 'experienceSignals',
      title: '经验信号',
      items: props.content.experienceSignals,
    },
  ].filter((section) => section.items.length > 0)
})
</script>

<template>
  <section class="job-parse-result">
    <el-empty v-if="!content" description="暂无结构化解析结果" :image-size="80" />
    <template v-else>
      <article v-if="content.jobTitle || content.summary" class="job-parse-summary">
        <span v-if="content.jobTitle">解析职位</span>
        <strong v-if="content.jobTitle">{{ content.jobTitle }}</strong>
        <p v-if="content.summary">{{ content.summary }}</p>
      </article>

      <div class="job-parse-grid">
        <article v-for="section in skillSections" :key="section.key">
          <h3>{{ section.title }}</h3>
          <div class="job-parse-tags">
            <el-tag v-for="item in section.items" :key="item" :type="section.tone">{{ item }}</el-tag>
          </div>
        </article>

        <article v-for="section in textSections" :key="section.key" class="span-full">
          <h3>{{ section.title }}</h3>
          <ol class="job-parse-list">
            <li v-for="item in section.items" :key="item">{{ item }}</li>
          </ol>
        </article>
      </div>
    </template>
  </section>
</template>

<style scoped>
.job-parse-result {
  display: grid;
  gap: 16px;
}

.job-parse-summary {
  display: grid;
  gap: 8px;
  padding: 18px;
  border: 1px solid var(--app-color-border);
  border-radius: 16px;
  background: var(--app-color-surface-soft);
}

.job-parse-summary span {
  color: var(--app-color-text-secondary);
  font-size: 13px;
}

.job-parse-summary strong {
  color: var(--app-color-text);
  font-size: 22px;
}

.job-parse-summary p {
  margin: 0;
  color: var(--app-color-text-secondary);
  line-height: 1.8;
}

.job-parse-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.job-parse-grid article {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--app-color-border);
  border-radius: 16px;
  background: var(--app-color-surface-soft);
}

.job-parse-grid article.span-full {
  grid-column: 1 / -1;
}

.job-parse-grid h3 {
  margin: 0 0 12px;
  color: var(--app-color-text);
  font-size: 15px;
  font-weight: 700;
}

.job-parse-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.job-parse-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
  color: var(--app-color-text);
  line-height: 1.7;
}

@media (max-width: 760px) {
  .job-parse-grid {
    grid-template-columns: 1fr;
  }
}
</style>
