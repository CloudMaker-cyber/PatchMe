package com.patchme.reply.vo;

import java.time.LocalDateTime;

/** 回复公开投影行（SELECT 不含 author_id）。 */
public class ReplyRow {

    private Long id;
    private Long postId;
    private String body;
    private String identityMode;
    private Boolean isHelpful;
    private LocalDateTime createdAt;
    private String username;
    private String nickname;
    private String avatarUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getIdentityMode() { return identityMode; }
    public void setIdentityMode(String identityMode) { this.identityMode = identityMode; }
    public Boolean getIsHelpful() { return isHelpful; }
    public void setIsHelpful(Boolean isHelpful) { this.isHelpful = isHelpful; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
