package com.patchme.interaction;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 支持/收藏 toggle：路径挂在帖子下，但归属只认 JWT。 */
@RestController
@RequestMapping("/api/posts")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/{id}/support")
    public ApiResponse<Long> toggleSupport(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id) {
        return ApiResponse.success(interactionService.toggleSupport(loginUser.userId(), id));
    }

    @PostMapping("/{id}/bookmark")
    public ApiResponse<Boolean> toggleBookmark(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id) {
        return ApiResponse.success(interactionService.toggleBookmark(loginUser.userId(), id));
    }
}
