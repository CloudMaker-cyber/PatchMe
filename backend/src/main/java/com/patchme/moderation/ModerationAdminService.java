package com.patchme.moderation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.ModerationAction;
import com.patchme.common.enums.ReportStatus;
import com.patchme.common.enums.ReportTargetType;
import com.patchme.common.enums.UserStatus;
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
import com.patchme.moderation.vo.AdminAuditItem;
import com.patchme.moderation.vo.AdminReportItem;
import com.patchme.post.entity.PostEntity;
import com.patchme.post.mapper.PostMapper;
import com.patchme.reply.entity.ReplyEntity;
import com.patchme.reply.mapper.ReplyMapper;
import com.patchme.user.entity.UserEntity;
import com.patchme.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审核后台（仅 ADMIN 可达，SecurityConfig 已挡普通用户）。
 * 三条铁律（02 风控原则）：
 * 1) 处置与举报解耦——审核确认违规、升级限制都以"人工核实"为准，举报数量本身不处罚；
 * 2) 每次操作写 moderation_actions 审计日志，并向相关用户生成处理通知，绝不泄露举报人；
 * 3) 限制真正作用于 users.status，由 RateLimitService 在写路径读取生效。
 */
@Service
public class ModerationAdminService {

    private final ReportMapper reportMapper;
    private final ModerationActionMapper auditMapper;
    private final UserRestrictionMapper restrictionMapper;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final ReplyMapper replyMapper;
    private final NotificationService notificationService;

    public ModerationAdminService(ReportMapper reportMapper, ModerationActionMapper auditMapper,
                                  UserRestrictionMapper restrictionMapper, UserMapper userMapper,
                                  PostMapper postMapper, ReplyMapper replyMapper,
                                  NotificationService notificationService) {
        this.reportMapper = reportMapper;
        this.auditMapper = auditMapper;
        this.restrictionMapper = restrictionMapper;
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.replyMapper = replyMapper;
        this.notificationService = notificationService;
    }

    /** 审核队列：默认只看待处理，DANGER 优先。 */
    public List<AdminReportItem> queue(String status, int limit) {
        String s = (status == null || status.isBlank()) ? ReportStatus.PENDING.name() : status;
        return reportMapper.selectQueue(s, Math.min(Math.max(limit, 1), 200)).stream()
                .map(AdminReportItem::from).toList();
    }

    public List<AdminAuditItem> audit(int limit) {
        return auditMapper.selectRecent(Math.min(Math.max(limit, 1), 200)).stream()
                .map(AdminAuditItem::from).toList();
    }

    /** 核实举报：置终态 + 审计 + 通知举报人；takedown=true 时顺带软下架被举报内容。 */
    @Transactional
    public void reviewReport(Long adminId, Long reportId, ReviewReportRequest request) {
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报单不存在");
        }
        if (!ReportStatus.PENDING.name().equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该举报已处理");
        }
        boolean confirm = Boolean.TRUE.equals(request.confirm());
        ReportStatus target = confirm ? ReportStatus.CONFIRMED : ReportStatus.REJECTED;
        report.setStatus(target.name());
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        reportMapper.updateById(report);

        if (confirm && Boolean.TRUE.equals(request.takedown())) {
            takedown(ReportTargetType.valueOf(report.getTargetType()), report.getTargetId());
        }
        audit(adminId, confirm ? ModerationAction.CONFIRM_REPORT : ModerationAction.REJECT_REPORT,
                "REPORT", reportId, request.reason(), reportId);
        // 举报人收到"处理完成"通知（只回状态，不含管理员与被处理内容细节）；系统自动单无举报人，跳过
        if (report.getReporterId() != null) {
            notificationService.onReportResolved(report.getReporterId(), reportId, target.display());
        }
    }

    /** 对账户执行渐进式限制：更新 users.status、记 user_restrictions、审计、通知本人。 */
    @Transactional
    public void act(Long adminId, ModerationActionRequest request) {
        ModerationAction action = request.action();
        if (!action.targetsUser()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该动作不针对账户");
        }
        UserEntity user = userMapper.selectById(request.targetUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标用户不存在");
        }
        UserStatus next = switch (action) {
            case WARN -> UserStatus.WARNED;
            case OBSERVE -> UserStatus.OBSERVED;
            case RESTRICT -> UserStatus.RESTRICTED;
            case BAN -> UserStatus.BANNED;
            case UNBAN -> UserStatus.NORMAL;
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "未知处置");
        };
        user.setStatus(next.name());
        userMapper.updateById(user);

        if (action != ModerationAction.UNBAN) {
            UserRestrictionEntity restriction = new UserRestrictionEntity();
            restriction.setUserId(user.getId());
            restriction.setLevel(next.name());
            restriction.setReason(request.reason() == null ? "" : request.reason().trim());
            restriction.setCreatedBy(adminId);
            restrictionMapper.insert(restriction);
        }
        audit(adminId, action, "USER", user.getId(), request.reason(), request.reportId());
        notificationService.onAccountAction(user.getId(), action.display(), request.reason());
    }

    private void takedown(ReportTargetType type, Long targetId) {
        LocalDateTime now = LocalDateTime.now();
        if (type == ReportTargetType.POST) {
            PostEntity post = postMapper.selectById(targetId);
            if (post != null && post.getDeletedAt() == null) {
                post.setDeletedAt(now);
                postMapper.updateById(post);
            }
        } else {
            ReplyEntity reply = replyMapper.selectById(targetId);
            if (reply != null && reply.getDeletedAt() == null) {
                reply.setDeletedAt(now);
                replyMapper.updateById(reply);
            }
        }
    }

    private void audit(Long adminId, ModerationAction action, String targetType, Long targetId,
                       String reason, Long reportId) {
        ModerationActionEntity row = new ModerationActionEntity();
        row.setAdminId(adminId);
        row.setAction(action.name());
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setReason(reason == null ? "" : reason.trim());
        row.setReportId(reportId);
        auditMapper.insert(row);
    }
}
