package com.patchme.moderation;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import com.patchme.moderation.dto.ModerationActionRequest;
import com.patchme.moderation.dto.ReviewReportRequest;
import com.patchme.moderation.vo.AdminAuditItem;
import com.patchme.moderation.vo.AdminReportItem;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审核后台。路径前缀 /api/admin 已被 SecurityConfig 限定 hasRole('ADMIN')，
 * 普通用户与游客在这里只会得到 401/403 统一信封，不泄露任何审核数据。
 * Controller 依然零业务：队列查询与处置规则全部在 ModerationAdminService。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminModerationController {

    private final ModerationAdminService adminService;

    public AdminModerationController(ModerationAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/reports")
    public ApiResponse<List<AdminReportItem>> queue(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(adminService.queue(status, limit));
    }

    @PostMapping("/reports/{id}/review")
    public ApiResponse<Void> review(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id,
                                    @Valid @RequestBody ReviewReportRequest request) {
        adminService.reviewReport(loginUser.userId(), id, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/actions")
    public ApiResponse<Void> act(@AuthenticationPrincipal LoginUser loginUser,
                                 @Valid @RequestBody ModerationActionRequest request) {
        adminService.act(loginUser.userId(), request);
        return ApiResponse.success(null);
    }

    @GetMapping("/audit")
    public ApiResponse<List<AdminAuditItem>> audit(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(adminService.audit(limit));
    }
}
