<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, register } from '@/api'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const username = ref('')
const nickname = ref('')
const submitting = ref(false)
const error = ref('')

const errors = computed(() => {
  const e: string[] = []
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) e.push('请填写合法邮箱')
  if (password.value.length < 8) e.push('密码至少 8 位')
  if (!/^[a-z0-9_]{4,20}$/.test(username.value)) e.push('用户名为 4–20 位小写字母、数字或下划线（公开主页地址用）')
  const n = nickname.value.trim()
  if (n.length < 1 || n.length > 16) e.push('昵称 1–16 个字')
  return e
})

async function submit() {
  if (errors.value.length || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    await register({
      email: email.value.trim(),
      password: password.value,
      username: username.value,
      nickname: nickname.value.trim(),
    })
    await auth.login(email.value.trim(), password.value)
    void router.replace('/me')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '注册失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="card auth">
    <h1>注册</h1>
    <p class="muted">
      邮箱仅用于登录，不会出现在任何公开页面；用户名是你公开主页 /u/用户名 的地址，昵称才会展示。
    </p>

    <form class="auth__form" @submit.prevent="submit">
      <label>
        <span>邮箱</span>
        <input v-model="email" type="email" autocomplete="email" required />
      </label>
      <label>
        <span>密码（至少 8 位）</span>
        <input v-model="password" type="password" autocomplete="new-password" required minlength="8" />
      </label>
      <label>
        <span>用户名（小写字母/数字/下划线）</span>
        <input v-model="username" autocomplete="username" required pattern="[a-z0-9_]{4,20}" />
      </label>
      <label>
        <span>昵称（公开展示名）</span>
        <input v-model="nickname" maxlength="16" required />
      </label>

      <ul v-if="errors.length" class="auth__errors">
        <li v-for="e in errors" :key="e">{{ e }}</li>
      </ul>
      <p v-if="error" class="auth__error">{{ error }}</p>

      <button class="btn btn--primary" type="submit" :disabled="!!errors.length || submitting">
        {{ submitting ? '注册中…' : '注册并登录' }}
      </button>
    </form>

    <p class="muted auth__foot">
      已有账号？<RouterLink to="/login">去登录</RouterLink>
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

.auth__errors,
.auth__error {
  color: #c62828;
  font-size: 0.85rem;
  margin: 0;
  padding-left: 1.2rem;
}

.auth__form .btn {
  padding: 0.6rem;
}

.auth__foot {
  margin-top: 1rem;
  font-size: 0.85rem;
}
</style>
