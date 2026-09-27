import axios, { type AxiosError, type AxiosResponse } from 'axios'
import { http } from './http'
import type {
  AccountStatus,
  AdminAuditItem,
  AdminReportItem,
  AppNotification,
  AuthorInfo,
  BlockUser,
  IdentityMode,
  Intent,
  ModerationLadderAction,
  MyReport,
  Post,
  PostDetail,
  PostFilters,
  ProfileSummary,
  Reply,
  ReportReason,
  ReportTargetType,
  UserSettings,
  DictItem,
} from '@/types'

/**
 * 真实 REST 层：函数签名与任务 1 的 mock/api.ts 一一对应，视图代码切换零成本。
 * 后端主键是数字（Long），这里统一转成前端字符串 id；请求方向再转回数字。
 */

export interface Envelope<T> {
  code: string
  message: string
  data?: T
}

export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 401（需要登录）判定：视图据此引导去登录页并保留回跳地址 */
export function isUnauthorized(e: unknown): boolean {
  return e instanceof ApiError && e.code === '40100'
}

async function call<T>(fn: () => Promise<AxiosResponse<Envelope<T>>>): Promise<T> {
  try {
    const res = await fn()
    if (res.data.code !== '0') throw new ApiError(res.data.code, res.data.message)
    return res.data.data as T
  } catch (e) {
    if (e instanceof ApiError) throw e
    if (axios.isAxiosError(e)) {
      const err = e as AxiosError<Envelope<unknown>>
      const body = err.response?.data
      throw new ApiError(body?.code ?? '50000', body?.message ?? '服务暂时不可用，请稍后重试')
    }
    throw e
  }
}

// ---------- 后端原始 VO（docs/v3/02 白名单字段；author 匿名时只可能是 {mode}） ----------

interface ApiAuthor {
  mode: 'anonymous' | 'public'
  username?: string | null
  nickname?: string | null
  avatarUrl?: string | null
}

interface ApiPost {
  id: number
  author: ApiAuthor
  intent: Intent
  title: string
  body: string
  tagIds: number[]
  createdAt: string
  commentsClosed: boolean
  replyCount: number
  supportCount: number
  riskHint: boolean
}

interface ApiReply {
  id: number
  postId: number
  author: ApiAuthor
  body: string
  createdAt: string
  isHelpful: boolean
}

interface ApiDetail {
  post: ApiPost
  replies: ApiReply[]
  isAuthorOfPost: boolean
  supportedByMe: boolean
  bookmarkedByMe: boolean
}

interface ApiMinePost extends Omit<ApiPost, 'author' | 'riskHint'> {
  identityMode: IdentityMode
}

interface ApiMineReply extends Omit<ApiReply, 'author'> {
  identityMode: IdentityMode
}

interface ApiDicts {
  schools: Array<{ id: number; name: string }>
  majors: Array<{ id: number; name: string }>
  tags: Array<{ id: number; name: string }>
}

interface ApiProfilePage {
  profile: { username: string; nickname: string; avatarUrl: string | null; bio: string }
  posts: ApiPost[]
  replies: ApiReply[]
}

function toAuthor(a: ApiAuthor): AuthorInfo {
  if (a.mode === 'public' && a.username) {
    return {
      mode: 'public',
      username: a.username,
      nickname: a.nickname ?? a.username,
      avatarUrl: a.avatarUrl ?? null,
    }
  }
  return { mode: 'anonymous' }
}

function toPost(p: ApiPost): Post {
  return {
    id: String(p.id),
    author: toAuthor(p.author),
    intent: p.intent,
    title: p.title,
    body: p.body,
    tagIds: p.tagIds.map(String),
    createdAt: p.createdAt,
    commentsClosed: p.commentsClosed,
    replyCount: p.replyCount,
    supportCount: p.supportCount,
    riskHint: p.riskHint,
  }
}

function toReply(r: ApiReply): Reply {
  return {
    id: String(r.id),
    postId: String(r.postId),
    author: toAuthor(r.author),
    body: r.body,
    createdAt: r.createdAt,
    isHelpful: r.isHelpful,
  }
}

/** “我的”列表没有 author：本人内容按 identityMode + 当前账号拼展示信息 */
function mineAuthor(identityMode: IdentityMode, me: { username: string; nickname: string }): AuthorInfo {
  return identityMode === 'PUBLIC'
    ? { mode: 'public', username: me.username, nickname: me.nickname, avatarUrl: null }
    : { mode: 'anonymous' }
}

function num(id: string | null): number | null {
  return id === null || id === '' ? null : Number(id)
}

// ---------- 鉴权（token 在 HttpOnly Cookie，前端 JS 读不到也不该读） ----------

