package com.patchme.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patchme.common.api.ErrorCode;
import com.patchme.common.exception.BusinessException;
import com.patchme.moderation.entity.BlockEntity;
import com.patchme.moderation.mapper.BlockMapper;
import com.patchme.moderation.vo.BlockVO;
import com.patchme.support.MpTableInfo;
import com.patchme.user.entity.UserProfileEntity;
import com.patchme.user.mapper.UserProfileMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 拉黑关系：只认公开用户名、不能拉黑自己、幂等；效果本身在读取 SQL（另有用例）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlockServiceTest {

    @Mock
    private BlockMapper blockMapper;
    @Mock
    private UserProfileMapper profileMapper;

    @InjectMocks
    private BlockService blockService;

    @BeforeAll
    static void registerTableInfo() {
        MpTableInfo.init(BlockEntity.class, UserProfileEntity.class);
    }

    private static UserProfileEntity profile(Long userId, String username) {
        UserProfileEntity p = new UserProfileEntity();
        p.setUserId(userId);
        p.setUsername(username);
        p.setNickname("小满");
        return p;
    }

    @Test
    void blockUnknownUserIs404() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> blockService.block(7L, "ghost"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户不存在");
    }

    @Test
    void cannotBlockSelf() {
        when(profileMapper.selectOne(any())).thenReturn(profile(7L, "me"));
        assertThatThrownBy(() -> blockService.block(7L, "me"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能拉黑自己");
        verify(blockMapper, never()).insert(any(BlockEntity.class));
    }

    @Test
    void duplicateBlockIsIdempotent() {
        when(profileMapper.selectOne(any())).thenReturn(profile(9L, "xiaoman"));
        when(blockMapper.exists(any())).thenReturn(true);
        blockService.block(7L, "xiaoman");
        verify(blockMapper, never()).insert(any(BlockEntity.class));
    }

    @Test
    void firstBlockWritesRelation() {
        when(profileMapper.selectOne(any())).thenReturn(profile(9L, "xiaoman"));
        when(blockMapper.exists(any())).thenReturn(false);
        blockService.block(7L, "xiaoman");
        verify(blockMapper).insert(any(BlockEntity.class));
    }

    @Test
    void unblockUnknownUserIsSilent() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        blockService.unblock(7L, "ghost");
        verify(blockMapper, never()).delete(any());
    }

    @Test
    void listMapsToPublicShapedVo() {
        BlockEntity rel = new BlockEntity();
        rel.setBlockerId(7L);
        rel.setBlockedId(9L);
        when(blockMapper.selectList(any())).thenReturn(List.of(rel));
        when(profileMapper.selectList(any())).thenReturn(List.of(profile(9L, "xiaoman")));

        List<BlockVO> vos = blockService.list(7L);

        assertThat(vos).hasSize(1);
        assertThat(vos.get(0).username()).isEqualTo("xiaoman");
        assertThat(vos.get(0).nickname()).isEqualTo("小满");
    }

    @Test
    void emptyListSkipsProfileQuery() {
        when(blockMapper.selectList(any())).thenReturn(List.of());
        assertThat(blockService.list(7L)).isEmpty();
        verify(profileMapper, never()).selectList(any());
    }
}
