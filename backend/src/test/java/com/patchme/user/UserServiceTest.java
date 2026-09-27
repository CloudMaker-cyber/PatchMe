package com.patchme.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patchme.post.PostService;
import com.patchme.post.mapper.PostMapper;
import com.patchme.post.vo.PostRow;
import com.patchme.post.vo.PublicPostVO;
import com.patchme.reply.mapper.ReplyMapper;
import com.patchme.support.MpTableInfo;
import com.patchme.user.dto.UpdateSettingsRequest;
import com.patchme.user.entity.UserSettingsEntity;
import com.patchme.user.mapper.UserProfileMapper;
import com.patchme.user.mapper.UserSettingsMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 账号设置（部分更新/缺行兜底）与浏览历史出口形状。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserProfileMapper profileMapper;
    @Mock
    private UserSettingsMapper settingsMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private ReplyMapper replyMapper;
    @Mock
    private PostService postService;

    @InjectMocks
    private UserService userService;

    @BeforeAll
    static void registerTableInfo() {
        MpTableInfo.init(UserSettingsEntity.class);
    }

    private static UserSettingsEntity settings(String mode, boolean notify, boolean history) {
        UserSettingsEntity s = new UserSettingsEntity();
        s.setUserId(7L);
        s.setDefaultIdentityMode(mode);
        s.setReplyNotificationEnabled(notify);
        s.setHistoryEnabled(history);
        return s;
    }

    @Test
    void missingRowFallsBackToSafeDefaults() {
        when(settingsMapper.selectById(7L)).thenReturn(null);
        UserService.SettingsVO vo = userService.getSettings(7L);
        assertThat(vo.defaultIdentityMode()).isEqualTo("ANONYMOUS");
        assertThat(vo.replyNotificationEnabled()).isTrue();
        assertThat(vo.historyEnabled()).isTrue();
    }

    @Test
    void partialUpdateOnlyAppliesProvidedFields() {
        when(settingsMapper.selectById(7L)).thenReturn(settings("PUBLIC", true, true));

        UserService.SettingsVO vo = userService.updateSettings(7L,
                new UpdateSettingsRequest(null, null, false));

        assertThat(vo.defaultIdentityMode()).isEqualTo("PUBLIC");
        assertThat(vo.replyNotificationEnabled()).isTrue();
        assertThat(vo.historyEnabled()).isFalse();
        ArgumentCaptor<UserSettingsEntity> captor = ArgumentCaptor.forClass(UserSettingsEntity.class);
        verify(settingsMapper).updateById(captor.capture());
        assertThat(captor.getValue().getHistoryEnabled()).isFalse();
        assertThat(captor.getValue().getDefaultIdentityMode()).isEqualTo("PUBLIC");
    }

    @Test
    void updateCreatesMissingRowWithDefaultsFirst() {
        // 模拟真实库：首查无行；insert/update 后同一实体可再查到
        AtomicReference<UserSettingsEntity> store = new AtomicReference<>();
        when(settingsMapper.selectById(7L)).thenAnswer(inv -> store.get());
        when(settingsMapper.insert(any(UserSettingsEntity.class))).thenAnswer(inv -> {
            store.set(inv.getArgument(0));
            return 1;
        });
        when(settingsMapper.updateById(any(UserSettingsEntity.class))).thenAnswer(inv -> {
            store.set(inv.getArgument(0));
            return 1;
        });

        UserService.SettingsVO vo = userService.updateSettings(7L,
                new UpdateSettingsRequest(null, false, null));

        verify(settingsMapper).insert(any(UserSettingsEntity.class));
        assertThat(vo.replyNotificationEnabled()).isFalse();
        assertThat(vo.defaultIdentityMode()).isEqualTo("ANONYMOUS");
        assertThat(vo.historyEnabled()).isTrue();
    }

    @Test
    void historyReturnsPublicShapedRowsOnly() {
        PostRow anon = new PostRow();
        anon.setId(3L);
        anon.setIntent("VENT");
        anon.setTitle("t");
        anon.setBody("b");
        anon.setIdentityMode("ANONYMOUS");
        anon.setCreatedAt(LocalDateTime.now());
        anon.setReplyCount(0L);
        anon.setSupportCount(0L);
        anon.setHelpful(false);
        when(postMapper.selectHistoryRows(7L, 100)).thenReturn(List.of(anon));
        when(postService.tagsOf(List.of(3L))).thenReturn(Map.of(3L, List.of(1L)));

        List<PublicPostVO> history = userService.myHistory(7L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).author().mode()).isEqualTo("anonymous");
        assertThat(history.get(0).author().nickname()).isNull();
    }
}
