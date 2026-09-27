package com.patchme.moderation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patchme.common.enums.ModerationAction;
import com.patchme.moderation.dto.ModerationActionRequest;
import com.patchme.moderation.dto.ReviewReportRequest;
import com.patchme.moderation.vo.AdminAuditItem;
import com.patchme.moderation.vo.AdminReportItem;
import com.patchme.moderation.vo.AuditRow;
import com.patchme.moderation.vo.ReportQueueRow;
import com.patchme.support.MvcAuth;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 审核后台切片（401/403 安全链在 SecurityChainTest 覆盖，这里只管绑定与出口）。
 * 队列出口是唯一允许出现 authorId 的地方（仅 ADMIN 路由）；审计出口不允许 reporterId。
 */
@WebMvcTest(AdminModerationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModerationAdminService adminService;

    @AfterEach
    void clearAuth() {
        MvcAuth.clear();
    }

    @Test
    void queueBindsStatusAndLimit() throws Exception {
        ReportQueueRow row = new ReportQueueRow();
        row.setId(1L);
        row.setSource("SYSTEM");
        row.setTargetType("POST");
        row.setTargetId(11L);
        row.setReason("DANGER");
        row.setStatus("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        row.setAuthorId(4L);
        row.setAuthorIdentityMode("ANONYMOUS");
        when(adminService.queue("PENDING", 5)).thenReturn(List.of(AdminReportItem.from(row)));

        mockMvc.perform(get("/api/admin/reports").param("status", "PENDING").param("limit", "5")
                        .with(MvcAuth.user(3L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].source").value("SYSTEM"))
                .andExpect(jsonPath("$.data[0].authorIdentityMode").value("ANONYMOUS"))
                // 审核队列没有举报人字段——连管理端出口也不展示
                .andExpect(jsonPath("$..reporterId").doesNotExist());
    }

    @Test
    void reviewBindsAdminIdAndConfirmFlag() throws Exception {
        mockMvc.perform(post("/api/admin/reports/3/review").with(MvcAuth.user(3L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewReportRequest(true, "核实为广告", true))))
                .andExpect(status().isOk());
        verify(adminService).reviewReport(eq(3L), eq(3L), any());
    }

    @Test
    void reviewWithoutConclusionIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/reports/3/review").with(MvcAuth.user(3L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("处理结论")));
    }

    @Test
    void actBindsLadderAction() throws Exception {
        ModerationActionRequest request = new ModerationActionRequest(ModerationAction.RESTRICT, 5L, "重复引流", 3L);
        mockMvc.perform(post("/api/admin/actions").with(MvcAuth.user(3L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        verify(adminService).act(eq(3L), any());
    }

    @Test
    void auditListMapsDisplayAndHidesReporter() throws Exception {
        AuditRow row = new AuditRow();
        row.setId(1L);
        row.setAdminUsername("admin");
        row.setAction(ModerationAction.BAN.name());
        row.setTargetType("USER");
        row.setTargetId(5L);
        row.setTargetUsername("spammer");
        row.setReason("持续广告");
        row.setReportId(3L);
        row.setCreatedAt(LocalDateTime.now());
        when(adminService.audit(100)).thenReturn(List.of(AdminAuditItem.from(row)));

        mockMvc.perform(get("/api/admin/audit").with(MvcAuth.user(3L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].actionDisplay").value("封禁"))
                .andExpect(jsonPath("$..reporterId").doesNotExist())
                .andExpect(jsonPath("$..reporter").doesNotExist());
    }
}