export interface AuthUser {
  username: string
  nickname: string
  role: string
}

export function register(input: {
  email: string
  password: string
  username: string
  nickname: string
}): Promise<void> {
  return call(() => http.post<Envelope<void>>('/auth/register', input))
}

export function login(email: string, password: string): Promise<AuthUser> {
  return call(() => http.post<Envelope<AuthUser>>('/auth/login', { email, password }))
}

export function logout(): Promise<void> {
  return call(() => http.post<Envelope<void>>('/auth/logout'))
}

export interface MeInfo extends AuthUser {
  userId: number
  /** 任务 5：账号状态（仅 /api/me 返回；登录响应没有它） */
  status: AccountStatus
}

export function fetchMe(): Promise<MeInfo | null> {
  return call(() => http.get<Envelope<MeInfo>>('/me')).catch((e) => {
    if (e instanceof ApiError && e.code === '40100') return null
    throw e
  })
}

// ---------- 字典 ----------

export interface Dicts {
  schools: DictItem[]
  majors: DictItem[]
  tags: DictItem[]
}

export function fetchDicts(): Promise<Dicts> {
  return call(() => http.get<Envelope<ApiDicts>>('/dicts')).then((d) => ({
    schools: d.schools.map((x) => ({ id: String(x.id), name: x.name })),
    majors: d.majors.map((x) => ({ id: String(x.id), name: x.name })),
    tags: d.tags.map((x) => ({ id: String(x.id), name: x.name })),
  }))
}

// ---------- 帖子与回复 ----------

function feedQuery(filters: PostFilters, resolved: boolean): Record<string, string> {
  const q: Record<string, string> = { resolved: String(resolved) }
  if (filters.school) q.school = filters.school
  if (filters.major) q.major = filters.major
  if (filters.intent) q.intent = filters.intent
  if (filters.tags.length) q.tags = filters.tags.join(',')
  return q
}

export function fetchFeed(filters: PostFilters): Promise<Post[]> {
  return call(() => http.get<Envelope<ApiPost[]>>('/posts', { params: feedQuery(filters, false) })).then((l) =>
    l.map(toPost),
  )
}

export function fetchResolved(filters: PostFilters): Promise<Post[]> {
  return call(() => http.get<Envelope<ApiPost[]>>('/posts', { params: feedQuery(filters, true) })).then((l) =>
    l.map(toPost),
  )
}

export function fetchPostDetail(id: string): Promise<PostDetail | null> {
  return call(() => http.get<Envelope<ApiDetail>>(`/posts/${id}`))
    .then((d) => ({
      post: toPost(d.post),
      replies: d.replies.map(toReply),
      isAuthorOfPost: d.isAuthorOfPost,
      supportedByMe: d.supportedByMe,
      bookmarkedByMe: d.bookmarkedByMe,
    }))
    .catch((e) => {
      if (e instanceof ApiError && e.code === '40400') return null
      throw e
    })
}

export interface CreatePostInput {
  intent: Intent
  title: string
  body: string
  schoolId: string | null
  majorId: string | null
  tagIds: string[]
  identityMode: IdentityMode
}

export function createPost(input: CreatePostInput): Promise<string> {
  return call(() =>
    http.post<Envelope<number>>('/posts', {
      intent: input.intent,
      title: input.title,
      body: input.body,
      schoolId: num(input.schoolId),
      majorId: num(input.majorId),
      tagIds: input.tagIds.map(Number),
      identity: input.identityMode,
    }),
  ).then(String)
}

export function createReply(postId: string, body: string, identityMode: IdentityMode): Promise<Reply> {
  return call(() =>
    http.post<Envelope<ApiReply>>(`/posts/${postId}/replies`, { body, identity: identityMode }),
  ).then(toReply)
}

/** 楼主标记/取消“有帮助”（每帖至多一条由后端 Service 保证） */
export function setHelpful(postId: string, replyId: string, helpful: boolean): Promise<void> {
  return call(() => http.patch<Envelope<void>>(`/replies/${replyId}/helpful`, { helpful }))
}

export function setCommentsClosed(postId: string, closed: boolean): Promise<boolean> {
  return call(() => http.patch<Envelope<void>>(`/posts/${postId}/comments`, { closed })).then(() => true)
}

export function toggleSupport(postId: string): Promise<number> {
  return call(() => http.post<Envelope<number>>(`/posts/${postId}/support`))
}

export function toggleBookmark(postId: string): Promise<boolean> {
  return call(() => http.post<Envelope<boolean>>(`/posts/${postId}/bookmark`))
}

// ---------- 公开主页与我的内容 ----------

