import {
  currentUserId,
  posts,
  replies,
  users,
  type PostRecord,
  type ReplyRecord,
} from './db'
import type {
  AuthorInfo,
  FeedStatus,
  IdentityMode,
  Intent,
  Post,
  PostDetail,
  PostFilters,
  ProfileSummary,
  Reply,
} from '@/types'

/**
 * 模拟后端：函数签名与任务 3 的真实 REST API 一一对应。
 * 关键规则（与 docs/v3/02 匿名安全一致）：
 * - toAuthorVO 是身份字段唯一出口：ANONYMOUS 只返回 { mode: 'anonymous' }；
 * - 公开主页查询强制 identity_mode = PUBLIC；
 * - 归属判断（楼主操作）只在“服务端”进行，VO 里没有 authorId。
 */

const LATENCY = 120

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY))
}

function toAuthorVO(record: PostRecord | ReplyRecord): AuthorInfo {
  if (record.identityMode === 'ANONYMOUS') {
    return { mode: 'anonymous' }
  }
  const user = users.find((u) => u.id === record.authorId)
  if (!user) return { mode: 'anonymous' }
  return { mode: 'public', username: user.username, nickname: user.nickname, avatarUrl: user.avatarUrl }
}

function visibleRepliesOf(postId: string): ReplyRecord[] {
  return replies.filter((r) => r.postId === postId && !r.deletedAt)
}

function toPostVO(record: PostRecord): Post {
  return {
    id: record.id,
    author: toAuthorVO(record),
    intent: record.intent,
    title: record.title,
    body: record.body,
    tagIds: [...record.tagIds],
    createdAt: record.createdAt,
    commentsClosed: record.commentsClosed,
    replyCount: visibleRepliesOf(record.id).length,
    supportCount: baseSupport(record.id),
  }
}

/** 支持数：种子值 + 当前用户的一次 toggle（模拟 post_supports 表） */
const extraSupport = new Set<string>()
const bookmarks = new Set<string>(['p4'])
const baseSupportCounts: Record<string, number> = {
  p1: 12, p2: 5, p3: 8, p4: 15, p5: 21, p6: 3, p7: 6, p8: 1, p9: 9, p10: 14,
}

function baseSupport(postId: string): number {
  const base = baseSupportCounts[postId] ?? 0
  return base + (extraSupport.has(postId) ? 1 : 0)
}

function toReplyVO(record: ReplyRecord): Reply {
  return {
    id: record.id,
    postId: record.postId,
    author: toAuthorVO(record),
    body: record.body,
    createdAt: record.createdAt,
    isHelpful: record.isHelpful,
  }
}

export function feedStatusOf(post: Post, repliesOfPost: Reply[]): FeedStatus {
  if (repliesOfPost.some((r) => r.isHelpful) || post.commentsClosed) return 'RESOLVED'
  if (post.replyCount === 0) return 'UNANSWERED'
  return 'NEED_HELP'
}

function matchFilters(record: PostRecord, filters: PostFilters): boolean {
  if (filters.school && record.schoolId !== filters.school) return false
  if (filters.major && record.majorId !== filters.major) return false
  if (filters.intent && record.intent !== filters.intent) return false
  // 标签多选：任一匹配即可
  if (filters.tags.length > 0 && !filters.tags.some((t) => record.tagIds.includes(t))) return false
  return true
}

/** 首页默认流：不含“已获得帮助”，待回答优先、同组早发布优先 */
export function fetchFeed(filters: PostFilters): Promise<Post[]> {
  const items = posts
    .filter((p) => !p.deletedAt)
    .map((p) => ({ record: p, vo: toPostVO(p), status: '' as FeedStatus }))
  for (const item of items) {
    item.status = feedStatusOf(item.vo, visibleRepliesOf(item.record.id).map(toReplyVO))
  }
  const feed = items
    .filter((i) => i.status !== 'RESOLVED')
    .filter((i) => matchFilters(i.record, filters))
    .sort((a, b) => {
      const rank = (s: FeedStatus) => (s === 'UNANSWERED' ? 0 : 1)
      if (rank(a.status) !== rank(b.status)) return rank(a.status) - rank(b.status)
      return a.record.createdAt.localeCompare(b.record.createdAt)
    })
    .map((i) => i.vo)
  return delay(feed)
}

