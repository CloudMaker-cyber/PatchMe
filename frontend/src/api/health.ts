import { http } from './http'

export interface Envelope<T> {
  code: string
  message: string
  data: T
}

export interface HealthData {
  status: string
}

/** 调后端健康检查，验证统一响应结构与联通性 */
export async function fetchHealth(): Promise<HealthData> {
  const res = await http.get<Envelope<HealthData>>('/health')
  return res.data.data
}
