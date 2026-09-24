import axios, { type AxiosError, type AxiosResponse } from 'axios'
import { http } from './http'
import type {
  AuthorInfo,
  IdentityMode,
  Intent,
  Post,
  PostDetail,
  PostFilters,
  ProfileSummary,
  Reply,
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

interface ApiMinePost extends Omit<ApiPost, 'author'> {
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

export function fetchMe(): Promise<(AuthUser & { userId: number }) | null> {
  return call(() => http.get<Envelope<AuthUser & { userId: number }>>('/me')).catch((e) => {
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