/** 筛选后结果集内仍可查看“已获得帮助”分组（验收：默认流不显示，而非不可见） */
export function fetchResolved(filters: PostFilters): Promise<Post[]> {
  const feed = posts
    .filter((p) => !p.deletedAt)
    .map((p) => ({ record: p, vo: toPostVO(p) }))
    .filter((i) => feedStatusOf(i.vo, visibleRepliesOf(i.record.id).map(toReplyVO)) === 'RESOLVED')
    .filter((i) => matchFilters(i.record, filters))
    .sort((a, b) => a.record.createdAt.localeCompare(b.record.createdAt))
    .map((i) => i.vo)
  return delay(feed)
}

export function fetchPostDetail(id: string): Promise<PostDetail | null> {
  const record = posts.find((p) => p.id === id && !p.deletedAt)
  if (!record) return delay(null)
  return delay({
    post: toPostVO(record),
    replies: visibleRepliesOf(id)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      .map(toReplyVO),
    isAuthorOfPost: record.authorId === currentUserId,
    supportedByMe: extraSupport.has(id),
    bookmarkedByMe: bookmarks.has(id),
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
  const id = `p${Date.now()}`
  posts.push({
    id,
    authorId: currentUserId,
    identityMode: input.identityMode,
    intent: input.intent,
    title: input.title.trim(),
    body: input.body.trim(),
    schoolId: input.schoolId,
    majorId: input.majorId,
    tagIds: [...input.tagIds],
    createdAt: new Date().toISOString(),
    commentsClosed: false,
    deletedAt: null,
  })
  return delay(id)
}

export function createReply(postId: string, body: string, identityMode: IdentityMode): Promise<Reply> {
  const record: ReplyRecord = {
    id: `r${Date.now()}`,
    postId,
    authorId: currentUserId,
    identityMode,
    body: body.trim(),
    createdAt: new Date().toISOString(),
    isHelpful: false,
    deletedAt: null,
  }
  replies.push(record)
  return delay(toReplyVO(record))
}

/** 楼主标记/取消有帮助：mock 层校验归属，与任务 3 的 Service 层校验同构 */
export function toggleHelpful(postId: string, replyId: string): Promise<boolean> {
  const post = posts.find((p) => p.id === postId)
  const reply = replies.find((r) => r.id === replyId && r.postId === postId)
  if (!post || !reply || post.authorId !== currentUserId) return delay(false)
  if (!reply.isHelpful) {
    // 每帖至多一个有帮助回复：标记新的自动取消旧的
    visibleRepliesOf(postId).forEach((r) => (r.isHelpful = false))
  }
  reply.isHelpful = !reply.isHelpful
  return delay(true)
}

export function setCommentsClosed(postId: string, closed: boolean): Promise<boolean> {
  const post = posts.find((p) => p.id === postId)
  if (!post || post.authorId !== currentUserId) return delay(false)
  post.commentsClosed = closed
  return delay(true)
}

export function toggleSupport(postId: string): Promise<number> {
  if (extraSupport.has(postId)) extraSupport.delete(postId)
  else extraSupport.add(postId)
  return delay(baseSupport(postId))
}

export function toggleBookmark(postId: string): Promise<boolean> {
  if (bookmarks.has(postId)) bookmarks.delete(postId)
  else bookmarks.add(postId)
  return delay(bookmarks.has(postId))
}

/** 公开主页：按公开用户名查询，强制只返回当前为 PUBLIC 且未删除的内容 */
export function fetchProfile(username: string): Promise<{
  profile: ProfileSummary
  posts: Post[]
  replies: Reply[]
} | null> {
  const user = users.find((u) => u.username === username)
  if (!user) return delay(null)
  const publicPosts = posts
    .filter((p) => p.authorId === user.id && p.identityMode === 'PUBLIC' && !p.deletedAt)
    .map(toPostVO)
  const publicReplies = replies
    .filter((r) => r.authorId === user.id && r.identityMode === 'PUBLIC' && !r.deletedAt)
    .map(toReplyVO)
  return delay({
    profile: { username: user.username, nickname: user.nickname, avatarUrl: user.avatarUrl, bio: user.bio },
    posts: publicPosts,
    replies: publicReplies,
  })
}

/** 我的内容：本人可见自己的匿名内容（含匿名标记），仅本人接口 */
export function fetchMine(): Promise<{ posts: Post[]; replies: Reply[]; bookmarks: Post[] }> {
  const mine = posts
    .filter((p) => p.authorId === currentUserId && !p.deletedAt)
    .map(toPostVO)
  const myReplies = replies
    .filter((r) => r.authorId === currentUserId && !r.deletedAt)
    .map(toReplyVO)
  const bookmarked = posts
    .filter((p) => bookmarks.has(p.id) && !p.deletedAt)
    .map(toPostVO)
  return delay({ posts: mine, replies: myReplies, bookmarks: bookmarked })
}
