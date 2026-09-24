package com.patchme.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.IdentityMode;
import com.patchme.common.enums.Intent;
import com.patchme.common.exception.BusinessException;
import com.patchme.dict.MajorMapper;
import com.patchme.dict.SchoolMapper;
import com.patchme.dict.TagEntity;
import com.patchme.dict.TagMapper;
import com.patchme.interaction.entity.BookmarkEntity;
import com.patchme.interaction.entity.PostSupportEntity;
import com.patchme.interaction.mapper.BookmarkMapper;
import com.patchme.interaction.mapper.PostSupportMapper;
import com.patchme.post.dto.CreatePostRequest;
import com.patchme.post.entity.PostEntity;
import com.patchme.post.entity.PostTagEntity;
import com.patchme.post.mapper.PostMapper;
import com.patchme.post.mapper.PostTagMapper;
import com.patchme.post.vo.PostDetailVO;
import com.patchme.post.vo.PostRow;
import com.patchme.post.vo.PublicPostVO;
import com.patchme.reply.mapper.ReplyMapper;
import com.patchme.support.MpTableInfo;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserSettingsMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 帖子业务规则单测：归属校验、身份闸门、匿名坍缩、字典校验、默认身份。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;
    @Mock
    private PostTagMapper postTagMapper;
    @Mock
    private ReplyMapper replyMapper;
    @Mock
    private PostSupportMapper supportMapper;
    @Mock
    private BookmarkMapper bookmarkMapper;
    @Mock
    private SchoolMapper schoolMapper;
    @Mock
    private MajorMapper majorMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private UserSettingsMapper settingsMapper;

    @InjectMocks
    private PostService postService;

    @BeforeAll
    static void registerTableInfo() {
        MpTableInfo.init(PostTagEntity.class, PostSupportEntity.class, BookmarkEntity.class);
    }

    private static PostRow row(Long id, String identityMode, String username) {
        PostRow r = new PostRow();
        r.setId(id);
        r.setIntent("ADVICE");
        r.setTitle("t");
        r.setBody("b");
        r.setIdentityMode(identityMode);
        r.setUsername(username);
        r.setNickname(username == null ? null : "小满");
        r.setCreatedAt(java.time.LocalDateTime.now());
        r.setReplyCount(0L);
        r.setSupportCount(2L);
        r.setHelpful(false);
        return r;
    }

    private static PostEntity post(Long id, Long authorId, IdentityMode mode) {
        PostEntity p = new PostEntity();
        p.setId(id);
        p.setAuthorId(authorId);
        p.setIdentityMode(mode.name());
        p.setIntent("ADVICE");
        p.setBody("b");
        p.setStatus("NORMAL");
        return p;
    }

    @Test
    void createRejectsInactiveTag() {
        TagEntity inactive = new TagEntity();
        inactive.setId(9L);
        inactive.setActive(false);
        when(tagMapper.selectById(9L)).thenReturn(inactive);
        CreatePostRequest request = new CreatePostRequest(Intent.ADVICE, "t", "b", null, null, List.of(9L), null);
        assertThatThrownBy(() -> postService.create(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("标签选项不合法");
    }

    @Test
    void createFallsBackToAnonymousWhenNoSettings() {
        AtomicLong seq = new AtomicLong(100);
        when(postMapper.insert(any(PostEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, PostEntity.class).setId(seq.incrementAndGet());
            return 1;
        });

        Long id = postService.create(7L,
                new CreatePostRequest(Intent.VENT, " 标题 ", " 正文 ", null, null, List.of(), null));

        assertThat(id).isEqualTo(101L);
        ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
        verify(postMapper).insert(captor.capture());
        PostEntity saved = captor.getValue();
        assertThat(saved.getIdentityMode()).isEqualTo("ANONYMOUS");
        assertThat(saved.getAuthorId()).isEqualTo(7L);
        assertThat(saved.getTitle()).isEqualTo("标题");
        assertThat(saved.getBody()).isEqualTo("正文");
    }

    @Test
    void createUsesAccountDefaultIdentity() {
        UserSettingsEntity settings = new UserSettingsEntity();
        settings.setUserId(7L);
        settings.setDefaultIdentityMode("PUBLIC");
        when(settingsMapper.selectById(7L)).thenReturn(settings);
        when(postMapper.insert(any(PostEntity.class))).thenReturn(1);

        postService.create(7L, new CreatePostRequest(Intent.VENT, null, "b", null, null, null, null));

        ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
        verify(postMapper).insert(captor.capture());
        assertThat(captor.getValue().getIdentityMode()).isEqualTo("PUBLIC");
    }

    @Test
    void feedCollapsesAnonymousAuthors() {
        when(postMapper.selectFeed(null, null, null, null, false, 100))
                .thenReturn(List.of(row(1L, "ANONYMOUS", null), row(2L, "PUBLIC", "xiaoman")));
        when(postTagMapper.selectList(any())).thenReturn(List.of());

        List<PublicPostVO> feed = postService.feed(null, null, null, null, false, 100);

        assertThat(feed).hasSize(2);
        assertThat(feed.get(0).author().mode()).isEqualTo("anonymous");
        assertThat(feed.get(0).author().username()).isNull();
        assertThat(feed.get(1).author().mode()).isEqualTo("public");
        assertThat(feed.get(1).author().username()).isEqualTo("xiaoman");
        assertThat(feed.get(1).supportCount()).isEqualTo(2);
    }

    @Test
    void publicModeWithoutProfileStillCollapsesToAnonymous() {
        // 防御：即使 JOIN 意外缺资料，也不允许输出半截身份
        when(postMapper.selectFeed(null, null, null, null, false, 100))
                .thenReturn(List.of(row(1L, "PUBLIC", null)));
        when(postTagMapper.selectList(any())).thenReturn(List.of());

        List<PublicPostVO> feed = postService.feed(null, null, null, null, false, 100);
        assertThat(feed.get(0).author().mode()).isEqualTo("anonymous");
    }

    @Test
    void detailThrowsNotFoundForMissingPost() {
        when(postMapper.selectRowById(404L)).thenReturn(null);
        assertThatThrownBy(() -> postService.detail(404L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void guestDetailFlagsAreAllFalse() {
        when(postMapper.selectRowById(1L)).thenReturn(row(1L, "ANONYMOUS", null));
        when(replyMapper.selectVisibleByPostId(1L)).thenReturn(List.of());
        when(postTagMapper.selectList(any())).thenReturn(List.of());

        PostDetailVO detail = postService.detail(1L, null);
        assertThat(detail.isAuthorOfPost()).isFalse();
        assertThat(detail.supportedByMe()).isFalse();
        assertThat(detail.bookmarkedByMe()).isFalse();
    }

    @Test
    void detailComputesOwnerAndInteractionFlagsFromJwtUser() {
        when(postMapper.selectRowById(1L)).thenReturn(row(1L, "ANONYMOUS", null));
        when(postMapper.selectById(1L)).thenReturn(post(1L, 7L, IdentityMode.ANONYMOUS));
        when(replyMapper.selectVisibleByPostId(1L)).thenReturn(List.of());
        when(supportMapper.exists(any())).thenReturn(true);
        when(bookmarkMapper.exists(any())).thenReturn(false);
        PostTagEntity pt = new PostTagEntity();
        pt.setPostId(1L);
        pt.setTagId(3L);
        when(postTagMapper.selectList(any())).thenReturn(List.of(pt));

        PostDetailVO detail = postService.detail(1L, new LoginUser(7L, "USER"));
        assertThat(detail.isAuthorOfPost()).isTrue();
        assertThat(detail.supportedByMe()).isTrue();
        assertThat(detail.bookmarkedByMe()).isFalse();
        assertThat(detail.post().tagIds()).containsExactly(3L);
        // 匿名帖楼主：前端拿不到任何归属线索
        assertThat(detail.post().author().mode()).isEqualTo("anonymous");
    }

    @Test
    void onlyOwnerMayCloseComments() {
        when(postMapper.selectById(1L)).thenReturn(post(1L, 8L, IdentityMode.ANONYMOUS));
        assertThatThrownBy(() -> postService.setCommentsClosed(7L, 1L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有楼主可以执行该操作");
        verify(postMapper, never()).updateById(any(PostEntity.class));
    }

    @Test
    void ownerClosesAndReopensComments() {
        when(postMapper.selectById(1L)).thenReturn(post(1L, 7L, IdentityMode.ANONYMOUS));

        postService.setCommentsClosed(7L, 1L, true);
        ArgumentCaptor<PostEntity> closed = ArgumentCaptor.forClass(PostEntity.class);
        verify(postMapper).updateById(closed.capture());
        assertThat(closed.getValue().getCommentsClosedAt()).isNotNull();

        postService.setCommentsClosed(7L, 1L, false);
        verify(postMapper, org.mockito.Mockito.times(2)).updateById(closed.capture());
        assertThat(closed.getAllValues().get(1).getCommentsClosedAt()).isNull();
    }

    @Test
    void anonymousContentCanNeverBecomePublic() {
        when(postMapper.selectById(1L)).thenReturn(post(1L, 7L, IdentityMode.ANONYMOUS));
        assertThatThrownBy(() -> postService.changeIdentity(7L, 1L, IdentityMode.PUBLIC))
                .isInstanceOf(BusinessException.class)
                .hasMessage("匿名内容发布后不可改为公开");
        verify(postMapper, never()).updateById(any(PostEntity.class));
    }

    @Test
    void publicContentMayDowngradeToAnonymous() {
        when(postMapper.selectById(1L)).thenReturn(post(1L, 7L, IdentityMode.PUBLIC));
        postService.changeIdentity(7L, 1L, IdentityMode.ANONYMOUS);
        ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
        verify(postMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIdentityMode()).isEqualTo("ANONYMOUS");
    }

    @Test
    void sameIdentityChangeIsNoop() {
        when(postMapper.selectById(1L)).thenReturn(post(1L, 7L, IdentityMode.PUBLIC));
        postService.changeIdentity(7L, 1L, IdentityMode.PUBLIC);
        verify(postMapper, never()).updateById(any(PostEntity.class));
    }
}
