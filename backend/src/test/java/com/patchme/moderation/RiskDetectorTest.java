package com.patchme.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 风险检测词表/正则单点：召回优先，且 atRisk 与 spam 两类信号互不混淆。 */
class RiskDetectorTest {

    private final RiskDetector detector = new RiskDetector();

    @Test
    void chineseRiskKeywordsAreCaught() {
        assertThat(detector.detect("我真的不想活了").atRisk()).isTrue();
        assertThat(detector.detect("有没有人陪我说说话").atRisk()).isFalse();
    }

    @Test
    void englishRiskKeywordsAreCaseInsensitive() {
        assertThat(detector.detect("I'm thinking about Suicide").atRisk()).isTrue();
        assertThat(detector.detect("KILL MYSELF tonight").atRisk()).isTrue();
    }

    @Test
    void spamSignalsCatchPhoneQQWechatAndLinks() {
        assertThat(detector.detect("加我微信 abc12345 详聊").spam()).isTrue();
        assertThat(detector.detect("QQ 号：123456789").spam()).isTrue();
        assertThat(detector.detect("看我博客 https://spam.example.com/post").spam()).isTrue();
        assertThat(detector.detect("我的电话 13812345678").spam()).isTrue();
        assertThat(detector.detect("1234567 只是数字也不是手机号").spam()).isFalse();
    }

    @Test
    void blankAndNullInputIsClean() {
        assertThat(detector.detect()).isEqualTo(RiskDetector.RiskFlags.CLEAN);
        assertThat(detector.detect(null)).isEqualTo(RiskDetector.RiskFlags.CLEAN);
        assertThat(detector.detect("  ", "")).isEqualTo(RiskDetector.RiskFlags.CLEAN);
    }

    @Test
    void riskAndSpamCanCoexist() {
        var flags = detector.detect("不想活了，加我 https://x.example");
        assertThat(flags.atRisk()).isTrue();
        assertThat(flags.spam()).isTrue();
    }
}
