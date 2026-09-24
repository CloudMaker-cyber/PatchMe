<script setup lang="ts">
import { intentOptions, majors, schools, tags } from '@/mock/dictionaries'
import { useFeedFilterStore } from '@/stores/feedFilters'
import type { Intent } from '@/types'

/**
 * 筛选 UI：学校/专业/意图单选，标签多选（任一匹配）；维度之间取交集。
 * 变更即写 URL（emit change → HomeView 同步 query 并刷新列表）。
 */
const emit = defineEmits<{ change: [] }>()
const filters = useFeedFilterStore()

function toggleTag(id: string) {
  const idx = filters.tags.indexOf(id)
  if (idx >= 0) filters.tags.splice(idx, 1)
  else filters.tags.push(id)
  emit('change')
}

function setSchool(e: Event) {
  filters.school = (e.target as HTMLSelectElement).value || null
  emit('change')
}

function setMajor(e: Event) {
  filters.major = (e.target as HTMLSelectElement).value || null
  emit('change')
}

function setIntent(v: Intent | null) {
  filters.intent = filters.intent === v ? null : v
  emit('change')
}

function reset() {
  filters.reset()
  emit('change')
}
</script>

<template>
  <section class="card filter-bar">
    <div class="filter-bar__row">
      <select :value="filters.school ?? ''" @change="setSchool" aria-label="按学校筛选">
        <option value="">全部学校</option>
        <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <select :value="filters.major ?? ''" @change="setMajor" aria-label="按专业方向筛选">
        <option value="">全部专业方向</option>
        <option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option>
      </select>
      <button v-if="filters.hasAny()" class="btn filter-bar__reset" @click="reset">清空筛选</button>
    </div>

    <div class="filter-bar__row">
      <button
        v-for="o in intentOptions"
        :key="o.value"
        type="button"
        class="chip"
        :class="{ 'chip--active': filters.intent === o.value }"
        @click="setIntent(o.value)"
      >{{ o.label }}</button>
    </div>

    <div class="filter-bar__row filter-bar__tags">
      <button
        v-for="t in tags"
        :key="t.id"
        type="button"
        class="chip"
        :class="{ 'chip--active': filters.tags.includes(t.id) }"
        @click="toggleTag(t.id)"
      >#{{ t.name }}</button>
    </div>
  </section>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 1rem;
  padding: 0.85rem 1rem;
}

.filter-bar__row {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex-wrap: wrap;
  margin-bottom: 0.5rem;
}

.filter-bar__row:last-child {
  margin-bottom: 0;
}

.filter-bar__tags {
  row-gap: 0.35rem;
}

.filter-bar select {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.3rem 0.5rem;
  font-size: 0.85rem;
  color: var(--color-text);
  background: var(--color-surface);
}

.filter-bar__reset {
  margin-left: auto;
}
</style>
