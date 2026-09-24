package com.patchme.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.post.entity.PostEntity;
import com.patchme.post.vo.PostRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 帖子查询。基础 CRUD 走 BaseMapper；首页流/筛选这类"投影 + 分桶排序"用自定义 SQL
 * （05 清单：MyBatis-Plus 管基础 CRUD，复杂筛选走自定义 SQL）。
 * 所有公开投影的 SELECT 列表刻意不含 author_id/school_id/major_id。
 * 注意：Java 文本块会去掉每行行尾空格，跨块拼接必须显式补 " "，否则关键字会和列名粘在一起。
 */
@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {

    String PUBLIC_COLUMNS = """
            p.id, p.intent, p.title, p.body, p.identity_mode, p.comments_closed_at, p.created_at,
            up.username, up.nickname, up.avatar_url,
            (SELECT COUNT(*) FROM replies r WHERE r.post_id = p.id AND r.deleted_at IS NULL) AS reply_count,
            (SELECT COUNT(*) FROM post_supports s WHERE s.post_id = p.id) AS support_count,
            EXISTS(SELECT 1 FROM replies rh WHERE rh.post_id = p.id AND rh.deleted_at IS NULL
                   AND rh.is_helpful = 1) AS helpful
            """;

    /**
     * 首页流：resolved=false 取"待回答+待帮助"（待回答在前，同组发布早优先），
     * resolved=true 取"已获得帮助"（有有帮助回复或评论已关闭）。筛选全部在 WHERE 完成。
     */
    @Select("<script>SELECT " + PUBLIC_COLUMNS + """
            FROM posts p
            LEFT JOIN user_profiles up ON up.user_id = p.author_id AND p.identity_mode = 'PUBLIC'
            WHERE p.deleted_at IS NULL AND p.status = 'NORMAL'
            <if test="schoolId != null">AND p.school_id = #{schoolId}</if>
            <if test="majorId != null">AND p.major_id = #{majorId}</if>
            <if test="intent != null">AND p.intent = #{intent}</if>
            <if test="tagIds != null and tagIds.size() > 0">
              AND EXISTS (SELECT 1 FROM post_tags pt WHERE pt.post_id = p.id AND pt.tag_id IN
              <foreach item="t" collection="tagIds" open="(" separator="," close=")">#{t}</foreach>)
            </if>
            <choose>
              <when test="resolved">
                AND (p.comments_closed_at IS NOT NULL OR EXISTS
                     (SELECT 1 FROM replies ra WHERE ra.post_id = p.id AND ra.deleted_at IS NULL AND ra.is_helpful = 1))
              </when>
              <otherwise>
                AND p.comments_closed_at IS NULL AND NOT EXISTS
                    (SELECT 1 FROM replies rb WHERE rb.post_id = p.id AND rb.deleted_at IS NULL AND rb.is_helpful = 1)
              </otherwise>
            </choose>
            ORDER BY (reply_count = 0) DESC, p.created_at ASC
            LIMIT #{limit}
            </script>
            """)
    List<PostRow> selectFeed(@Param("schoolId") Long schoolId,
                             @Param("majorId") Long majorId,
                             @Param("intent") String intent,
                             @Param("tagIds") List<Long> tagIds,
                             @Param("resolved") boolean resolved,
                             @Param("limit") int limit);

    /** 详情投影；不存在或已删除返回 null。 */
    @Select("SELECT " + PUBLIC_COLUMNS + """
            FROM posts p
            LEFT JOIN user_profiles up ON up.user_id = p.author_id AND p.identity_mode = 'PUBLIC'
            WHERE p.id = #{id} AND p.deleted_at IS NULL AND p.status = 'NORMAL'
            """)
    PostRow selectRowById(@Param("id") Long id);

    /** 某人主页/我的列表。publicOnly=true 用于公开主页（匿名永不现身）；false 用于"我的"。 */
    @Select("<script>SELECT " + PUBLIC_COLUMNS + """
            FROM posts p
            LEFT JOIN user_profiles up ON up.user_id = p.author_id AND p.identity_mode = 'PUBLIC'
            WHERE p.author_id = #{authorId} AND p.deleted_at IS NULL AND p.status = 'NORMAL'
            <if test="publicOnly">AND p.identity_mode = 'PUBLIC'</if>
            ORDER BY p.created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<PostRow> selectRowsByAuthor(@Param("authorId") Long authorId,
                                     @Param("publicOnly") boolean publicOnly,
                                     @Param("limit") int limit);

    /** 我的收藏（仅本人查询入口存在）。 */
    @Select("SELECT " + PUBLIC_COLUMNS + """
            FROM posts p
            JOIN bookmarks bk ON bk.post_id = p.id AND bk.user_id = #{userId}
            LEFT JOIN user_profiles up ON up.user_id = p.author_id AND p.identity_mode = 'PUBLIC'
            WHERE p.deleted_at IS NULL AND p.status = 'NORMAL'
            ORDER BY bk.created_at DESC
            LIMIT #{limit}
            """)
    List<PostRow> selectBookmarkedRows(@Param("userId") Long userId, @Param("limit") int limit);
}
