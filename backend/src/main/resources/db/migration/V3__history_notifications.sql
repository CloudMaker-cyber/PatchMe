-- 任务 4：浏览历史与通知（规范 docs/v3/02）。仅本人可见的数据放这里；users/settings/posts 零改动。
-- 历史一人一帖一行（重复浏览刷新 viewed_at），30 天保留在 Service 写入时顺带清理，不引入调度器。

CREATE TABLE browsing_history (
  user_id   BIGINT   NOT NULL,
  post_id   BIGINT   NOT NULL,
  viewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, post_id),
  KEY idx_history_user_viewed (user_id, viewed_at),
  CONSTRAINT fk_history_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_history_post FOREIGN KEY (post_id) REFERENCES posts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE notifications (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  user_id      BIGINT      NOT NULL COMMENT '接收者，仅本人可查',
  type         VARCHAR(20) NOT NULL COMMENT 'REPLY/MODERATION/REPORT/SECURITY（后三类任务5启用）',
  payload_json VARCHAR(512) NOT NULL COMMENT '仅存公开 id 与摘要，不存任何身份字段',
  read_at      DATETIME    NULL COMMENT 'NULL 即未读',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notifications_user_created (user_id, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
