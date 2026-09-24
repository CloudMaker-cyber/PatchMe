/** 身份模式：与后端 identity_mode 字段一致 */
export type IdentityMode = 'ANONYMOUS' | 'PUBLIC'

/** 帖子意图：倾诉 / 求建议 / 找同路人 */
export type Intent = 'VENT' | 'ADVICE' | 'COMPANION'

/**
/**
 * 作者展示对象 —— 与后端 AuthorView 的序列化结果一一对应：
 * 匿名内容只可能是 { mode: 'anonymous' }，不存在任何可反推身份的字段。
 * （真实归属只在数据库与 Service 层，API 出口由 src/api 的白名单 VO 收口。）
 */
export type AuthorInfo =
  | { mode: 'anonymous' }
  | { mode: 'public'; username: string; nickname: string; avatarUrl: string | null }

export interface Post {
  id: string
  author: AuthorInfo
  intent: Intent
  title: string
  body: string
  tagIds: string[]
  createdAt: string
  commentsClosed: boolean
  /** 回复数（含未展示统计），mock 内由 db 计算 */
  replyCount: number
  supportCount: number
}

export interface Reply {
  id: string
  postId: string
  author: AuthorInfo
  body: string
  createdAt: string
  isHelpful: boolean
}

/** 详情接口返回：帖子 + 回复列表；楼主/关闭等能力标记由 mock 层按内部归属计算 */
export interface PostDetail {
  post: Post
  replies: Reply[]
  /** 当前模拟登录用户是否是楼主（任务 2 起由 JWT 判定） */
  isAuthorOfPost: boolean
  supportedByMe: boolean
  bookmarkedByMe: boolean
}

export interface DictItem {
  id: string
  name: string
}

/** 筛选条件：多维交集；tags 内部为任一匹配 */
export interface PostFilters {
  school: string | null
  major: string | null
  intent: Intent | null
  tags: string[]
}

/** 首页流状态分组（规则见 docs/v3/01） */
export type FeedStatus = 'UNANSWERED' | 'NEED_HELP' | 'RESOLVED'

export interface ProfileSummary {
  username: string
  nickname: string
  avatarUrl: string | null
  bio: string
}
