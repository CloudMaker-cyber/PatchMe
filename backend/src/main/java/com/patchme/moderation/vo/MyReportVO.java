package com.patchme.moderation.vo;

import java.time.LocalDateTime;

/**
 * 我的举报记录出口 VO（仅举报人本人可见）：处理进度只到状态粒度。
 * 不含管理员身份、不含管理员结论说明；被举报内容是否为匿名也不在此暴露。
 */
public record MyReportVO(
        Long id,
        String targetType,
        Long targetId,
        String reason,
        String reasonDisplay,
        String status,
        String statusDisplay,
        LocalDateTime createdAt) {

    public static MyReportVO from(MyReportRow row) {
        var reason = com.patchme.common.enums.ReportReason.valueOf(row.getReason());
        var status = com.patchme.common.enums.ReportStatus.valueOf(row.getStatus());
        return new MyReportVO(row.getId(), row.getTargetType(), row.getTargetId(),
                reason.name(), reason.display(), status.name(), status.display(), row.getCreatedAt());
    }
}
