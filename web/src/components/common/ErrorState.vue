<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  actionText?: string
  reason?: string
  secondaryText?: string
  compact?: boolean
}>()

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <section class="ui-error-state" :class="{ 'is-compact': compact }">
    <span class="ui-state-eyebrow">需要处理</span>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <small v-if="reason">可能原因：{{ reason }}</small>
    <small v-if="secondaryText">{{ secondaryText }}</small>
    <el-button v-if="actionText" type="primary" plain @click="emit('action')">{{ actionText }}</el-button>
  </section>
</template>
