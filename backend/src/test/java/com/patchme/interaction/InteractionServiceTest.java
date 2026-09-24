package com.patchme.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patchme.common.api.ErrorCode;
import com.patchme.common.exception.BusinessException;
import com.patchme.interaction.entity.BookmarkEntity;
import com.patchme.interaction.entity.PostSupportEntity;
import com.patchme.interaction.mapper.BookmarkMapper;
import com.patchme.interaction.mapper.PostSupportMapper;
import com.patchme.post.PostService;
import com.patchme.post.entity.PostEntity;
import com.patchme.support.MpTableInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 支持/收藏 toggle 语义单测。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InteractionServiceTest {

    @Mock
    private PostSupportMapper supportMapper;
    @Mock
    private BookmarkMapper bookmarkMapper;
    @Mock
    private PostService postService;

    @InjectMocks
    private InteractionService interactionService;

    @BeforeAll
    static void registerTableInfo() {
        MpTableInfo.init(PostSupportEntity.class, BookmarkEntity.class);
    }

    private static PostEntity visiblePost() {
        PostEntity p = new PostEntity();
        p.setId(1L);
        p.setAuthorId(9L);
        p.setIdentityMode("ANONYMOUS");
        p.setStatus("NORMAL");
        return p;
    }

    @Test
    void supportIsAddedWhenAbsent() {
        when(postService.requireVisiblePost(1L)).thenReturn(visiblePost());
        when(supportMapper.selectOne(any())).thenReturn(null);
        when(supportMapper.selectCount(any())).thenReturn(4L);

        assertThat(interactionService.toggleSupport(7L, 1L)).isEqualTo(4);
        verify(supportMapper).insert(any(PostSupportEntity.class));
        verify(supportMapper, never()).delete(any());
    }

    @Test
    void supportIsRemovedWhenPresent() {
        when(postService.requireVisiblePost(1L)).thenReturn(visiblePost());
        when(supportMapper.selectOne(any())).thenReturn(new PostSupportEntity());
        when(supportMapper.selectCount(any())).thenReturn(0L);

        assertThat(interactionService.toggleSupport(7L, 1L)).isZero();
        verify(supportMapper).delete(any());
    }

    @Test
    void interactionOnMissingPostIs404() {
        when(postService.requireVisiblePost(404L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "内容不存在或已删除"));
        assertThatThrownBy(() -> interactionService.toggleBookmark(7L, 404L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void bookmarkTogglesBothWays() {
        when(postService.requireVisiblePost(1L)).thenReturn(visiblePost());
        when(bookmarkMapper.selectOne(any())).thenReturn(null);
        assertThat(interactionService.toggleBookmark(7L, 1L)).isTrue();
        verify(bookmarkMapper).insert(any(BookmarkEntity.class));

        when(bookmarkMapper.selectOne(any())).thenReturn(new BookmarkEntity());
        assertThat(interactionService.toggleBookmark(7L, 1L)).isFalse();
        verify(bookmarkMapper).delete(any());
    }
}
