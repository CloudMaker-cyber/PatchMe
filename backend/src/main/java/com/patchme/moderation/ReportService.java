package com.patchme.moderation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.patchme.common.api.ErrorCode;
import com.patchme.common.enums.GuardedAction;
import com.patchme.common.enums.ReportReason;
import com.patchme.common.enums.ReportStatus;
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
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 举报（02 风控原则：举报只是线索，数量永不自动处罚）。
 * 三道规则都收在本 Service：
 * 1) 目标必须是当前可见的帖子/回复（软删/已下架不可举报）；
 * 2) 同一人对同一目标只允许一条未处理举报（幂等，防刷）；
 * 3) 举报动作受 RateLimitService 窗口限流。
 * 风控自动送审（source=SYSTEM）走 systemFlag：不占用户额度、同目标已有待处理单则跳过。
 */
@Service
public class ReportService {

    private final ReportMapper reportMapper;
    private final PostMapper postMapper;
    private final ReplyMapper replyMapper;
    private final RateLimitService rateLimitService;

    public ReportService(ReportMapper reportMapper, PostMapper postMapper, ReplyMapper replyMapper,
                         RateLimitService rateLimitService) {
        this.reportMapper = reportMapper;
        this.postMapper = postMapper;
        this.replyMapper = replyMapper;
        this.rateLimitService = rateLimitService;
    }

    public Long create(Long reporterId, CreateReportRequest request) {
        requireVisibleTarget(request.targetType(), request.targetId());
        if (reportMapper.exists(Wrappers.<ReportEntity>lambdaQuery()
                .eq(ReportEntity::getReporterId, reporterId)
                .eq(ReportEntity::getTargetType, request.targetType().name())
                .eq(ReportEntity::getTargetId, request.targetId())
                .eq(ReportEntity::getStatus, ReportStatus.PENDING.name()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "你已举报过该内容，正在处理中");
        }
        rateLimitService.check(reporterId, GuardedAction.REPORT);
        ReportEntity report = new ReportEntity();
        report.setReporterId(reporterId);
        report.setSource("USER");
        report.setTargetType(request.targetType().name());
        report.setTargetId(request.targetId());
        report.setReason(request.reason().name());
        report.setNote(request.note() == null ? "" : request.note().trim());
        report.setStatus(ReportStatus.PENDING.name());
        reportMapper.insert(report);
        rateLimitService.record(reporterId, GuardedAction.REPORT);
        return report.getId();
    }

    /** 发布链风控自动送审：命中即时风险词或引流特征时调用（与写入同事务）。 */
    public void systemFlag(ReportTargetType targetType, Long targetId, RiskDetector.RiskFlags flags) {
        if (!flags.atRisk() && !flags.spam()) {
            return;
        }
        if (reportMapper.exists(Wrappers.<ReportEntity>lambdaQuery()
                .eq(ReportEntity::getTargetType, targetType.name())
                .eq(ReportEntity::getTargetId, targetId)
                .eq(ReportEntity::getStatus, ReportStatus.PENDING.name()))) {
            return;
        }
        ReportEntity report = new ReportEntity();
        report.setSource("SYSTEM");
        report.setTargetType(targetType.name());
        report.setTargetId(targetId);
        report.setReason(flags.atRisk() ? ReportReason.DANGER.name() : ReportReason.SPAM.name());
        report.setNote(flags.atRisk() ? "系统风控：疑似即时风险内容，请优先审核（注意：风险求助不等于违规）"
                : "系统风控：正文含联系方式/外链等引流特征");
        report.setStatus(ReportStatus.PENDING.name());
        reportMapper.insert(report);
    }

    /** 我的举报记录（仅本人）。 */
    public List<MyReportVO> mine(Long reporterId, int limit) {
        return reportMapper.selectMine(reporterId, Math.min(Math.max(limit, 1), 200)).stream()
                .map(MyReportVO::from).toList();
    }

    /** 目标必须真实存在且当前可见；否则 404（不区分"被删/被下架"，少给信息）。 */
    private void requireVisibleTarget(ReportTargetType type, Long targetId) {
        if (type == ReportTargetType.POST) {
            PostEntity post = postMapper.selectById(targetId);
            if (post == null || post.getDeletedAt() != null || !"NORMAL".equals(post.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
            }
        } else {
            ReplyEntity reply = replyMapper.selectById(targetId);
            if (reply == null || reply.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
            }
            PostEntity post = postMapper.selectById(reply.getPostId());
            if (post == null || post.getDeletedAt() != null || !"NORMAL".equals(post.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
            }
        }
    }
}
