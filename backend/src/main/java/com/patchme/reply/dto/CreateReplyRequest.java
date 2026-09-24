package com.patchme.reply.dto;

import com.patchme.common.enums.IdentityMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 回复请求。identity 可省略（用账号默认身份）。 */
public record CreateReplyRequest(
        @NotBlank(message = "回复不能为空") @Size(max = 2000, message = "回复最多 2000 字") String body,
        IdentityMode identity
) {
}
