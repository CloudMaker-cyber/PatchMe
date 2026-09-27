package com.patchme.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.GuardedAction;
import com.patchme.common.exception.BusinessException;
import com.patchme.moderation.entity.ModerationLogEntity;
import com.patchme.moderation.mapper.ModerationLogMapper;
import com.patchme.user.entity.UserEntity;
import com.patchme.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 风控闸两类拦截：
 * 1) 限制状态真正阻止写入（验收：渐进式限制不停留在标记层面）；
 * 2) 窗口内流水达到阈值 → 429；登录失败按邮箱计数。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitServiceTest {

    @Mock
    private ModerationLogMapper logMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private RateLimitService rateLimitService;

    private static UserEntity userWithStatus(String status) {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setStatus(status);
        return user;
    }

    @Test
    void normalUserWithinWindowPasses() {
        when(userMapper.selectById(7L)).thenReturn(userWithStatus("NORMAL"));
        when(logMapper.selectCount(any())).thenReturn(0L);
        rateLimitService.check(7L, GuardedAction.POST);
    }

    @Test
    void missingStatusRowIsTreatedAsNormal() {
        when(userMapper.selectById(7L)).thenReturn(userWithStatus(null));
        when(logMapper.selectCount(any())).thenReturn(0L);
        rateLimitService.check(7L, GuardedAction.REPLY);
    }

    @Test
    void restrictedAndBannedAccountsAreBlockedFromWrites() {
        when(userMapper.selectById(7L)).thenReturn(userWithStatus("RESTRICTED"));
        assertThatThrownBy(() -> rateLimitService.check(7L, GuardedAction.POST))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发布受限")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        when(userMapper.selectById(7L)).thenReturn(userWithStatus("BANNED"));
        assertThatThrownBy(() -> rateLimitService.check(7L, GuardedAction.REPLY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        // 状态闸在窗口计数之前，连流水都不该查
        verify(logMapper, never()).selectCount(any());
    }

    @Test
    void restrictedUserCannotReportEither() {
        when(userMapper.selectById(7L)).thenReturn(userWithStatus("RESTRICTED"));
        assertThatThrownBy(() -> rateLimitService.check(7L, GuardedAction.REPORT))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号状态下不能提交举报");
    }

    @Test
    void warnedAndObservedCanStillWrite() {
        when(userMapper.selectById(7L)).thenReturn(userWithStatus("WARNED"));
        when(logMapper.selectCount(any())).thenReturn(0L);
        rateLimitService.check(7L, GuardedAction.POST);
        when(userMapper.selectById(7L)).thenReturn(userWithStatus("OBSERVED"));
        rateLimitService.check(7L, GuardedAction.POST);
    }

    @Test
    void windowQuotaTriggersTooManyRequests() {
        when(userMapper.selectById(7L)).thenReturn(userWithStatus("NORMAL"));
        // POST 配额是 1：窗口内已有 1 条即拦截
        when(logMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> rateLimitService.check(7L, GuardedAction.POST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        // REPLY 配额是 5：4 条放行、5 条拦截
        when(logMapper.selectCount(any())).thenReturn(4L);
        rateLimitService.check(7L, GuardedAction.REPLY);
        when(logMapper.selectCount(any())).thenReturn(5L);
        assertThatThrownBy(() -> rateLimitService.check(7L, GuardedAction.REPLY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("60秒内最多 5 条回复");
    }

    @Test
    void recordWritesFlowRowAfterSuccess() {
        rateLimitService.record(7L, GuardedAction.POST);
        ArgumentCaptor<ModerationLogEntity> captor = ArgumentCaptor.forClass(ModerationLogEntity.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getAction()).isEqualTo("POST");
    }

    @Test
    void loginFailureCountingIsKeyedByLowercasedEmail() {
        rateLimitService.recordLoginFailure("Hacker@Example.COM");
        ArgumentCaptor<ModerationLogEntity> captor = ArgumentCaptor.forClass(ModerationLogEntity.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getSubjectKey()).isEqualTo("hacker@example.com");
        assertThat(captor.getValue().getAction()).isEqualTo("LOGIN_FAILURE");
        assertThat(captor.getValue().getUserId()).isNull();

        when(logMapper.selectCount(any())).thenReturn(10L);
        assertThatThrownBy(() -> rateLimitService.checkLoginFailure("hacker@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void accountCheckRejectsLoginFailureAction() {
        assertThatThrownBy(() -> rateLimitService.check(7L, GuardedAction.LOGIN_FAILURE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
