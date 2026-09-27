package com.patchme.common.enums;

/**
 * 举报原因（docs/v3/01 固定五种，用户不可自造）。
 * display() 是文案单点：管理后台与举报记录共用，新增取值只改这里。
 */
public enum ReportReason {
    HARASSMENT("骚扰辱骂"),
    SPAM("广告诈骗"),
    PRIVACY("泄露隐私"),
    DANGER("危险内容"),
    OTHER("其他");

    private final String display;

    ReportReason(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
