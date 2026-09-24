package com.patchme.post.vo;

import com.patchme.common.vo.AuthorView;
import java.util.List;

/**
 * 投影行 -> 对外 VO 的唯一转换点。
 * 匿名规则集中在这里：identity_mode 非 PUBLIC、或 PUBLIC 但资料缺失，一律坍缩为 anonymous ——
 * 宁可少显示，不可多泄露。
 */
public final class PostVoMapper {

    private PostVoMapper() {
    }

    public static AuthorView authorOf(String identityMode, String username, String nickname, String avatarUrl) {
        if (!"PUBLIC".equals(identityMode) || username == null) {
            return AuthorView.anonymous();
        }
        return AuthorView.publicAuthor(username, nickname, avatarUrl);
    }

    public static PublicPostVO toPublicVO(PostRow row, List<Long> tagIds) {
        return new PublicPostVO(
                row.getId(),
                authorOf(row.getIdentityMode(), row.getUsername(), row.getNickname(), row.getAvatarUrl()),
                row.getIntent(),
                row.getTitle(),
                row.getBody(),
                tagIds,
                row.getCreatedAt(),
                row.getCommentsClosedAt() != null,
                row.getReplyCount() == null ? 0 : row.getReplyCount(),
                row.getSupportCount() == null ? 0 : row.getSupportCount());
    }

    /** "我的内容"专用：本人可见自己的 identityMode，但同样不带 authorId。 */
    public static MinePostVO toMineVO(PostRow row, List<Long> tagIds) {
        return new MinePostVO(
                row.getId(),
                row.getIdentityMode(),
                row.getIntent(),
                row.getTitle(),
                row.getBody(),
                tagIds,
                row.getCreatedAt(),
                row.getCommentsClosedAt() != null,
                row.getReplyCount() == null ? 0 : row.getReplyCount(),
                row.getSupportCount() == null ? 0 : row.getSupportCount());
    }
}
