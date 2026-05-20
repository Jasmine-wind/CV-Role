<script setup lang="ts">
import type { JobDescriptionStructuredContent } from '@/types/job-description'

defineProps<{
  content: JobDescriptionStructuredContent | null
}>()
</script>

<template>
  <section class="job-parse-result">
    <el-empty v-if="!content" description="暂无结构化解析结果" :image-size="80" />
    <template v-else>
      <article class="job-parse-summary">
        <span>解析职位</span>
        <strong>{{ content.jobTitle || '-' }}</strong>
        <p>{{ content.summary || '暂无岗位摘要' }}</p>
      </article>
      <div class="job-parse-grid">
        <article>
          <h3>必备技能</h3>
          <el-tag v-for="item in content.requiredSkills" :key="item" type="success">{{ item }}</el-tag>
          <el-empty v-if="!content.requiredSkills.length" description="暂无必备技能" :image-size="64" />
        </article>
        <article>
          <h3>加分技能</h3>
          <el-tag v-for="item in content.bonusSkills" :key="item" type="warning">{{ item }}</el-tag>
          <el-empty v-if="!content.bonusSkills.length" description="暂无加分技能" :image-size="64" />
        </article>
        <article>
          <h3>岗位职责</h3>
          <p v-for="item in content.responsibilities" :key="item">{{ item }}</p>
          <el-empty v-if="!content.responsibilities.length" description="暂无岗位职责" :image-size="64" />
        </article>
        <article>
          <h3>经验要求</h3>
          <p v-for="item in content.experienceSignals" :key="item">{{ item }}</p>
          <el-empty v-if="!content.experienceSignals.length" description="暂无经验要求" :image-size="64" />
        </article>
        <article class="span-full">
          <h3>关键词</h3>
          <el-tag v-for="item in content.keywords" :key="item" type="info">{{ item }}</el-tag>
          <el-empty v-if="!content.keywords.length" description="暂无关键词" :image-size="64" />
        </article>
      </div>
    </template>
  </section>
</template>
