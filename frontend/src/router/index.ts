import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

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
    },
  ],
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
