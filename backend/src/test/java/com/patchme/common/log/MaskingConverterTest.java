package com.patchme.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 日志脱敏回归：任何命中样式都不得原样出现在日志输出里。 */
class MaskingConverterTest {

    @Test
    @DisplayName("邮箱只留首字符，域名整体打码")
    void masksEmail() {
        String out = MaskingConverter.mask("登录失败 subject_key=tangshihan.example@mail.corp.cn");
        assertThat(out).doesNotContain("tangshihan.example").doesNotContain("mail.corp.cn");
        assertThat(out).contains("t***@***");
    }

    @Test
    @DisplayName("JWT 三段式令牌整体打码")
    void masksJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijklmnopqrstuvwxyzABCDEFGH";
        assertThat(MaskingConverter.mask("Cookie: " + jwt)).doesNotContain("eyJ").contains("[jwt-masked]");
    }

    @Test
    @DisplayName("password 字段与 pm_access / Bearer 头打码")
    void masksCredentialFields() {
        String out = MaskingConverter.mask(
                "body={\"email\":\"a@b.co\",\"password\":\" hunter2\"} Set-Cookie: pm_access=abc.def.ghi; HttpOnly Authorization: Bearer eyJhbGciOi.eyJzdWIiOidz.aW5mbw");
        assertThat(out).doesNotContain("hunter2").doesNotContain("abc.def.ghi");
        assertThat(out).contains("pm_access=***").contains("Bearer [masked]");
    }

    @Test
    @DisplayName("普通业务日志不受影响")
    void keepsPlainText() {
        String msg = "举报已受理 reportId=42 targetType=POST";
        assertThat(MaskingConverter.mask(msg)).isEqualTo(msg);
    }
}
