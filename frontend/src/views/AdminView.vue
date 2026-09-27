<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { AdminAuditItem, AdminReportItem, ModerationLadderAction } from '@/types'
import { ApiError, fetchAdminAudit, fetchAdminQueue, moderateUser, reviewReport } from '@/api'
import { relativeTime } from '@/utils/time'

/**
 * 审核后台（仅 ADMIN 可见入口；数据门禁在后端 /api/admin/** hasRole('ADMIN')）。
 * 三条铁律的界面表达：
 * 1) 队列只呈现线索，处置必须逐单人工核实（没有"批量处罚"入口）；
 * 2) 所有操作都会进审计列表，可回溯；
 * 3) 界面不出现、也拿不到举报人身份（后端出口就没有该字段）。
 */

const queue = ref<AdminReportItem[]>([])
const audit = ref<AdminAuditItem[]>([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const statusFilter = ref<'' | 'CONFIRMED' | 'REJECTED'>('')

/** 展开处理表单的举报单 id（同一时刻只展开一条，避免误点） */
const reviewingId = ref<string | null>(null)
const reviewReason = ref('')
const reviewTakedown = ref(false)

/** 阶梯处置表单 */
const ladderUserId = ref('')
const ladderAction = ref<ModerationLadderAction>('WARN')
const ladderReason = ref('')

const ladderOptions: Array<{ value: ModerationLadderAction; label: string }> = [
  { value: 'WARN', label: '提醒' },
  { value: 'OBSERVE', label: '观察' },
  { value: 'RESTRICT', label: '限制发布' },
  { value: 'BAN', label: '封禁' },
  { value: 'UNBAN', label: '解除限制' },
]

function showError(e: unknown) {
  error.value = e instanceof ApiError ? e.message : '操作失败，请稍后重试'
}

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const [q, a] = await Promise.all([
      fetchAdminQueue(statusFilter.value || undefined),
      fetchAdminAudit(),
    ])
    queue.value = q
    audit.value = a
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

onMounted(reload)

function openReview(item: AdminReportItem, takedownDefault: boolean) {
  reviewingId.value = item.id
  reviewReason.value = ''
  reviewTakedown.value = takedownDefault
}

async function onReview(item: AdminReportItem, confirm: boolean) {
  try {
    await reviewReport(item.id, confirm, reviewReason.value.trim(), confirm ? reviewTakedown.value : false)
    notice.value = `举报 #${item.id} 已${confirm ? '确认违规' : '驳回'}。`
    reviewingId.value = null
    await reload()
  } catch (e) {
    showError(e)
  }
}

async function onLadder() {
  if (!ladderUserId.value.trim()) {
    error.value = '请填写目标用户 id（来自举报单的作者列）'
    return
  }
  const label = ladderOptions.find((o) => o.value === ladderAction.value)?.label ?? ladderAction.value
  if (!window.confirm(`确定对用户 #${ladderUserId.value.trim()} 执行「${label}」？该操作会写入审计日志并通知本人。`)) return
  try {
    await moderateUser(ladderAction.value, ladderUserId.value.trim(), ladderReason.value.trim())
    notice.value = `已对用户 #${ladderUserId.value.trim()} 执行「${label}」。`
    ladderReason.value = ''
    await reload()
  } catch (e) {
    showError(e)
  }
}
</script>

<template>
  <div class="admin">
    <header class="admin__header card">
      <h1>审核后台</h1>
      <p class="muted">
        举报只是线索：任何处置都必须由你逐单核实后手动执行；系统永远不会因举报数量自动处罚任何人。
      </p>
      <div class="admin__filter">
        <button
          v-for="f in [
            { value: '', label: '待处理' },
            { value: 'CONFIRMED', label: '已确认' },
            { value: 'REJECTED', label: '已驳回' },
          ]"
          :key="f.value"
          class="chip"
          :class="{ 'chip--active': statusFilter === f.value }"
          @click="statusFilter = f.value as typeof statusFilter; reload()"
        >{{ f.label }}</button>
      </div>
    </header>

    <p v-if="error" class="admin__error" @click="error = ''">{{ error }}</p>
    <p v-if="notice" class="admin__notice" @click="notice = ''">{{ notice }}</p>
    <p v-if="loading" class="empty">加载中…</p>

    <section class="card admin__section">
      <h2>举报队列（{{ queue.length }}）</h2>
      <p v-if="queue.length === 0" class="empty">没有待办举报。</p>
      <article v-for="item in queue" :key="item.id" class="admin__item">
        <div class="admin__item-head">
          <span class="badge" :class="item.source === 'SYSTEM' ? 'admin__badge--system' : 'admin__badge--user'">
            {{ item.source === 'SYSTEM' ? '系统风控' : '用户举报' }}
          </span>
          <span class="badge">{{ item.reasonDisplay }}</span>
          <span class="muted">{{ item.statusDisplay }} · {{ relativeTime(item.createdAt) }}</span>
          <span class="muted admin__author">
            作者 #{{ item.authorId }}
            <template v-if="item.authorIdentityMode === 'ANONYMOUS'">（匿名发布，处置与通知不会暴露线索来源）</template>
          </span>
        </div>
        <p class="admin__target">
          {{ item.targetType === 'POST' ? '帖子' : '回复' }} #{{ item.targetId }}：
          {{ item.targetTitle || '（无标题）' }} —— {{ item.targetExcerpt || '（无正文）' }}
        </p>
        <p v-if="item.note" class="admin__note">备注：{{ item.note }}</p>
        <div class="admin__item-actions">
          <button class="btn admin__confirm" @click="openReview(item, true)">确认违规…</button>
          <button class="btn" @click="onReview(item, false)">不予处理</button>
        </div>
        <div v-if="reviewingId === item.id" class="admin__review-form">
          <input v-model="reviewReason" maxlength="300" placeholder="结论说明（写入审计，不对外展示）" />
          <label class="admin__takedown">
            <input v-model="reviewTakedown" type="checkbox" />
            <span>同时软下架该内容</span>
          </label>
          <div class="admin__review-foot">
            <button class="btn" @click="reviewingId = null">取消</button>
            <button class="btn admin__confirm" @click="onReview(item, true)">提交核实结论</button>
          </div>
        </div>
      </article>
    </section>

    <section class="card admin__section">
      <h2>渐进式限制（对账户执行，逐级升级或解除）</h2>
      <div class="admin__ladder">
        <input v-model="ladderUserId" class="admin__ladder-id" placeholder="目标用户 id" />
        <select v-model="ladderAction">
          <option v-for="o in ladderOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
        <input v-model="ladderReason" maxlength="300" placeholder="原因（会通知本人）" />
        <button class="btn btn--primary" @click="onLadder">执行</button>
      </div>
      <p class="muted admin__tip">限制发布/封禁会实际拦截写操作；封禁同时拒绝登录。解除限制恢复为正常状态。</p>
    </section>

    <section class="card admin__section">
      <h2>操作审计（最近 100 条）</h2>
      <p v-if="audit.length === 0" class="empty">还没有审核操作。</p>
      <ul class="admin__audit-list">
        <li v-for="a in audit" :key="a.id" class="admin__audit-item">
          <span class="admin__audit-who">{{ a.adminUsername }}</span>
          <span class="badge">{{ a.actionDisplay }}</span>
          <span>{{ a.targetType === 'USER' ? `用户 #${a.targetId}` : `举报 #${a.targetId}` }}</span>
          <span v-if="a.reason" class="muted">（{{ a.reason }}）</span>
          <span class="muted admin__audit-time">{{ relativeTime(a.createdAt) }}</span>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.admin__header {
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}

.admin__header h1 {
  margin: 0 0 0.35rem;
  font-size: 1.3rem;
}

.admin__header p {
  margin: 0;
  font-size: 0.85rem;
}

.admin__filter {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.75rem;
  flex-wrap: wrap;
}

.admin__error {
  background: #fdecea;
  color: #c62828;
  border-radius: 8px;
  padding: 0.5rem 0.8rem;
  font-size: 0.85rem;
  cursor: pointer;
}

.admin__notice {
  background: #e8f5e9;
  color: #2e7d32;
  border-radius: 8px;
  padding: 0.5rem 0.8rem;
  font-size: 0.85rem;
  cursor: pointer;
}

.admin__section {
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}

.admin__section h2 {
  font-size: 1rem;
  margin: 0 0 0.75rem;
}

.admin__item {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 0.7rem 0.85rem;
  margin-bottom: 0.7rem;
}

.admin__item-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  font-size: 0.82rem;
}

