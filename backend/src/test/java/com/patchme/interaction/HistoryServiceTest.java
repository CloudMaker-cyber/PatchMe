package com.patchme.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patchme.interaction.entity.BrowsingHistoryEntity;
import com.patchme.interaction.mapper.BrowsingHistoryMapper;
import com.patchme.support.MpTableInfo;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserSettingsMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 浏览历史：开关遵从、UPSERT 记录、清空。列表投影复用 PostMapper 公开列（出口形状由 SQL 保证）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HistoryServiceTest {

    @Mock
    private BrowsingHistoryMapper historyMapper;
    @Mock
    private UserSettingsMapper settingsMapper;

    @InjectMocks
    private HistoryService historyService;

    @BeforeAll
    static void registerTableInfo() {
        MpTableInfo.init(BrowsingHistoryEntity.class);
    }

    private static UserSettingsEntity settings(boolean historyEnabled) {
        UserSettingsEntity s = new UserSettingsEntity();
        s.setUserId(7L);
        s.setHistoryEnabled(historyEnabled);
        return s;
    }

    @Test
    void viewIsRecordedWhenEnabled() {
        when(settingsMapper.selectById(7L)).thenReturn(settings(true));
        historyService.recordView(7L, 1L);
        verify(historyMapper).touch(7L, 1L);
    }

    @Test
    void viewNotRecordedWhenDisabled() {
        when(settingsMapper.selectById(7L)).thenReturn(settings(false));
        historyService.recordView(7L, 1L);
        verify(historyMapper, never()).touch(any(), any());
        verify(historyMapper, never()).delete(any());
    }

    @Test
    void missingSettingsRowFallsBackToRecording() {
        when(settingsMapper.selectById(7L)).thenReturn(null);
        historyService.recordView(7L, 1L);
        verify(historyMapper).touch(7L, 1L);
    }

    @Test
    void clearRemovesAllRowsOfCurrentUser() {
        historyService.clear(7L);
        verify(historyMapper).delete(any());
    }
}
