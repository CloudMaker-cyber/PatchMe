package com.patchme.interaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.common.enums.NotificationType;
import com.patchme.interaction.entity.NotificationEntity;
import com.patchme.interaction.mapper.NotificationMapper;
import com.patchme.interaction.vo.NotificationVO;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserSettingsMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 通知（仅本人可见）。生成规则（docs/v3/01）：
 * - 回复事件触发 REPLY，接收者是楼主；楼主回复自己的帖子不生成；
 * - 接收者关闭回复通知（reply_notification_enabled=false）则不生成；
 * - payload 只含公开 id 与摘要文本，绝不含回复者身份——匿名回复同样只说"有人回复"；
 * - 任务 5：SECURITY（账号处置）与 REPORT（举报处理）不受回复开关影响，且永不携带举报人线索。
 */
@Service
public class NotificationService {

    static final int EXCERPT_MAX_LENGTH = 80;

    private final NotificationMapper notificationMapper;
    private final UserSettingsMapper settingsMapper;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationMapper notificationMapper, UserSettingsMapper settingsMapper,
                               ObjectMapper objectMapper) {
        this.notificationMapper = notificationMapper;
        this.settingsMapper = settingsMapper;
        this.objectMapper = objectMapper;
    }

    /** 回复成功后调用（与插入回复同一事务）。 */
    public void onReply(Long recipientUserId, Long replierUserId, Long postId, Long replyId, String replyBody) {
        if (recipientUserId == null || recipientUserId.equals(replierUserId)) {
            return;
        }
        UserSettingsEntity settings = settingsMapper.selectById(recipientUserId);
        if (settings != null && !Boolean.TRUE.equals(settings.getReplyNotificationEnabled())) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", postId);
        payload.put("replyId", replyId);
        payload.put("excerpt", excerpt(replyBody));
        NotificationEntity notification = new NotificationEntity();
        notification.setUserId(recipientUserId);
        notification.setType(NotificationType.REPLY.name());
        notification.setPayloadJson(writeJson(payload));
        notificationMapper.insert(notification);
    }

    /** 最新在前；limit 由调用方钳制。 */
    public List<NotificationVO> list(Long userId, int limit) {
        List<NotificationEntity> rows = notificationMapper.selectList(Wrappers.<NotificationEntity>lambdaQuery()
                .eq(NotificationEntity::getUserId, userId)
                .orderByDesc(NotificationEntity::getCreatedAt)
                .orderByDesc(NotificationEntity::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
        return rows.stream().map(this::toVO).toList();
    }

    /** 全部标记已读（一次批量 UPDATE，无逐条操作）。 */
    public void markAllRead(Long userId) {
        notificationMapper.update(null, Wrappers.<NotificationEntity>lambdaUpdate()
                .eq(NotificationEntity::getUserId, userId)
                .isNull(NotificationEntity::getReadAt)
                .set(NotificationEntity::getReadAt, LocalDateTime.now()));
    }

    // ---------- 任务 5：审核/风控产生的通知（不受回复通知开关约束，永不携带举报人） ----------

    /**
     * 账号被处置时通知本人（MODERATION 类型）。payload 只说"你的账号被…/原因…"，
     * 绝不写是谁举报、依据哪条举报，避免反推举报人。
     */
    public void onAccountAction(Long userId, String actionDisplay, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", actionDisplay);
        payload.put("reason", excerpt(reason == null ? "" : reason));
        insert(userId, NotificationType.MODERATION, payload);
    }

    /** 举报被处理时通知举报人（REPORT 类型）：只回状态，不回管理员身份与对方内容。 */
    public void onReportResolved(Long reporterUserId, Long reportId, String statusDisplay) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", reportId);
        payload.put("status", statusDisplay);
        insert(reporterUserId, NotificationType.REPORT, payload);
    }

    /** 账号安全事件（如封禁/解封）通知（SECURITY 类型）。 */
    public void onSecurity(Long userId, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", excerpt(message));
        insert(userId, NotificationType.SECURITY, payload);
    }

    private void insert(Long userId, NotificationType type, Map<String, Object> payload) {
        if (userId == null) {
            return;
        }
        NotificationEntity notification = new NotificationEntity();
        notification.setUserId(userId);
        notification.setType(type.name());
        notification.setPayloadJson(writeJson(payload));
        notificationMapper.insert(notification);
    }

    private NotificationVO toVO(NotificationEntity row) {
        return new NotificationVO(row.getId(), NotificationType.valueOf(row.getType()),
                readPayload(row.getPayloadJson()), row.getReadAt(), row.getCreatedAt());
    }

    private static String excerpt(String body) {
        String trimmed = body == null ? "" : body.trim();
        return trimmed.length() <= EXCERPT_MAX_LENGTH ? trimmed : trimmed.substring(0, EXCERPT_MAX_LENGTH) + "…";
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("通知 payload 序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("通知 payload 反序列化失败", e);
        }
    }
}
