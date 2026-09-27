package com.patchme.moderation.vo;

import java.time.LocalDateTime;

/** 管理端审计出口：SQL 行 + 动作中文文案。仅 /api/admin 可达。 */
public record AdminAuditItem(
        Long id,
        String adminUsername,
        String action,
        String actionDisplay,
        String targetType,
        Long targetId,
        String targetUsername,
        String reason,
        Long reportId,
        LocalDateTime createdAt) {

    public static AdminAuditItem from(AuditRow row) {
        var action = com.patchme.common.enums.ModerationAction.valueOf(row.getAction());
        return new AdminAuditItem(row.getId(), row.getAdminUsername(), action.name(), action.display(),
                row.getTargetType(), row.getTargetId(), row.getTargetUsername(),
                row.getReason(), row.getReportId(), row.getCreatedAt());
    }
}
