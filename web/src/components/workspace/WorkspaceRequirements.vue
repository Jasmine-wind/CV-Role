<script setup lang="ts">
import RequirementNavigator from '@/components/task/RequirementNavigator.vue'
import type { EvidenceRequirementItem } from '@/types/evidence-analysis'

defineProps<{
  requirements: EvidenceRequirementItem[]
  selectedRequirementId: number | null
  jobTitle: string
}>()

const emit = defineEmits<{
  select: [requirementId: number]
}>()
</script>

<template>
  <RequirementNavigator
    :requirements="requirements"
    :selected-requirement-id="selectedRequirementId"
    :job-title="jobTitle"
    @select="emit('select', $event)"
  />
</template>
