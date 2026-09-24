package com.patchme.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯 Mockito 单测没有 MyBatis 容器，TableInfo/lambda 缓存不会自动建立；
 * Service 里构造 LambdaQueryWrapper 时会报 "can not find lambda cache"。测试前手动注册。
 */
public final class MpTableInfo {

    private MpTableInfo() {
    }

    public static void init(Class<?>... entities) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }
}
