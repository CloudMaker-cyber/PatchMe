package com.patchme.user;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import com.patchme.post.vo.MinePostVO;
import com.patchme.post.vo.PublicPostVO;
import com.patchme.reply.vo.MineReplyVO;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开主页（/api/users/{username}，游客可访问，只含 PUBLIC 内容）
 * 与"我的内容"（/api/me/**，仅本人；userId 只来自 JWT）。
 */
@RestController
public class UserContentController {

    private final UserService userService;

    public UserContentController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users/{username}")
    public ApiResponse<UserService.ProfilePageVO> profile(@PathVariable String username) {
        return ApiResponse.success(userService.profile(username));
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
}
