package com.patchme.reply.vo;

import java.time.LocalDateTime;

/** "我的回复" VO：仅本人，允许 identityMode。 */
public record MineReplyVO(
        Long id,
        Long postId,
        String identityMode,
        String body,
        LocalDateTime createdAt,
        boolean isHelpful
) {
}
