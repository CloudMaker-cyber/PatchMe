package com.patchme.post;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.auth.LoginUser;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.IdentityMode;
import com.patchme.common.exception.BusinessException;
import com.patchme.dict.MajorEntity;
import com.patchme.dict.MajorMapper;
import com.patchme.dict.SchoolEntity;
import com.patchme.dict.SchoolMapper;
import com.patchme.dict.TagEntity;
import com.patchme.dict.TagMapper;
import com.patchme.interaction.mapper.BookmarkMapper;
import com.patchme.interaction.mapper.PostSupportMapper;
import com.patchme.interaction.entity.BookmarkEntity;
import com.patchme.interaction.entity.PostSupportEntity;
import com.patchme.post.dto.CreatePostRequest;
import com.patchme.post.entity.PostEntity;
import com.patchme.post.entity.PostTagEntity;
import com.patchme.post.mapper.PostMapper;
import com.patchme.post.mapper.PostTagMapper;
import com.patchme.post.vo.PostDetailVO;
import com.patchme.post.vo.PostRow;
import com.patchme.post.vo.PostVoMapper;
import com.patchme.post.vo.PublicPostVO;
import com.patchme.reply.mapper.ReplyMapper;
import com.patchme.reply.vo.PublicReplyVO;
import com.patchme.reply.vo.ReplyVoMapper;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserSettingsMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 帖子核心业务。安全要点（docs/v3/02）：
 * - 当前用户身份只来自 LoginUser（JWT），方法签名里不存在 userId 入参给前端伪造的机会；
 * - 楼主类操作先过 requireOwned；school/major 只参与写入与筛选，不出现在任何 VO；
 * - 匿名内容发布后永不转公开（防止匿名承诺被事后改变）。
 */
@Service
public class PostService {

    private final PostMapper postMapper;
    private final PostTagMapper postTagMapper;
    private final ReplyMapper replyMapper;
    private final PostSupportMapper supportMapper;
    private final BookmarkMapper bookmarkMapper;
    private final SchoolMapper schoolMapper;
    private final MajorMapper majorMapper;
    private final TagMapper tagMapper;
    private final UserSettingsMapper settingsMapper;

    public PostService(PostMapper postMapper, PostTagMapper postTagMapper, ReplyMapper replyMapper,
                       PostSupportMapper supportMapper, BookmarkMapper bookmarkMapper,
                       SchoolMapper schoolMapper, MajorMapper majorMapper, TagMapper tagMapper,
                       UserSettingsMapper settingsMapper) {
        this.postMapper = postMapper;
        this.postTagMapper = postTagMapper;
        this.replyMapper = replyMapper;
        this.supportMapper = supportMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.schoolMapper = schoolMapper;
        this.majorMapper = majorMapper;
        this.tagMapper = tagMapper;
        this.settingsMapper = settingsMapper;
    }

    /** 首页流/筛选：bucket 与排序全部在 SQL 内完成，这里只做投影转换。 */
    public List<PublicPostVO> feed(Long schoolId, Long majorId, String intent, List<Long> tagIds,
                                   boolean resolved, int limit) {
        List<PostRow> rows = postMapper.selectFeed(schoolId, majorId, intent, tagIds, resolved, clampLimit(limit));
        Map<Long, List<Long>> tags = tagsOf(rows.stream().map(PostRow::getId).toList());
        return rows.stream().map(r -> PostVoMapper.toPublicVO(r, tags.getOrDefault(r.getId(), List.of()))).toList();
    }

    /** 详情：楼主/支持/收藏标记按"当前登录者"计算；游客 LoginUser 为 null，全 false。 */
    public PostDetailVO detail(Long postId, LoginUser loginUser) {
        PostRow row = postMapper.selectRowById(postId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
        }
        List<PublicReplyVO> replies = replyMapper.selectVisibleByPostId(postId).stream()
                .map(ReplyVoMapper::toPublicVO).toList();
        Long uid = loginUser == null ? null : loginUser.userId();
        return new PostDetailVO(
                PostVoMapper.toPublicVO(row, tagIdsOf(postId)),
                replies,
                uid != null && uid.equals(authorIdOf(postId)),
                uid != null && supportMapper.exists(Wrappers.<PostSupportEntity>lambdaQuery()
                        .eq(PostSupportEntity::getPostId, postId).eq(PostSupportEntity::getUserId, uid)),
                uid != null && bookmarkMapper.exists(Wrappers.<BookmarkEntity>lambdaQuery()
                        .eq(BookmarkEntity::getPostId, postId).eq(BookmarkEntity::getUserId, uid)));
    }

