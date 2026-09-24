import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Intent, PostFilters } from '@/types'

/**
 * 首页筛选状态。真实值同步在 URL query（可分享/刷新不丢），
 * store 负责在组件间提供同一份读写入口。
 */
export const useFeedFilterStore = defineStore('feedFilters', () => {
  const school = ref<string | null>(null)
  const major = ref<string | null>(null)
  const intent = ref<Intent | null>(null)
  const tags = ref<string[]>([])

  function current(): PostFilters {
    return {
      school: school.value,
      major: major.value,
      intent: intent.value,
      tags: [...tags.value],
    }
  }

  function patch(next: Partial<PostFilters>) {
    if (next.school !== undefined) school.value = next.school
    if (next.major !== undefined) major.value = next.major
    if (next.intent !== undefined) intent.value = next.intent
    if (next.tags !== undefined) tags.value = [...next.tags]
  }

  /** 从 URL query 恢复（返回 false 表示与当前值相同，无需触发刷新） */
  function restoreFromQuery(q: Record<string, string | string[] | undefined>): boolean {
    const nextTags = Array.isArray(q.tags) ? q.tags : q.tags ? [q.tags] : []
    const same =
      (q.school || null) === school.value &&
      (q.major || null) === major.value &&
      (q.intent || null) === intent.value &&
      nextTags.join(',') === tags.value.join(',')
    if (same) return false
    patch({
      school: (q.school as string) || null,
      major: (q.major as string) || null,
      intent: (q.intent as Intent) || null,
      tags: nextTags,
    })
    return true
  }

  function reset() {
    patch({ school: null, major: null, intent: null, tags: [] })
  }

  function hasAny(): boolean {
    return !!(school.value || major.value || intent.value || tags.value.length)
  }

  return { school, major, intent, tags, current, patch, restoreFromQuery, reset, hasAny }
})
