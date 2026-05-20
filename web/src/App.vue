<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/layout/AppLayout.vue'

const route = useRoute()

const usesAppLayout = computed(() => Boolean(route.meta.requiresAuth))
</script>

<template>
  <AppLayout v-if="usesAppLayout">
    <router-view />
  </AppLayout>
  <router-view v-else />
</template>
