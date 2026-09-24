package com.patchme.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** user_settings 表：仅本人可见。default_identity_mode 是发布默认身份（首次匿名）。 */
@TableName("user_settings")
public class UserSettingsEntity {

    @TableId(type = IdType.INPUT)
    private Long userId;
    private String defaultIdentityMode;
    private Boolean replyNotificationEnabled;
    private Boolean historyEnabled;
    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDefaultIdentityMode() { return defaultIdentityMode; }
    public void setDefaultIdentityMode(String defaultIdentityMode) { this.defaultIdentityMode = defaultIdentityMode; }
    public Boolean getReplyNotificationEnabled() { return replyNotificationEnabled; }
    public void setReplyNotificationEnabled(Boolean replyNotificationEnabled) { this.replyNotificationEnabled = replyNotificationEnabled; }
    public Boolean getHistoryEnabled() { return historyEnabled; }
    public void setHistoryEnabled(Boolean historyEnabled) { this.historyEnabled = historyEnabled; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
