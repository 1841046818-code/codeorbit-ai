import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from './DashboardView.vue'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import { getToken } from './auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView, meta: { guest: true } },
    { path: '/register', component: RegisterView, meta: { guest: true } },
    { path: '/workspace', component: DashboardView, meta: { requiresAuth: true } },
  ],
})

router.beforeEach((to) => {
  const authenticated = Boolean(getToken())
  if (to.meta.requiresAuth && !authenticated) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.guest && authenticated) return '/workspace'
  return true
})

export default router
