package com.patchme.moderation.dto;

import com.patchme.common.enums.ReportReason;
import com.patchme.common.enums.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 举报请求：目标只可能是公开帖子或公开回复；note 可选，给管理员看，绝不回显给被举报人。 */
public record CreateReportRequest(
        @NotNull(message = "请选择举报对象类型") ReportTargetType targetType,
        @NotNull(message = "缺少举报对象") Long targetId,
        @NotNull(message = "请选择举报原因") ReportReason reason,
        @Size(max = 300, message = "补充说明最多 300 字") String note
) {
}
