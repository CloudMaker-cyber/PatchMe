package com.patchme.post.vo;

import com.patchme.reply.vo.PublicReplyVO;
import java.util.List;

/**
 * 详情响应。isAuthorOfPost/supportedByMe/bookmarkedByMe 是按"当前 JWT 身份"计算的布尔标记，
 * 本身不携带任何身份标识（匿名帖楼主是谁，前端无从得知）。游客全为 false。
 */
public record PostDetailVO(
        PublicPostVO post,
        List<PublicReplyVO> replies,
        boolean isAuthorOfPost,
        boolean supportedByMe,
        boolean bookmarkedByMe
) {
}
