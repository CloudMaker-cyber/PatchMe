package com.patchme.reply;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.IdentityMode;
import com.patchme.common.exception.BusinessException;
import com.patchme.post.PostService;
import com.patchme.post.entity.PostEntity;
import com.patchme.reply.dto.CreateReplyRequest;
import com.patchme.reply.entity.ReplyEntity;
import com.patchme.reply.mapper.ReplyMapper;
import com.patchme.reply.vo.PublicReplyVO;
import com.patchme.reply.vo.ReplyVoMapper;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserSettingsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回复业务。规则（docs/v3/01/02）：
 * - 评论已关闭的帖子拒绝回复；
 * - "有帮助"只能由楼主标记，且每帖至多一条（先清后设，同一事务）；
 * - 回复身份同样受"匿名永不转公开"闸门约束，且只有作者本人可改。
 */
@Service
public class ReplyService {

    private final ReplyMapper replyMapper;
    private final PostService postService;
    private final UserSettingsMapper settingsMapper;

    public ReplyService(ReplyMapper replyMapper, PostService postService, UserSettingsMapper settingsMapper) {
        this.replyMapper = replyMapper;
        this.postService = postService;
        this.settingsMapper = settingsMapper;
    }

    public PublicReplyVO create(Long userId, Long postId, CreateReplyRequest request) {
        PostEntity post = postService.requireVisiblePost(postId);
        if (post.getCommentsClosedAt() != null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该帖评论已关闭");
        }
        ReplyEntity reply = new ReplyEntity();
        reply.setPostId(postId);
        reply.setAuthorId(userId);
        reply.setIdentityMode(resolveIdentity(userId, request.identity()).name());
        reply.setBody(request.body().trim());
        reply.setIsHelpful(false);
        replyMapper.insert(reply);
        return ReplyVoMapper.toPublicVO(replyMapper.selectRowById(reply.getId()));
    }

    /** 楼主标记/取消"有帮助"。非楼主 403；标记新的自动取消旧的。 */
    @Transactional
    public void setHelpful(Long userId, Long replyId, boolean helpful) {
        ReplyEntity reply = requireVisibleReply(replyId);
        PostEntity post = postService.requireVisiblePost(reply.getPostId());
        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有楼主可以标记有帮助");
        }
        if (helpful) {
            replyMapper.update(null, Wrappers.<ReplyEntity>lambdaUpdate()
                    .eq(ReplyEntity::getPostId, reply.getPostId())
                    .ne(ReplyEntity::getId, replyId)
                    .eq(ReplyEntity::getIsHelpful, true)
                    .set(ReplyEntity::getIsHelpful, false));
        }
        reply.setIsHelpful(helpful);
        replyMapper.updateById(reply);
    }

    /** 作者本人将自己的回复转匿名（匿名不可转公开）。 */
    public void changeIdentity(Long userId, Long replyId, IdentityMode mode) {
        ReplyEntity reply = requireVisibleReply(replyId);
        if (!reply.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有作者可以修改该回复");
        }
        PostService.applyIdentityChange(IdentityMode.valueOf(reply.getIdentityMode()), mode, m -> {
            reply.setIdentityMode(m.name());
            replyMapper.updateById(reply);
        });
    }

    private ReplyEntity requireVisibleReply(Long replyId) {
        ReplyEntity reply = replyMapper.selectById(replyId);
        if (reply == null || reply.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回复不存在或已删除");
        }
        return reply;
    }

    private IdentityMode resolveIdentity(Long userId, IdentityMode requested) {
        if (requested != null) {
            return requested;
        }
        UserSettingsEntity settings = settingsMapper.selectById(userId);
        if (settings == null || settings.getDefaultIdentityMode() == null) {
            return IdentityMode.ANONYMOUS;
        }
        return IdentityMode.valueOf(settings.getDefaultIdentityMode());
    }
}
