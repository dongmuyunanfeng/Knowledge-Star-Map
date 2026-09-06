import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      name: 'StarMap',
      component: () => import('@/views/StarMapView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/files',
      name: 'FileManage',
      component: () => import('@/views/FileManageView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/search',
      name: 'KnowledgeSearch',
      component: () => import('@/views/KnowledgeSearchView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/agent',
      name: 'AgentChat',
      component: () => import('@/views/AgentChatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/study-plan',
      name: 'StudyPlan',
      component: () => import('@/views/StudyPlanView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/resume',
      name: 'Resume',
      component: () => import('@/views/ResumeView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/projects',
      name: 'ProjectExperience',
      component: () => import('@/views/ProjectExperienceView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { requiresAuth: true }
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth !== false && !authStore.isLoggedIn) {
    next('/login')
  } else if (to.path === '/login' && authStore.isLoggedIn) {
    next('/')
  } else {
    next()
  }
})

export default router