export function fetchProfile(
  username: string,
): Promise<{ profile: ProfileSummary; posts: Post[]; replies: Reply[] } | null> {
  return call(() => http.get<Envelope<ApiProfilePage>>(`/users/${username}`))
    .then((d) => ({
      profile: {
        username: d.profile.username,
        nickname: d.profile.nickname,
        avatarUrl: d.profile.avatarUrl,
        bio: d.profile.bio,
      },
      posts: d.posts.map(toPost),
      replies: d.replies.map(toReply),
    }))
    .catch((e) => {
      if (e instanceof ApiError && e.code === '40400') return null
      throw e
    })
}

export function fetchMine(me: {
  username: string
  nickname: string
}): Promise<{ posts: Post[]; replies: Reply[]; bookmarks: Post[] }> {
  return Promise.all([
    call(() => http.get<Envelope<ApiMinePost[]>>('/me/posts')),
    call(() => http.get<Envelope<ApiMineReply[]>>('/me/replies')),
    call(() => http.get<Envelope<ApiPost[]>>('/me/bookmarks')),
  ]).then(([posts, replies, bookmarks]) => ({
    posts: posts.map((p) => ({
      id: String(p.id),
      author: mineAuthor(p.identityMode, me),
      intent: p.intent,
      title: p.title,
      body: p.body,
      tagIds: p.tagIds.map(String),
      createdAt: p.createdAt,
      commentsClosed: p.commentsClosed,
      replyCount: p.replyCount,
      supportCount: p.supportCount,
      // 我的列表出口没有 riskHint（那是公开侧的派生提示），本人内容无需提示
      riskHint: false,
    })),
    replies: replies.map((r) => ({
      id: String(r.id),
      postId: String(r.postId),
      author: mineAuthor(r.identityMode, me),
      body: r.body,
      createdAt: r.createdAt,
      isHelpful: r.isHelpful,
    })),
    bookmarks: bookmarks.map(toPost),
  }))
}

// ---------- 删除（软删除，成功后内容即刻从所有公开出口消失） ----------

export function deletePost(id: string): Promise<void> {
  return call(() => http.delete<Envelope<void>>(`/posts/${id}`))
}

export function deleteReply(id: string): Promise<void> {
  return call(() => http.delete<Envelope<void>>(`/replies/${id}`))
}

// ---------- 公开转匿名（单向：匿名内容永不可再转公开，由后端闸门保证） ----------

/** 楼主将自己的公开帖转匿名：成功后立即从公开主页消失 */
export function makePostAnonymous(id: string): Promise<void> {
  return call(() => http.patch<Envelope<void>>(`/posts/${id}/identity`, { mode: 'ANONYMOUS' }))
}

/** 作者将自己的公开回复转匿名 */
export function makeReplyAnonymous(id: string): Promise<void> {
  return call(() => http.patch<Envelope<void>>(`/replies/${id}/identity`, { mode: 'ANONYMOUS' }))
}

// ---------- 通知（仅本人） ----------

interface ApiNotification {
  id: number
  type: AppNotification['type']
  payload: Record<string, unknown>
  readAt: string | null
  createdAt: string
}

function str(v: unknown): string | undefined {
  return v == null ? undefined : String(v)
}

export function fetchNotifications(): Promise<AppNotification[]> {
  return call(() => http.get<Envelope<ApiNotification[]>>('/notifications')).then((l) =>
    l.map((n) => ({
      id: String(n.id),
      type: n.type,
      // 四类通知 payload 键各不相同（后端 Map 出口）；缺键只会得到 undefined，不会崩
      postId: str(n.payload.postId) ?? null,
      replyId: str(n.payload.replyId) ?? null,
      excerpt: str(n.payload.excerpt) ?? '',
      action: str(n.payload.action),
      reason: str(n.payload.reason),
      reportId: str(n.payload.reportId),
      status: str(n.payload.status),
      message: str(n.payload.message),
      read: n.readAt != null,
      createdAt: n.createdAt,
    })),
  )
}

export function markNotificationsRead(): Promise<void> {
  return call(() => http.patch<Envelope<void>>('/notifications/read'))
}

// ---------- 浏览历史与账号设置（仅本人） ----------

export function fetchHistory(): Promise<Post[]> {
  return call(() => http.get<Envelope<ApiPost[]>>('/me/history')).then((l) => l.map(toPost))
}

export function clearHistory(): Promise<void> {
  return call(() => http.delete<Envelope<void>>('/me/history'))
}

interface ApiSettings {
  defaultIdentityMode: IdentityMode
  replyNotificationEnabled: boolean
  historyEnabled: boolean
}

export function fetchSettings(): Promise<UserSettings> {
  return call(() => http.get<Envelope<ApiSettings>>('/me/settings'))
}

