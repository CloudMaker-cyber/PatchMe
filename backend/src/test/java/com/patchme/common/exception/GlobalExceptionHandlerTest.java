package com.patchme.common.exception;

import com.patchme.common.api.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证全局异常处理：业务异常、参数校验失败、未知异常均收敛为统一 ApiResponse，
 * 且不向前端泄露内部细节。
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class TestController {

        public record EchoRequest(@NotBlank @Size(max = 10) String body) {
        }

        @PostMapping("/api/test/echo")
        public String echo(@Valid @RequestBody EchoRequest request) {
            return request.body();
        }

        @GetMapping("/api/test/forbidden")
        public String forbidden() {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不是该内容作者");
        }

        @GetMapping("/api/test/unexpected")
        public String unexpected() {
            // 模拟底层异常：消息含敏感信息，必须被兜底处理器屏蔽
            throw new IllegalStateException("jdbc:mysql://secret-host 连接池耗尽");
        }
    }

    @Test
    void businessExceptionKeepsGivenMessageAndCode() throws Exception {
        mockMvc.perform(get("/api/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40300"))
                .andExpect(jsonPath("$.message").value("你不是该内容作者"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void validationFailureReturnsParamInvalid() throws Exception {
        mockMvc.perform(post("/api/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40000"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("body")));
    }

    @Test
    void unexpectedExceptionNeverLeaksInternals() throws Exception {
        mockMvc.perform(get("/api/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("50000"))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"));
    }
}
