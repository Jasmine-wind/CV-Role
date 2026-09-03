<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import RequirementNavigator from '@/components/task/RequirementNavigator.vue'
import type { EvidenceRequirementItem } from '@/types/evidence-analysis'

const props = defineProps<{
  requirements: EvidenceRequirementItem[]
  selectedRequirementId: number | null
  jobTitle: string
}>()

const emit = defineEmits<{
  select: [requirementId: number]
}>()

const container = ref<HTMLElement | null>(null)

watch(
  () => [props.selectedRequirementId, props.requirements.length],
  async () => {
    await nextTick()
    const selected = container.value?.querySelector<HTMLElement>('.requirement-item.is-selected')
    selected?.scrollIntoView?.({
      behavior: 'auto',
      block: 'nearest',
      inline: 'nearest',
    })
  },
  { immediate: true },
)
</script>

<template>
  <div ref="container" class="workspace-requirements">
    <RequirementNavigator
      :requirements="requirements"
      :selected-requirement-id="selectedRequirementId"
      :job-title="jobTitle"
      @select="emit('select', $event)"
    />
  </div>
</template>

<style scoped>
.workspace-requirements {
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.workspace-requirements :deep(.requirements-rail) {
  height: 100%;
}

@media (max-width: 1119px) {
  .workspace-requirements,
  .workspace-requirements :deep(.requirements-rail) {
    height: auto;
  }
}
</style>
