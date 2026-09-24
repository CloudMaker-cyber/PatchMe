package com.patchme.reply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.IdentityMode;
import com.patchme.common.exception.BusinessException;
import com.patchme.post.PostService;
import com.patchme.post.entity.PostEntity;
import com.patchme.reply.dto.CreateReplyRequest;
import com.patchme.reply.entity.ReplyEntity;
import com.patchme.reply.mapper.ReplyMapper;
import com.patchme.reply.vo.PublicReplyVO;
import com.patchme.support.MpTableInfo;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserSettingsMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 回复规则单测：关帖拒答、楼主 exclusive-helpful、身份单向闸。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReplyServiceTest {

    @Mock
    private ReplyMapper replyMapper;
    @Mock
    private PostService postService;
    @Mock
    private UserSettingsMapper settingsMapper;

    @InjectMocks
    private ReplyService replyService;

    @BeforeAll
    static void registerTableInfo() {
        MpTableInfo.init(ReplyEntity.class);
    }

    private static PostEntity post(Long id, Long authorId, boolean closed) {
        PostEntity p = new PostEntity();
        p.setId(id);
        p.setAuthorId(authorId);
        p.setIdentityMode("ANONYMOUS");
        p.setStatus("NORMAL");
        p.setCommentsClosedAt(closed ? LocalDateTime.now() : null);
        return p;
    }

    private static ReplyEntity reply(Long id, Long postId, Long authorId, IdentityMode mode, boolean helpful) {
        ReplyEntity r = new ReplyEntity();
        r.setId(id);
        r.setPostId(postId);
        r.setAuthorId(authorId);
        r.setIdentityMode(mode.name());
        r.setBody("b");
        r.setIsHelpful(helpful);
        return r;
    }

    @Test
    void closedPostRejectsReplies() {
        when(postService.requireVisiblePost(1L)).thenReturn(post(1L, 9L, true));
        assertThatThrownBy(() -> replyService.create(7L, 1L, new CreateReplyRequest("hi", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该帖评论已关闭");
    }

    @Test
    void createUsesDefaultIdentityAndAnonymizesVO() {
        when(postService.requireVisiblePost(1L)).thenReturn(post(1L, 9L, false));
        UserSettingsEntity settings = new UserSettingsEntity();
        settings.setDefaultIdentityMode("PUBLIC");
        when(settingsMapper.selectById(7L)).thenReturn(settings);
        when(replyMapper.insert(any(ReplyEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, ReplyEntity.class).setId(50L);
            return 1;
        });
        com.patchme.reply.vo.ReplyRow row = new com.patchme.reply.vo.ReplyRow();
        row.setId(50L);
        row.setPostId(1L);
        row.setBody("hi");
        row.setIdentityMode("PUBLIC");
        row.setIsHelpful(false);
        row.setCreatedAt(LocalDateTime.now());
        // 投影缺 username（资料缺失）-> 展示仍必须匿名
        when(replyMapper.selectRowById(50L)).thenReturn(row);

        PublicReplyVO vo = replyService.create(7L, 1L, new CreateReplyRequest(" hi ", null));

        ArgumentCaptor<ReplyEntity> captor = ArgumentCaptor.forClass(ReplyEntity.class);
        verify(replyMapper).insert(captor.capture());
        assertThat(captor.getValue().getIdentityMode()).isEqualTo("PUBLIC");
        assertThat(captor.getValue().getBody()).isEqualTo("hi");
        assertThat(vo.author().mode()).isEqualTo("anonymous");
    }

    @Test
    void onlyPostAuthorMayMarkHelpful() {
        when(replyMapper.selectById(2L)).thenReturn(reply(2L, 1L, 8L, IdentityMode.ANONYMOUS, false));
        when(postService.requireVisiblePost(1L)).thenReturn(post(1L, 9L, false));
        assertThatThrownBy(() -> replyService.setHelpful(7L, 2L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有楼主可以标记有帮助");
        verify(replyMapper, never()).updateById(any(ReplyEntity.class));
    }

    @Test
    void markingHelpfulClearsPreviousOne() {
        ReplyEntity target = reply(2L, 1L, 5L, IdentityMode.ANONYMOUS, false);
        when(replyMapper.selectById(2L)).thenReturn(target);
        when(postService.requireVisiblePost(1L)).thenReturn(post(1L, 7L, false));

        replyService.setHelpful(7L, 2L, true);

        // 先清后设，且清除语句排除目标本身
        verify(replyMapper).update(any(), any());
        ArgumentCaptor<ReplyEntity> captor = ArgumentCaptor.forClass(ReplyEntity.class);
        verify(replyMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsHelpful()).isTrue();
    }

    @Test
    void unmarkingHelpfulDoesNotClearOthers() {
        ReplyEntity target = reply(2L, 1L, 5L, IdentityMode.ANONYMOUS, true);
        when(replyMapper.selectById(2L)).thenReturn(target);
        when(postService.requireVisiblePost(1L)).thenReturn(post(1L, 7L, false));

        replyService.setHelpful(7L, 2L, false);

        verify(replyMapper, never()).update(any(), any());
        ArgumentCaptor<ReplyEntity> captor = ArgumentCaptor.forClass(ReplyEntity.class);
        verify(replyMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsHelpful()).isFalse();
    }

    @Test
    void helpfulOnMissingReplyIs404() {
        when(replyMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> replyService.setHelpful(7L, 404L, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void onlyReplyAuthorMayChangeIdentity() {
        when(replyMapper.selectById(2L)).thenReturn(reply(2L, 1L, 8L, IdentityMode.PUBLIC, false));
        assertThatThrownBy(() -> replyService.changeIdentity(7L, 2L, IdentityMode.ANONYMOUS))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有作者可以修改该回复");
    }

    @Test
    void anonymousReplyCanNeverBecomePublic() {
        when(replyMapper.selectById(2L)).thenReturn(reply(2L, 1L, 7L, IdentityMode.ANONYMOUS, false));
        assertThatThrownBy(() -> replyService.changeIdentity(7L, 2L, IdentityMode.PUBLIC))
                .isInstanceOf(BusinessException.class)
                .hasMessage("匿名内容发布后不可改为公开");
        verify(replyMapper, never()).updateById(any(ReplyEntity.class));
    }

    @Test
    void publicReplyMayDowngradeToAnonymous() {
        when(replyMapper.selectById(2L)).thenReturn(reply(2L, 1L, 7L, IdentityMode.PUBLIC, false));
        replyService.changeIdentity(7L, 2L, IdentityMode.ANONYMOUS);
        ArgumentCaptor<ReplyEntity> captor = ArgumentCaptor.forClass(ReplyEntity.class);
        verify(replyMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIdentityMode()).isEqualTo("ANONYMOUS");
    }
}
