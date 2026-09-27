package com.patchme.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.moderation.entity.ReportEntity;
import com.patchme.moderation.vo.MyReportRow;
import com.patchme.moderation.vo.ReportQueueRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 举报单：写入走 BaseMapper；两个出口投影各自单点定义（02"数据出口单一"）。 */
@Mapper
public interface ReportMapper extends BaseMapper<ReportEntity> {

    /**
     * 管理端审核队列（仅 /api/admin 可达）：带目标作者与正文摘要供核实。
     * 这是全系统唯一允许把"匿名内容的作者 id"投影给读方的 SQL——读方已被 SecurityConfig 限定为 ADMIN。
     */
    @Select("<script>SELECT r.id, r.source, r.target_type, r.target_id, r.reason, r.note, r.status, r.created_at,"
            + """
              CASE WHEN r.target_type = 'POST' THEN (SELECT p.title FROM posts p WHERE p.id = r.target_id) ELSE '' END AS target_title,
              COALESCE(
                (SELECT LEFT(p.body, 120) FROM posts p WHERE r.target_type = 'POST' AND p.id = r.target_id),
                (SELECT LEFT(rb.body, 120) FROM replies rb WHERE r.target_type = 'REPLY' AND rb.id = r.target_id)
              ) AS target_excerpt,
              COALESCE(
                (SELECT p.author_id FROM posts p WHERE r.target_type = 'POST' AND p.id = r.target_id),
                (SELECT rb3.author_id FROM replies rb3 WHERE r.target_type = 'REPLY' AND rb3.id = r.target_id)
              ) AS author_id,
              COALESCE(
                (SELECT p.identity_mode FROM posts p WHERE r.target_type = 'POST' AND p.id = r.target_id),
                (SELECT rb4.identity_mode FROM replies rb4 WHERE r.target_type = 'REPLY' AND rb4.id = r.target_id)
              ) AS author_identity_mode
            FROM reports r
            WHERE 1 = 1
            <if test="status != null">AND r.status = #{status}</if>
            ORDER BY (r.reason = 'DANGER') DESC, r.created_at ASC, r.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<ReportQueueRow> selectQueue(@Param("status") String status, @Param("limit") int limit);

    /** 我的举报记录（仅举报人本人）：id、对象、原因、状态、时间；不含 note 之外的处理细节，更不含管理员。 */
    @Select("""
            SELECT id, target_type, target_id, reason, status, created_at
            FROM reports
            WHERE reporter_id = #{reporterId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<MyReportRow> selectMine(@Param("reporterId") Long reporterId, @Param("limit") int limit);
}
