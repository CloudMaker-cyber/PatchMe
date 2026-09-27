package com.patchme.common.enums;

/**
 * 账号状态机（users.status 列在任务 2 已预留，本任务启用）。
 * 渐进式：NORMAL → WARNED → OBSERVED → RESTRICTED → BANNED，管理员可逐级升级或 UNBAN 回 NORMAL。
 * 只有 RESTRICTED/BANNED 实际拦截写入；BANNED 额外拒绝登录。
 * 举报数量绝不自动流转本状态机——处罚只来自人工核实（02 风控原则）。
 */
public enum UserStatus {
    NORMAL("正常"),
    WARNED("已提醒"),
    OBSERVED("观察中"),
    RESTRICTED("发布受限"),
    BANNED("已封禁");

    private final String display;

    UserStatus(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /** 是否拦截发帖/回复等写操作。 */
    public boolean blocksWrite() {
        return this == RESTRICTED || this == BANNED;
    }

    /** 是否允许登录（封禁者不允许；WARNED/OBSERVED 仅影响展示与审核优先级）。 */
    public boolean allowsLogin() {
        return this != BANNED;
    }
}
