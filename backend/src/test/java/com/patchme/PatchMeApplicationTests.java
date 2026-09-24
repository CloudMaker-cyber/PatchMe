package com.patchme;

import com.patchme.user.mapper.UserMapper;
import com.patchme.user.mapper.UserProfileMapper;
import com.patchme.user.mapper.UserSettingsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 冒烟测试：验证完整上下文（Security + MyBatis-Plus + Flyway 依赖）可装配。
 * 测试配置排除了数据源，Mapper 用 mock 顶替，因此无需本地 MySQL。
 */
@SpringBootTest
class PatchMeApplicationTests {

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private UserProfileMapper userProfileMapper;

    @MockitoBean
    private UserSettingsMapper userSettingsMapper;

    @Test
    void contextLoads() {
    }
}
