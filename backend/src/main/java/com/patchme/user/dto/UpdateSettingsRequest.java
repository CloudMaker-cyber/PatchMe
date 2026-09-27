package com.patchme.user.dto;

import com.patchme.common.enums.IdentityMode;

/**
 * 更新本人设置：字段全部可选，只应用非 null 项（前向兼容：以后加新开关不破坏旧调用）。
 * defaultIdentityMode 用枚举承载，非法值在 Jackson 绑定阶段即被拒绝。
 */
public record UpdateSettingsRequest(IdentityMode defaultIdentityMode,
                                    Boolean replyNotificationEnabled,
                                    Boolean historyEnabled) {
}
