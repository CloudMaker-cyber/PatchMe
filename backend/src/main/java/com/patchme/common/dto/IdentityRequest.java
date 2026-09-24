package com.patchme.common.dto;

import com.patchme.common.enums.IdentityMode;
import jakarta.validation.constraints.NotNull;

/** 身份变更请求：只允许 PUBLIC -> ANONYMOUS 方向，Service 层强制。 */
public record IdentityRequest(@NotNull(message = "请选择身份模式") IdentityMode mode) {
}
