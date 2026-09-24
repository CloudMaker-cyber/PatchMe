package com.patchme.auth;

/**
 * 认证主体：userId 只来自 JWT 验签结果，绝不来自请求参数。
 * 所有 Service 层归属校验都以它的 userId 为准。
 */
public record LoginUser(Long userId, String role) {
}
