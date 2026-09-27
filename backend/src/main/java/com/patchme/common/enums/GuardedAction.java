package com.patchme.common.enums;

/**
 * 需要限流的写动作（单点字典）：阈值在 RateLimitService 里按枚举取值映射，
 * 新增受限动作只改这两处，不散落魔法字符串。
 */
public enum GuardedAction {
    POST,
    REPLY,
    REPORT,
    /** 登录失败按邮箱计数（不走用户写闸，只走窗口） */
    LOGIN_FAILURE
}
