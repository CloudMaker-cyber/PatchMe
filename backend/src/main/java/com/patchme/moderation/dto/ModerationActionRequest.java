package com.patchme.moderation.dto;

import com.patchme.common.enums.ModerationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员处置请求（仅 /api/admin 可达）。
 * action 为用户阶梯（WARN/OBSERVE/RESTRICT/BAN/UNBAN）；reportId 可选，标记本处置所依据的已核实举报。
 */
public record ModerationActionRequest(
        @NotNull(message = "请选择处置动作") ModerationAction action,
        @NotNull(message = "缺少目标用户") Long targetUserId,
        @Size(max = 300, message = "原因最多 300 字") String reason,
        Long reportId
) {
}
