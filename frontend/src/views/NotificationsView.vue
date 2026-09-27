<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { AppNotification } from '@/types'
import { ApiError, fetchNotifications, markNotificationsRead } from '@/api'
import { relativeTime } from '@/utils/time'

const notifications = ref<AppNotification[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    notifications.value = await fetchNotifications()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function onReadAll() {
  try {
    await markNotificationsRead()
    notifications.value = notifications.value.map((n) => ({ ...n, read: true }))
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '操作失败'
  }
}

onMounted(load)
</script>

<template>
  <div class="notifications">
    <header class="notifications__header card">
      <h1>通知</h1>
      <p class="muted">仅本人可见。匿名回复的提醒不会透露回复者是谁。</p>
      <button
        class="btn notifications__read"
        :disabled="notifications.every((n) => n.read)"
        @click="onReadAll"
      >
        全部标为已读
      </button>
    </header>

    <p v-if="error" class="notifications__error" @click="error = ''">{{ error }}</p>
    <p v-if="loading" class="empty">加载中…</p>

    <ul v-else-if="notifications.length" class="notifications__list card">
      <li v-for="n in notifications" :key="n.id" class="notifications__item">
        <RouterLink :to="`/posts/${n.postId}`" class="notifications__link">
          <span class="notifications__dot" :class="{ 'notifications__dot--unread': !n.read }" />
          <span class="notifications__text">
            有人回复了你的帖子：{{ n.excerpt || '（无摘要）' }}
          </span>
        </RouterLink>
        <span class="muted notifications__time">{{ relativeTime(n.createdAt) }}</span>
      </li>
    </ul>

    <p v-else class="empty">还没有通知。当别人回复你发布的帖子时，会在这里提醒你。</p>
  </div>
</template>

<style scoped>
.notifications__header {
  position: relative;
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}

.notifications__header h1 {
  margin: 0 0 0.35rem;
  font-size: 1.3rem;
}

.notifications__header p {
  margin: 0;
  font-size: 0.85rem;
}

.notifications__read {
  position: absolute;
  top: 1rem;
  right: 1.25rem;
  font-size: 0.8rem;
  padding: 0.2rem 0.6rem;
}

.notifications__error {
  background: #fdecea;
  color: #c62828;
  border-radius: 8px;
  padding: 0.5rem 0.8rem;
  font-size: 0.85rem;
  cursor: pointer;
}

.notifications__list {
  list-style: none;
  margin: 0;
  padding: 0.5rem 1rem;
}

.notifications__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.7rem 0;
  border-bottom: 1px solid var(--color-border);
}

.notifications__item:last-child {
  border-bottom: none;
}

.notifications__link {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  color: var(--color-text);
  text-decoration: none;
}

.notifications__link:hover .notifications__text {
  color: var(--color-primary);
}

.notifications__dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: transparent;
  border: 1px solid var(--color-border);
}

.notifications__dot--unread {
  background: #e53935;
  border-color: #e53935;
}

.notifications__text {
  font-size: 0.9rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notifications__time {
  flex: none;
  font-size: 0.78rem;
}
</style>
