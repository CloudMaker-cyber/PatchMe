<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { FeedStatus, Post } from '@/types'
import { intentLabels, tags as allTags, dictName } from '@/mock/dictionaries'
import { relativeTime } from '@/utils/time'
import AuthorDisplay from './AuthorDisplay.vue'

const props = defineProps<{ post: Post; status?: FeedStatus }>()

const tagNames = computed(() =>
  props.post.tagIds.map((id) => dictName(allTags, id)).filter((n): n is string => !!n),
)
const excerpt = computed(() =>
  props.post.body.length > 88 ? props.post.body.slice(0, 88) + '…' : props.post.body,
)
const intentClass = computed(
  () => ({ VENT: 'badge--vent', ADVICE: 'badge--advice', COMPANION: 'badge--companion' })[props.post.intent],
)
</script>

<template>
  <article class="card post-card">
    <div class="post-card__meta">
      <span class="badge" :class="intentClass">{{ intentLabels[post.intent] }}</span>
      <span v-if="status === 'UNANSWERED'" class="badge badge--unanswered">待回答</span>
      <span v-else-if="status === 'NEED_HELP'" class="badge badge--needhelp">待帮助</span>
      <AuthorDisplay :author="post.author" />
      <span class="muted">{{ relativeTime(post.createdAt) }}</span>
    </div>
    <h3 class="post-card__title">
      <RouterLink :to="`/posts/${post.id}`">{{ post.title || '（无标题）' }}</RouterLink>
    </h3>
    <p class="post-card__body">{{ excerpt }}</p>
    <div class="post-card__foot">
      <span class="post-card__tags">
        <span v-for="t in tagNames" :key="t" class="chip chip--static">#{{ t }}</span>
      </span>
      <span class="muted">回复 {{ post.replyCount }} · 支持 {{ post.supportCount }}</span>
    </div>
  </article>
</template>

<style scoped>
.post-card {
  position: relative;
  margin-bottom: 0.9rem;
}

.post-card__meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 0.4rem;
}

.post-card__title {
  margin: 0 0 0.3rem;
  font-size: 1.05rem;
}

.post-card__title a {
  color: var(--color-text);
  text-decoration: none;
}

/* 整卡可点：标题链接的覆盖层铺满卡片，作者主页链接靠 z-index 保持在层上可单独点击 */
.post-card__title a::after {
  content: '';
  position: absolute;
  inset: 0;
}

.post-card__title a:hover {
  color: var(--color-primary);
}

.post-card__body {
  margin: 0 0 0.6rem;
  font-size: 0.92rem;
  line-height: 1.7;
}

.post-card__foot {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
  justify-content: space-between;
}

/* 标签挨着靠左成组，统计信息单独靠右 */
.post-card__tags {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.chip--static {
  cursor: default;
  border: none;
  background: transparent;
  padding: 0;
}
</style>
