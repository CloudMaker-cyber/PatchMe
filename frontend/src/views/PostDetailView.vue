<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { IdentityMode, PostDetail } from '@/types'
import { ApiError, blockUser, deletePost, fetchMyBlocks, fetchPostDetail, makePostAnonymous, makeReplyAnonymous, setHelpful, createReply, setCommentsClosed, toggleBookmark, toggleSupport, unblockUser } from '@/api'
import { intentLabels } from '@/utils/dict'
import { useDictStore } from '@/stores/dicts'
import { useAuthStore } from '@/stores/auth'
import { relativeTime } from '@/utils/time'
import AuthorDisplay from '@/components/AuthorDisplay.vue'
import ReplyItem from '@/components/ReplyItem.vue'
import ReportDialog from '@/components/ReportDialog.vue'

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

async function onDeletePost() {
  if (!post.value) return
  if (!window.confirm('删除后该帖将从首页、主页与分享链接中消失，且无法恢复。确定删除？')) return
  try {
    await deletePost(post.value.id)
    await router.push({ name: 'home' })
  } catch (e) {
    onActionError(e)
  }
}

/** 楼主的公开帖可转匿名（单向：转后从公开主页消失，且永不可再转回公开） */
const canMakePostAnonymous = computed(() => !!detail.value?.isAuthorOfPost && post.value?.author.mode === 'public')

async function onMakePostAnonymous() {
  if (!post.value) return
  if (!window.confirm('转为匿名后，该帖将立即从你的公开主页消失，且此后不可再改回公开。确定转为匿名？')) return
  try {
    await makePostAnonymous(post.value.id)
    await load()
  } catch (e) {
    onActionError(e)
  }
}

/** 该回复是否是我本人发布的公开回复（匿名回复无 username，天然不匹配） */
function isMyPublicReply(replyId: string): boolean {
  const me = auth.user
  const r = detail.value?.replies.find((x) => x.id === replyId)
  return !!me && !!r && r.author.mode === 'public' && r.author.username === me.username
}

async function onMakeReplyAnonymous(replyId: string) {
  if (!window.confirm('转为匿名后，该回复将立即从公开主页消失，且此后不可再改回公开。确定转为匿名？')) return
  try {
    await makeReplyAnonymous(replyId)
    await load()
  } catch (e) {
    onActionError(e)
  }
}

// ---------- 任务 5：举报 / 拉黑 ----------

/** 当前展开举报表单的目标：'post' 或回复 id；null 表示关闭 */
const reportingTarget = ref<'post' | string | null>(null)
const blockedAuthors = ref<Set<string>>(new Set())

/** 公开作者的用户名；匿名作者没有可拉黑的稳定身份，返回 null */
function publicAuthorName(): string | null {
  return post.value?.author.mode === 'public' ? post.value.author.username : null
}

const authorBlockable = computed(() => {
  const name = publicAuthorName()
  return !!name && name !== auth.user?.username
})
const authorBlocked = computed(() => {
  const name = publicAuthorName()
  return !!name && blockedAuthors.value.has(name)
})

async function loadBlocks() {
  if (!auth.isLoggedIn) return
  try {
    const list = await fetchMyBlocks()
    blockedAuthors.value = new Set(list.map((b) => b.username))
  } catch {
    // 拉黑状态只是按钮文案，加载失败不打扰
  }
}

onMounted(loadBlocks)

async function onToggleBlock() {
  const name = publicAuthorName()
  if (!name) return
  try {
    if (blockedAuthors.value.has(name)) {
      await unblockUser(name)
    } else {
      if (!window.confirm(`拉黑 @${name} 后，你将不再看到该用户以公开身份发布的内容，对方也收不到你的回复提醒。确定拉黑？`)) return
      await blockUser(name)
    }
    await loadBlocks()
  } catch (e) {
    onActionError(e)
  }
}

function onReportDone(message: string) {
  reportingTarget.value = null
  actionError.value = ''
  notice.value = message
}

const notice = ref('')
</script>