/** 部分更新：只传需要改的字段（与后端 PATCH 语义一致） */
export function updateSettings(patch: Partial<UserSettings>): Promise<UserSettings> {
  return call(() => http.patch<Envelope<ApiSettings>>('/me/settings', patch))
}

// ---------- 任务 5：举报 / 拉黑（仅本人） ----------

export function submitReport(input: {
  targetType: ReportTargetType
  targetId: string
  reason: ReportReason
  note?: string
}): Promise<string> {
  return call(() =>
    http.post<Envelope<{ id: number }>>('/reports', {
      targetType: input.targetType,
      targetId: Number(input.targetId),
      reason: input.reason,
      note: input.note ?? '',
    }),
  ).then((r) => String(r.id))
}

export interface ApiMyReport {
  id: number
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  reasonDisplay: string
  status: MyReport['status']
  statusDisplay: string
  createdAt: string
}

export function fetchMyReports(): Promise<MyReport[]> {
  return call(() => http.get<Envelope<ApiMyReport[]>>('/reports/mine')).then((l) =>
    l.map((r) => ({
      id: String(r.id),
      targetType: r.targetType,
      targetId: String(r.targetId),
      reason: r.reason,
      reasonDisplay: r.reasonDisplay,
      status: r.status,
      statusDisplay: r.statusDisplay,
      createdAt: r.createdAt,
    })),
  )
}

export function blockUser(username: string): Promise<void> {
  return call(() => http.post<Envelope<void>>('/blocks', { username }))
}

export function unblockUser(username: string): Promise<void> {
  return call(() => http.delete<Envelope<void>>(`/blocks/${encodeURIComponent(username)}`))
}

interface ApiBlockUser {
  userId: number
  username: string
  nickname: string
  avatarUrl: string | null
}

export function fetchMyBlocks(): Promise<BlockUser[]> {
  return call(() => http.get<Envelope<ApiBlockUser[]>>('/blocks/mine')).then((l) =>
    l.map((b) => ({ userId: String(b.userId), username: b.username, nickname: b.nickname, avatarUrl: b.avatarUrl })),
  )
}

// ---------- 任务 5：审核后台（/api/admin/**，后端 SecurityConfig 已限 ADMIN；普通用户只会拿到 401/403） ----------

interface ApiAdminReport {
  id: number
  source: 'USER' | 'SYSTEM'
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  reasonDisplay: string
  note: string
  status: MyReport['status']
  statusDisplay: string
  createdAt: string
  targetTitle: string | null
  targetExcerpt: string | null
  authorId: number
  authorIdentityMode: IdentityMode
}

export function fetchAdminQueue(status?: string): Promise<AdminReportItem[]> {
  return call(() => http.get<Envelope<ApiAdminReport[]>>('/admin/reports', { params: status ? { status } : {} })).then(
    (l) =>
      l.map((r) => ({
        id: String(r.id),
        source: r.source,
        targetType: r.targetType,
        targetId: String(r.targetId),
        reason: r.reason,
        reasonDisplay: r.reasonDisplay,
        note: r.note,
        status: r.status,
        statusDisplay: r.statusDisplay,
        createdAt: r.createdAt,
        targetTitle: r.targetTitle,
        targetExcerpt: r.targetExcerpt,
        authorId: String(r.authorId),
        authorIdentityMode: r.authorIdentityMode,
      })),
  )
}

/** confirm=核实结论；takedown 仅在确认违规时生效（软下架目标内容） */
export function reviewReport(reportId: string, confirm: boolean, reason: string, takedown: boolean): Promise<void> {
  return call(() => http.post<Envelope<void>>(`/admin/reports/${reportId}/review`, { confirm, reason, takedown }))
}

export function moderateUser(action: ModerationLadderAction, targetUserId: string, reason: string): Promise<void> {
  return call(() => http.post<Envelope<void>>('/admin/actions', { action, targetUserId: Number(targetUserId), reason }))
}

interface ApiAuditItem {
  id: number
  adminUsername: string
  action: string
  actionDisplay: string
  targetType: string
  targetId: number
  targetUsername: string | null
  reason: string
  reportId: number | null
  createdAt: string
}

export function fetchAdminAudit(): Promise<AdminAuditItem[]> {
  return call(() => http.get<Envelope<ApiAuditItem[]>>('/admin/audit')).then((l) =>
    l.map((a) => ({
      id: String(a.id),
      adminUsername: a.adminUsername,
      action: a.action,
      actionDisplay: a.actionDisplay,
      targetType: a.targetType,
      targetId: String(a.targetId),
      targetUsername: a.targetUsername,
      reason: a.reason,
      reportId: a.reportId == null ? null : String(a.reportId),
      createdAt: a.createdAt,
    })),
  )
}
