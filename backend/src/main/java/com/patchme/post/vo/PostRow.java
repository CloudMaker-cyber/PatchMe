package com.patchme.post.vo;

import java.time.LocalDateTime;

/**
 * 公开列表 SQL 投影行（内部类，不出接口）。
 * 注意：SELECT 里没有 author_id/school_id/major_id —— 泄露在字段层面就不可能发生。
 */
public class PostRow {

    private Long id;
    private String intent;
    private String title;
    private String body;
    private String identityMode;
    private LocalDateTime commentsClosedAt;
    private LocalDateTime createdAt;
    private String username;
    private String nickname;
    private String avatarUrl;
    private Long replyCount;
    private Long supportCount;
    private Boolean helpful;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getIdentityMode() { return identityMode; }
    public void setIdentityMode(String identityMode) { this.identityMode = identityMode; }
    public LocalDateTime getCommentsClosedAt() { return commentsClosedAt; }
    public void setCommentsClosedAt(LocalDateTime commentsClosedAt) { this.commentsClosedAt = commentsClosedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Long getReplyCount() { return replyCount; }
    public void setReplyCount(Long replyCount) { this.replyCount = replyCount; }
    public Long getSupportCount() { return supportCount; }
    public void setSupportCount(Long supportCount) { this.supportCount = supportCount; }
    public Boolean getHelpful() { return helpful; }
    public void setHelpful(Boolean helpful) { this.helpful = helpful; }
}
