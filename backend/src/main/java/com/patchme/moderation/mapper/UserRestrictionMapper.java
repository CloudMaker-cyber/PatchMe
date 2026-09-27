package com.patchme.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.moderation.entity.UserRestrictionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 限制历史：只增；当前生效级别读 users.status。 */
@Mapper
public interface UserRestrictionMapper extends BaseMapper<UserRestrictionEntity> {
}
