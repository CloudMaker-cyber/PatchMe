package com.patchme.common.enums;

/**
 * 管理员处置动作（同时是审计日志的动作字典）。
 * 对用户的四档即渐进式限制阶梯；CONFIRM/REJECT 只作用于举报本身。
 */
public enum ModerationAction {
    WARN("提醒"),
    OBSERVE("观察"),
    RESTRICT("限制发布"),
    BAN("封禁"),
    UNBAN("解除限制恢复正常"),
    CONFIRM_REPORT("确认举报"),
    REJECT_REPORT("驳回举报");

    private final String display;

    ModerationAction(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /** 该动作是否针对用户账户（否则针对举报单）。 */
    public boolean targetsUser() {
        return this == WARN || this == OBSERVE || this == RESTRICT || this == BAN || this == UNBAN;
    }
}
