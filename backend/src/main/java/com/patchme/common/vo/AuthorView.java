package com.patchme.common.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 作者展示对象 —— 匿名安全的前端唯一出口（docs/v3/02）：
 * ANONYMOUS 构造时只有 mode 字段有值，NON_NULL 序列化后即为 {"mode":"anonymous"}，
 * 不存在昵称/头像/学校/稳定标识可泄露的空间。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorView(String mode, String username, String nickname, String avatarUrl) {

    public static AuthorView anonymous() {
        return new AuthorView("anonymous", null, null, null);
    }

    public static AuthorView publicAuthor(String username, String nickname, String avatarUrl) {
        return new AuthorView("public", username, nickname, avatarUrl);
    }
}
