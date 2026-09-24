package com.patchme.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.exception.BusinessException;
import com.patchme.post.mapper.PostMapper;
import com.patchme.post.PostService;
import com.patchme.post.vo.MinePostVO;
import com.patchme.post.vo.PostRow;
import com.patchme.post.vo.PostVoMapper;
import com.patchme.post.vo.PublicPostVO;
import com.patchme.reply.mapper.ReplyMapper;
import com.patchme.reply.vo.MineReplyVO;
import com.patchme.reply.vo.PublicReplyVO;
import com.patchme.reply.vo.ReplyVoMapper;
import com.patchme.user.entity.UserProfileEntity;
import com.patchme.user.mapper.UserProfileMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 公开主页与"我的内容"查询。
 * 关键边界（docs/v3/02）：
 * - 公开主页强制 identity_mode = PUBLIC —— 匿名内容在这里永远不可能出现；
 * - "我的"接口只按 JWT 用户 id 查询，本人可见自己的匿名内容，但不存在"查他人 mine"的路径；
 * - 用户不存在与从未公开统一返回空列表语义（404 仅用于 username 不存在）。
 */
@Service
public class UserService {

    private static final int PAGE_LIMIT = 100;

    private final UserProfileMapper profileMapper;
    private final PostMapper postMapper;
    private final ReplyMapper replyMapper;
    private final PostService postService;

    public UserService(UserProfileMapper profileMapper, PostMapper postMapper, ReplyMapper replyMapper,
                       PostService postService) {
        this.profileMapper = profileMapper;
        this.postMapper = postMapper;
        this.replyMapper = replyMapper;
        this.postService = postService;
    }

    public record ProfileVO(String username, String nickname, String avatarUrl, String bio) {
    }

    public record ProfilePageVO(ProfileVO profile, List<PublicPostVO> posts, List<PublicReplyVO> replies) {
    }

    /** 公开主页：只可能看到 PUBLIC 且未删除的内容。 */
    public ProfilePageVO profile(String username) {
        UserProfileEntity profile = profileMapper.selectOne(
                Wrappers.<UserProfileEntity>lambdaQuery().eq(UserProfileEntity::getUsername, username));
        if (profile == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        List<PostRow> postRows = postMapper.selectRowsByAuthor(profile.getUserId(), true, PAGE_LIMIT);
        Map<Long, List<Long>> tags = postService.tagsOf(postRows.stream().map(PostRow::getId).toList());
        List<PublicPostVO> posts = postRows.stream()
                .map(r -> PostVoMapper.toPublicVO(r, tags.getOrDefault(r.getId(), List.of()))).toList();
        List<PublicReplyVO> replies = replyMapper.selectRowsByAuthor(profile.getUserId(), true, PAGE_LIMIT).stream()
                .map(ReplyVoMapper::toPublicVO).toList();
        return new ProfilePageVO(
                new ProfileVO(profile.getUsername(), profile.getNickname(), profile.getAvatarUrl(), profile.getBio()),
                posts, replies);
    }

    /** 我的帖子（含本人为匿名发布的内容，带 identityMode 供本人区分）。 */
    public List<MinePostVO> minePosts(Long userId) {
        List<PostRow> rows = postMapper.selectRowsByAuthor(userId, false, PAGE_LIMIT);
        Map<Long, List<Long>> tags = postService.tagsOf(rows.stream().map(PostRow::getId).toList());
        return rows.stream().map(r -> PostVoMapper.toMineVO(r, tags.getOrDefault(r.getId(), List.of()))).toList();
    }

    public List<MineReplyVO> mineReplies(Long userId) {
        return replyMapper.selectRowsByAuthor(userId, false, PAGE_LIMIT).stream()
                .map(ReplyVoMapper::toMineVO).toList();
    }

    /** 我的收藏：都是公开可见的帖子 VO。 */
    public List<PublicPostVO> myBookmarks(Long userId) {
        List<PostRow> rows = postMapper.selectBookmarkedRows(userId, PAGE_LIMIT);
        Map<Long, List<Long>> tags = postService.tagsOf(rows.stream().map(PostRow::getId).toList());
        return rows.stream().map(r -> PostVoMapper.toPublicVO(r, tags.getOrDefault(r.getId(), List.of()))).toList();
    }
}
