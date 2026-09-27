package com.patchme.common.enums;

/**
 * 通知类型（docs/v3/01："新回复、内容处理、举报处理、账号安全"）。
 * 任务 4 只产生 REPLY；其余三个值对应任务 5 的审核/风控事件，先集中定义防止字符串散落。
 */
public enum NotificationType {
    REPLY,
    MODERATION,
    REPORT,
    SECURITY
}
