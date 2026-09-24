import type { IdentityMode, Intent } from '@/types'

/**
 * 内部数据层 —— 模拟 MySQL 表结构（docs/v3/02）。
 * 这里保留 authorId / identityMode 等内部字段，仅供 mock“后端”做归属判断；
 * 视图代码只能拿到 api.ts 构造的 VO，规则与真实后端一致。
 */

export interface UserRecord {
  id: string
  username: string
  nickname: string
  avatarUrl: string | null
  bio: string
}

export interface PostRecord {
  id: string
  authorId: string
  identityMode: IdentityMode
  intent: Intent
  title: string
  body: string
  schoolId: string | null
  majorId: string | null
  tagIds: string[]
  createdAt: string
  commentsClosed: boolean
  deletedAt: string | null
}

export interface ReplyRecord {
  id: string
  postId: string
  authorId: string
  identityMode: IdentityMode
  body: string
  createdAt: string
  isHelpful: boolean
  deletedAt: string | null
}

export const users: UserRecord[] = [
  { id: 'u_lin', username: 'linxiaoman', nickname: '小满', avatarUrl: null, bio: '种自己的花，爱自己的宇宙。' },
  { id: 'u_zhe', username: 'zhezhiyuan', nickname: '折枝', avatarUrl: null, bio: '熬夜冠军，科研咸鱼。' },
  { id: 'u_an', username: 'annagrad', nickname: '安纳', avatarUrl: null, bio: '' },
  { id: 'u_ghost', username: 'ghost', nickname: '无名', avatarUrl: null, bio: '' },
]

/** 模拟当前登录用户。任务 2 起由 JWT + Spring Security 上下文替代。 */
export const currentUserId = 'u_lin'

export const posts: PostRecord[] = [
  {
    id: 'p1', authorId: 'u_ghost', identityMode: 'ANONYMOUS', intent: 'VENT',
    title: '感觉整个人被绩点压扁了',
    body: '每天睁眼就是排名，排名掉一名我能难受一星期。家里人还总问“是不是努力了”，我不敢说我已经很努力了。说出来可能没人信，我连做梦都在算加权平均。',
    schoolId: 's_zju', majorId: 'm_cs', tagIds: ['t_study', 't_family'],
    createdAt: '2026-09-20T08:12:00Z', commentsClosed: false, deletedAt: null,
  },
  {
    id: 'p2', authorId: 'u_lin', identityMode: 'ANONYMOUS', intent: 'ADVICE',
    title: '室友作息和我完全相反，要换宿舍吗',
    body: '她凌晨两点开麦打游戏，我七点有早八。沟通过两次，改三天，又恢复原样。是在忍耐和换宿舍之间纠结的第 37 天。',
    schoolId: 's_scu', majorId: 'm_biz', tagIds: ['t_roommate'],
    createdAt: '2026-09-20T10:30:00Z', commentsClosed: false, deletedAt: null,
  },
  {
    id: 'p3', authorId: 'u_zhe', identityMode: 'PUBLIC', intent: 'COMPANION',
    title: '有没有人也一个人去食堂吃饭',
    body: '不是被孤立，是朋友们课表完全错开。一开始觉得自由，两个月后有点怀疑人生。想问问大家怎么平衡“享受独处”和“真的需要同伴”。',
    schoolId: 's_whu', majorId: 'm_law', tagIds: ['t_relation', 't_adapt'],
    createdAt: '2026-09-21T03:00:00Z', commentsClosed: false, deletedAt: null,
  },
  {
    id: 'p4', authorId: 'u_an', identityMode: 'PUBLIC', intent: 'ADVICE',
    title: '保研边缘人，该冲夏令营还是稳预推免',
    body: '排名 15% 左右，文书还没头绪。学长学姐们当年是怎么做这个二选一决策的？',
    schoolId: 's_nju', majorId: 'm_sci', tagIds: ['t_career', 't_study'],
    createdAt: '2026-09-21T06:45:00Z', commentsClosed: false, deletedAt: null,
  },
  {
    id: 'p5', authorId: 'u_ghost', identityMode: 'ANONYMOUS', intent: 'VENT',
    title: '给家里打电话总是吵架收场',
    body: '他们只关心我什么时候考证、什么时候回家，我试着说过一次“我最近很累”，被教育了二十分钟“大家都很累”。后来我就什么都不想说了。',
    schoolId: null, majorId: null, tagIds: ['t_family', 't_emotion'],
    createdAt: '2026-09-22T11:20:00Z', commentsClosed: false, deletedAt: null,
  },
  {
    id: 'p6', authorId: 'u_zhe', identityMode: 'ANONYMOUS', intent: 'ADVICE',
    title: '第一次分期买手机，现在有点慌',
    body: '看到免息就下单了，这月才发现账单叠上来了。生活费一千八，有点撑不住。有没有过来人说说这种消费观怎么调整？',
    schoolId: 's_sjtu', majorId: 'm_art', tagIds: ['t_money'],
    createdAt: '2026-09-22T14:00:00Z', commentsClosed: false, deletedAt: null,
  },
  {
    id: 'p7', authorId: 'u_lin', identityMode: 'PUBLIC', intent: 'VENT',
    title: '实验数据跑不出来，怀疑自己不适合读研',
    body: '同一组参数，师兄跑通了，我复现不出来。已经第三周了。每天去实验室的路上都要做心理建设。',
    schoolId: 's_pku', majorId: 'm_cs', tagIds: ['t_study', 't_health'],
    createdAt: '2026-09-19T01:10:00Z', commentsClosed: false, deletedAt: null,
  },
  {
    id: 'p8', authorId: 'u_an', identityMode: 'ANONYMOUS', intent: 'COMPANION',
    title: '有人假期留校且同样在准备转专业吗',
    body: '图书馆三楼靠窗那排位置每天都空着，想找个搭子互相监督，转专业考试还有四十天。',
    schoolId: 's_fudan', majorId: 'm_biz', tagIds: ['t_adapt', 't_study'],
    createdAt: '2026-09-23T02:00:00Z', commentsClosed: false, deletedAt: null,
  },
  {
    id: 'p9', authorId: 'u_ghost', identityMode: 'ANONYMOUS', intent: 'ADVICE',
    title: '（楼主已关闭评论）关于宿舍矛盾的处理更新',
    body: '谢谢大家上条帖子的建议，最后和室友约法三章成功了。这条留作记录，已关闭评论。',
    schoolId: 's_scu', majorId: 'm_cs', tagIds: ['t_roommate'],
    createdAt: '2026-09-18T09:00:00Z', commentsClosed: true, deletedAt: null,
  },
  {
    id: 'p10', authorId: 'u_zhe', identityMode: 'PUBLIC', intent: 'VENT',
    title: '（已获得帮助）投了三十份实习简历的回音',
    body: '之前发帖崩溃的那位回来了。已经有两位学姐帮我改了简历，上周拿到第一个面试。特来更新，已标记有帮助。',
    schoolId: 's_whu', majorId: 'm_biz', tagIds: ['t_career'],
    createdAt: '2026-09-17T05:30:00Z', commentsClosed: false, deletedAt: null,
  },
]

