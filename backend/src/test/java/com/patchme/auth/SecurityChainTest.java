package com.patchme.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.patchme.common.web.HealthController;
import com.patchme.user.MeController;
import com.patchme.user.entity.UserEntity;
import com.patchme.user.entity.UserProfileEntity;
import com.patchme.user.mapper.UserMapper;
import com.patchme.user.mapper.UserProfileMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 安全链路集成切片：加载真实的 SecurityConfig + JwtService + JwtAuthFilter（@Import 装配），
 * 只 mock 数据访问层。验收点全部在此：游客 401、伪造 token 401、USER 进不了 admin、
 * 公开 GET 放行、写接口必须登录、身份只认 JWT（查询参数 userId 无效）。
 */
@WebMvcTest(controllers = {HealthController.class, MeController.class, AuthController.class,
        SecurityChainTest.ProbeController.class})
@Import({SecurityConfig.class, JwtService.class, SecurityChainTest.ProbeController.class})
class SecurityChainTest {

    private static final long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private UserProfileMapper profileMapper;

    /** 供授权规则验证的探针端点（模拟任务 3 的公开只读 / 写接口 / 管理端）。 */
    @RestController
    static class ProbeController {

        @GetMapping("/api/posts")
        public String listPosts() {
            return "ok";
        }

        @PostMapping("/api/posts")
        public String createPost() {
            return "ok";
        }

        @GetMapping("/api/users/xiaoman")
        public String publicProfile() {
            return "ok";
        }

        @GetMapping("/api/admin/ping")
        public String adminPing() {
            return "ok";
        }
    }

    private void stubOwnedUser() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setRole("USER");
        user.setStatus("NORMAL");
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(USER_ID);
        profile.setUsername("fanhai_01");
        profile.setNickname("平凡海");
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(profileMapper.selectById(USER_ID)).thenReturn(profile);
    }

    @Test
    void guestCanReadHealthAndPublicGetEndpoints() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/posts")).andExpect(status().isOk());
        mockMvc.perform(get("/api/users/xiaoman")).andExpect(status().isOk());
    }

    @Test
    void guestCannotReachProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40100"));
        // 公开只读仅限 GET：游客发帖必须被拒
        mockMvc.perform(post("/api/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40100"));
    }

    @Test
    void guestCannotReachAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40100"));
    }

    @Test
    void tamperedOrForeignTokenIsTreatedAsGuest() throws Exception {
        String valid = jwtService.issue(USER_ID, "USER");
        mockMvc.perform(get("/api/me").cookie(new Cookie(JwtAuthFilter.COOKIE_NAME, valid + "tamper")))
                .andExpect(status().isUnauthorized());
        // 用另一个密钥签发的 ADMIN token（攻击者自签）也必须失效
        String forged = new JwtService("attacker-made-up-secret-long-enough-0123456789ab",
                java.time.Duration.ofHours(1)).issue(999L, "ADMIN");
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void identityComesFromJwtNotFromRequestParameters() throws Exception {
        stubOwnedUser();
        String token = jwtService.issue(USER_ID, "USER");
        // 即使前端伪造 userId=999，服务端也只用 token 里的 7 号身份
        mockMvc.perform(get("/api/me").cookie(new Cookie(JwtAuthFilter.COOKIE_NAME, token))
                        .param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.username").value("fanhai_01"));
        // 响应中不出现 email/密码哈希等内部字段
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void userRoleCannotEnterAdminApi() throws Exception {
        String token = jwtService.issue(USER_ID, "USER");
        mockMvc.perform(get("/api/admin/ping").cookie(new Cookie(JwtAuthFilter.COOKIE_NAME, token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40300"));
    }

    @Test
    void adminRoleCanEnterAdminApi() throws Exception {
        String token = jwtService.issue(USER_ID, "ADMIN");
        mockMvc.perform(get("/api/admin/ping").cookie(new Cookie(JwtAuthFilter.COOKIE_NAME, token)))
                .andExpect(status().isOk());
    }

    @Test
    void loggedInUserMayPost() throws Exception {
        String token = jwtService.issue(USER_ID, "USER");
        mockMvc.perform(post("/api/posts").cookie(new Cookie(JwtAuthFilter.COOKIE_NAME, token)))
                .andExpect(status().isOk());
    }

    @Test
    void authEndpointsRemainOpenForLoginAndRegister() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"12345678\",\"username\":\"abcd_1\",\"nickname\":\"x\"}"))
                .andExpect(status().isOk());
        when(authService.login(any())).thenReturn(new AuthService.LoginResult("t", "u", "n", "USER"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"12345678\"}"))
                .andExpect(status().isOk());
    }
}
