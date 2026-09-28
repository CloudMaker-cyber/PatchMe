package com.patchme.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi：开发/内测下访问 /swagger-ui.html 查看接口文档。
 * 生产 profile（application-prod.yml）用 springdoc.api-docs.enabled=false 整体关闭，
 * 不对外暴露接口面。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI patchMeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("人生补丁包 API")
                .version("v3-任务6")
                .description("""
                        匿名互助社区 REST 接口。认证：登录成功后签发 HttpOnly Cookie `pm_access`（JWT），\
                        浏览器自动携带；身份只认 JWT，任何 userId 请求参数都不作为权限依据。\
                        统一响应：{code, message, data}，code=0 表示成功；\
                        401 未登录、403 无权限、429 触发限流/软锁。"""));
    }
}
