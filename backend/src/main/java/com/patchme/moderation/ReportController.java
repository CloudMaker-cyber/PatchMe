package com.patchme.moderation;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import com.patchme.moderation.dto.CreateReportRequest;
import com.patchme.moderation.vo.MyReportVO;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报入口（仅登录用户）。举报人身份取自 JWT；
 * "我的举报记录"挂在 /api/me/reports（UserContentController），这里只有提交动作。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@AuthenticationPrincipal LoginUser loginUser,
                                                 @Valid @RequestBody CreateReportRequest request) {
        Long id = reportService.create(loginUser.userId(), request);
        return ApiResponse.success(Map.of("id", id));
    }

    /** 我的举报记录（仅本人可见，处理进度只到状态粒度）。 */
    @GetMapping("/mine")
    public ApiResponse<List<MyReportVO>> mine(@AuthenticationPrincipal LoginUser loginUser,
                                              @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(reportService.mine(loginUser.userId(), limit));
    }
}
