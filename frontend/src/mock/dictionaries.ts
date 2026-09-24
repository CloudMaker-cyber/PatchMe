import type { DictItem, Intent } from '@/types'

/** 固定字典：模拟 schools / majors / tags 三张字典表（任务 2 由后端接口提供） */

export const schools: DictItem[] = [
  { id: 's_pku', name: '北京大学' },
  { id: 's_tsinghua', name: '清华大学' },
  { id: 's_fudan', name: '复旦大学' },
  { id: 's_sjtu', name: '上海交通大学' },
  { id: 's_zju', name: '浙江大学' },
  { id: 's_nju', name: '南京大学' },
  { id: 's_whu', name: '武汉大学' },
  { id: 's_scu', name: '四川大学' },
]

/** 专业方向为固定大类，不允许用户自由创建（docs/v3/01） */
export const majors: DictItem[] = [
  { id: 'm_cs', name: '计算机与电子' },
  { id: 'm_biz', name: '经管与金融' },
  { id: 'm_med', name: '医学与健康' },
  { id: 'm_law', name: '法学与人文' },
  { id: 'm_sci', name: '理学' },
  { id: 'm_art', name: '艺术与设计' },
]

export const tags: DictItem[] = [
  { id: 't_study', name: '学业压力' },
  { id: 't_relation', name: '人际关系' },
  { id: 't_roommate', name: '室友相处' },
  { id: 't_family', name: '家庭' },
  { id: 't_emotion', name: '情感' },
  { id: 't_career', name: '就业保研' },
  { id: 't_health', name: '身心健康' },
  { id: 't_money', name: '消费借贷' },
  { id: 't_adapt', name: '适应迷茫' },
  { id: 't_hobby', name: '兴趣同好' },
]

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

export function dictName(list: DictItem[], id: string | null): string | null {
  if (!id) return null
  return list.find((d) => d.id === id)?.name ?? null
}
