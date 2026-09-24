<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import type { Post, ProfileSummary, Reply } from '@/types'
import { fetchProfile } from '@/api'
import { relativeTime } from '@/utils/time'
import PostCard from '@/components/PostCard.vue'

const route = useRoute()
const profile = ref<ProfileSummary | null>(null)
const profilePosts = ref<Post[]>([])
const profileReplies = ref<Reply[]>([])
const loading = ref(true)
const notFound = ref(false)
const tab = ref<'posts' | 'replies'>('posts')

watchEffect(async () => {
  loading.value = true
  const data = await fetchProfile(String(route.params.username))
  profile.value = data?.profile ?? null
  profilePosts.value = data?.posts ?? []
  profileReplies.value = data?.replies ?? []
  notFound.value = !data
  loading.value = false
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
      <section class="card profile__head">
        <div class="profile__avatar">{{ profile.nickname.slice(0, 1) }}</div>
        <div>
          <h1 class="profile__name">{{ profile.nickname }}</h1>
          <p class="muted">@{{ profile.username }}</p>
          <p v-if="profile.bio" class="profile__bio">{{ profile.bio }}</p>
        </div>
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
