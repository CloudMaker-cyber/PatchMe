package com.patchme.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * JwtService 纯单元测试：签发/解析往返、篡改/异密钥/过期一律解析失败（empty），
 * 保证 filter 层拿到"未认证"而不是错误细节。
 */
class JwtServiceTest {

    private static final String SECRET = "patchme-unit-test-secret-0123456789-abcdefghij";

    private final JwtService jwtService = new JwtService(SECRET, Duration.ofHours(2));

    @Test
    void issueAndParseRoundTrip() {
        String token = jwtService.issue(42L, "ADMIN");
        LoginUser user = jwtService.parse(token).orElseThrow();
        assertThat(user.userId()).isEqualTo(42L);
        assertThat(user.role()).isEqualTo("ADMIN");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.issue(1L, "USER");
        assertThat(jwtService.parse(token + "x")).isEmpty();
    }

    @Test
    void tokenFromDifferentSecretIsRejected() {
        JwtService attacker = new JwtService("another-secret-key-that-is-long-enough-0123456789", Duration.ofHours(2));
        String forged = attacker.issue(999L, "ADMIN");
        assertThat(jwtService.parse(forged)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expired = new JwtService(SECRET, Duration.ofSeconds(-5));
        String old = expired.issue(1L, "USER");
        assertThat(jwtService.parse(old)).isEmpty();
    }
}