<template>
  <div>
    <p v-if="loading" class="empty">加载中…</p>

    <p v-else-if="notFound" class="empty">
      内容不存在或已被作者删除。<RouterLink to="/">返回首页</RouterLink>
    </p>

    <template v-else-if="post && detail">
      <p v-if="actionError" class="detail__error" @click="actionError = ''">{{ actionError }}</p>
      <p v-if="notice" class="detail__notice" @click="notice = ''">{{ notice }}</p>
      <p v-if="post.riskHint" class="detail__risk">
        这条内容可能被系统标记为求助信号。它不代表违规——如果你也是这样想的，请记得
        <RouterLink to="/rules">社区公约</RouterLink>
        里写着：你不必独自扛着，拨打心理援助热线 12356（24 小时）或联系学校心理中心。
      </p>
      <article class="card">
        <div class="detail__meta">
          <span class="badge badge--advice" :class="{
            'badge--vent': post.intent === 'VENT',
            'badge--companion': post.intent === 'COMPANION',
          }">{{ intentLabels[post.intent] }}</span>
          <AuthorDisplay :author="post.author" />
          <button v-if="authorBlockable" class="btn detail__block" @click="onToggleBlock">
            {{ authorBlocked ? '取消拉黑' : '拉黑作者' }}
          </button>
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
          <button class="btn detail__report" @click="reportingTarget = reportingTarget === 'post' ? null : 'post'">
            举报
          </button>
          <button v-if="detail.isAuthorOfPost" class="btn detail__close" @click="onToggleClosed">
            {{ post.commentsClosed ? '重新开放评论' : '关闭评论' }}
          </button>
          <button v-if="canMakePostAnonymous" class="btn detail__anon" @click="onMakePostAnonymous">转为匿名</button>
          <button v-if="detail.isAuthorOfPost" class="btn detail__delete" @click="onDeletePost">删除帖子</button>
        </div>
        <ReportDialog
          v-if="reportingTarget === 'post'"
          target-type="POST"
          :target-id="post.id"
          :target-label="post.title || post.body.slice(0, 30)"
          @done="onReportDone"
          @close="reportingTarget = null"
        />
        <p v-if="detail.isAuthorOfPost" class="muted detail__author-tip">
          你是楼主：可以标记某条回复“有帮助”、关闭评论、把公开帖转为匿名或删除本帖。
        </p>
      </article>

      <section class="card detail__replies">
        <h2>回复 {{ post.replyCount }}</h2>
        <p v-if="post.commentsClosed" class="muted">评论已被楼主关闭。</p>
        <ul v-if="detail.replies.length" class="detail__reply-list">
          <template v-for="r in detail.replies" :key="r.id">
            <ReplyItem
              :reply="r"
              :can-mark-helpful="detail.isAuthorOfPost"
              :can-make-anonymous="isMyPublicReply(r.id)"
              @toggle-helpful="onToggleHelpful"
              @make-anonymous="onMakeReplyAnonymous"
              @report="reportingTarget = reportingTarget === r.id ? null : r.id"
            />
            <ReportDialog
              v-if="reportingTarget === r.id"
              target-type="REPLY"
              :target-id="r.id"
              :target-label="r.body.slice(0, 30)"
              @done="onReportDone"
              @close="reportingTarget = null"
            />
          </template>
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

.detail__delete {
  color: #c62828;
  border-color: #e8b4ae;
}

.detail__anon {
  color: #c62828;
  border-color: #e8b4ae;
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

.detail__notice {
  background: #e8f5e9;
  color: #2e7d32;
  border-radius: 8px;
  padding: 0.5rem 0.8rem;
  font-size: 0.85rem;
  cursor: pointer;
}

/* 风险求助提示：温和底色，明确"不是违规"，并给出求助出口 */
.detail__risk {
  background: #fff8e1;
  border: 1px solid #f0d48a;
  color: #7a5c00;
  border-radius: 10px;
  padding: 0.6rem 0.85rem;
  font-size: 0.85rem;
  line-height: 1.7;
  margin-bottom: 0.75rem;
}

.detail__risk a {
  color: inherit;
  font-weight: 600;
}

.detail__block {
  font-size: 0.75rem;
  padding: 0.1rem 0.5rem;
  color: var(--color-text-muted);
}

.detail__report {
  color: var(--color-text-muted);
}

/* 回复列表内嵌的举报表单不是 li，去掉列表缩进 */
.detail__reply-list > div {
  padding: 0 0.4rem 0.6rem;
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
