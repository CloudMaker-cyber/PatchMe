package com.patchme.user;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import com.patchme.interaction.HistoryService;
import com.patchme.post.vo.MinePostVO;
import com.patchme.post.vo.PublicPostVO;
import com.patchme.reply.vo.MineReplyVO;
import com.patchme.user.dto.UpdateSettingsRequest;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开主页（/api/users/{username}，游客可访问，只含 PUBLIC 内容）
 * 与"我的内容"（/api/me/**，仅本人；userId 只来自 JWT）。
 */
@RestController
public class UserContentController {

    private final UserService userService;
    private final HistoryService historyService;

    public UserContentController(UserService userService, HistoryService historyService) {
        this.userService = userService;
        this.historyService = historyService;
    }

    @GetMapping("/api/users/{username}")
    public ApiResponse<UserService.ProfilePageVO> profile(@PathVariable String username,
                                                          @AuthenticationPrincipal LoginUser loginUser) {
        // 公开页游客可看（viewerId=null 不过滤）；登录者带 JWT 时拉黑过滤生效
        return ApiResponse.success(userService.profile(username, loginUser == null ? null : loginUser.userId()));
    }

    @GetMapping("/api/me/posts")
    public ApiResponse<List<MinePostVO>> myPosts(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(userService.minePosts(loginUser.userId()));
    }

    @GetMapping("/api/me/replies")
    public ApiResponse<List<MineReplyVO>> myReplies(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(userService.mineReplies(loginUser.userId()));
    }

    @GetMapping("/api/me/bookmarks")
    public ApiResponse<List<PublicPostVO>> myBookmarks(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(userService.myBookmarks(loginUser.userId()));
    }

    @GetMapping("/api/me/history")
    public ApiResponse<List<PublicPostVO>> myHistory(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(userService.myHistory(loginUser.userId()));
    }

    /** 一键清空本人浏览历史。 */
    @DeleteMapping("/api/me/history")
    public ApiResponse<Void> clearHistory(@AuthenticationPrincipal LoginUser loginUser) {
        historyService.clear(loginUser.userId());
        return ApiResponse.success(null);
    }

    @GetMapping("/api/me/settings")
    public ApiResponse<UserService.SettingsVO> settings(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(userService.getSettings(loginUser.userId()));
    }

    @PatchMapping("/api/me/settings")
    public ApiResponse<UserService.SettingsVO> updateSettings(@AuthenticationPrincipal LoginUser loginUser,
                                                              @RequestBody UpdateSettingsRequest request) {
        return ApiResponse.success(userService.updateSettings(loginUser.userId(), request));
    }
}
