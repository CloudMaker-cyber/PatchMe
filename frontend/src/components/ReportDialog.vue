<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ReportReason } from '@/types'
import { ApiError, submitReport } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const props = defineProps<{
  targetType: 'POST' | 'REPLY'
  targetId: string
  /** 展示用：被举报内容的简短描述（标题或正文摘录） */
  targetLabel: string
}>()

const emit = defineEmits<{ done: [message: string]; close: [] }>()

const auth = useAuthStore()
const router = useRouter()

/** 五种固定原因（docs/v3/01），文案与后端枚举 display() 一致 */
const reasons: Array<{ value: ReportReason; label: string }> = [
  { value: 'HARASSMENT', label: '骚扰辱骂' },
  { value: 'SPAM', label: '广告诈骗' },
  { value: 'PRIVACY', label: '泄露隐私' },
  { value: 'DANGER', label: '危险内容' },
  { value: 'OTHER', label: '其他' },
]

const selected = ref<ReportReason | ''>('')
const note = ref('')
const submitting = ref(false)
const error = ref('')

const canSubmit = computed(() => selected.value !== '' && !submitting.value)

async function onSubmit() {
  if (!selected.value) return
  submitting.value = true
  error.value = ''
  try {
    await submitReport({
      targetType: props.targetType,
      targetId: props.targetId,
      reason: selected.value,
      note: note.value.trim() || undefined,
    })
    emit('done', '举报已提交，等待管理员核实。举报人身份不会向对方透露。')
  } catch (e) {
    if (e instanceof ApiError && e.code === '40100') {
      auth.requireLogin(router, e)
      emit('close')
      return
    }
    error.value = e instanceof ApiError ? e.message : '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="report" @click.stop>
    <p class="report__target">举报内容：{{ targetLabel }}</p>
    <p v-if="error" class="report__error" @click="error = ''">{{ error }}</p>
    <div class="report__reasons">
      <label v-for="r in reasons" :key="r.value" class="report__radio">
        <input v-model="selected" type="radio" name="report-reason" :value="r.value" />
        <span>{{ r.label }}</span>
      </label>
    </div>
    <textarea
      v-model="note"
      rows="2"
      maxlength="300"
      placeholder="补充说明（可选，仅管理员可见）"
      aria-label="举报补充说明"
    />
    <div class="report__foot">
      <button class="btn" @click="$emit('close')">取消</button>
      <button class="btn btn--primary" :disabled="!canSubmit" @click="onSubmit">
        {{ submitting ? '提交中…' : '提交举报' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.report {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 0.75rem;
  margin-top: 0.5rem;
  background: var(--color-surface, #fff);
}

.report__target {
  margin: 0 0 0.5rem;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.report__error {
  background: #fdecea;
  color: #c62828;
  border-radius: 8px;
  padding: 0.4rem 0.6rem;
  font-size: 0.82rem;
  cursor: pointer;
}

.report__reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-bottom: 0.5rem;
}

.report__radio {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
}

.report textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.4rem 0.55rem;
  font-family: inherit;
  font-size: 0.85rem;
}

.report__foot {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
</style>
