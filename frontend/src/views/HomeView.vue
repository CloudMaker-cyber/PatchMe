<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Post } from '@/types'
import { fetchFeed, fetchResolved } from '@/mock/api'
import { useFeedFilterStore } from '@/stores/feedFilters'
import FilterBar from '@/components/FilterBar.vue'
import PostCard from '@/components/PostCard.vue'

const route = useRoute()
const router = useRouter()
const filters = useFeedFilterStore()

const feed = ref<Post[]>([])
const resolved = ref<Post[]>([])
const showResolved = ref(false)
const loading = ref(true)

function queryOf() {
  const q: Record<string, string | string[]> = {}
  if (filters.school) q.school = filters.school
  if (filters.major) q.major = filters.major
  if (filters.intent) q.intent = filters.intent
  if (filters.tags.length) q.tags = [...filters.tags]
  return q
}

async function reload() {
  loading.value = true
  const f = filters.current()
  feed.value = await fetchFeed(f)
  resolved.value = await fetchResolved(f)
  loading.value = false
}

function syncUrlAndReload() {
  router.replace({ path: '/', query: queryOf() })
  void reload()
}

onMounted(() => {
  filters.restoreFromQuery(route.query as Record<string, string | string[] | undefined>)
  void reload()
})

// 浏览器前进/后退时从 URL 恢复筛选
watch(
  () => route.query,
  (q) => {
    if (route.path !== '/') return
    if (filters.restoreFromQuery(q as Record<string, string | string[] | undefined>)) void reload()
  },
)
</script>

<template>
  <div>
    <FilterBar @change="syncUrlAndReload" />

    <p v-if="loading" class="empty">加载中…</p>
    <template v-else>
      <PostCard v-for="p in feed" :key="p.id" :post="p" :status="p.replyCount === 0 ? 'UNANSWERED' : 'NEED_HELP'" />
      <p v-if="feed.length === 0" class="empty">
        没有符合条件的待回答内容。<br />换个筛选，或者<a href="/post">发第一帖</a>。
      </p>
    </template>

    <section v-if="resolved.length" class="resolved">
      <button class="btn resolved__toggle" @click="showResolved = !showResolved">
        {{ showResolved ? '收起' : '查看' }}已获得帮助（{{ resolved.length }}）
      </button>
      <template v-if="showResolved">
        <PostCard v-for="p in resolved" :key="p.id" :post="p" />
      </template>
    </section>
  </div>
</template>

<style scoped>
.resolved {
  margin-top: 1.2rem;
  border-top: 1px dashed var(--color-border);
  padding-top: 0.9rem;
}

.resolved__toggle {
  margin-bottom: 0.8rem;
}
</style>
