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
import com.patchme.common.enums.ReportReason;
import com.patchme.common.enums.ReportTargetType;
import com.patchme.moderation.dto.CreateReportRequest;
import com.patchme.moderation.vo.MyReportRow;
import com.patchme.moderation.vo.MyReportVO;
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
 * 举报切片：身份只取 JWT；校验文案；出口 JSON 无 reporterId/reviewedBy/note
 * （举报人与审核细节泄露=02 铁律）。
 */
@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReportService reportService;

    @AfterEach
    void clearAuth() {
        MvcAuth.clear();
    }

    @Test
    void createBindsJwtIdentityAndReturnsId() throws Exception {
        when(reportService.create(eq(7L), any())).thenReturn(55L);
        CreateReportRequest request = new CreateReportRequest(ReportTargetType.POST, 1L, ReportReason.SPAM, "广告");

        mockMvc.perform(post("/api/reports").with(MvcAuth.user(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(55));
        verify(reportService).create(eq(7L), any());
    }

    @Test
    void missingReasonIsRejectedWithFieldMessage() throws Exception {
        mockMvc.perform(post("/api/reports").with(MvcAuth.user(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40000"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("举报原因")));
    }

    @Test
    void mineReturnsStatusProgressOnly() throws Exception {
        MyReportRow row = new MyReportRow();
        row.setId(9L);
        row.setTargetType("POST");
        row.setTargetId(1L);
        row.setReason("DANGER");
        row.setStatus("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        when(reportService.mine(7L, 50)).thenReturn(List.of(MyReportVO.from(row)));

        mockMvc.perform(get("/api/reports/mine").with(MvcAuth.user(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reasonDisplay").value("危险内容"))
                .andExpect(jsonPath("$.data[0].statusDisplay").value("待处理"))
                // 泄露扫描：举报人/审核人/管理员说明绝不出现在出口 JSON
                .andExpect(jsonPath("$..reporterId").doesNotExist())
                .andExpect(jsonPath("$..reviewedBy").doesNotExist())
                .andExpect(jsonPath("$..note").doesNotExist());
    }
}
