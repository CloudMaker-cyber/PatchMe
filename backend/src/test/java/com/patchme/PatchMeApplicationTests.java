package com.patchme;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 冒烟测试：验证 Spring 上下文（含 Flyway/MyBatis-Plus 依赖下的装配）可正常启动。
 * 数据源在测试配置中被排除，因此无需本地 MySQL。
 */
@SpringBootTest
class PatchMeApplicationTests {

    @Test
    void contextLoads() {
    }
}
