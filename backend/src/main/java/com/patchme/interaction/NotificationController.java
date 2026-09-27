package com.patchme.interaction;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import com.patchme.interaction.vo.NotificationVO;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通知：路径不带 userId，归属只认 JWT——他人通知无法被枚举。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationVO>> list(@AuthenticationPrincipal LoginUser loginUser,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(notificationService.list(loginUser.userId(), limit));
    }

    @PatchMapping("/read")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal LoginUser loginUser) {
        notificationService.markAllRead(loginUser.userId());
        return ApiResponse.success(null);
    }
}
