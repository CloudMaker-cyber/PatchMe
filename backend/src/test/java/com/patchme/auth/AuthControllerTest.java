package com.patchme.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patchme.auth.dto.LoginRequest;
import com.patchme.auth.dto.RegisterRequest;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.exception.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * AuthController 切片测试（关闭安全过滤器链，授权行为由 SecurityChainTest 覆盖）。
 * 重点验收：token 只出现在 Set-Cookie（HttpOnly），绝不出现在 JSON 响应体。
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    private static final String VALID_REGISTER = """
            {"email":"fanhai@example.com","password":"good-pass-123",
             "username":"fanhai_01","nickname":"平凡海"}
            """;

    @Test
    void registerWithValidBodySucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void registerRejectsWeakPasswordAndBadUsername() throws Exception {
        String body = """
                {"email":"not-an-email","password":"short",
                 "username":" BAD NAME ","nickname":"x"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40000"));
    }

    @Test
    void duplicateEmailMapsToConflict() throws Exception {
        doThrow(new BusinessException(ErrorCode.CONFLICT, "该邮箱已注册"))
                .when(authService).register(any(RegisterRequest.class));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("40900"))
                .andExpect(jsonPath("$.message").value("该邮箱已注册"));
    }

    @Test
    void loginPutsTokenOnlyInHttpOnlyCookie() throws Exception {
        when(jwtService.accessTtl()).thenReturn(Duration.ofHours(2));
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthService.LoginResult("jwt-token-value", "fanhai_01", "平凡海", "USER"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"good-pass-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("fanhai_01"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                // token 绝不能出现在响应体的任何字段里
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$..jwt-token-value").doesNotExist())
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("pm_access=jwt-token-value"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"))));
    }

    @Test
    void wrongPasswordReturnsGenericUnauthorized() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "邮箱或密码错误"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"wrong-pass-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40100"))
                .andExpect(jsonPath("$.message").value("邮箱或密码错误"));
    }

    @Test
    void logoutClearsCookieImmediately() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("pm_access=;"),
                                org.hamcrest.Matchers.containsString("Max-Age=0"))));
    }

    @Test
    void blankLoginFieldsFailValidation() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40000"));
    }
}
