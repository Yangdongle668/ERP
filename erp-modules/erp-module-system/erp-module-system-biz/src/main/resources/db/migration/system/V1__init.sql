-- 系统管理模块初始化：用户、角色、权限、编码规则
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL COMMENT '用户名（登录名）',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    real_name     VARCHAR(64)  NOT NULL COMMENT '姓名',
    email         VARCHAR(128) NULL,
    mobile        VARCHAR(32)  NULL,
    org_id        BIGINT       NULL COMMENT '所属组织',
    dept_id       BIGINT       NULL COMMENT '主部门',
    status        VARCHAR(16)  NOT NULL COMMENT 'ENABLED/DISABLED',
    fail_count    INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_until    DATETIME     NULL COMMENT '锁定截止时间',
    last_login_at DATETIME     NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL,
    updated_by    BIGINT       NULL,
    updated_at    DATETIME     NOT NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
) COMMENT '用户';

CREATE TABLE sys_role (
    id         BIGINT      NOT NULL PRIMARY KEY,
    code       VARCHAR(64) NOT NULL COMMENT '角色编码',
    name       VARCHAR(64) NOT NULL,
    data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT 'ALL/ORG/DEPT_AND_CHILD/DEPT/SELF/CUSTOM',
    status     VARCHAR(16) NOT NULL,
    version    INT         NOT NULL DEFAULT 0,
    created_by BIGINT      NULL,
    created_at DATETIME    NOT NULL,
    updated_by BIGINT      NULL,
    updated_at DATETIME    NOT NULL,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_role_code UNIQUE (code)
) COMMENT '角色';

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) COMMENT '用户-角色';

CREATE TABLE sys_role_permission (
    role_id    BIGINT       NOT NULL,
    permission VARCHAR(128) NOT NULL COMMENT '权限标识，如 eng:material:query；* 表示全部',
    PRIMARY KEY (role_id, permission)
) COMMENT '角色-权限';

CREATE TABLE sys_code_rule (
    id           BIGINT      NOT NULL PRIMARY KEY,
    biz_code     VARCHAR(64) NOT NULL COMMENT '业务编码',
    name         VARCHAR(64) NOT NULL,
    prefix       VARCHAR(32) NOT NULL DEFAULT '',
    date_pattern VARCHAR(16) NOT NULL DEFAULT '',
    seq_length   INT         NOT NULL,
    reset_cycle  VARCHAR(16) NOT NULL COMMENT 'NEVER/YEAR/MONTH/DAY',
    version      INT         NOT NULL DEFAULT 0,
    created_by   BIGINT      NULL,
    created_at   DATETIME    NOT NULL,
    updated_by   BIGINT      NULL,
    updated_at   DATETIME    NOT NULL,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_code_rule_biz UNIQUE (biz_code)
) COMMENT '编码规则';

CREATE TABLE sys_code_seq (
    biz_code      VARCHAR(64) NOT NULL,
    reset_key     VARCHAR(32) NOT NULL COMMENT '重置键，如 202609；不重置时为 ALL',
    current_value BIGINT      NOT NULL,
    PRIMARY KEY (biz_code, reset_key)
) COMMENT '编码流水号';

-- 初始超级管理员 admin / admin123（上线后必须修改密码）
INSERT INTO sys_user (id, username, password_hash, real_name, status, created_at, updated_at)
VALUES (1, 'admin', '$2a$10$ootXMnPpouq/3p4KBce7/eNcGtS/jGYRmW2fbzLEi7NS0wTpWZoIy', '超级管理员', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO sys_role (id, code, name, data_scope, status, created_at, updated_at)
VALUES (1, 'SUPER_ADMIN', '超级管理员', 'ALL', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_role_permission (role_id, permission) VALUES (1, '*');
