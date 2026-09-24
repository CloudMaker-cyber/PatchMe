<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { IdentityMode, Intent } from '@/types'
import { intentOptions, majors, schools, tags } from '@/mock/dictionaries'
import { createPost } from '@/mock/api'

const router = useRouter()

const intent = ref<Intent | ''>('')
const body = ref('')
const title = ref('')
const schoolId = ref('')
const majorId = ref('')
const tagIds = ref<string[]>([])
/** 首次默认匿名（docs/v3/01） */
const identity = ref<IdentityMode>('ANONYMOUS')
const submitting = ref(false)

const errors = computed(() => {
  const e: string[] = []
  if (!intent.value) e.push('请选择帖子意图')
  const b = body.value.trim()
  if (b.length < 10) e.push('正文至少 10 个字')
  if (b.length > 2000) e.push('正文不能超过 2000 字')
  if (title.value.length > 50) e.push('标题不能超过 50 字')
  if (tagIds.value.length > 3) e.push('标签最多选 3 个')
  return e
})

function toggleTag(id: string) {
  const idx = tagIds.value.indexOf(id)
  if (idx >= 0) tagIds.value.splice(idx, 1)
  else if (tagIds.value.length < 3) tagIds.value.push(id)
}

async function submit() {
  if (errors.value.length || submitting.value) return
  submitting.value = true
  const id = await createPost({
    intent: intent.value as Intent,
    title: title.value,
    body: body.value,
    schoolId: schoolId.value || null,
    majorId: majorId.value || null,
    tagIds: tagIds.value,
    identityMode: identity.value,
  })
  void router.push(`/posts/${id}`)
}
</script>

<template>
  <section class="card create">
    <h1>发帖</h1>
    <p class="muted create__tip">
      说出你的困扰。其他用户会看到你的内容——{{ identity === 'ANONYMOUS' ? '以“匿名”身份' : '以“小满”公开身份' }}。
    </p>

    <fieldset class="create__group">
      <legend>帖子意图 <em>*</em></legend>
      <button
        v-for="o in intentOptions"
        :key="o.value"
        type="button"
        class="chip"
        :class="{ 'chip--active': intent === o.value }"
        @click="intent = o.value"
      >{{ o.label }}</button>
    </fieldset>

    <label class="create__group">
      <span>正文 <em>*</em>（{{ body.trim().length }}/2000）</span>
      <textarea v-model="body" rows="6" placeholder="发生了什么事？你希望得到倾听、建议，还是同路人？" />
    </label>

    <label class="create__group">
      <span>标题（可选）</span>
      <input v-model="title" maxlength="60" placeholder="一句话概括，可留空" />
    </label>

    <div class="create__row">
      <label class="create__group create__group--half">
        <span>学校（可选，仅用于他人筛选）</span>
        <select v-model="schoolId">
          <option value="">不显示</option>
          <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>
      <label class="create__group create__group--half">
        <span>专业方向（可选）</span>
        <select v-model="majorId">
          <option value="">不限</option>
          <option v-for="m in majors" :key="m.id" :value="m.id">{{ m.name }}</option>
        </select>
      </label>
    </div>

    <fieldset class="create__group">
      <legend>话题标签（可选，最多 3 个）</legend>
      <button
        v-for="t in tags"
        :key="t.id"
        type="button"
        class="chip"
        :class="{ 'chip--active': tagIds.includes(t.id) }"
        @click="toggleTag(t.id)"
      >#{{ t.name }}</button>
    </fieldset>

    <fieldset class="create__group">
      <legend>以什么身份发布</legend>
      <label class="create__radio">
        <input v-model="identity" type="radio" value="ANONYMOUS" />
        <span>匿名（默认，不显示任何账号信息）</span>
      </label>
      <label class="create__radio">
        <input v-model="identity" type="radio" value="PUBLIC" />
        <span>公开昵称“小满”（可进入我的公开主页）</span>
      </label>
    </fieldset>

    <ul v-if="errors.length" class="create__errors">
      <li v-for="e in errors" :key="e">{{ e }}</li>
    </ul>

    <button class="btn btn--primary create__submit" :disabled="!!errors.length || submitting" @click="submit">
      {{ submitting ? '发布中…' : '发布' }}
    </button>
  </section>
</template>

<style scoped>
.create h1 {
  margin-top: 0;
  font-size: 1.2rem;
}

.create__tip {
  margin-bottom: 1rem;
}

.create__group {
  display: block;
  border: none;
  padding: 0;
  margin: 0 0 1rem;
}

.create__group > span {
  display: block;
  font-size: 0.85rem;
  margin-bottom: 0.35rem;
}

.create__group em {
  color: #c62828;
  font-style: normal;
}

.create__group input:not([type="radio"]),
.create__group textarea,
.create__group select {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.5rem 0.6rem;
  font-size: 0.9rem;
  font-family: inherit;
  color: var(--color-text);
  background: var(--color-surface);
}

.create__group legend {
  font-size: 0.85rem;
  margin-bottom: 0.35rem;
  padding: 0;
}

.create__group .chip {
  margin: 0 0.4rem 0.4rem 0;
}

.create__row {
  display: flex;
  gap: 0.8rem;
}

.create__group--half {
  flex: 1;
}

.create__radio {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.9rem;
  margin-bottom: 0.3rem;
}

.create__errors {
  color: #c62828;
  font-size: 0.85rem;
  margin: 0 0 0.8rem;
  padding-left: 1.2rem;
}

.create__submit {
  width: 100%;
  padding: 0.6rem;
  font-size: 1rem;
}
</style>
