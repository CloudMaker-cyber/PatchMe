package com.patchme.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.moderation.entity.ModerationActionEntity;
import com.patchme.moderation.vo.AuditRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 审计日志：只增不改不删；读取带操作者/目标用户名投影，仅 /api/admin 出口使用。 */
@Mapper
public interface ModerationActionMapper extends BaseMapper<ModerationActionEntity> {

    @Select("""
            SELECT m.id, up.username AS admin_username, m.action, m.target_type, m.target_id,
                   tu.username AS target_username, m.reason, m.report_id, m.created_at
            FROM moderation_actions m
            JOIN user_profiles up ON up.user_id = m.admin_id
            LEFT JOIN user_profiles tu ON m.target_type = 'USER' AND tu.user_id = m.target_id
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT #{limit}
            """)
    List<AuditRow> selectRecent(int limit);
}
