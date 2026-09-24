import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchDicts, type Dicts } from '@/api'

/**
 * 固定字典（学校/专业/标签）来自后端 /api/dicts，全局只拉一次；
 * 未就绪时为空数组，组件渲染保持安静，加载完成后响应式补上。
 */
export const useDictStore = defineStore('dicts', () => {
  const schools = ref<Dicts['schools']>([])
  const majors = ref<Dicts['majors']>([])
  const tags = ref<Dicts['tags']>([])
  let loading: Promise<void> | null = null

  function ensureLoaded(): Promise<void> {
    if (!loading) {
      loading = fetchDicts().then((d) => {
        schools.value = d.schools
        majors.value = d.majors
        tags.value = d.tags
      })
    }
    return loading
  }

  function nameOf(list: Dicts['schools'], id: string | null): string | null {
    if (!id) return null
    return list.find((d) => d.id === id)?.name ?? null
  }

  return { schools, majors, tags, ensureLoaded, nameOf }
})