.admin__badge--system {
  background: #ede7f6;
  color: #5e35b1;
}

.admin__badge--user {
  background: #e3f2fd;
  color: #1565c0;
}

.admin__author {
  font-size: 0.78rem;
}

.admin__target {
  margin: 0.45rem 0 0;
  font-size: 0.88rem;
}

.admin__note {
  margin: 0.3rem 0 0;
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

.admin__item-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.55rem;
}

.admin__confirm {
  color: #c62828;
  border-color: #e8b4ae;
}

.admin__review-form {
  margin-top: 0.55rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.admin__review-form input {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.35rem 0.55rem;
  font-family: inherit;
  font-size: 0.85rem;
}

.admin__takedown {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.83rem;
}

.admin__review-foot {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.admin__ladder {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  align-items: center;
}

.admin__ladder-id {
  width: 7rem;
}

.admin__ladder input,
.admin__ladder select {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.35rem 0.55rem;
  font-family: inherit;
  font-size: 0.85rem;
}

.admin__tip {
  font-size: 0.78rem;
  margin: 0.5rem 0 0;
}

.admin__audit-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.admin__audit-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  font-size: 0.83rem;
  padding: 0.4rem 0;
  border-bottom: 1px solid var(--color-border);
}

.admin__audit-item:last-child {
  border-bottom: none;
}

.admin__audit-who {
  font-weight: 600;
}

.admin__audit-time {
  margin-left: auto;
  font-size: 0.76rem;
}
</style>
