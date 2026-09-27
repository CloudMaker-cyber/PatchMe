package com.patchme.moderation.vo;

/**
 * 拉黑列表项（仅本人可见）：能进拉黑名单的都是当初以公开身份被拉黑的账户，
 * 因此这里返回公开用户名/昵称不违反匿名规则——匿名内容本就无法被拉黑。
 */
public record BlockVO(Long userId, String username, String nickname, String avatarUrl) {
}
