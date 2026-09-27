package com.patchme.moderation.vo;

import java.time.LocalDateTime;

/**
 * 审核队列行（仅管理端投影）。authorId 在此只面向 ADMIN 出口；普通用户任何接口都拿不到它。
 * authorIdentityMode=ANONYMOUS 提醒管理员：处置与通知都不会暴露举报线索来源。
 */
public class ReportQueueRow {

    private Long id;
    private String source;
    private String targetType;
    private Long targetId;
    private String reason;
    private String note;
    private String status;
    private LocalDateTime createdAt;
    private String targetTitle;
    private String targetExcerpt;
    private Long authorId;
    private String authorIdentityMode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getTargetTitle() { return targetTitle; }
    public void setTargetTitle(String targetTitle) { this.targetTitle = targetTitle; }
    public String getTargetExcerpt() { return targetExcerpt; }
    public void setTargetExcerpt(String targetExcerpt) { this.targetExcerpt = targetExcerpt; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getAuthorIdentityMode() { return authorIdentityMode; }
    public void setAuthorIdentityMode(String authorIdentityMode) { this.authorIdentityMode = authorIdentityMode; }
}
