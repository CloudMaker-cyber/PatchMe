<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { Post, Reply } from '@/types'
import { fetchMine } from '@/mock/api'
import { relativeTime } from '@/utils/time'
import AuthorDisplay from '@/components/AuthorDisplay.vue'
import PostCard from '@/components/PostCard.vue'

type Tab = 'posts' | 'replies' | 'bookmarks'

const tab = ref<Tab>('posts')
const loading = ref(true)
const myPosts = ref<Post[]>([])
const myReplies = ref<Reply[]>([])
const bookmarks = ref<Post[]>([])

onMounted(async () => {
  const data = await fetchMine()
  myPosts.value = data.posts
  myReplies.value = data.replies
  bookmarks.value = data.bookmarks
  loading.value = false
})
</script>

<template>
  <div class="me">
    <header class="me__header card">
      <h1>我的</h1>
      <p class="muted">
        当前模拟登录：小满（u_lin）。任务 2 起这里将是真实 JWT 登录态；本页内容仅本人可见，
        所以能看到自己以匿名身份发布的内容。
      </p>
    </header>

    <nav class="me__tabs">
      <button class="chip" :class="{ 'chip--active': tab === 'posts' }" @click="tab = 'posts'">
        我的帖子（{{ myPosts.length }}）
      </button>
      <button class="chip" :class="{ 'chip--active': tab === 'replies' }" @click="tab = 'replies'">
        我的回复（{{ myReplies.length }}）
      </button>
      <button class="chip" :class="{ 'chip--active': tab === 'bookmarks' }" @click="tab = 'bookmarks'">
        收藏（{{ bookmarks.length }}）
      </button>
    </nav>

    <p v-if="loading" class="empty">加载中…</p>

    <template v-else-if="tab === 'posts'">
      <PostCard v-for="p in myPosts" :key="p.id" :post="p" />
      <p v-if="myPosts.length === 0" class="empty">还没有发过帖子。</p>
    </template>

    <template v-else-if="tab === 'replies'">
      <ul class="reply-list card">
        <li v-for="r in myReplies" :key="r.id" class="reply-list__item">
          <div class="reply-list__meta">
            <RouterLink :to="`/posts/${r.postId}`">查看原帖</RouterLink>
            <span class="muted">{{ relativeTime(r.createdAt) }}</span>
            <span v-if="r.isHelpful" class="reply-list__helpful">被楼主标记为有帮助 ✓</span>
          </div>
          <div class="reply-list__author">
            <AuthorDisplay :author="r.author" />
          </div>
          <p class="reply-list__body">{{ r.body }}</p>
        </li>
      </ul>
      <p v-if="myReplies.length === 0" class="empty">还没有回复过别人。</p>
    </template>

    <template v-else>
      <PostCard v-for="p in bookmarks" :key="p.id" :post="p" />
      <p v-if="bookmarks.length === 0" class="empty">还没有收藏。在帖子详情页点「收藏」即可。</p>
    </template>
  </div>
</template>

<style scoped>
.me__header {
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}

.me__header h1 {
  margin: 0 0 0.35rem;
  font-size: 1.3rem;
}

.me__header p {
  margin: 0;
  font-size: 0.85rem;
}

.me__tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.reply-list {
  list-style: none;
  margin: 0;
  padding: 0.5rem 1rem;
}

.reply-list__item {
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--color-border);
}

.reply-list__item:last-child {
  border-bottom: none;
}

.reply-list__meta {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  font-size: 0.85rem;
}

.reply-list__helpful {
  color: #2e7d32;
  font-weight: 600;
}

.reply-list__author {
  margin: 0.35rem 0;
}

.reply-list__body {
  margin: 0;
  font-size: 0.92rem;
  line-height: 1.65;
}
</style>
