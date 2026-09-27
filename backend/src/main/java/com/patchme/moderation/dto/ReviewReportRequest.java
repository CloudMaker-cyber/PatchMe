package com.patchme.moderation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 举报单核实：confirm=true 认定违规，false 不予处理；reason 记入审计，不外露；takedown 同时软下架目标内容。 */
public record ReviewReportRequest(
        @NotNull(message = "请给出处理结论") Boolean confirm,
        @Size(max = 300, message = "结论说明最多 300 字") String reason,
        Boolean takedown
) {
}
