package com.patchme.moderation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** blocks 表：联合主键 (blocker_id, blocked_id)，只用 Wrapper 条件读写，不走 byId 方法。 */
@TableName("blocks")
public class BlockEntity {

    private Long blockerId;
    private Long blockedId;
    private LocalDateTime createdAt;

    public Long getBlockerId() { return blockerId; }
    public void setBlockerId(Long blockerId) { this.blockerId = blockerId; }
    public Long getBlockedId() { return blockedId; }
    public void setBlockedId(Long blockedId) { this.blockedId = blockedId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
