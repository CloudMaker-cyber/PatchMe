package com.patchme.reply.vo;

import com.patchme.common.vo.AuthorView;
import java.time.LocalDateTime;

/** 公开回复 VO：与 PublicPostVO 同样的匿名规则，不含 authorId。 */
public record PublicReplyVO(
        Long id,
        Long postId,
        AuthorView author,
        String body,
        LocalDateTime createdAt,
        boolean isHelpful
) {
}
