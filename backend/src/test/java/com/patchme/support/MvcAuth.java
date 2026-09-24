package com.patchme.support;

import com.patchme.auth.LoginUser;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 切片测试注入登录态：addFilters=false 时直接写 SecurityContextHolder（MockMvc 同线程生效）。 */
public final class MvcAuth {

    private MvcAuth() {
    }

    public static RequestPostProcessor user(long userId) {
        return request -> {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new LoginUser(userId, "USER"), null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            return request;
        };
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
