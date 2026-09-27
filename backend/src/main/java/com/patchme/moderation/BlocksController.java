package com.patchme.moderation;

import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import com.patchme.moderation.dto.BlockRequest;
import com.patchme.moderation.vo.BlockVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 拉黑管理（仅本人）：按公开用户名操作；列表挂在 /api/blocks/mine 避免 DELETE 带 body。 */
@RestController
@RequestMapping("/api/blocks")
public class BlocksController {

    private final BlockService blockService;

    public BlocksController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping
    public ApiResponse<Void> block(@AuthenticationPrincipal LoginUser loginUser,
                                   @Valid @RequestBody BlockRequest request) {
        blockService.block(loginUser.userId(), request.username());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{username}")
    public ApiResponse<Void> unblock(@AuthenticationPrincipal LoginUser loginUser,
                                     @PathVariable String username) {
        blockService.unblock(loginUser.userId(), username);
        return ApiResponse.success(null);
    }

    @GetMapping("/mine")
    public ApiResponse<List<BlockVO>> mine(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(blockService.list(loginUser.userId()));
    }
}
