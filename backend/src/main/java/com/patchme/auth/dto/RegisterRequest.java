package com.patchme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册请求：邮箱+密码+公开资料（username 供 /u/{username} 主页寻址）。 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "只能包含小写字母、数字、下划线，长度 4-20") String username,
        @NotBlank @Size(min = 1, max = 16) String nickname
) {
}
