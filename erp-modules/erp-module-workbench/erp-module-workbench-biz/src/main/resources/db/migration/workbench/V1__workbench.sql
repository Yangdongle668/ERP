-- 工作台（需求 02-工作台）：待办、消息、预警、公告、首页布局与快捷入口

CREATE TABLE wb_todo (
    id          BIGINT       NOT NULL PRIMARY KEY,
    todo_key    VARCHAR(128) NOT NULL COMMENT '业务唯一键，如 WF_TASK:123',
    user_id     BIGINT       NOT NULL COMMENT '处理人',
    category    VARCHAR(16)  NOT NULL COMMENT 'APPROVAL/TASK',
    biz_type    VARCHAR(64)  NULL,
    biz_id      BIGINT       NULL,
    biz_no      VARCHAR(64)  NULL,
    title       VARCHAR(256) NOT NULL,
    route       VARCHAR(256) NULL,
    priority    VARCHAR(8)   NOT NULL DEFAULT 'NORMAL' COMMENT 'HIGH/NORMAL/LOW',
    due_time    DATETIME     NULL,
    todo_status VARCHAR(16)  NOT NULL COMMENT 'PENDING/DONE/CANCELED',
    done_at     DATETIME     NULL,
    version     INT          NOT NULL DEFAULT 0,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_wb_todo_key_user UNIQUE (todo_key, user_id)
) COMMENT '待办';

CREATE INDEX idx_wb_todo_user ON wb_todo (user_id, todo_status);

CREATE TABLE wb_message (
    id          BIGINT        NOT NULL PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    msg_type    VARCHAR(20)   NOT NULL COMMENT 'NOTICE/APPROVAL_RESULT/TASK_DONE/REMIND/SYSTEM',
    title       VARCHAR(256)  NOT NULL,
    content     VARCHAR(2000) NULL,
    route       VARCHAR(256)  NULL,
    read_flag   TINYINT       NOT NULL DEFAULT 0,
    read_at     DATETIME      NULL,
    email_sent  TINYINT       NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0
) COMMENT '站内消息';

CREATE INDEX idx_wb_message_user ON wb_message (user_id, read_flag);

CREATE TABLE wb_alert (
    id              BIGINT        NOT NULL PRIMARY KEY,
    alert_key       VARCHAR(128)  NOT NULL COMMENT '同一对象同一类型只有一条',
    alert_type      VARCHAR(32)   NOT NULL,
    level           VARCHAR(16)   NOT NULL COMMENT 'INFO/WARNING/CRITICAL',
    biz_type        VARCHAR(64)   NULL,
    biz_id          BIGINT        NULL,
    title           VARCHAR(256)  NOT NULL,
    content         VARCHAR(2000) NULL,
    route           VARCHAR(256)  NULL,
    alert_status    VARCHAR(16)   NOT NULL COMMENT 'OPEN/HANDLED/IGNORED/RESOLVED',
    first_raised_at DATETIME      NOT NULL,
    last_raised_at  DATETIME      NOT NULL,
    handled_by      BIGINT        NULL,
    handled_at      DATETIME      NULL,
    handle_remark   VARCHAR(500)  NULL,
    ignore_until    DATETIME      NULL COMMENT '忽略期截止（7 天内同级别不再提醒）',
    version         INT           NOT NULL DEFAULT 0,
    created_by      BIGINT        NULL,
    created_at      DATETIME      NOT NULL,
    updated_by      BIGINT        NULL,
    updated_at      DATETIME      NOT NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_wb_alert_key UNIQUE (alert_key)
) COMMENT '预警';

CREATE INDEX idx_wb_alert_status ON wb_alert (alert_status, level);

CREATE TABLE wb_alert_user (
    id          BIGINT   NOT NULL PRIMARY KEY,
    alert_id    BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    version     INT      NOT NULL DEFAULT 0,
    created_by  BIGINT   NULL,
    created_at  DATETIME NOT NULL,
    updated_by  BIGINT   NULL,
    updated_at  DATETIME NOT NULL,
    deleted     TINYINT  NOT NULL DEFAULT 0,
    CONSTRAINT uk_wb_alert_user UNIQUE (alert_id, user_id)
) COMMENT '预警接收人';

CREATE INDEX idx_wb_alert_user_user ON wb_alert_user (user_id);

CREATE TABLE wb_notice (
    id            BIGINT       NOT NULL PRIMARY KEY,
    title         VARCHAR(128) NOT NULL,
    content       TEXT         NOT NULL COMMENT '富文本（已过滤）',
    scope         VARCHAR(8)   NOT NULL COMMENT 'ALL/DEPT',
    dept_ids      VARCHAR(512) NULL,
    important     TINYINT      NOT NULL DEFAULT 0,
    publish_at    DATETIME     NOT NULL,
    expire_at     DATETIME     NULL,
    notice_status VARCHAR(16)  NOT NULL COMMENT 'DRAFT/PUBLISHED/WITHDRAWN',
    publisher_id  BIGINT       NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NOT NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0
) COMMENT '公告';

CREATE TABLE wb_notice_read (
    id          BIGINT   NOT NULL PRIMARY KEY,
    notice_id   BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    read_at     DATETIME NOT NULL,
    version     INT      NOT NULL DEFAULT 0,
    created_by  BIGINT   NULL,
    created_at  DATETIME NOT NULL,
    updated_by  BIGINT   NULL,
    updated_at  DATETIME NOT NULL,
    deleted     TINYINT  NOT NULL DEFAULT 0,
    CONSTRAINT uk_wb_notice_read UNIQUE (notice_id, user_id)
) COMMENT '公告阅读记录';

CREATE TABLE wb_layout (
    id          BIGINT        NOT NULL PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    layout_json VARCHAR(4000) NOT NULL COMMENT '[{"code":"..","visible":true}]，按顺序',
    version     INT           NOT NULL DEFAULT 0,
    created_by  BIGINT        NULL,
    created_at  DATETIME      NOT NULL,
    updated_by  BIGINT        NULL,
    updated_at  DATETIME      NOT NULL,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_wb_layout_user UNIQUE (user_id)
) COMMENT '首页布局';

CREATE TABLE wb_shortcut (
    id          BIGINT       NOT NULL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    menu_route  VARCHAR(256) NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0
) COMMENT '快捷入口';

CREATE INDEX idx_wb_shortcut_user ON wb_shortcut (user_id);
