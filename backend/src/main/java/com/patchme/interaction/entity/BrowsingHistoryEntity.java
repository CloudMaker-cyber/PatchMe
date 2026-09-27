package com.patchme.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** browsing_history：一人一帖一行，重复浏览刷新 viewed_at。仅本人。 */
@TableName("browsing_history")
public class BrowsingHistoryEntity {

    @TableId(type = IdType.INPUT)
    private Long userId;
    private Long postId;
    private LocalDateTime viewedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public LocalDateTime getViewedAt() { return viewedAt; }
    public void setViewedAt(LocalDateTime viewedAt) { this.viewedAt = viewedAt; }
}
