<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { IdentityMode, Post, Reply, UserSettings } from '@/types'
import {
  clearHistory,
  deletePost,
  deleteReply,
  fetchHistory,
  fetchMine,
  fetchSettings,
  makePostAnonymous,
  makeReplyAnonymous,
  updateSettings,
} from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useDictStore } from '@/stores/dicts'
import { relativeTime } from '@/utils/time'
import AuthorDisplay from '@/components/AuthorDisplay.vue'
import PostCard from '@/components/PostCard.vue'

type Tab = 'posts' | 'replies' | 'bookmarks' | 'history' | 'settings'

const auth = useAuthStore()
const tab = ref<Tab>('posts')
const loading = ref(true)
const actionError = ref('')
const myPosts = ref<Post[]>([])
const myReplies = ref<Reply[]>([])
const bookmarks = ref<Post[]>([])
const history = ref<Post[]>([])
const historyLoading = ref(false)
const settings = ref<UserSettings | null>(null)
const settingsLoading = ref(false)

onMounted(async () => {
  void useDictStore().ensureLoaded()
  await auth.ensureLoaded()
  if (!auth.user) {
    loading.value = false
    return
  }
  const data = await fetchMine({ username: auth.user.username, nickname: auth.user.nickname })
  myPosts.value = data.posts
  myReplies.value = data.replies
  bookmarks.value = data.bookmarks
  loading.value = false
})

/** 历史/设置只在打开对应标签时才请求（懒加载，也避免每页都多打两次接口） */
async function openTab(t: Tab) {
  tab.value = t
  actionError.value = ''
  if (t === 'history' && history.value.length === 0) {
    historyLoading.value = true
    try {
      history.value = await fetchHistory()
    } catch (e) {
      actionError.value = e instanceof Error ? e.message : '加载失败'
    } finally {
      historyLoading.value = false
    }
  }
  if (t === 'settings' && !settings.value) {
    settingsLoading.value = true
    try {
      settings.value = await fetchSettings()
    } catch (e) {
      actionError.value = e instanceof Error ? e.message : '加载失败'
    } finally {
      settingsLoading.value = false
    }
  }
}

async function onToggleSetting(field: 'replyNotificationEnabled' | 'historyEnabled', value: boolean) {
  if (!settings.value) return
  try {
    settings.value = await updateSettings({ [field]: value } as Partial<UserSettings>)
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '保存失败'
  }
}

async function onDefaultIdentity(mode: IdentityMode) {
  if (!settings.value) return
  try {
    settings.value = await updateSettings({ defaultIdentityMode: mode })
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '保存失败'
  }
}

async function onClearHistory() {
  try {
    await clearHistory()
    history.value = []
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '清空失败'
  }
}

async function onRemovePost(id: string) {
  if (!window.confirm('删除后该内容将从所有页面消失，且无法恢复。确定删除？')) return
  try {
    await deletePost(id)
    myPosts.value = myPosts.value.filter((p) => p.id !== id)
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '删除失败'
  }
}

async function onRemoveReply(id: string) {
  if (!window.confirm('删除后该回复将从帖子页消失，且无法恢复。确定删除？')) return
  try {
    await deleteReply(id)
    myReplies.value = myReplies.value.filter((r) => r.id !== id)
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '删除失败'
  }
}

/** 公开内容转匿名（单向）：成功后本地把作者收敛为 anonymous，按钮随之消失 */
async function onAnonPost(id: string) {
  if (!window.confirm('转为匿名后，该内容将立即从公开主页消失，且此后不可再改回公开。确定转为匿名？')) return
  try {
    await makePostAnonymous(id)
    const p = myPosts.value.find((x) => x.id === id)
    if (p) p.author = { mode: 'anonymous' }
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '操作失败'
  }
}

async function onAnonReply(id: string) {
  if (!window.confirm('转为匿名后，该回复将立即从公开主页消失，且此后不可再改回公开。确定转为匿名？')) return
  try {
    await makeReplyAnonymous(id)
    const r = myReplies.value.find((x) => x.id === id)
    if (r) r.author = { mode: 'anonymous' }
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '操作失败'
  }
}
</script>

