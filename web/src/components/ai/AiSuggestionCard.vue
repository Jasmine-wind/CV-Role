<script setup lang="ts">
defineProps<{
  priority?: string | null
  type?: string | null
  issue?: string | null
  suggestion?: string | null
  evidence?: string[]
  caution?: string | null
}>()
</script>

<template>
  <article class="ai-suggestion-card">
    <header>
      <el-space wrap>
        <el-tag v-if="priority" size="small" type="warning">{{ priority }}</el-tag>
        <el-tag v-if="type" size="small" type="info">{{ type }}</el-tag>
      </el-space>
    </header>
    <p v-if="issue"><strong>问题：</strong>{{ issue }}</p>
    <p v-if="suggestion"><strong>建议：</strong>{{ suggestion }}</p>
    <div v-if="evidence?.length" class="ai-suggestion-evidence">
      <span v-for="item in evidence" :key="item">{{ item }}</span>
    </div>
    <p v-if="caution" class="ai-suggestion-caution"><strong>风险提示：</strong>{{ caution }}</p>
    <slot name="actions" />
  </article>
</template>
