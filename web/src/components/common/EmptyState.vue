<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  actionText?: string
}>()

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <section class="ui-empty-state">
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <el-button v-if="actionText" type="primary" @click="emit('action')">{{ actionText }}</el-button>
  </section>
</template>
