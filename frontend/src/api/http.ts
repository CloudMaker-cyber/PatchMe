import axios from 'axios'

/**
 * Axios 实例：统一走 /api 前缀（开发环境由 Vite 代理到 Spring Boot 8080）。
 * 后续任务的鉴权头、错误码处理都在此文件的拦截器中扩展。
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})
