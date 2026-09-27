package com.patchme.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.IdentityMode;
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
import com.patchme.user.dto.UpdateSettingsRequest;
import com.patchme.user.entity.UserProfileEntity;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserProfileMapper;
import com.patchme.user.mapper.UserSettingsMapper;
import java.time.LocalDateTime;
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
    private final UserSettingsMapper settingsMapper;
    private final PostMapper postMapper;
    private final ReplyMapper replyMapper;
    private final PostService postService;

    public UserService(UserProfileMapper profileMapper, UserSettingsMapper settingsMapper,
                       PostMapper postMapper, ReplyMapper replyMapper, PostService postService) {
        this.profileMapper = profileMapper;
        this.settingsMapper = settingsMapper;
        this.postMapper = postMapper;
        this.replyMapper = replyMapper;
        this.postService = postService;
    }

    public record ProfileVO(String username, String nickname, String avatarUrl, String bio) {
    }

    public record ProfilePageVO(ProfileVO profile, List<PublicPostVO> posts, List<PublicReplyVO> replies) {
    }

    /** 账号设置（仅本人）：枚举以名称字符串出参，缺行时回落到安全默认（首次匿名、通知/历史开）。 */
    public record SettingsVO(String defaultIdentityMode, boolean replyNotificationEnabled, boolean historyEnabled) {
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

    /** 我的浏览历史：复用帖子公开投影，已删除的帖自动不出现。 */
    public List<PublicPostVO> myHistory(Long userId) {
        List<PostRow> rows = postMapper.selectHistoryRows(userId, PAGE_LIMIT);
        Map<Long, List<Long>> tags = postService.tagsOf(rows.stream().map(PostRow::getId).toList());
        return rows.stream().map(r -> PostVoMapper.toPublicVO(r, tags.getOrDefault(r.getId(), List.of()))).toList();
    }

    public SettingsVO getSettings(Long userId) {
        UserSettingsEntity settings = settingsMapper.selectById(userId);
        if (settings == null) {
            return new SettingsVO(IdentityMode.ANONYMOUS.name(), true, true);
        }
        return new SettingsVO(
                settings.getDefaultIdentityMode() == null ? IdentityMode.ANONYMOUS.name()
                        : settings.getDefaultIdentityMode(),
                !Boolean.FALSE.equals(settings.getReplyNotificationEnabled()),
                !Boolean.FALSE.equals(settings.getHistoryEnabled()));
    }

    /** 部分更新：只应用请求里非 null 的字段。设置行缺失时补建（历史账号兜底）。 */
    public SettingsVO updateSettings(Long userId, UpdateSettingsRequest request) {
        UserSettingsEntity settings = settingsMapper.selectById(userId);
        if (settings == null) {
            settings = new UserSettingsEntity();
            settings.setUserId(userId);
            settings.setDefaultIdentityMode(IdentityMode.ANONYMOUS.name());
            settings.setReplyNotificationEnabled(true);
            settings.setHistoryEnabled(true);
            settings.setUpdatedAt(LocalDateTime.now());
            settingsMapper.insert(settings);
        }
        if (request.defaultIdentityMode() != null) {
            settings.setDefaultIdentityMode(request.defaultIdentityMode().name());
        }
        if (request.replyNotificationEnabled() != null) {
            settings.setReplyNotificationEnabled(request.replyNotificationEnabled());
        }
        if (request.historyEnabled() != null) {
            settings.setHistoryEnabled(request.historyEnabled());
        }
        settingsMapper.updateById(settings);
        return getSettings(userId);
    }
}
