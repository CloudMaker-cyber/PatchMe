package com.patchme.interaction.vo;

import com.patchme.common.enums.NotificationType;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通知 VO（仅本人可见）。刻意不含 userId、不含任何作者身份字段：
 * payload 只有公开 id 与摘要（帖子标题/回复正文截断），匿名回复也不暴露回复者。
 * payload 用 Map 承载：后续新增通知类型只需扩展 JSON 键，旧前端不受影响。
 */
public record NotificationVO(Long id, NotificationType type, Map<String, Object> payload,
                             LocalDateTime readAt, LocalDateTime createdAt) {
}
