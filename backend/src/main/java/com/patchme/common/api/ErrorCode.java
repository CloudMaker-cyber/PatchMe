package com.patchme.common.api;

import org.springframework.http.HttpStatus;

/**
 * 全局错误码。code 前缀与 HTTP 状态一致，便于前端按 code 分支处理；
 * message 是对外安全文案，绝不携带 SQL、堆栈或内部字段。
 */
public enum ErrorCode {

    OK(HttpStatus.OK, "0", "ok"),
    PARAM_INVALID(HttpStatus.BAD_REQUEST, "40000", "请求参数不合法"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "40100", "请先登录"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "40300", "没有权限执行该操作"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "40400", "资源不存在"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "50000", "服务暂时不可用，请稍后重试");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
