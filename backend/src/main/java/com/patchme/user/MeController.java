package com.patchme.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.auth.LoginUser;
import com.patchme.common.api.ApiResponse;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.exception.BusinessException;
import com.patchme.user.entity.UserEntity;
import com.patchme.user.entity.UserProfileEntity;
import com.patchme.user.mapper.UserMapper;
import com.patchme.user.mapper.UserProfileMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户上下文探针：userId 完全取自 JWT（@AuthenticationPrincipal），
 * 接口不存在任何 userId 请求参数——这就是"伪造 userId 无效"的结构保证。
 * 返回自己的资料不受匿名规则限制（仅本人可见），但绝不带 passwordHash/email 之外的内部字段。
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserMapper userMapper;
    private final UserProfileMapper profileMapper;

    public MeController(UserMapper userMapper, UserProfileMapper profileMapper) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    @GetMapping
    public ApiResponse<MeVO> me(@AuthenticationPrincipal LoginUser loginUser) {
        UserEntity user = userMapper.selectById(loginUser.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        UserProfileEntity profile = profileMapper.selectById(user.getId());
        return ApiResponse.success(new MeVO(user.getId(), profile.getUsername(), profile.getNickname(), user.getRole()));
    }

    /** 仅返回给本人：自己的 id 对自己不是秘密；email/密码哈希仍不出现在此 VO。 */
    public record MeVO(Long userId, String username, String nickname, String role) {
    }
}
