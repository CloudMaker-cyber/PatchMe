package com.patchme.post;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import com.patchme.common.dto.IdentityRequest;
import com.patchme.common.enums.Intent;
import com.patchme.post.dto.CloseCommentsRequest;
import com.patchme.post.dto.CreatePostRequest;
import com.patchme.post.vo.PostDetailVO;
import com.patchme.post.vo.PublicPostVO;
import com.patchme.reply.dto.CreateReplyRequest;
import com.patchme.reply.ReplyService;
import com.patchme.reply.vo.PublicReplyVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子端点。GET 对游客开放（SecurityConfig permitAll）；写操作必须登录，
 * 操作者身份一律取 @AuthenticationPrincipal（JWT），参数表里没有任何 userId。
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final ReplyService replyService;

    public PostController(PostService postService, ReplyService replyService) {
        this.postService = postService;
        this.replyService = replyService;
    }

    /** 首页流/筛选：resolved=true 才返回"已获得帮助"分组。 */
    @GetMapping
    public ApiResponse<List<PublicPostVO>> feed(@RequestParam(required = false) Long school,
                                                @RequestParam(required = false) Long major,
                                                @RequestParam(required = false) Intent intent,
                                                @RequestParam(required = false) List<Long> tags,
                                                @RequestParam(defaultValue = "false") boolean resolved,
                                                @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(
                postService.feed(school, major, intent == null ? null : intent.name(), tags, resolved, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<PostDetailVO> detail(@PathVariable Long id,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(postService.detail(id, loginUser));
    }

    @PostMapping
    public ApiResponse<Long> create(@AuthenticationPrincipal LoginUser loginUser,
                                    @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.success(postService.create(loginUser.userId(), request));
    }

    @PostMapping("/{id}/replies")
    public ApiResponse<PublicReplyVO> reply(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable Long id,
                                            @Valid @RequestBody CreateReplyRequest request) {
        return ApiResponse.success(replyService.create(loginUser.userId(), id, request));
    }

    /** 楼主开关评论。 */
    @PatchMapping("/{id}/comments")
    public ApiResponse<Void> closeComments(@AuthenticationPrincipal LoginUser loginUser,
                                           @PathVariable Long id,
                                           @Valid @RequestBody CloseCommentsRequest request) {
        postService.setCommentsClosed(loginUser.userId(), id, request.closed());
        return ApiResponse.success(null);
    }

    /** 楼主变更帖子身份（仅 PUBLIC -> ANONYMOUS）。 */
    @PatchMapping("/{id}/identity")
    public ApiResponse<Void> changeIdentity(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable Long id,
                                            @Valid @RequestBody IdentityRequest request) {
        postService.changeIdentity(loginUser.userId(), id, request.mode());
        return ApiResponse.success(null);
    }
}
