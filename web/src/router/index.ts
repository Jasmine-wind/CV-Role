import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
    title?: string
    layoutWidth?: 'default' | 'wide' | 'reading'
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'landing',
      component: () => import('@/views/landing/LandingView.vue'),
      meta: {
        title: '岗位定向简历优化',
      },
    },
    {
      path: '/app',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: {
        requiresAuth: true,
        title: '首页',
        layoutWidth: 'wide',
      },
    },
    {
      path: '/job-analysis/:optimizationTaskId',
      name: 'job-analysis',
      component: () => import('@/views/job/JobAnalysisView.vue'),
      meta: {
        requiresAuth: true,
        title: '岗位分析',
        layoutWidth: 'wide',
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
    {
      path: '/:pathMatch(.*)*',
      redirect: '/app',
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
