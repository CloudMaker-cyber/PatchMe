package com.patchme.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.auth.dto.LoginRequest;
import com.patchme.auth.dto.RegisterRequest;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.UserStatus;
import com.patchme.common.exception.BusinessException;
import com.patchme.user.entity.UserEntity;
import com.patchme.user.entity.UserProfileEntity;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserMapper;
import com.patchme.user.mapper.UserProfileMapper;
import com.patchme.user.mapper.UserSettingsMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册/登录业务。要点：
 * - 密码只存 BCrypt 哈希，日志与响应永不出现明文；
 * - 登录失败统一报"邮箱或密码错误"，不透露哪个字段错了（防账号枚举）；
 * - 注册跨三张表（users/profiles/settings），必须一个事务。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final UserSettingsMapper settingsMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.patchme.moderation.RateLimitService rateLimitService;

    public AuthService(UserMapper userMapper, UserProfileMapper profileMapper, UserSettingsMapper settingsMapper,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       com.patchme.moderation.RateLimitService rateLimitService) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.settingsMapper = settingsMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userMapper.exists(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getEmail, request.email()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "该邮箱已注册");
        }
        if (profileMapper.exists(Wrappers.<UserProfileEntity>lambdaQuery()
                .eq(UserProfileEntity::getUsername, request.username()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }

        UserEntity user = new UserEntity();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setStatus("NORMAL");
        userMapper.insert(user);

        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(user.getId());
        profile.setUsername(request.username());
        profile.setNickname(request.nickname());
        profileMapper.insert(profile);

        UserSettingsEntity settings = new UserSettingsEntity();
        settings.setUserId(user.getId());
        settings.setDefaultIdentityMode("ANONYMOUS");
        settings.setReplyNotificationEnabled(true);
        settings.setHistoryEnabled(true);
        settingsMapper.insert(settings);
    }

    /** 校验凭据并签发 JWT；返回体只带公开资料，token 由 Controller 写 Cookie。 */
    public LoginResult login(LoginRequest request) {
        // 登录失败限流（任务5）：按小写邮箱计数，窗口内失败过多直接 429，不再继续比对密码。
        rateLimitService.checkLoginFailure(request.email());
        UserEntity user = userMapper.selectOne(
                Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getEmail, request.email()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimitService.recordLoginFailure(request.email());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "邮箱或密码错误");
        }
        // 渐进式限制（任务5）：只有 BANNED 拒绝登录；WARNED/OBSERVED 正常登录，
        // RESTRICTED 能登录但写操作在 Service 层被 RateLimitService 拦截。
        if (!UserStatus.valueOf(user.getStatus() == null ? "NORMAL" : user.getStatus()).allowsLogin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被封禁，请联系管理员");
        }
        UserProfileEntity profile = profileMapper.selectById(user.getId());
        String token = jwtService.issue(user.getId(), user.getRole());
        return new LoginResult(token, profile.getUsername(), profile.getNickname(), user.getRole());
    }

    public record LoginResult(String token, String username, String nickname, String role) {
    }
}
