<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function onLogout() {
  await auth.logout()
  void router.push('/')
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <span class="app-header__brand">人生补丁包</span>
      <nav class="app-header__nav">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/post">发帖</RouterLink>
        <template v-if="auth.isLoggedIn && auth.user">
          <RouterLink :to="`/u/${auth.user.username}`">{{ auth.user.nickname }}</RouterLink>
          <RouterLink to="/me">我的</RouterLink>
          <RouterLink to="/notifications">通知</RouterLink>
          <RouterLink v-if="auth.isAdmin" to="/admin">审核后台</RouterLink>
          <a href="#" @click.prevent="onLogout">退出</a>
        </template>
        <template v-else>
          <RouterLink to="/login">登录</RouterLink>
          <RouterLink to="/register">注册</RouterLink>
        </template>
      </nav>
    </div>
  </header>

  <main class="app-main">
    <RouterView />
  </main>

  <footer class="app-footer">
    <RouterLink to="/privacy">隐私说明</RouterLink>
    <span class="app-footer__sep">·</span>
    <RouterLink to="/rules">社区公约</RouterLink>
    <span class="app-footer__sep">·</span>
    <span>匿名是这里的底线：发布出去的每一句话，都由你自己决定说多少。</span>
  </footer>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  /* 卡片 stretched-link 的 ::after 覆盖层会随滚动压到导航上，必须抬高层级 */
  z-index: 10;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.app-header__inner {
  max-width: 720px;
  margin: 0 auto;
  padding: 0.75rem 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.app-header__brand {
  font-weight: 700;
  color: var(--color-primary);
}

.app-header__nav a {
  margin-left: 1.25rem;
  text-decoration: none;
  color: var(--color-text-muted);
}

.app-header__nav a.router-link-active {
  color: var(--color-text);
  font-weight: 600;
}

.app-main {
  flex: 1;
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  padding: 1rem;
}

.app-footer {
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  padding: 1rem 1rem 1.5rem;
  font-size: 0.78rem;
  color: var(--color-text-muted);
  text-align: center;
  line-height: 1.8;
}

.app-footer a {
  color: var(--color-text-muted);
}

.app-footer__sep {
  margin: 0 0.35rem;
}
</style>