<template>
  <div class="me">
    <header class="me__header card">
      <h1>我的</h1>
      <p class="muted">
        当前登录：{{ auth.user?.nickname }}（@{{ auth.user?.username }}）。
        本页内容仅本人可见，所以能看到自己以匿名身份发布的内容。
      </p>
    </header>

    <nav class="me__tabs">
      <button class="chip" :class="{ 'chip--active': tab === 'posts' }" @click="openTab('posts')">
        我的帖子（{{ myPosts.length }}）
      </button>
      <button class="chip" :class="{ 'chip--active': tab === 'replies' }" @click="openTab('replies')">
        我的回复（{{ myReplies.length }}）
      </button>
      <button class="chip" :class="{ 'chip--active': tab === 'bookmarks' }" @click="openTab('bookmarks')">
        收藏（{{ bookmarks.length }}）
      </button>
      <button class="chip" :class="{ 'chip--active': tab === 'history' }" @click="openTab('history')">
        浏览历史
      </button>
      <button class="chip" :class="{ 'chip--active': tab === 'settings' }" @click="openTab('settings')">
        账号设置
      </button>
    </nav>

    <p v-if="actionError" class="me__error" @click="actionError = ''">{{ actionError }}</p>

    <p v-if="loading" class="empty">加载中…</p>

    <template v-else-if="tab === 'posts'">
      <div v-for="p in myPosts" :key="p.id" class="mine-item">
        <PostCard :post="p" class="mine-item__card" />
        <div class="mine-item__actions">
          <button v-if="p.author.mode === 'public'" class="btn" @click="onAnonPost(p.id)">转匿名</button>
          <button class="btn mine-item__delete" @click="onRemovePost(p.id)">删除</button>
        </div>
      </div>
      <p v-if="myPosts.length === 0" class="empty">还没有发过帖子。</p>
    </template>

    <template v-else-if="tab === 'replies'">
      <ul class="reply-list card">
        <li v-for="r in myReplies" :key="r.id" class="reply-list__item">
          <div class="reply-list__meta">
            <RouterLink :to="`/posts/${r.postId}`">查看原帖</RouterLink>
            <span class="muted">{{ relativeTime(r.createdAt) }}</span>
            <span v-if="r.isHelpful" class="reply-list__helpful">被楼主标记为有帮助 ✓</span>
            <button v-if="r.author.mode === 'public'" class="btn reply-list__anon" @click="onAnonReply(r.id)">转匿名</button>
            <button class="btn reply-list__delete" @click="onRemoveReply(r.id)">删除</button>
          </div>
          <div class="reply-list__author">
            <AuthorDisplay :author="r.author" />
          </div>
          <p class="reply-list__body">{{ r.body }}</p>
        </li>
      </ul>
      <p v-if="myReplies.length === 0" class="empty">还没有回复过别人。</p>
    </template>

    <template v-else-if="tab === 'bookmarks'">
      <PostCard v-for="p in bookmarks" :key="p.id" :post="p" />
      <p v-if="bookmarks.length === 0" class="empty">还没有收藏。在帖子详情页点「收藏」即可。</p>
    </template>

    <template v-else-if="tab === 'history'">
      <p v-if="historyLoading" class="empty">加载中…</p>
      <template v-else>
        <div class="me__history-bar">
          <span class="muted">仅保留最近 30 天，且只对自己可见；匿名浏览也会留痕，介意可关闭后清空。</span>
          <button class="btn" :disabled="history.length === 0" @click="onClearHistory">清空历史</button>
        </div>
        <PostCard v-for="p in history" :key="p.id" :post="p" />
        <p v-if="history.length === 0" class="empty">还没有浏览记录。</p>
      </template>
    </template>

    <template v-else>
      <p v-if="settingsLoading" class="empty">加载中…</p>
      <section v-else-if="settings" class="card me__settings">
        <h2>账号设置</h2>
        <label class="me__setting">
          <span>发布时默认身份</span>
          <select :value="settings.defaultIdentityMode" @change="onDefaultIdentity(($event.target as HTMLSelectElement).value as IdentityMode)">
            <option value="ANONYMOUS">匿名</option>
            <option value="PUBLIC">公开昵称</option>
          </select>
        </label>
        <label class="me__setting">
          <span>有人回复我的帖子时通知我</span>
          <input
            type="checkbox"
            :checked="settings.replyNotificationEnabled"
            @change="onToggleSetting('replyNotificationEnabled', ($event.target as HTMLInputElement).checked)"
          />
        </label>
        <label class="me__setting">
          <span>记录浏览历史（关闭后不再记录，可用上方「清空历史」删除已有记录）</span>
          <input
            type="checkbox"
            :checked="settings.historyEnabled"
            @change="onToggleSetting('historyEnabled', ($event.target as HTMLInputElement).checked)"
          />
        </label>
        <p class="muted">设置仅本人可见、即时生效。</p>
      </section>
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

.me__error {
  background: #fdecea;
  color: #c62828;
  border-radius: 8px;
  padding: 0.5rem 0.8rem;
  font-size: 0.85rem;
  cursor: pointer;
}

/* 卡片列表项：删除按钮放在卡片外，避开整卡 stretched-link 覆盖层 */
.mine-item {
  position: relative;
}

.mine-item .mine-item__card {
  margin-bottom: 0;
}

.mine-item__actions {
  position: absolute;
  top: 0.6rem;
  right: 0.6rem;
  z-index: 2;
  display: flex;
  gap: 0.4rem;
}

.mine-item__actions .btn {
  font-size: 0.78rem;
  padding: 0.15rem 0.55rem;
}

.mine-item__delete {
  color: #c62828;
  border-color: #e8b4ae;
}

.reply-list__anon {
  margin-left: auto;
  font-size: 0.78rem;
  padding: 0.15rem 0.55rem;
}

.reply-list__delete {
  margin-left: auto;
  font-size: 0.78rem;
  padding: 0.15rem 0.55rem;
  color: #c62828;
  border-color: #e8b4ae;
}

/* 转匿名+删除同时出现时，删除紧跟其后，避免两个 auto 撑开 */
.reply-list__anon + .reply-list__delete {
  margin-left: 0.4rem;
}

.me__history-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
  font-size: 0.85rem;
}

.me__settings {
  padding: 1rem 1.25rem;
}

.me__settings h2 {
  font-size: 1rem;
  margin-top: 0;
}

.me__setting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.9rem;
}

.me__setting select {
  font-family: inherit;
  font-size: 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 0.2rem 0.4rem;
}

.me__settings .muted {
  font-size: 0.8rem;
  margin-bottom: 0;
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
