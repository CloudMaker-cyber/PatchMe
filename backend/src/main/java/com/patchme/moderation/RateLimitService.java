package com.patchme.moderation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.GuardedAction;
import com.patchme.common.enums.UserStatus;
import com.patchme.common.exception.BusinessException;
import com.patchme.moderation.entity.ModerationLogEntity;
import com.patchme.moderation.mapper.ModerationLogMapper;
import com.patchme.user.entity.UserEntity;
import com.patchme.user.mapper.UserMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 写操作风控闸（02 风控原则：限流在 Service 层、MySQL 计数窗口实现）。
 * 两道检查合一处调用：
 * 1) 账号状态：RESTRICTED/BANNED 真正拦截写入（渐进式限制不停留在"标记"层面）；
 * 2) 时间窗口：同一动作在窗口内的流水数达到阈值即 429。
 * record 在业务成功后调用（与写入同事务）；并发下窗口计数是软限制，够首版使用。
 */
@Service
public class RateLimitService {

    private final ModerationLogMapper logMapper;
    private final UserMapper userMapper;

    public RateLimitService(ModerationLogMapper logMapper, UserMapper userMapper) {
        this.logMapper = logMapper;
        this.userMapper = userMapper;
    }

    /** 阈值单点表：新增受限动作只在这里加一行。 */
    private static String limitRule(GuardedAction action) {
        return switch (action) {
            case POST -> "60秒内最多 1 条发帖";
            case REPLY -> "60秒内最多 5 条回复";
            case REPORT -> "10分钟内最多 10 条举报";
            case LOGIN_FAILURE -> "10分钟内密码错误最多 10 次";
        };
    }

    private static Duration limitWindow(GuardedAction action) {
        return switch (action) {
            case POST, REPLY -> Duration.ofSeconds(60);
            case REPORT, LOGIN_FAILURE -> Duration.ofMinutes(10);
        };
    }

    private static int limitQuota(GuardedAction action) {
        return switch (action) {
            case POST -> 1;
            case REPLY -> 5;
            case REPORT, LOGIN_FAILURE -> 10;
        };
    }

    /** 发帖/回复/举报前调用；不通过直接抛业务异常，写入不会发生。 */
    public void check(Long userId, GuardedAction action) {
        if (action == GuardedAction.LOGIN_FAILURE) {
            throw new IllegalArgumentException("登录失败按邮箱限流，请用 checkLoginFailure");
        }
        UserEntity user = userMapper.selectById(userId);
        UserStatus status = user == null || user.getStatus() == null
                ? UserStatus.NORMAL : UserStatus.valueOf(user.getStatus());
        if (status.blocksWrite()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                action == GuardedAction.REPORT
                        ? "当前账号状态下不能提交举报"
                        : "账号处于" + status.display() + "状态，暂时不能发布内容");
        }
        LocalDateTime since = LocalDateTime.now().minus(limitWindow(action));
        long count = logMapper.selectCount(Wrappers.<ModerationLogEntity>lambdaQuery()
                .eq(ModerationLogEntity::getUserId, userId)
                .eq(ModerationLogEntity::getAction, action.name())
                .ge(ModerationLogEntity::getCreatedAt, since));
        if (count >= limitQuota(action)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "操作太频繁：" + limitRule(action));
        }
    }

    /** 业务成功后记录流水（与写入同一事务）。 */
    public void record(Long userId, GuardedAction action) {
        ModerationLogEntity row = new ModerationLogEntity();
        row.setUserId(userId);
        row.setAction(action.name());
        logMapper.insert(row);
    }

    /** 登录入口前调用：按小写邮箱计数失败次数（按邮箱而非 userId——失败时可能根本没有账户）。 */
    public void checkLoginFailure(String email) {
        LocalDateTime since = LocalDateTime.now().minus(limitWindow(GuardedAction.LOGIN_FAILURE));
        long count = logMapper.selectCount(Wrappers.<ModerationLogEntity>lambdaQuery()
                .eq(ModerationLogEntity::getSubjectKey, email.toLowerCase())
                .eq(ModerationLogEntity::getAction, GuardedAction.LOGIN_FAILURE.name())
                .ge(ModerationLogEntity::getCreatedAt, since));
        if (count >= limitQuota(GuardedAction.LOGIN_FAILURE)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "失败次数过多，请 10 分钟后再试（或先核对邮箱/密码）");
        }
    }

    /** 每次凭据校验失败后记一笔（不区分"邮箱不存在/密码错"，响应保持同一文案）。 */
    public void recordLoginFailure(String email) {
        ModerationLogEntity row = new ModerationLogEntity();
        row.setSubjectKey(email.toLowerCase());
        row.setAction(GuardedAction.LOGIN_FAILURE.name());
        logMapper.insert(row);
    }
}
