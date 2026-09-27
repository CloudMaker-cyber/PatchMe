<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Post, ProfileSummary, Reply } from '@/types'
import { ApiError, blockUser, fetchMyBlocks, fetchProfile, unblockUser } from '@/api'
import { relativeTime } from '@/utils/time'
import { useAuthStore } from '@/stores/auth'
import PostCard from '@/components/PostCard.vue'

const route = useRoute()
const auth = useAuthStore()
const router = useRouter()
const profile = ref<ProfileSummary | null>(null)
const profilePosts = ref<Post[]>([])
const profileReplies = ref<Reply[]>([])
const loading = ref(true)
const notFound = ref(false)
const tab = ref<'posts' | 'replies'>('posts')
const actionError = ref('')

/** 被拉黑集合驱动按钮文案；主页作者就是自己时不显示按钮 */
const blockedSet = ref<Set<string>>(new Set())
const isSelf = computed(() => !!auth.user && auth.user.username === String(route.params.username))
const isBlocked = computed(() => blockedSet.value.has(String(route.params.username)))

async function loadBlocks() {
  if (!auth.isLoggedIn) return
  try {
    blockedSet.value = new Set((await fetchMyBlocks()).map((b) => b.username))
  } catch {
    /* 只影响按钮文案，失败静默 */
  }
}

onMounted(loadBlocks)

async function onToggleBlock() {
  const username = String(route.params.username)
  try {
    if (isBlocked.value) {
      await unblockUser(username)
    } else {
      if (!window.confirm(`拉黑 @${username} 后，你将不再看到该用户以公开身份发布的内容。确定拉黑？`)) return
      await blockUser(username)
    }
    await loadBlocks()
  } catch (e) {
    if (auth.requireLogin(router, e)) return
    actionError.value = e instanceof ApiError ? e.message : '操作失败'
  }
}

watchEffect(async () => {
  loading.value = true
  const data = await fetchProfile(String(route.params.username))
  profile.value = data?.profile ?? null
  profilePosts.value = data?.posts ?? []
  profileReplies.value = data?.replies ?? []
  notFound.value = !data
  loading.value = false
  // 换一个主页就重查拉黑态（未登录时 fetchMyBlocks 会 401，loadBlocks 内部已挡）
  await loadBlocks()
})

const empty = computed(() =>
  tab.value === 'posts' ? profilePosts.value.length === 0 : profileReplies.value.length === 0,
)
</script>

<template>
  <div>
    <p v-if="loading" class="empty">加载中…</p>
    <p v-else-if="notFound" class="empty">
      该主页不存在。<RouterLink to="/">返回首页</RouterLink>
    </p>

    <template v-else-if="profile">
      <p v-if="actionError" class="profile__error" @click="actionError = ''">{{ actionError }}</p>
      <section class="card profile__head">
        <div class="profile__avatar">{{ profile.nickname.slice(0, 1) }}</div>
        <div class="profile__head-info">
          <h1 class="profile__name">{{ profile.nickname }}</h1>
          <p class="muted">@{{ profile.username }}</p>
          <p v-if="profile.bio" class="profile__bio">{{ profile.bio }}</p>
        </div>
        <button v-if="auth.isLoggedIn && !isSelf" class="btn profile__block" @click="onToggleBlock">
          {{ isBlocked ? '取消拉黑' : '拉黑' }}
        </button>
      </section>

      <p class="muted profile__notice">
        公开主页只展示该用户当前以公开昵称发布的内容。
      </p>

      <div class="profile__tabs">
        <button class="btn" :class="{ 'btn--primary': tab === 'posts' }" @click="tab = 'posts'">
          帖子 {{ profilePosts.length }}
        </button>
        <button class="btn" :class="{ 'btn--primary': tab === 'replies' }" @click="tab = 'replies'">
          回复 {{ profileReplies.length }}
        </button>
      </div>

      <template v-if="!empty">
        <template v-if="tab === 'posts'">
          <PostCard v-for="p in profilePosts" :key="p.id" :post="p" />
        </template>
        <ul v-else class="profile__reply-list">
          <li v-for="r in profileReplies" :key="r.id" class="card profile__reply">
            <RouterLink :to="`/posts/${r.postId}`" class="profile__reply-link">回到原帖 →</RouterLink>
            <p class="muted">{{ relativeTime(r.createdAt) }}</p>
            <p>“{{ r.body }}”</p>
          </li>
        </ul>
      </template>
      <p v-else class="empty">这里还很安静。</p>
    </template>
  </div>
</template>

<style scoped>
.profile__head {
  display: flex;
  gap: 0.9rem;
  align-items: center;
}

.profile__avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.3rem;
  flex-shrink: 0;
}

.profile__name {
  margin: 0;
  font-size: 1.15rem;
}

.profile__head-info {
  flex: 1;
  min-width: 0;
}

.profile__block {
  flex: none;
  font-size: 0.8rem;
  padding: 0.2rem 0.6rem;
  color: var(--color-text-muted);
}

.profile__error {
  background: #fdecea;
  color: #c62828;
  border-radius: 8px;
  padding: 0.5rem 0.8rem;
  font-size: 0.85rem;
  cursor: pointer;
}

.profile__bio {
  font-size: 0.9rem;
  margin: 0.3rem 0 0;
}

.profile__notice {
  margin: 0.7rem 0.2rem;
}

.profile__tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.9rem;
}

.profile__reply-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.profile__reply {
  margin-bottom: 0.8rem;
  font-size: 0.92rem;
}

.profile__reply p {
  margin: 0.3rem 0 0;
}

.profile__reply-link {
  color: var(--color-primary);
  text-decoration: none;
  font-size: 0.85rem;
}
</style>
