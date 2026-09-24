-- 任务 2：核心表结构与索引（规范 docs/v3/02）
-- 时间按 JDBC 连接时区存 DATETIME；软删除用 deleted_at；匿名归属字段（author_id/school_id/major_id）仅存在于库内。

CREATE TABLE users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  email         VARCHAR(128) NOT NULL COMMENT '仅登录用，绝不公开',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt，绝不存明文',
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
  status        VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/WARNED/OBSERVED/RESTRICTED/BANNED（任务5启用）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_profiles (
  user_id    BIGINT      NOT NULL,
  username   VARCHAR(32) NOT NULL COMMENT '公开主页标识 /u/{username}',
  nickname   VARCHAR(32) NOT NULL,
  avatar_url VARCHAR(255) NULL,
  bio        VARCHAR(200) NOT NULL DEFAULT '',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_profiles_username (username),
  CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_settings (
  user_id                    BIGINT      NOT NULL,
  default_identity_mode      VARCHAR(10) NOT NULL DEFAULT 'ANONYMOUS' COMMENT '发帖/回复默认身份',
  reply_notification_enabled TINYINT(1)  NOT NULL DEFAULT 1,
  history_enabled            TINYINT(1)  NOT NULL DEFAULT 1,
  updated_at                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE schools (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(64) NOT NULL,
  active     TINYINT(1)  NOT NULL DEFAULT 1,
  sort_order INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_schools_active_sort (active, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE majors (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(64) NOT NULL,
  active     TINYINT(1)  NOT NULL DEFAULT 1,
  sort_order INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_majors_active_sort (active, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE tags (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(32) NOT NULL,
  active     TINYINT(1)  NOT NULL DEFAULT 1,
  sort_order INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tags_name (name),
  KEY idx_tags_active_sort (active, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE posts (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  author_id         BIGINT      NOT NULL COMMENT '内部归属：删除/楼主操作/风控用，绝不进公开 VO',
  identity_mode     VARCHAR(10) NOT NULL COMMENT 'ANONYMOUS / PUBLIC，发布时定死；匿名内容永不转公开',
  intent            VARCHAR(10) NOT NULL COMMENT 'VENT / ADVICE / COMPANION',
  title             VARCHAR(60) NOT NULL DEFAULT '',
  body              TEXT        NOT NULL,
  school_id         BIGINT      NULL COMMENT '仅用于筛选 WHERE，绝不进 VO',
  major_id          BIGINT      NULL,
  status            VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  comments_closed_at DATETIME   NULL COMMENT '非 NULL 即评论已关闭（楼主操作）',
  deleted_at        DATETIME    NULL COMMENT '软删除',
  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_posts_created_status (created_at, status),
  KEY idx_posts_author (author_id),
  KEY idx_posts_school (school_id),
  KEY idx_posts_major (major_id),
  CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users (id),
  CONSTRAINT fk_posts_school FOREIGN KEY (school_id) REFERENCES schools (id),
  CONSTRAINT fk_posts_major FOREIGN KEY (major_id) REFERENCES majors (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE post_tags (
  post_id    BIGINT   NOT NULL,
  tag_id     BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (post_id, tag_id),
  KEY idx_post_tags_tag (tag_id),
  CONSTRAINT fk_post_tags_post FOREIGN KEY (post_id) REFERENCES posts (id),
  CONSTRAINT fk_post_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE replies (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  post_id       BIGINT      NOT NULL,
  author_id     BIGINT      NOT NULL,
  identity_mode VARCHAR(10) NOT NULL,
  body          VARCHAR(2000) NOT NULL,
  is_helpful    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '楼主标记，每帖至多一条（Service 层保证）',
  deleted_at    DATETIME    NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_replies_post_created (post_id, created_at),
  KEY idx_replies_author (author_id),
  CONSTRAINT fk_replies_post FOREIGN KEY (post_id) REFERENCES posts (id),
  CONSTRAINT fk_replies_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE post_supports (
  post_id    BIGINT   NOT NULL,
  user_id    BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (post_id, user_id),
  KEY idx_supports_user (user_id),
  CONSTRAINT fk_supports_post FOREIGN KEY (post_id) REFERENCES posts (id),
  CONSTRAINT fk_supports_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE bookmarks (
  user_id    BIGINT   NOT NULL,
  post_id    BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, post_id),
  KEY idx_bookmarks_post (post_id),
  CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_bookmarks_post FOREIGN KEY (post_id) REFERENCES posts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 字典种子数据（与前端 mock 的固定字典一致）
INSERT INTO schools (id, name, sort_order) VALUES
  (1, '北京大学', 10), (2, '清华大学', 20), (3, '复旦大学', 30), (4, '上海交通大学', 40),
  (5, '浙江大学', 50), (6, '南京大学', 60), (7, '武汉大学', 70), (8, '四川大学', 80);

INSERT INTO majors (id, name, sort_order) VALUES
  (1, '计算机与电子', 10), (2, '经管与金融', 20), (3, '医学与健康', 30),
  (4, '法学与人文', 40), (5, '理学', 50), (6, '艺术与设计', 60);

INSERT INTO tags (id, name, sort_order) VALUES
  (1, '学业压力', 10), (2, '人际关系', 20), (3, '室友相处', 30), (4, '家庭', 40), (5, '情感', 50),
  (6, '就业保研', 60), (7, '身心健康', 70), (8, '消费借贷', 80), (9, '适应迷茫', 90), (10, '兴趣同好', 100);
