package com.patchme.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.ModerationAction;
import com.patchme.common.enums.ReportStatus;
import com.patchme.common.exception.BusinessException;
import com.patchme.interaction.NotificationService;
import com.patchme.moderation.dto.ModerationActionRequest;
import com.patchme.moderation.dto.ReviewReportRequest;
import com.patchme.moderation.entity.ModerationActionEntity;
import com.patchme.moderation.entity.ReportEntity;
import com.patchme.moderation.entity.UserRestrictionEntity;
import com.patchme.moderation.mapper.ModerationActionMapper;
import com.patchme.moderation.mapper.ReportMapper;
import com.patchme.moderation.mapper.UserRestrictionMapper;
import com.patchme.moderation.vo.AdminReportItem;
import com.patchme.moderation.vo.ReportQueueRow;
import com.patchme.post.entity.PostEntity;
import com.patchme.post.mapper.PostMapper;
import com.patchme.reply.mapper.ReplyMapper;
import com.patchme.user.entity.UserEntity;
import com.patchme.user.mapper.UserMapper;
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
 * 审核后台规则：举报单终态不可再流转；确认+下架才动内容；
 * 处置写状态/限制/审计/通知四件套；通知只发给举报人且永不带管理员身份。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationAdminServiceTest {

    @Mock
    private ReportMapper reportMapper;
    @Mock
    private ModerationActionMapper auditMapper;
    @Mock
    private UserRestrictionMapper restrictionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private ReplyMapper replyMapper;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ModerationAdminService adminService;

    private static ReportEntity pendingReport(Long id, Long reporterId) {
        ReportEntity r = new ReportEntity();
        r.setId(id);
        r.setReporterId(reporterId);
        r.setSource(reporterId == null ? "SYSTEM" : "USER");
        r.setTargetType("POST");
        r.setTargetId(11L);
        r.setReason("SPAM");
        r.setStatus(ReportStatus.PENDING.name());
        return r;
    }

    @Test
    void queueDefaultsToPendingAndMapsDisplay() {
        ReportQueueRow row = new ReportQueueRow();
        row.setId(1L);
        row.setSource("USER");
        row.setTargetType("POST");
        row.setTargetId(11L);
        row.setReason("DANGER");
        row.setStatus("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        when(reportMapper.selectQueue("PENDING", 5)).thenReturn(List.of(row));

        List<AdminReportItem> items = adminService.queue(null, 5);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).reasonDisplay()).isEqualTo("危险内容");
        assertThat(items.get(0).statusDisplay()).isEqualTo("待处理");
    }

    @Test
    void confirmWithTakedownSoftDeletesTargetAndNotifiesReporter() {
        ReportEntity report = pendingReport(3L, 7L);
        when(reportMapper.selectById(3L)).thenReturn(report);
        PostEntity post = new PostEntity();
        post.setId(11L);
        post.setStatus("NORMAL");
        when(postMapper.selectById(11L)).thenReturn(post);

        adminService.reviewReport(99L, 3L, new ReviewReportRequest(true, "确认引流广告", true));

        assertThat(report.getStatus()).isEqualTo("CONFIRMED");
        assertThat(report.getReviewedBy()).isEqualTo(99L);
        assertThat(report.getReviewedAt()).isNotNull();
        ArgumentCaptor<PostEntity> takedown = ArgumentCaptor.forClass(PostEntity.class);
        verify(postMapper).updateById(takedown.capture());
        assertThat(takedown.getValue().getDeletedAt()).isNotNull();
        // 只通知举报人处理结果；系统单没有举报人则跳过（另一用例）
        verify(notificationService).onReportResolved(7L, 3L, "已确认违规");
        verify(auditMapper).insert(any(ModerationActionEntity.class));
    }

    @Test
    void rejectKeepsContentOnlineAndSkipsSystemReports() {
        ReportEntity systemReport = pendingReport(4L, null);
        when(reportMapper.selectById(4L)).thenReturn(systemReport);

        adminService.reviewReport(99L, 4L, new ReviewReportRequest(false, "误报", null));

        assertThat(systemReport.getStatus()).isEqualTo("REJECTED");
        verify(postMapper, never()).updateById(any(PostEntity.class));
        verify(notificationService, never()).onReportResolved(any(), any(), any());
    }

    @Test
    void processedReportCannotBeReviewedAgain() {
        ReportEntity report = pendingReport(3L, 7L);
        report.setStatus(ReportStatus.CONFIRMED.name());
        when(reportMapper.selectById(3L)).thenReturn(report);
        assertThatThrownBy(() -> adminService.reviewReport(99L, 3L, new ReviewReportRequest(true, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该举报已处理");
        verify(reportMapper, never()).updateById(any(ReportEntity.class));
    }

    @Test
    void missingReportIs404() {
        when(reportMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> adminService.reviewReport(99L, 404L, new ReviewReportRequest(true, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void banUpdatesStatusWritesRestrictionAuditAndNotification() {
        UserEntity target = new UserEntity();
        target.setId(5L);
        target.setStatus("RESTRICTED");
        when(userMapper.selectById(5L)).thenReturn(target);

        adminService.act(99L, new ModerationActionRequest(ModerationAction.BAN, 5L, "持续引流广告", 3L));

        assertThat(target.getStatus()).isEqualTo("BANNED");
        ArgumentCaptor<UserRestrictionEntity> restriction = ArgumentCaptor.forClass(UserRestrictionEntity.class);
        verify(restrictionMapper).insert(restriction.capture());
        assertThat(restriction.getValue().getLevel()).isEqualTo("BANNED");
        assertThat(restriction.getValue().getCreatedBy()).isEqualTo(99L);
        ArgumentCaptor<ModerationActionEntity> audit = ArgumentCaptor.forClass(ModerationActionEntity.class);
        verify(auditMapper).insert(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("BAN");
        assertThat(audit.getValue().getReportId()).isEqualTo(3L);
        verify(notificationService).onAccountAction(5L, "封禁", "持续引流广告");
    }

    @Test
    void unbanRestoresNormalWithoutNewRestriction() {
        UserEntity target = new UserEntity();
        target.setId(5L);
        target.setStatus("BANNED");
        when(userMapper.selectById(5L)).thenReturn(target);

        adminService.act(99L, new ModerationActionRequest(ModerationAction.UNBAN, 5L, "申诉通过", null));

        assertThat(target.getStatus()).isEqualTo("NORMAL");
        verify(restrictionMapper, never()).insert(any(UserRestrictionEntity.class));
        verify(notificationService).onAccountAction(5L, "解除限制恢复正常", "申诉通过");
    }

    @Test
    void reportScopedActionsCannotTargetUsers() {
        assertThatThrownBy(() -> adminService.act(99L,
                new ModerationActionRequest(ModerationAction.CONFIRM_REPORT, 5L, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该动作不针对账户");
    }

    @Test
    void actOnMissingUserIs404() {
        when(userMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> adminService.act(99L,
                new ModerationActionRequest(ModerationAction.WARN, 404L, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
