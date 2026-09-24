package com.patchme.interaction;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.interaction.entity.BookmarkEntity;
import com.patchme.interaction.entity.PostSupportEntity;
import com.patchme.interaction.mapper.BookmarkMapper;
import com.patchme.interaction.mapper.PostSupportMapper;
import com.patchme.post.PostService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 支持 / 收藏：联合主键天然保证"每人每帖至多一条"，
 * toggle 并发下撞唯一键时按"已存在"吞掉即可，无需悲观锁。
 */
@Service
public class InteractionService {

    private final PostSupportMapper supportMapper;
    private final BookmarkMapper bookmarkMapper;
    private final PostService postService;

    public InteractionService(PostSupportMapper supportMapper, BookmarkMapper bookmarkMapper, PostService postService) {
        this.supportMapper = supportMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.postService = postService;
    }

    /** 支持 toggle，返回最新支持数。 */
    public long toggleSupport(Long userId, Long postId) {
        postService.requireVisiblePost(postId);
        PostSupportEntity existing = supportMapper.selectOne(Wrappers.<PostSupportEntity>lambdaQuery()
                .eq(PostSupportEntity::getPostId, postId).eq(PostSupportEntity::getUserId, userId));
        if (existing != null) {
            supportMapper.delete(Wrappers.<PostSupportEntity>lambdaQuery()
                    .eq(PostSupportEntity::getPostId, postId).eq(PostSupportEntity::getUserId, userId));
        } else {
            PostSupportEntity s = new PostSupportEntity();
            s.setPostId(postId);
            s.setUserId(userId);
            try {
                supportMapper.insert(s);
            } catch (DuplicateKeyException ignored) {
                // 并发重复点击：唯一键已兜底，视为成功
            }
        }
        return supportMapper.selectCount(Wrappers.<PostSupportEntity>lambdaQuery()
                .eq(PostSupportEntity::getPostId, postId));
    }

    /** 收藏 toggle，返回收藏后的新状态。 */
    public boolean toggleBookmark(Long userId, Long postId) {
        postService.requireVisiblePost(postId);
        BookmarkEntity existing = bookmarkMapper.selectOne(Wrappers.<BookmarkEntity>lambdaQuery()
                .eq(BookmarkEntity::getUserId, userId).eq(BookmarkEntity::getPostId, postId));
        if (existing != null) {
            bookmarkMapper.delete(Wrappers.<BookmarkEntity>lambdaQuery()
                    .eq(BookmarkEntity::getUserId, userId).eq(BookmarkEntity::getPostId, postId));
            return false;
        }
        BookmarkEntity b = new BookmarkEntity();
        b.setUserId(userId);
        b.setPostId(postId);
        try {
            bookmarkMapper.insert(b);
        } catch (DuplicateKeyException ignored) {
            // 同上
        }
        return true;
    }
}
