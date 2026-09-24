package com.patchme.post.vo;

import com.patchme.common.vo.AuthorView;
import java.time.LocalDateTime;
import java.util.List;

/** 公开帖子 VO（docs/v3/02 规定的白名单字段集）。 */
public record PublicPostVO(
        Long id,
        AuthorView author,
        String intent,
        String title,
        String body,
        List<Long> tagIds,
        LocalDateTime createdAt,
        boolean commentsClosed,
        long replyCount,
        long supportCount
) {
}
