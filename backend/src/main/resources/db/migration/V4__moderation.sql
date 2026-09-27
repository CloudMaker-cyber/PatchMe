-- 任务 5：举报、拉黑、审核与渐进式限制（规范 docs/v3/02）。
-- 原则：举报只是线索，处罚只来自管理员核实；本迁移不给任何既有表加 NOT NULL 约束，旧行零影响。
-- 初始管理员：任务 2 起 role 列即存在，这里把演示账号 salt_demo_a（id=3）提升为 ADMIN，
-- 密码仍走注册时的 BCrypt 哈希与 .env 流程，本文件不出现任何明文密码。

UPDATE users SET role = 'ADMIN' WHERE id = 3;

CREATE TABLE blocks (
  blocker_id BIGINT   NOT NULL COMMENT '拉黑发者',
  blocked_id BIGINT   NOT NULL COMMENT '被拉黑者；仅对其公开身份（PUBLIC 内容/主页）生效',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (blocker_id, blocked_id),
  KEY idx_blocks_blocked (blocked_id),
  CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id),
  CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reports (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  reporter_id BIGINT      NULL COMMENT '举报人；source=SYSTEM 时为 NULL（风控自动送审）。任何出口 VO 都不携带',
  source      VARCHAR(10) NOT NULL DEFAULT 'USER' COMMENT 'USER=用户举报 / SYSTEM=风险检测自动送审（优先审核线索）',
  target_type VARCHAR(10) NOT NULL COMMENT 'POST / REPLY',
  target_id   BIGINT      NOT NULL,
  reason      VARCHAR(20) NOT NULL COMMENT 'HARASSMENT/SPAM/PRIVACY/DANGER/OTHER',
  note        VARCHAR(300) NOT NULL DEFAULT '',
  status      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/REJECTED；数量本身不触发任何处罚',
  reviewed_by BIGINT      NULL COMMENT '处理管理员',
  reviewed_at DATETIME    NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_reports_status_created (status, created_at),
  KEY idx_reports_reporter (reporter_id, created_at),
  CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
  CONSTRAINT fk_reports_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE moderation_actions (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  admin_id       BIGINT       NOT NULL COMMENT '操作管理员',
  action         VARCHAR(20)  NOT NULL COMMENT 'WARN/OBSERVE/RESTRICT/BAN/UNBAN/CONFIRM_REPORT/REJECT_REPORT',
  target_type    VARCHAR(10)  NOT NULL COMMENT 'USER / REPORT',
  target_id      BIGINT       NOT NULL,
  reason         VARCHAR(300) NOT NULL DEFAULT '',
  report_id      BIGINT       NULL COMMENT '处罚所依据的已核实举报（可为空：风险内容直接处置）',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_moderation_actions_created (created_at),
  CONSTRAINT fk_moderation_admin FOREIGN KEY (admin_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_restrictions (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  level      VARCHAR(20) NOT NULL COMMENT 'WARNED/OBSERVED/RESTRICTED/BANNED',
  starts_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME    NULL COMMENT 'NULL 表示需管理员手动解除',
  reason     VARCHAR(300) NOT NULL DEFAULT '',
  created_by BIGINT      NOT NULL COMMENT '作出限制的管理员',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_restrictions_user (user_id, starts_at),
  CONSTRAINT fk_restrictions_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_restrictions_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 限流动作流水（02：初版用 MySQL 计数窗口，出现压力后再迁移 Redis）。与审计日志分表：
-- 这是机器写的原始计数素材，可按窗口清理，不承载"谁处置了谁"的语义。
-- 用户动作按 user_id 计数；登录失败按 subject_key（小写邮箱）计数——失败时可能还没有账户 id。
CREATE TABLE moderation_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NULL,
  subject_key VARCHAR(191) NULL COMMENT '非用户维度的计数主体（登录失败=小写邮箱）',
  action      VARCHAR(20)  NOT NULL COMMENT 'GuardedAction：POST/REPLY/REPORT/LOGIN_FAILURE',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_moderation_log_user_action_created (user_id, action, created_at),
  KEY idx_moderation_log_subject_action_created (subject_key, action, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
