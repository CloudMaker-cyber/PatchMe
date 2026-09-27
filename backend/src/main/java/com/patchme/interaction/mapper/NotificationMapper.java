package com.patchme.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.interaction.entity.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;

/** 通知查询：列表/批量已读都走 BaseMapper + lambda 条件，无自定义 SQL。 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
}
