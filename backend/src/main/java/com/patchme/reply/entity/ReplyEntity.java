package com.patchme.reply.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** replies 表：仅一级回复。is_helpful 每帖至多一条由 Service 保证。 */
@TableName("replies")
public class ReplyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long authorId;
    private String identityMode;
    private String body;
    private Boolean isHelpful;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getIdentityMode() { return identityMode; }
    public void setIdentityMode(String identityMode) { this.identityMode = identityMode; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Boolean getIsHelpful() { return isHelpful; }
    public void setIsHelpful(Boolean isHelpful) { this.isHelpful = isHelpful; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
