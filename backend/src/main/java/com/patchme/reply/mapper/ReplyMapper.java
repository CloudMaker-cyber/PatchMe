package com.patchme.reply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patchme.reply.entity.ReplyEntity;
import com.patchme.reply.vo.ReplyRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 回复查询：公开投影同样不选 author_id；"我的回复/主页回复"要求父帖可见。
 * 注意：Java 文本块会去掉每行行尾空格，跨块拼接必须显式补 " "，否则关键字会和列名粘在一起。
 */
@Mapper
public interface ReplyMapper extends BaseMapper<ReplyEntity> {

    String COLUMNS = """
            r.id, r.post_id, r.body, r.identity_mode, r.is_helpful, r.created_at,
            up.username, up.nickname, up.avatar_url
            """;

    @Select("SELECT " + COLUMNS + """
            FROM replies r
            LEFT JOIN user_profiles up ON up.user_id = r.author_id AND r.identity_mode = 'PUBLIC'
            WHERE r.post_id = #{postId} AND r.deleted_at IS NULL
            ORDER BY r.created_at ASC
            """)
    List<ReplyRow> selectVisibleByPostId(@Param("postId") Long postId);

    /** 单条回复的公开投影（发布成功后回给前端用）。 */
    @Select("SELECT " + COLUMNS + """
            FROM replies r
            LEFT JOIN user_profiles up ON up.user_id = r.author_id AND r.identity_mode = 'PUBLIC'
            WHERE r.id = #{id} AND r.deleted_at IS NULL
            """)
    ReplyRow selectRowById(@Param("id") Long id);

    @Select("<script>SELECT " + COLUMNS + """
            FROM replies r
            JOIN posts p ON p.id = r.post_id AND p.deleted_at IS NULL AND p.status = 'NORMAL'
            LEFT JOIN user_profiles up ON up.user_id = r.author_id AND r.identity_mode = 'PUBLIC'
            WHERE r.author_id = #{authorId} AND r.deleted_at IS NULL
            <if test="publicOnly">AND r.identity_mode = 'PUBLIC'</if>
            ORDER BY r.created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<ReplyRow> selectRowsByAuthor(@Param("authorId") Long authorId,
                                      @Param("publicOnly") boolean publicOnly,
                                      @Param("limit") int limit);
}
