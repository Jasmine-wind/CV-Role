<script setup lang="ts">
interface ProcessStep {
  title: string
  description?: string
  status?: 'done' | 'current' | 'pending' | 'failed'
}

defineProps<{
  steps: ProcessStep[]
}>()
</script>

<template>
  <div class="workflow-stepper">
    <div
      v-for="(step, index) in steps"
      :key="`${step.title}-${index}`"
      class="workflow-step"
      :class="`is-${step.status || 'pending'}`"
    >
      <span class="workflow-step-marker">{{ step.status === 'done' ? '✓' : index + 1 }}</span>
      <div>
        <strong>{{ step.title }}</strong>
        <p v-if="step.description">{{ step.description }}</p>
      </div>
    </div>
  </div>
</template>
