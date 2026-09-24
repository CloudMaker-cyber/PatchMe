import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 启动即确认登录态（Cookie 里的 JWT 换 /api/me），失败静默按游客处理
void useAuthStore().ensureLoaded()

app.mount('#app')
