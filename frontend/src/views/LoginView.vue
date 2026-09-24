<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref('')

const canSubmit = computed(() => email.value.trim() && password.value.length >= 8 && !submitting.value)

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  error.value = ''
  try {
    await auth.login(email.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    void router.replace(redirect)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="card auth">
    <h1>登录</h1>
    <p class="muted">欢迎回来。登录后发帖、回复与支持才会记在你的账号上。</p>

    <form class="auth__form" @submit.prevent="submit">
      <label>
        <span>邮箱</span>
        <input v-model="email" type="email" autocomplete="email" required />
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" required minlength="8" />
      </label>
      <p v-if="error" class="auth__error">{{ error }}</p>
      <button class="btn btn--primary" type="submit" :disabled="!canSubmit">
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </form>

    <p class="muted auth__foot">
      还没有账号？<RouterLink to="/register">注册一个</RouterLink>
    </p>
  </section>
</template>

<style scoped>
.auth h1 {
  margin-top: 0;
  font-size: 1.2rem;
}

.auth__form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  margin-top: 1rem;
}

.auth__form label span {
  display: block;
  font-size: 0.85rem;
  margin-bottom: 0.3rem;
}

.auth__form input {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 0.5rem 0.6rem;
  font-size: 0.9rem;
}

.auth__error {
  color: #c62828;
  font-size: 0.85rem;
  margin: 0;
}

.auth__form .btn {
  padding: 0.6rem;
}

.auth__foot {
  margin-top: 1rem;
  font-size: 0.85rem;
}
</style>
