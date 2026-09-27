package com.patchme.interaction;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.patchme.interaction.vo.NotificationVO;
import com.patchme.support.MvcAuth;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 通知出口切片：JSON 只允许出现 公开 id/类型/摘要/时间，
 * 全响应扫描不得出现 userId/authorId/nickname/username（匿名回复的通知也不暴露回复者）。
 */
@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @AfterEach
    void clearAuth() {
        MvcAuth.clear();
    }

    private NotificationVO replyNotification() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", 1L);
        payload.put("replyId", 50L);
        payload.put("excerpt", "抱抱你");
        return new NotificationVO(9L, com.patchme.common.enums.NotificationType.REPLY, payload,
                null, LocalDateTime.now());
    }

    @Test
    void listShapeLeaksNoIdentityFields() throws Exception {
        when(notificationService.list(eq(7L), anyInt())).thenReturn(List.of(replyNotification()));
        mockMvc.perform(get("/api/notifications").with(MvcAuth.user(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("REPLY"))
                .andExpect(jsonPath("$.data[0].payload.postId").value(1))
                .andExpect(jsonPath("$.data[0].payload.excerpt").value("抱抱你"))
                .andExpect(jsonPath("$.data[0].readAt").doesNotExist())
                .andExpect(jsonPath("$..userId").doesNotExist())
                .andExpect(jsonPath("$..authorId").doesNotExist())
                .andExpect(jsonPath("$..nickname").doesNotExist())
                .andExpect(jsonPath("$..username").doesNotExist());
    }

    @Test
    void markAllReadUsesJwtUser() throws Exception {
        mockMvc.perform(patch("/api/notifications/read").with(MvcAuth.user(7L)))
                .andExpect(status().isOk());
        verify(notificationService).markAllRead(7L);
    }
}
