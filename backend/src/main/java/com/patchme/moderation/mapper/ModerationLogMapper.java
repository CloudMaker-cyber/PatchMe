package com.patchme.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.moderation.entity.ModerationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/** 限流流水：插入 + 窗口计数（Wrapper selectCount），无自定义 SQL。 */
@Mapper
public interface ModerationLogMapper extends BaseMapper<ModerationLogEntity> {
}
