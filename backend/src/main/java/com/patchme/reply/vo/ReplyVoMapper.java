package com.patchme.reply.vo;

import com.patchme.post.vo.PostVoMapper;

/** 回复投影行 -> 对外 VO；匿名坍缩规则复用 PostVoMapper.authorOf。 */
public final class ReplyVoMapper {

    private ReplyVoMapper() {
    }

    public static PublicReplyVO toPublicVO(ReplyRow row) {
        return new PublicReplyVO(
                row.getId(),
                row.getPostId(),
                PostVoMapper.authorOf(row.getIdentityMode(), row.getUsername(), row.getNickname(), row.getAvatarUrl()),
                row.getBody(),
                row.getCreatedAt(),
                Boolean.TRUE.equals(row.getIsHelpful()));
    }

    public static MineReplyVO toMineVO(ReplyRow row) {
        return new MineReplyVO(
                row.getId(),
                row.getPostId(),
                row.getIdentityMode(),
                row.getBody(),
                row.getCreatedAt(),
                Boolean.TRUE.equals(row.getIsHelpful()));
    }
}