export const replies: ReplyRecord[] = [
  { id: 'r1', postId: 'p3', authorId: 'u_lin', identityMode: 'PUBLIC', body: '一个人吃饭三年路过。后来找到课表重合的饭搭子是靠一门大课的小组群，缘分很玄学。', createdAt: '2026-09-21T04:10:00Z', isHelpful: false, deletedAt: null },
  { id: 'r2', postId: 'p3', authorId: 'u_ghost', identityMode: 'ANONYMOUS', body: '独处不需要向任何人交代，但想找人陪也不是软弱。两个都成立。', createdAt: '2026-09-21T07:20:00Z', isHelpful: true, deletedAt: null },
  { id: 'r3', postId: 'p4', authorId: 'u_zhe', identityMode: 'PUBLIC', body: '边缘排名建议夏令营当练兵、预推免当主战场，两边都投，心态会稳很多。', createdAt: '2026-09-21T09:00:00Z', isHelpful: false, deletedAt: null },
  { id: 'r4', postId: 'p7', authorId: 'u_an', identityMode: 'PUBLIC', body: '复现不出来先查环境和随机种子，八成不是你的问题。别把工具的锅背成能力的锅。', createdAt: '2026-09-19T03:40:00Z', isHelpful: true, deletedAt: null },
  { id: 'r5', postId: 'p7', authorId: 'u_ghost', identityMode: 'ANONYMOUS', body: '同经历过，那段时间每天逼自己写“今日三个小进展”，回头看会发现自己其实动了很远。', createdAt: '2026-09-19T06:00:00Z', isHelpful: false, deletedAt: null },
  { id: 'r6', postId: 'p10', authorId: 'u_lin', identityMode: 'PUBLIC', body: '恭喜！你的帖子也帮到了后来的我。', createdAt: '2026-09-17T08:00:00Z', isHelpful: true, deletedAt: null },
  { id: 'r7', postId: 'p9', authorId: 'u_an', identityMode: 'ANONYMOUS', body: '约法三章真的有用+1，关键是白纸黑字。', createdAt: '2026-09-18T10:00:00Z', isHelpful: false, deletedAt: null },
]
