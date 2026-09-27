<script setup lang="ts">
import type { Reply } from '@/types'
import { relativeTime } from '@/utils/time'
import AuthorDisplay from './AuthorDisplay.vue'

defineProps<{
  reply: Reply
  /** 当前（模拟）用户是否为楼主：只有楼主能看到并操作“有帮助” */
  canMarkHelpful: boolean
  /** 当前用户是否为该回复作者且回复为公开：只有本人能把公开回复转匿名 */
  canMakeAnonymous: boolean
}>()

defineEmits<{ toggleHelpful: [replyId: string]; makeAnonymous: [replyId: string] }>()
</script>

<template>
  <li class="reply" :class="{ 'reply--helpful': reply.isHelpful }">
    <div class="reply__meta">
      <AuthorDisplay :author="reply.author" />
      <span class="muted">{{ relativeTime(reply.createdAt) }}</span>
      <span v-if="reply.isHelpful" class="reply__helpful-tag">有帮助 ✓</span>
      <button
        v-else-if="canMarkHelpful"
        class="btn reply__helpful-btn"
        @click="$emit('toggleHelpful', reply.id)"
      >标记有帮助</button>
      <button
        v-if="canMakeAnonymous"
        class="btn reply__anon-btn"
        @click="$emit('makeAnonymous', reply.id)"
      >转为匿名</button>
    </div>
    <p class="reply__body">{{ reply.body }}</p>
  </li>
</template>

<style scoped>
.reply {
  list-style: none;
  padding: 0.7rem 0;
  border-bottom: 1px solid var(--color-border);
}

.reply:last-child {
  border-bottom: none;
}

.reply--helpful {
  background: #f6fbf5;
  border-radius: 8px;
  padding: 0.7rem;
  border-bottom: none;
}

.reply__meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.25rem;
}

.reply__helpful-tag {
  color: #2e7d32;
  font-size: 0.8rem;
  font-weight: 600;
}

.reply__helpful-btn {
  margin-left: auto;
  font-size: 0.75rem;
  padding: 0.15rem 0.55rem;
}

.reply__anon-btn {
  margin-left: auto;
  font-size: 0.75rem;
  padding: 0.15rem 0.55rem;
  color: #c62828;
  border-color: #e8b4ae;
}

/* 两个按钮同时出现时并排靠右，避免两个 auto 把间距撑开 */
.reply__helpful-btn + .reply__anon-btn {
  margin-left: 0.4rem;
}

.reply__body {
  margin: 0;
  font-size: 0.92rem;
  line-height: 1.7;
}
</style>
