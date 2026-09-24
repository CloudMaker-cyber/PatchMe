package com.patchme.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** post_supports：联合主键即"每人每帖最多一次"，无需代码查重。 */
@TableName("post_supports")
public class PostSupportEntity {

    @TableId(type = IdType.INPUT)
    private Long postId;
    private Long userId;
    private LocalDateTime createdAt;

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
