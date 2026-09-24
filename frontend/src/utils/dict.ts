import type { Intent } from '@/types'

/** 意图文案是前端展示常量，与字典无关，不随后端下发 */
export const intentLabels: Record<Intent, string> = {
  VENT: '倾诉',
  ADVICE: '求建议',
  COMPANION: '找同路人',
}

export const intentOptions: Array<{ value: Intent; label: string }> = [
  { value: 'VENT', label: '倾诉' },
  { value: 'ADVICE', label: '求建议' },
  { value: 'COMPANION', label: '找同路人' },
]
