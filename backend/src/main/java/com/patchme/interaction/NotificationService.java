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
 * 通知（仅本人可见）。任务 4 只生成 REPLY 类型；MODERATION/REPORT/SECURITY 属任务 5。
 * 生成规则（docs/v3/01）：
 * - 回复事件触发，接收者是楼主；楼主回复自己的帖子不生成；
 * - 接收者在设置里关闭回复通知（reply_notification_enabled=false）则不生成；
 * - payload 只含公开 id 与摘要文本，绝不含回复者身份——匿名回复同样只说"有人回复"。
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
