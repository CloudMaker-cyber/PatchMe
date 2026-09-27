package com.patchme.moderation.vo;

import java.time.LocalDateTime;

/** 管理端队列出口：SQL 行 + 枚举中文文案（文案单点在枚举）。仅 /api/admin 可达。 */
public record AdminReportItem(
        Long id,
        String source,
        String targetType,
        Long targetId,
        String reason,
        String reasonDisplay,
        String note,
        String status,
        String statusDisplay,
        LocalDateTime createdAt,
        String targetTitle,
        String targetExcerpt,
        Long authorId,
        String authorIdentityMode) {

    public static AdminReportItem from(ReportQueueRow row) {
        var reason = com.patchme.common.enums.ReportReason.valueOf(row.getReason());
        var status = com.patchme.common.enums.ReportStatus.valueOf(row.getStatus());
        return new AdminReportItem(row.getId(), row.getSource(), row.getTargetType(), row.getTargetId(),
                reason.name(), reason.display(), row.getNote(), status.name(), status.display(),
                row.getCreatedAt(), row.getTargetTitle(), row.getTargetExcerpt(),
                row.getAuthorId(), row.getAuthorIdentityMode());
    }
}
