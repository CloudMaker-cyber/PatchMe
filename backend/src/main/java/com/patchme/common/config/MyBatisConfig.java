package com.patchme.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 持久层装配。刻意不放主类：@SpringBootApplication 上的注解会被 @WebMvcTest 切片处理，
 * 切片里没有 DataSource/sqlSessionFactory，MapperFactoryBean 会创建失败（任务 2 实测踩坑）。
 */
@Configuration
@EnableTransactionManagement
// dict 包的三个字典 Mapper 与实体同包，必须显式列入扫描路径
@MapperScan({"com.patchme.**.mapper", "com.patchme.dict"})
public class MyBatisConfig {
}
