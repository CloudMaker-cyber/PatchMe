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
  /** 存在待处理的风险求助线索：只提示、不代表违规，也不携带任何审核细节 */
  riskHint: boolean
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

/**
 * 通知（仅本人可见）。REPLY 类型的 payload 只有公开 id 与摘要，
 * 匿名回复的通知也不携带回复者任何信息；任务 5 起三类审核通知复用同一出口：
 * MODERATION 带处置动作与原因、REPORT 带举报单终态、SECURITY 只有一句话文案。
 */
export interface AppNotification {
  id: string
  type: 'REPLY' | 'MODERATION' | 'REPORT' | 'SECURITY'
  /** 仅 REPLY 类型有值：可深链回帖子详情 */
  postId: string | null
  replyId: string | null
  excerpt: string
  /** MODERATION：处置动作展示文案（如"封禁"） */
  action?: string
  /** MODERATION：管理员填写的原因（可能被截断） */
  reason?: string
  /** REPORT：举报单 id */
  reportId?: string
  /** REPORT：处理结果展示文案（如"已确认违规"） */
  status?: string
  /** SECURITY：安全事件文案 */
  message?: string
  read: boolean
  createdAt: string
}

/** 账号设置（GET/PATCH /api/me/settings 的出口形状） */
export interface UserSettings {
  defaultIdentityMode: IdentityMode
  replyNotificationEnabled: boolean
  historyEnabled: boolean
}

// ---------- 任务 5：举报 / 拉黑 / 账号状态 ----------

export type ReportTargetType = 'POST' | 'REPLY'

/** 举报原因固定五种（docs/v3/01），前端只做展示映射，用户不可自造 */
export type ReportReason = 'HARASSMENT' | 'SPAM' | 'PRIVACY' | 'DANGER' | 'OTHER'

/** 我的举报记录项：进度只到状态粒度，无管理员身份/结论说明 */
export interface MyReport {
  id: string
  targetType: ReportTargetType
  targetId: string
  reason: ReportReason
  reasonDisplay: string
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED'
  statusDisplay: string
  createdAt: string
}

/** 拉黑列表项：能进列表的都是当初以公开身份出现的账户 */
export interface BlockUser {
  userId: string
  username: string
  nickname: string
  avatarUrl: string | null
}

/** 账号状态：RESTRICTED/BANNED 会实际拦截发布；BANNED 连登录都不允许 */
export type AccountStatus = 'NORMAL' | 'WARNED' | 'OBSERVED' | 'RESTRICTED' | 'BANNED'

// ---------- 任务 5：审核后台（仅 ADMIN 路由出现，普通用户接口永不返回这些形状） ----------

export type ModerationLadderAction = 'WARN' | 'OBSERVE' | 'RESTRICT' | 'BAN' | 'UNBAN'

export interface AdminReportItem {
  id: string
  source: 'USER' | 'SYSTEM'
  targetType: ReportTargetType
  targetId: string
  reason: ReportReason
  reasonDisplay: string
  note: string
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED'
  statusDisplay: string
  createdAt: string
  targetTitle: string | null
  targetExcerpt: string | null
  /** 仅管理端可见的作者内部 id（普通用户任何出口都没有它） */
  authorId: string
  authorIdentityMode: IdentityMode
}

export interface AdminAuditItem {
  id: string
  adminUsername: string
  action: string
  actionDisplay: string
  targetType: string
  targetId: string
  targetUsername: string | null
  reason: string
  reportId: string | null
  createdAt: string
}
