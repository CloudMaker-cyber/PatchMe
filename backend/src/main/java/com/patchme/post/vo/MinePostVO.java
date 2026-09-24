package com.patchme.post.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * "我的内容" VO：仅本人接口可用，因此允许带 identityMode（本人需要知道自己是匿名还是公开发的）。
 * 依然不含 authorId 等他人不可见字段的必要——但这层的真正边界是路径必须登录且按 token id 查询。
 */
public record MinePostVO(
        Long id,
        String identityMode,
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
