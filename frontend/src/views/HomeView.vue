<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchHealth } from '@/api/health'

type HealthState = 'checking' | 'up' | 'down'

const health = ref<HealthState>('checking')

onMounted(async () => {
  try {
    const data = await fetchHealth()
    health.value = data.status === 'UP' ? 'up' : 'down'
  } catch {
    health.value = 'down'
  }
})
</script>

<template>
  <section class="placeholder">
    <h1>首页 · 待回答流</h1>
    <p class="placeholder__note">任务 1 将实现真实列表与筛选，当前为工程基线占位页。</p>
    <p class="placeholder__health">
      后端健康检查：
      <strong v-if="health === 'checking'">检测中…</strong>
      <strong v-else-if="health === 'up'" class="is-up">已连接（UP）</strong>
      <strong v-else class="is-down">未连接（请确认后端已在 8080 端口启动）</strong>
    </p>
  </section>
</template>

<style scoped>
.placeholder {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 1.5rem;
}

.placeholder h1 {
  margin-top: 0;
  font-size: 1.25rem;
}

.placeholder__note,
.placeholder__health {
  color: var(--color-text-muted);
}

.is-up {
  color: #2e7d32;
}

.is-down {
  color: #c62828;
}
</style>
