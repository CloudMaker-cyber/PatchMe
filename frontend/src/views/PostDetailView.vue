<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { IdentityMode, PostDetail } from '@/types'
import { ApiError, fetchPostDetail, setHelpful, createReply, setCommentsClosed, toggleBookmark, toggleSupport } from '@/api'
import { intentLabels } from '@/utils/dict'
import { useDictStore } from '@/stores/dicts'
import { useAuthStore } from '@/stores/auth'
import { relativeTime } from '@/utils/time'
import AuthorDisplay from '@/components/AuthorDisplay.vue'
import ReplyItem from '@/components/ReplyItem.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const dicts = useDictStore()

const detail = ref<PostDetail | null>(null)
const loading = ref(true)
const notFound = ref(false)
const actionError = ref('')

const replyBody = ref('')
const replyIdentity = ref<IdentityMode>('ANONYMOUS')

const post = computed(() => detail.value?.post ?? null)
const tagNames = computed(() =>
  (post.value?.tagIds ?? []).map((id) => dicts.nameOf(dicts.tags, id)).filter((n): n is string => !!n),
)
const replyValid = computed(() => {
  const b = replyBody.value.trim()
  return b.length >= 2 && b.length <= 1000
})

async function load() {
  loading.value = true
  const id = String(route.params.id)
  const d = await fetchPostDetail(id)
  detail.value = d
  notFound.value = !d
  loading.value = false
}

watchEffect(load)
onMounted(() => void dicts.ensureLoaded())

/** 写操作失败统一处理：401 跳登录，其余展示后端安全文案 */
function onActionError(e: unknown) {
  if (auth.requireLogin(router, e)) return
  actionError.value = e instanceof ApiError ? e.message : '操作失败，请稍后重试'
}

async function onToggleHelpful(replyId: string) {
  if (!post.value || !detail.value) return
  const target = detail.value.replies.find((r) => r.id === replyId)
  try {
    await setHelpful(post.value.id, replyId, !(target?.isHelpful ?? false))
    await load()
  } catch (e) {
    onActionError(e)
  }
}

async function onToggleClosed() {
  if (!post.value) return
  try {
    await setCommentsClosed(post.value.id, !post.value.commentsClosed)
    await load()
  } catch (e) {
    onActionError(e)
  }
}

async function onSupport() {
  if (!detail.value) return
  try {
    detail.value.post.supportCount = await toggleSupport(detail.value.post.id)
    detail.value.supportedByMe = !detail.value.supportedByMe
  } catch (e) {
    onActionError(e)
  }
}

async function onBookmark() {
  if (!detail.value) return
  try {
    detail.value.bookmarkedByMe = await toggleBookmark(detail.value.post.id)
  } catch (e) {
    onActionError(e)
  }
}

async function onReply() {
  if (!post.value || !replyValid.value) return
  try {
    await createReply(post.value.id, replyBody.value, replyIdentity.value)
    replyBody.value = ''
    await load()
  } catch (e) {
    onActionError(e)
  }
}
</script>

<template>
  <div>
    <p v-if="loading" class="empty">加载中…</p>

    <p v-else-if="notFound" class="empty">
      内容不存在或已被作者删除。<RouterLink to="/">返回首页</RouterLink>
    </p>

    <template v-else-if="post && detail">
      <p v-if="actionError" class="detail__error" @click="actionError = ''">{{ actionError }}</p>
      <article class="card">
        <div class="detail__meta">
          <span class="badge badge--advice" :class="{
            'badge--vent': post.intent === 'VENT',
            'badge--companion': post.intent === 'COMPANION',
          }">{{ intentLabels[post.intent] }}</span>
          <AuthorDisplay :author="post.author" />
          <span class="muted">{{ relativeTime(post.createdAt) }}</span>
        </div>
        <h1 class="detail__title">{{ post.title || '（无标题）' }}</h1>
        <p class="detail__body">{{ post.body }}</p>
        <div class="detail__tags">
          <span v-for="t in tagNames" :key="t" class="chip chip--static">#{{ t }}</span>
        </div>
        <div class="detail__actions">
          <button class="btn" :class="{ 'btn--primary': detail.supportedByMe }" @click="onSupport">
            支持 {{ post.supportCount }}
          </button>
          <button class="btn" :class="{ 'btn--primary': detail.bookmarkedByMe }" @click="onBookmark">
            {{ detail.bookmarkedByMe ? '已收藏' : '收藏' }}
          </button>
          <button v-if="detail.isAuthorOfPost" class="btn detail__close" @click="onToggleClosed">
            {{ post.commentsClosed ? '重新开放评论' : '关闭评论' }}
          </button>
        </div>
        <p v-if="detail.isAuthorOfPost" class="muted detail__author-tip">
          你是楼主：可以标记某条回复“有帮助”，或关闭评论。
        </p>
      </article>

      <section class="card detail__replies">
        <h2>回复 {{ post.replyCount }}</h2>
        <p v-if="post.commentsClosed" class="muted">评论已被楼主关闭。</p>
        <ul v-if="detail.replies.length" class="detail__reply-list">
          <ReplyItem
            v-for="r in detail.replies"
            :key="r.id"
            :reply="r"
            :can-mark-helpful="detail.isAuthorOfPost"
            @toggle-helpful="onToggleHelpful"
          />
        </ul>
        <p v-else class="muted">还没有人回复。成为第一个支持者。</p>

        <div v-if="!post.commentsClosed" class="detail__reply-form">
          <textarea
            v-model="replyBody"
            rows="3"
            placeholder="说点什么…（2–1000 字）"
            aria-label="回复内容"
          />
          <div class="detail__reply-foot">
            <label class="detail__radio">
              <input v-model="replyIdentity" type="radio" value="ANONYMOUS" />
              <span>匿名</span>
            </label>
            <label class="detail__radio">
              <input v-model="replyIdentity" type="radio" value="PUBLIC" />
              <span>公开昵称“{{ auth.user?.nickname ?? '登录后可选' }}”</span>
            </label>
            <button class="btn btn--primary" :disabled="!replyValid" @click="onReply">回复</button>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.detail__meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.detail__title {
  font-size: 1.2rem;
  margin: 0.6rem 0 0.4rem;
}

.detail__body {
  line-height: 1.9;
  font-size: 0.95rem;
  white-space: pre-wrap;
}

.detail__tags {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin: 0.6rem 0;
}

.chip--static {
  cursor: default;
  border: none;
  background: transparent;
  padding: 0;
}

.detail__actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-top: 0.6rem;
}

.detail__close {
  margin-left: auto;
}

.detail__author-tip {
  margin-top: 0.6rem;
  margin-bottom: 0;
}

.detail__replies {
  margin-top: 1rem;
}

.detail__error {
  background: #fdecea;
  color: #c62828;
  border-radius: 8px;
  padding: 0.5rem 0.8rem;
  font-size: 0.85rem;
  cursor: pointer;
}

.detail__replies h2 {
  font-size: 1rem;
  margin-top: 0;
}

.detail__reply-list {
  margin: 0;
  padding: 0;
}

.detail__reply-form {
  margin-top: 0.9rem;
}

.detail__reply-form textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.5rem 0.6rem;
  font-family: inherit;
  font-size: 0.9rem;
}

.detail__reply-foot {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  margin-top: 0.5rem;
}

.detail__radio {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
}

.detail__reply-foot .btn {
  margin-left: auto;
}
</style>
