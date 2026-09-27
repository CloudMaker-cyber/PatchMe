package com.patchme.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.interaction.entity.BrowsingHistoryEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 浏览历史：主键冲突即刷新 viewed_at（并发重复浏览天然幂等）。 */
@Mapper
public interface BrowsingHistoryMapper extends BaseMapper<BrowsingHistoryEntity> {

    @Insert("""
            INSERT INTO browsing_history (user_id, post_id, viewed_at)
            VALUES (#{userId}, #{postId}, NOW())
            ON DUPLICATE KEY UPDATE viewed_at = NOW()
            """)
    void touch(@Param("userId") Long userId, @Param("postId") Long postId);
}
