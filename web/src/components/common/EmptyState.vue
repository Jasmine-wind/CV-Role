<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  actionText?: string
  eyebrow?: string
  secondaryText?: string
  compact?: boolean
}>()

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <section class="ui-empty-state" :class="{ 'is-compact': compact }">
    <span v-if="eyebrow" class="ui-state-eyebrow">{{ eyebrow }}</span>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <small v-if="secondaryText">{{ secondaryText }}</small>
    <el-button v-if="actionText" type="primary" @click="emit('action')">{{ actionText }}</el-button>
  </section>
</template>
