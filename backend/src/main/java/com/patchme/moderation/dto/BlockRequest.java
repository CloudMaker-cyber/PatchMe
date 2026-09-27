package com.patchme.moderation.dto;

import jakarta.validation.constraints.NotBlank;

/** 拉黑/取消拉黑：按对方的公开用户名定位账户（拉黑只对公开身份生效——01）。 */
public record BlockRequest(@NotBlank(message = "缺少用户名") String username) {
}
