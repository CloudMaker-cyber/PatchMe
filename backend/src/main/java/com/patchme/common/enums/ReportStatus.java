package com.patchme.common.enums;

/**
 * 举报处理状态机：PENDING → CONFIRMED / REJECTED，终态不可再流转（Service 层闸）。
 * 举报数量永不自动流转状态——只有管理员核实动作才改变它（02 风控原则）。
 */
public enum ReportStatus {
    PENDING("待处理"),
    CONFIRMED("已确认违规"),
    REJECTED("不予处理");

    private final String display;

    ReportStatus(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
