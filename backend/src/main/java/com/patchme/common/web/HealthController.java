package com.patchme.common.web;

import com.patchme.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口：验证统一响应框架与前后端联通，不依赖数据库。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    public record HealthVO(String status) {
    }

    @GetMapping("/health")
    public ApiResponse<HealthVO> health() {
        return ApiResponse.success(new HealthVO("UP"));
    }
}
