package com.patchme.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patchme.interaction.entity.NotificationEntity;
import com.patchme.interaction.mapper.NotificationMapper;
import com.patchme.support.MpTableInfo;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserSettingsMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 回复通知生成规则：楼主自回复不通知、接收者开关优先、payload 只含公开 id 与摘要
 * （绝不携带回复者身份——匿名回复的通知对楼主也只说"有人回复"）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private UserSettingsMapper settingsMapper;

    private NotificationService service;

    @BeforeAll
    static void registerTableInfo() {
        MpTableInfo.init(NotificationEntity.class);
    }

    private NotificationService newService() {
        // 真 ObjectMapper：payload JSON 的读写本身也是被测行为
        return new NotificationService(notificationMapper, settingsMapper, new ObjectMapper());
    }

    @Test
    void replyToOthersPostCreatesNotification() throws Exception {
        service = newService();
        service.onReply(9L, 7L, 1L, 50L, "抱抱你，你已经做得很好了");

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationMapper).insert(captor.capture());
        NotificationEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(9L);
        assertThat(saved.getType()).isEqualTo("REPLY");
        assertThat(saved.getReadAt()).isNull();
        Map<?, ?> payload = new ObjectMapper().readValue(saved.getPayloadJson(), Map.class);
        assertThat(payload.get("postId")).isEqualTo(1);
        assertThat(payload.get("replyId")).isEqualTo(50);
        assertThat((String) payload.get("excerpt")).startsWith("抱抱你");
    }

    @Test
    void payloadNeverCarriesReplierIdentity() throws Exception {
        service = newService();
        service.onReply(9L, 7L, 1L, 50L, "body");
        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationMapper).insert(captor.capture());
        String json = captor.getValue().getPayloadJson();
        assertThat(json).doesNotContain("authorId").doesNotContain("userId").doesNotContain("nickname")
                .doesNotContain("username");
    }

    @Test
    void selfReplyCreatesNoNotification() {
        service = newService();
        service.onReply(7L, 7L, 1L, 50L, "自己顶一下");
        verify(notificationMapper, never()).insert(any(NotificationEntity.class));
    }

    @Test
    void recipientSwitchOffSuppressesNotification() {
        UserSettingsEntity recipient = new UserSettingsEntity();
        recipient.setUserId(9L);
        recipient.setReplyNotificationEnabled(false);
        when(settingsMapper.selectById(9L)).thenReturn(recipient);

        service = newService();
        service.onReply(9L, 7L, 1L, 50L, "body");
        verify(notificationMapper, never()).insert(any(NotificationEntity.class));
    }

    @Test
    void excerptIsTruncated() throws Exception {
        service = newService();
        service.onReply(9L, 7L, 1L, 50L, "长".repeat(200));
        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationMapper).insert(captor.capture());
        Map<?, ?> payload = new ObjectMapper().readValue(captor.getValue().getPayloadJson(), Map.class);
        String excerpt = (String) payload.get("excerpt");
        assertThat(excerpt).hasSize(NotificationService.EXCERPT_MAX_LENGTH + 1).endsWith("…");
    }

    @Test
    void markAllReadIssuesSingleBatchUpdate() {
        service = newService();
        service.markAllRead(7L);
        verify(notificationMapper).update(any(), any());
    }
}
