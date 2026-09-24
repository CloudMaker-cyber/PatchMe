import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/post',
      name: 'post',
      component: () => import('../views/PostView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/posts/:id',
      name: 'post-detail',
      component: () => import('../views/PostDetailView.vue'),
    },
    {
      path: '/u/:username',
      name: 'profile',
      component: () => import('../views/ProfileView.vue'),
    },
    {
      path: '/me',
      name: 'me',
      component: () => import('../views/MeView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
    },
  ],
})

// 需要登录的页面：先等 /api/me 回来再判定；未登录跳登录页并带回跳地址。
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true
  const auth = useAuthStore()
  await auth.ensureLoaded()
  if (auth.isLoggedIn) return true
  return { path: '/login', query: { redirect: to.fullPath } }
})

export default router

// 懒加载路由块加载失败（dev 期间文件多次热更、或生产环境发版后旧页面残留）时，
// 导航会静默失败——表现为"点了没反应"。这里捕获后强制刷新一次恢复可用；
// 5 秒内不重复刷新，避免模块真缺失时陷入 reload 死循环。
router.onError((error) => {
  if (!/Failed to fetch dynamically imported module|Importing a module script failed/i.test(String(error))) return
  const last = Number(sessionStorage.getItem('chunk-reload-at') ?? 0)
  if (Date.now() - last < 5000) return
  sessionStorage.setItem('chunk-reload-at', String(Date.now()))
  window.location.reload()
})
