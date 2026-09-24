<script setup lang="ts">
import type { AuthorInfo } from '@/types'
import { RouterLink } from 'vue-router'

/**
 * 作者展示组件 —— 匿名/公开差异的唯一渲染出口：
 * anonymous 不渲染任何可点击元素（验收：匿名无主页入口）。
 */
defineProps<{ author: AuthorInfo }>()
</script>

<template>
  <span v-if="author.mode === 'anonymous'" class="identity--anon">匿名</span>
  <RouterLink v-else class="author-link" :to="`/u/${author.username}`">
    {{ author.nickname }}
  </RouterLink>
</template>
