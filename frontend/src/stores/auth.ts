import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as api from '@/api'
import { isUnauthorized } from '@/api'

/**
 * 登录态：token 在 HttpOnly Cookie 里，前端只缓存"是谁"用于展示与入口控制。
 * 真正的权限判定永远在后端（JWT），这里被篡改也不会获得任何数据。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<api.AuthUser | null>(null)
  const ready = ref(false)
  let bootstrap: Promise<void> | null = null

  const isLoggedIn = computed(() => !!user.value)

  /** 应用启动/路由守卫调用：同一时刻只发一次 /api/me */
  function ensureLoaded(): Promise<void> {
    if (!bootstrap) {
      bootstrap = (async () => {
        user.value = await api.fetchMe()
        ready.value = true
      })()
    }
    return bootstrap
  }

  async function login(email: string, password: string) {
    user.value = await api.login(email, password)
    ready.value = true
  }

  async function logout() {
    await api.logout().catch(() => undefined)
    user.value = null
  }

  /** 视图写操作遇到 401：跳登录页并带回原地址；返回是否为未登录错误 */
  function requireLogin(router: ReturnType<typeof useRouter>, e: unknown): boolean {
    if (!isUnauthorized(e)) return false
    user.value = null
    const current = router.currentRoute.value
    void router.push({ path: '/login', query: { redirect: current.fullPath } })
    return true
  }

  return { user, ready, isLoggedIn, ensureLoaded, login, logout, requireLogin }
})
