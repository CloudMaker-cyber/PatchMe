package com.patchme.moderation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.exception.BusinessException;
import com.patchme.moderation.entity.BlockEntity;
import com.patchme.moderation.mapper.BlockMapper;
import com.patchme.moderation.vo.BlockVO;
import com.patchme.user.entity.UserProfileEntity;
import com.patchme.user.mapper.UserProfileMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 拉黑（仅对公开身份账户生效——01）。匿名内容无法被拉黑，因为拿不到可关联的稳定身份。
 * 拉黑效果在读取 SQL 中以 NOT EXISTS(blocks) 实现，这里只维护关系本身。
 */
@Service
public class BlockService {

    private final BlockMapper blockMapper;
    private final UserProfileMapper profileMapper;

    public BlockService(BlockMapper blockMapper, UserProfileMapper profileMapper) {
        this.blockMapper = blockMapper;
        this.profileMapper = profileMapper;
    }

    public void block(Long blockerId, String username) {
        UserProfileEntity target = profileMapper.selectOne(
                Wrappers.<UserProfileEntity>lambdaQuery().eq(UserProfileEntity::getUsername, username));
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (target.getUserId().equals(blockerId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不能拉黑自己");
        }
        if (isBlocking(blockerId, target.getUserId())) {
            return; // 幂等：重复拉黑不报错也不重复写
        }
        BlockEntity row = new BlockEntity();
        row.setBlockerId(blockerId);
        row.setBlockedId(target.getUserId());
        blockMapper.insert(row);
    }

    /** 取消拉黑按用户名定位；未拉黑过则静默成功（幂等）。 */
    public void unblock(Long blockerId, String username) {
        UserProfileEntity target = profileMapper.selectOne(
                Wrappers.<UserProfileEntity>lambdaQuery().eq(UserProfileEntity::getUsername, username));
        if (target == null) {
            return;
        }
        blockMapper.delete(Wrappers.<BlockEntity>lambdaQuery()
                .eq(BlockEntity::getBlockerId, blockerId)
                .eq(BlockEntity::getBlockedId, target.getUserId()));
    }

    /** 我的拉黑列表（仅本人）：返回当初公开身份的用户名/昵称。 */
    public List<BlockVO> list(Long blockerId) {
        List<Long> blockedIds = blockMapper.selectList(Wrappers.<BlockEntity>lambdaQuery()
                        .eq(BlockEntity::getBlockerId, blockerId)).stream()
                .map(BlockEntity::getBlockedId).toList();
        if (blockedIds.isEmpty()) {
            return List.of();
        }
        return profileMapper.selectList(Wrappers.<UserProfileEntity>lambdaQuery()
                        .in(UserProfileEntity::getUserId, blockedIds)).stream()
                .map(p -> new BlockVO(p.getUserId(), p.getUsername(), p.getNickname(), p.getAvatarUrl()))
                .toList();
    }

    public boolean isBlocking(Long blockerId, Long blockedId) {
        return blockMapper.exists(Wrappers.<BlockEntity>lambdaQuery()
                .eq(BlockEntity::getBlockerId, blockerId)
                .eq(BlockEntity::getBlockedId, blockedId));
    }
}
