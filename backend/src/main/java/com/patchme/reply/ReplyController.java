package com.patchme.reply;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import com.patchme.common.dto.IdentityRequest;
import com.patchme.reply.dto.HelpfulRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 回复端点：楼主标记有帮助；作者本人转匿名。 */
@RestController
@RequestMapping("/api/replies")
public class ReplyController {

    private final ReplyService replyService;

    public ReplyController(ReplyService replyService) {
        this.replyService = replyService;
    }

    /** 作者本人删除自己的回复（软删除）。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id) {
        replyService.deleteReply(loginUser.userId(), id);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/helpful")
    public ApiResponse<Void> setHelpful(@AuthenticationPrincipal LoginUser loginUser,
                                        @PathVariable Long id,
                                        @Valid @RequestBody HelpfulRequest request) {
        replyService.setHelpful(loginUser.userId(), id, request.helpful());
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/identity")
    public ApiResponse<Void> changeIdentity(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable Long id,
                                            @Valid @RequestBody IdentityRequest request) {
        replyService.changeIdentity(loginUser.userId(), id, request.mode());
        return ApiResponse.success(null);
    }
}
