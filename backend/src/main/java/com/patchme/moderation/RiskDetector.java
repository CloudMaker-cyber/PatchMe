package com.patchme.moderation;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 内容风险检测（服务端二次判定，前端提示只是体验辅助——02 风控原则）。
 * 词库/正则在唯一点位定义：误报由人工审核兜底，所以召回优先、允许宁多勿漏。
 * 两类信号语义完全不同：
 * - atRisk：疑似自伤/伤人等即时风险 → 送优先审核 + 向读者显示求助提示；这不是违规，绝不自动处罚；
 * - spam  ：联系方式/外链/广告特征 → 随举报进审核队列供管理员参考，同样不自动处罚。
 */
@Component
public class RiskDetector {

    /** 即时风险信号词（中文子串 + 英文小写子串混排）。 */
    private static final List<String> RISK_KEYWORDS = List.of(
            "自杀", "不想活", "活不下去", "结束生命", "了结自己", "轻生",
            "自伤", "割腕", "吞药", "跳楼", "烧炭",
            "同归于尽", "杀了", "伤害他人"
    );
    private static final List<String> RISK_KEYWORDS_EN = List.of(
            "suicide", "kill myself", "end my life", "self-harm", "hurt myself"
    );

    /** 广告/引流特征：手机号、QQ/微信号、任何 http 外链。 */
    private static final List<Pattern> SPAM_PATTERNS = List.of(
            Pattern.compile("1[3-9]\\d{9}"),
            Pattern.compile("(?i)qq\\s*号?\\s*[:：]?\\s*\\d{5,}"),
            Pattern.compile("(?i)(微信|wx|vx|v信)\\s*[:：号]?\\s*[a-z0-9][a-z0-9_-]{4,}"),
            Pattern.compile("(?i)https?://\\S+")
    );

    public record RiskFlags(boolean atRisk, boolean spam) {
        public static final RiskFlags CLEAN = new RiskFlags(false, false);
    }

    public RiskFlags detect(String... bodyParts) {
        String text = String.join("\n", bodyParts == null ? new String[0] : bodyParts);
        if (text.isBlank()) {
            return RiskFlags.CLEAN;
        }
        String lower = text.toLowerCase();
        boolean atRisk = RISK_KEYWORDS.stream().anyMatch(text::contains)
                || RISK_KEYWORDS_EN.stream().anyMatch(lower::contains);
        boolean spam = SPAM_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
        return new RiskFlags(atRisk, spam);
    }
}
