package com.patchme.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.GuardedAction;
import com.patchme.common.enums.ReportReason;
import com.patchme.common.enums.ReportTargetType;
import com.patchme.common.exception.BusinessException;
import com.patchme.moderation.dto.CreateReportRequest;
import com.patchme.moderation.entity.ReportEntity;
import com.patchme.moderation.mapper.ReportMapper;
import com.patchme.moderation.vo.MyReportRow;
import com.patchme.moderation.vo.MyReportVO;
import com.patchme.post.entity.PostEntity;
import com.patchme.post.mapper.PostMapper;
import com.patchme.reply.entity.ReplyEntity;
import com.patchme.reply.mapper.ReplyMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 举报规则：目标必须可见、同人同目标仅一条待处理、走限流闸；
 * systemFlag 只建审核线索——不触状态、不发通知（验收：举报数量不自动处罚）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock
    private ReportMapper reportMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private ReplyMapper replyMapper;
    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private ReportService reportService;

    private static PostEntity post(Long id, String status, LocalDateTime deletedAt) {
        PostEntity p = new PostEntity();
        p.setId(id);
        p.setStatus(status);
        p.setDeletedAt(deletedAt);
        return p;
    }

    private static ReplyEntity reply(Long id, Long postId, LocalDateTime deletedAt) {
        ReplyEntity r = new ReplyEntity();
        r.setId(id);
        r.setPostId(postId);
        r.setDeletedAt(deletedAt);
        return r;
    }

    private static CreateReportRequest request(ReportTargetType type, Long id) {
        return new CreateReportRequest(type, id, ReportReason.SPAM, "广告内容");
    }

    @Test
    void createInsertsPendingUserReportAndConsumesQuota() {
        when(postMapper.selectById(1L)).thenReturn(post(1L, "NORMAL", null));
        when(reportMapper.exists(any())).thenReturn(false);
        when(reportMapper.insert(any(ReportEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, ReportEntity.class).setId(77L);
            return 1;
        });

        Long id = reportService.create(7L, request(ReportTargetType.POST, 1L));

        assertThat(id).isEqualTo(77L);
        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportMapper).insert(captor.capture());
        ReportEntity row = captor.getValue();
        assertThat(row.getSource()).isEqualTo("USER");
        assertThat(row.getStatus()).isEqualTo("PENDING");
        assertThat(row.getReason()).isEqualTo("SPAM");
        verify(rateLimitService).check(7L, GuardedAction.REPORT);
        verify(rateLimitService).record(7L, GuardedAction.REPORT);
    }

    @Test
    void duplicatePendingReportIsConflictAndSkipsRateLimit() {
        when(postMapper.selectById(1L)).thenReturn(post(1L, "NORMAL", null));
        when(reportMapper.exists(any())).thenReturn(true);
        assertThatThrownBy(() -> reportService.create(7L, request(ReportTargetType.POST, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("你已举报过该内容，正在处理中");
        verify(reportMapper, never()).insert(any(ReportEntity.class));
        // 幂等拒绝不消耗限流额度
        verify(rateLimitService, never()).check(any(), any());
    }

    @Test
    void deletedOrTakedownPostCannotBeReported() {
        when(postMapper.selectById(1L)).thenReturn(post(1L, "NORMAL", LocalDateTime.now()));
        assertThatThrownBy(() -> reportService.create(7L, request(ReportTargetType.POST, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        when(postMapper.selectById(2L)).thenReturn(post(2L, "TAKEDOWN", null));
        assertThatThrownBy(() -> reportService.create(7L, request(ReportTargetType.POST, 2L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void replyTargetRequiresItsPostVisibleToo() {
        when(replyMapper.selectById(5L)).thenReturn(reply(5L, 1L, LocalDateTime.now()));
        assertThatThrownBy(() -> reportService.create(7L, request(ReportTargetType.REPLY, 5L)))
                .isInstanceOf(BusinessException.class);

        when(replyMapper.selectById(6L)).thenReturn(reply(6L, 1L, null));
        when(postMapper.selectById(1L)).thenReturn(post(1L, "NORMAL", LocalDateTime.now()));
        assertThatThrownBy(() -> reportService.create(7L, request(ReportTargetType.REPLY, 6L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("内容不存在或已删除");
    }

    @Test
    void systemFlagCreatesLeadOnlyNeverPunishes() {
        when(reportMapper.exists(any())).thenReturn(false);

        reportService.systemFlag(ReportTargetType.POST, 11L, new RiskDetector.RiskFlags(true, false));

        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportMapper).insert(captor.capture());
        ReportEntity row = captor.getValue();
        assertThat(row.getSource()).isEqualTo("SYSTEM");
        assertThat(row.getReporterId()).isNull();
        assertThat(row.getReason()).isEqualTo("DANGER");
        // 关键断言：风险求助≠违规——状态保持 NORMAL，且无任何用户侧动作
        assertThat(row.getStatus()).isEqualTo("PENDING");
        verify(rateLimitService, never()).check(any(), any());
        verify(postMapper, never()).updateById(any(PostEntity.class));
    }

    @Test
    void systemFlagSkipsCleanContentAndExistingPendingLead() {
        reportService.systemFlag(ReportTargetType.POST, 11L, RiskDetector.RiskFlags.CLEAN);
        verify(reportMapper, never()).insert(any(ReportEntity.class));

        when(reportMapper.exists(any())).thenReturn(true);
        reportService.systemFlag(ReportTargetType.POST, 11L, new RiskDetector.RiskFlags(true, true));
        verify(reportMapper, never()).insert(any(ReportEntity.class));
    }

    @Test
    void systemSpamFlagUsesSpamReason() {
        when(reportMapper.exists(any())).thenReturn(false);
        reportService.systemFlag(ReportTargetType.REPLY, 3L, new RiskDetector.RiskFlags(false, true));
        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("SPAM");
    }

    @Test
    void mineExposesOnlyStatusProgressWithDisplayText() {
        MyReportRow row = new MyReportRow();
        row.setId(9L);
        row.setTargetType("POST");
        row.setTargetId(1L);
        row.setReason("HARASSMENT");
        row.setStatus("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        when(reportMapper.selectMine(7L, 2)).thenReturn(List.of(row));

        List<MyReportVO> vos = reportService.mine(7L, 2);

        assertThat(vos).hasSize(1);
        assertThat(vos.get(0).reasonDisplay()).isEqualTo("骚扰辱骂");
        assertThat(vos.get(0).statusDisplay()).isEqualTo("待处理");
    }
}
