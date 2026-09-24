<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <span class="app-header__brand">人生补丁包</span>
      <nav class="app-header__nav">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/post">发帖</RouterLink>
        <RouterLink to="/me">我的</RouterLink>
      </nav>
    </div>
  </header>

  <main class="app-main">
    <RouterView />
  </main>
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
</style>
