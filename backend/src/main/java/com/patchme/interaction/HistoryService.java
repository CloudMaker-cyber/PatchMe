package com.patchme.interaction;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.interaction.entity.BrowsingHistoryEntity;
import com.patchme.interaction.mapper.BrowsingHistoryMapper;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserSettingsMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 浏览历史（仅本人）。规则（docs/v3/01）：
 * - history_enabled=false 时不再记录（已有记录保留，可一键清空）；
 * - 只保留最近 RETENTION_DAYS 天，写入时顺手清理该用户的过期记录（无需调度器）；
 * - 同一帖重复浏览只刷新时间（UPSERT），列表天然去重。
 */
@Service
public class HistoryService {

    static final int RETENTION_DAYS = 30;

    private final BrowsingHistoryMapper historyMapper;
    private final UserSettingsMapper settingsMapper;

    public HistoryService(BrowsingHistoryMapper historyMapper, UserSettingsMapper settingsMapper) {
        this.historyMapper = historyMapper;
        this.settingsMapper = settingsMapper;
    }

    /** 登录用户浏览帖子详情时记录；游客与关闭开关的用户不记录。 */
    public void recordView(Long userId, Long postId) {
        UserSettingsEntity settings = settingsMapper.selectById(userId);
        if (settings != null && !Boolean.TRUE.equals(settings.getHistoryEnabled())) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        historyMapper.delete(Wrappers.<BrowsingHistoryEntity>lambdaQuery()
                .eq(BrowsingHistoryEntity::getUserId, userId)
                .lt(BrowsingHistoryEntity::getViewedAt, cutoff));
        historyMapper.touch(userId, postId);
    }

    /** 一键清空本人历史。 */
    public void clear(Long userId) {
        historyMapper.delete(Wrappers.<BrowsingHistoryEntity>lambdaQuery()
                .eq(BrowsingHistoryEntity::getUserId, userId));
    }
}
