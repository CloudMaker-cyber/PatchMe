package com.patchme.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.moderation.entity.BlockEntity;
import org.apache.ibatis.annotations.Mapper;

/** 拉黑关系：联合主键表，只用 BaseMapper 的条件方法（insert/delete/exists/selectList）。 */
@Mapper
public interface BlockMapper extends BaseMapper<BlockEntity> {
}
