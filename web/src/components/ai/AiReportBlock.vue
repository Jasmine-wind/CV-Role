<script setup lang="ts">
interface ReportItem {
  label: string
  value: string | number | null | undefined
}

defineProps<{
  title: string
  conclusion?: string | null
  evidence?: string[]
  problems?: string[]
  suggestions?: string[]
  cautions?: string[]
  nextSteps?: string[]
  meta?: ReportItem[]
}>()
</script>

<template>
  <article class="ai-report-block">
    <header>
      <h3>{{ title }}</h3>
      <p v-if="conclusion">{{ conclusion }}</p>
    </header>

    <section v-if="meta?.length" class="ai-report-meta">
      <span v-for="item in meta" :key="item.label">
        {{ item.label }}
        <strong>{{ item.value ?? '-' }}</strong>
      </span>
    </section>

    <section class="ai-report-grid">
      <div v-if="evidence?.length">
        <h4>依据</h4>
        <p v-for="item in evidence" :key="item">{{ item }}</p>
      </div>
      <div v-if="problems?.length">
        <h4>问题</h4>
        <p v-for="item in problems" :key="item">{{ item }}</p>
      </div>
      <div v-if="suggestions?.length">
        <h4>建议</h4>
        <p v-for="item in suggestions" :key="item">{{ item }}</p>
      </div>
      <div v-if="cautions?.length">
        <h4>注意事项</h4>
        <p v-for="item in cautions" :key="item">{{ item }}</p>
      </div>
      <div v-if="nextSteps?.length">
        <h4>下一步</h4>
        <p v-for="item in nextSteps" :key="item">{{ item }}</p>
      </div>
    </section>
  </article>
</template>