    @Transactional
    public Long create(Long userId, CreatePostRequest request) {
        validateDict(request.schoolId(), request.majorId(), request.tagIds());
        PostEntity post = new PostEntity();
        post.setAuthorId(userId);
        post.setIdentityMode(resolveIdentity(userId, request.identity()).name());
        post.setIntent(request.intent().name());
        post.setTitle(request.title() == null ? "" : request.title().trim());
        post.setBody(request.body().trim());
        post.setSchoolId(request.schoolId());
        post.setMajorId(request.majorId());
        post.setStatus("NORMAL");
        postMapper.insert(post);
        for (Long tagId : request.tagIds() == null ? List.<Long>of() : request.tagIds()) {
            PostTagEntity pt = new PostTagEntity();
            pt.setPostId(post.getId());
            pt.setTagId(tagId);
            postTagMapper.insert(pt);
        }
        return post.getId();
    }

    /** 楼主开关评论。 */
    public void setCommentsClosed(Long userId, Long postId, boolean closed) {
        PostEntity post = requireOwned(postId, userId);
        post.setCommentsClosedAt(closed ? LocalDateTime.now() : null);
        postMapper.updateById(post);
    }

    /** 楼主变更帖子身份：只允许 PUBLIC -> ANONYMOUS；转后立即从公开主页消失（查询条件 identity_mode='PUBLIC'）。 */
    public void changeIdentity(Long userId, Long postId, IdentityMode mode) {
        PostEntity post = requireOwned(postId, userId);
        applyIdentityChange(IdentityMode.valueOf(post.getIdentityMode()), mode, m -> {
            post.setIdentityMode(m.name());
            postMapper.updateById(post);
        });
    }

    // ---------- 供 ReplyService / InteractionService 复用的内部规则 ----------

    /** 可见帖子（未删除、状态正常），否则 404。 */
    public PostEntity requireVisiblePost(Long postId) {
        PostEntity post = postMapper.selectById(postId);
        if (post == null || post.getDeletedAt() != null || !"NORMAL".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
        }
        return post;
    }

    /** 楼主校验：非楼主一律 FORBIDDEN，且响应不区分"不存在/无权限"以外的信息。 */
    PostEntity requireOwned(Long postId, Long userId) {
        PostEntity post = requireVisiblePost(postId);
        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有楼主可以执行该操作");
        }
        return post;
    }

    /** 身份单向闸：ANONYMOUS -> PUBLIC 永远拒绝（帖子与回复共用）。 */
    public static void applyIdentityChange(IdentityMode current, IdentityMode target,
                                           java.util.function.Consumer<IdentityMode> apply) {
        if (current == IdentityMode.ANONYMOUS && target == IdentityMode.PUBLIC) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "匿名内容发布后不可改为公开");
        }
        if (current == target) {
            return;
        }
        apply.accept(target);
    }

    private Long authorIdOf(Long postId) {
        PostEntity post = postMapper.selectById(postId);
        return post == null ? null : post.getAuthorId();
    }

    /** 发布身份：前端未指定时取账号默认身份；设置缺失兜底 ANONYMOUS（首次更安全）。 */
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

    private void validateDict(Long schoolId, Long majorId, List<Long> tagIds) {
        if (schoolId != null && !schoolActive(schoolId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "学校选项不合法");
        }
        if (majorId != null && !majorActive(majorId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "专业选项不合法");
        }
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                if (!tagActive(tagId)) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "标签选项不合法");
                }
            }
        }
    }

    private boolean schoolActive(Long id) {
        SchoolEntity s = schoolMapper.selectById(id);
        return s != null && Boolean.TRUE.equals(s.getActive());
    }

    private boolean majorActive(Long id) {
        MajorEntity m = majorMapper.selectById(id);
        return m != null && Boolean.TRUE.equals(m.getActive());
    }

    private boolean tagActive(Long id) {
        TagEntity t = tagMapper.selectById(id);
        return t != null && Boolean.TRUE.equals(t.getActive());
    }

    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), 200);
    }

    /** 批量取标签，避免列表 N+1。 */
    public Map<Long, List<Long>> tagsOf(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        List<PostTagEntity> rows = postTagMapper.selectList(
                Wrappers.<PostTagEntity>lambdaQuery().in(PostTagEntity::getPostId, postIds));
        Map<Long, List<Long>> map = new HashMap<>();
        for (PostTagEntity row : rows) {
            map.computeIfAbsent(row.getPostId(), k -> new ArrayList<>()).add(row.getTagId());
        }
        return map;
    }

    private List<Long> tagIdsOf(Long postId) {
        return postTagMapper.selectList(
                        Wrappers.<PostTagEntity>lambdaQuery().eq(PostTagEntity::getPostId, postId)).stream()
                .map(PostTagEntity::getTagId).toList();
    }
}
