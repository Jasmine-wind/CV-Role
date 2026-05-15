import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: {
        requiresAuth: true,
        title: '工作台',
      },
    },
    {
      path: '/resumes',
      name: 'resumes',
      component: () => import('@/views/resume/ResumeView.vue'),
      meta: {
        requiresAuth: true,
        title: '我的简历',
      },
    },
    {
      path: '/jobs',
      name: 'jobs',
      component: () => import('@/views/job/JobListView.vue'),
      meta: {
        requiresAuth: true,
        title: '岗位库',
      },
    },
    {
      path: '/jobs/:id',
      name: 'job-detail',
      component: () => import('@/views/job/JobDetailView.vue'),
      meta: {
        requiresAuth: true,
        title: '岗位详情',
      },
    },
    {
      path: '/job-descriptions',
      name: 'job-description-list',
      component: () => import('@/views/job/JobDescriptionListView.vue'),
      meta: {
        requiresAuth: true,
        title: '目标岗位',
      },
    },
    {
      path: '/job-descriptions/new',
      name: 'job-description-create',
      component: () => import('@/views/job/JobDescriptionCreateView.vue'),
      meta: {
        requiresAuth: true,
        title: '新增目标岗位',
      },
    },
    {
      path: '/job-descriptions/:id',
      name: 'job-description-detail',
      component: () => import('@/views/job/JobDescriptionDetailView.vue'),
      meta: {
        requiresAuth: true,
        title: '目标岗位详情',
      },
    },
    {
      path: '/ai-job-matches',
      name: 'ai-job-matches',
      component: () => import('@/views/job/AiJobMatchView.vue'),
      meta: {
        requiresAuth: true,
        title: '匹配与优化',
      },
    },
    {
      path: '/history',
      name: 'history',
      component: () => import('@/views/history/HistoryView.vue'),
      meta: {
        requiresAuth: true,
        title: 'AI 历史',
      },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: {
        guestOnly: true,
        title: '登录',
      },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/auth/RegisterView.vue'),
      meta: {
        guestOnly: true,
        title: '注册',
      },
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return { name: 'home' }
  }

  return true
})

export default router
