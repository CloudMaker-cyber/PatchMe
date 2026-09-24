package com.patchme.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.user.entity.UserSettingsEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserSettingsMapper extends BaseMapper<UserSettingsEntity> {
}
