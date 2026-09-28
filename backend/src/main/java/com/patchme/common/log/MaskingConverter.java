package com.patchme.common.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 日志脱敏转换器（logback-spring.xml 以 %mask 注册）。
 * 原则：宁可多遮不可漏遮——命中即替换，不尝试保留原文片段。
 * 覆盖：邮箱、BCrypt/明文密码字段、JWT 三段式令牌、pm_access Cookie 值、Bearer 头。
 */
public class MaskingConverter extends ClassicConverter {

    /** 邮箱：保留首字符与域名前缀提示，其余打码。 */
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9-]+)[A-Za-z0-9.-]*\\.[A-Za-z]{2,}");
    /** JWT：三段 base64url（以 eyJ 开头是 HS 头 {"alg"... 的特征）。 */
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");
    /** "password": "xxx" 或 password=xxx。 */
    private static final Pattern PASSWORD_FIELD = Pattern.compile("(?i)\"?(password|passwd|pwd)\"?\\s*[:=]\\s*\"?[^\"]*\"?");
    /** pm_access=xxx（Set-Cookie / Cookie 头原文）。 */
    private static final Pattern COOKIE = Pattern.compile("pm_access=[^;,\\s\"']+", Pattern.CASE_INSENSITIVE);
    /** Authorization: Bearer xxx。 */
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+={0,2}");

    private static final List<java.util.function.Function<String, String>> RULES = List.of(
            s -> BEARER.matcher(s).replaceAll("Bearer [masked]"),
            s -> COOKIE.matcher(s).replaceAll("pm_access=***"),
            s -> JWT.matcher(s).replaceAll("[jwt-masked]"),
            s -> EMAIL.matcher(s).replaceAll("$1***@***"),
            s -> PASSWORD_FIELD.matcher(s).replaceAll("$1=***")
    );

    /** 供转换器与单元测试共用。 */
    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String out = message;
        for (var rule : RULES) {
            out = rule.apply(out);
        }
        return out;
    }

    @Override
    public String convert(ILoggingEvent event) {
        return mask(event.getFormattedMessage());
    }
}
