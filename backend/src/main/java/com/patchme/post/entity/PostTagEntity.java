package com.patchme.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** post_tags 联合主键表；@TableId 仅为满足 MP 映射，业务上按 (postId,tagId) 整体增删。 */
@TableName("post_tags")
public class PostTagEntity {

    @TableId(type = IdType.INPUT)
    private Long postId;
    private Long tagId;
    private LocalDateTime createdAt;

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
