package com.patchme.auth;

import com.patchme.auth.dto.LoginRequest;
import com.patchme.auth.dto.RegisterRequest;
import com.patchme.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * token 只走 HttpOnly Cookie，绝不进 JSON 响应体（docs/v3/02：降低前端脚本窃取风险）。
 * JS 读不到该 Cookie；logout 即清除。
 * 任务 6：Secure 标志由 `app.cookie.secure`（环境变量 APP_COOKIE_SECURE）控制——
 * 本机 http 开发保持 false，生产 HTTPS 下必须为 true（application-prod.yml 默认 true）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final boolean cookieSecure;

    public AuthController(AuthService authService, JwtService jwtService,
                          @Value("${app.cookie.secure:false}") boolean cookieSecure) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(result.token(), jwtService.accessTtl().toSeconds()));
        return ApiResponse.success(new LoginVO(result.username(), result.nickname(), result.role()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0));
        return ApiResponse.success(null);
    }

    private String buildCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(JwtAuthFilter.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }

    /** 登录成功返回给前端的展示信息；身份凭证（token）在 Set-Cookie 里。 */
    public record LoginVO(String username, String nickname, String role) {
    }
}
