-- 系统管理第 1 步：组织、用户扩展、角色权限、数据字典、编码规则扩展、计量单位、币别汇率、付款条件、系统参数、日志
-- 字段命名避开 MySQL 保留字：precision → qty_precision，usage → term_usage，value → item_value / param_value，level → org_level

-- ==================== 组织 ====================
CREATE TABLE sys_org (
    id                 BIGINT       NOT NULL PRIMARY KEY,
    parent_id          BIGINT       NULL COMMENT '上级组织，根节点为空',
    code               VARCHAR(32)  NOT NULL COMMENT '组织编码（大写）',
    name               VARCHAR(64)  NOT NULL,
    short_name         VARCHAR(32)  NULL,
    org_type           VARCHAR(16)  NOT NULL COMMENT 'COMPANY/DEPT',
    leader_user_id     BIGINT       NULL COMMENT '负责人',
    phone              VARCHAR(32)  NULL,
    address            VARCHAR(256) NULL,
    name_en            VARCHAR(128) NULL,
    address_en         VARCHAR(256) NULL,
    tax_no             VARCHAR(32)  NULL,
    logo_file_id       BIGINT       NULL,
    path               VARCHAR(512) NOT NULL COMMENT '祖先路径 /1/5/12/（含自身）',
    org_level          INT          NOT NULL COMMENT '层级，根为 1',
    sort               INT          NOT NULL DEFAULT 0,
    status             VARCHAR(16)  NOT NULL,
    remark             VARCHAR(256) NULL,
    version            INT          NOT NULL DEFAULT 0,
    created_by         BIGINT       NULL,
    created_at         DATETIME     NOT NULL,
    updated_by         BIGINT       NULL,
    updated_at         DATETIME     NOT NULL,
    deleted            TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_org_code UNIQUE (code)
) COMMENT '组织（公司/部门）';
CREATE INDEX idx_sys_org_parent ON sys_org (parent_id);
CREATE INDEX idx_sys_org_path ON sys_org (path);

INSERT INTO sys_org (id, parent_id, code, name, short_name, org_type, path, org_level, sort, status, created_at, updated_at)
VALUES (100, NULL, 'HQ', '本公司', '本公司', 'COMPANY', '/100/', 1, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ==================== 用户（补充字段） ====================
ALTER TABLE sys_user ADD COLUMN employee_no VARCHAR(32) NULL COMMENT '工号';
ALTER TABLE sys_user ADD COLUMN position VARCHAR(32) NULL COMMENT '岗位（字典 sys_position）';
ALTER TABLE sys_user ADD COLUMN superior_user_id BIGINT NULL COMMENT '直属上级';
ALTER TABLE sys_user ADD COLUMN gender VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'MALE/FEMALE/UNKNOWN';
ALTER TABLE sys_user ADD COLUMN avatar_file_id BIGINT NULL;
ALTER TABLE sys_user ADD COLUMN language VARCHAR(8) NOT NULL DEFAULT 'zh-CN';
ALTER TABLE sys_user ADD COLUMN is_admin TINYINT NOT NULL DEFAULT 0 COMMENT '内置超级管理员';
ALTER TABLE sys_user ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 1 COMMENT '下次登录必须修改密码';
ALTER TABLE sys_user ADD COLUMN password_changed_at DATETIME NULL;
ALTER TABLE sys_user ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT '令牌版本';
ALTER TABLE sys_user ADD COLUMN last_login_ip VARCHAR(64) NULL;
ALTER TABLE sys_user ADD COLUMN remark VARCHAR(256) NULL;
CREATE UNIQUE INDEX uk_sys_user_employee_no ON sys_user (employee_no);
CREATE UNIQUE INDEX uk_sys_user_mobile ON sys_user (mobile);
CREATE INDEX idx_sys_user_dept ON sys_user (dept_id);

UPDATE sys_user SET is_admin = 1, must_change_password = 0, org_id = 100, dept_id = 100, password_changed_at = CURRENT_TIMESTAMP WHERE id = 1;

CREATE TABLE sys_user_dept (
    user_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, dept_id)
) COMMENT '用户兼职部门';
CREATE INDEX idx_sys_user_dept_dept ON sys_user_dept (dept_id);
CREATE INDEX idx_sys_user_role_role ON sys_user_role (role_id);

CREATE TABLE sys_password_history (
    id            BIGINT       NOT NULL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME     NOT NULL
) COMMENT '历史密码（每个用户保留最近 10 条）';
CREATE INDEX idx_sys_password_history_user ON sys_password_history (user_id, created_at);

-- ==================== 角色与权限 ====================
ALTER TABLE sys_role ADD COLUMN is_builtin TINYINT NOT NULL DEFAULT 0;
ALTER TABLE sys_role ADD COLUMN sort INT NOT NULL DEFAULT 0;
ALTER TABLE sys_role ADD COLUMN remark VARCHAR(256) NULL;
CREATE UNIQUE INDEX uk_sys_role_name ON sys_role (name);
UPDATE sys_role SET is_builtin = 1, sort = 0 WHERE id = 1;

CREATE TABLE sys_role_data_dept (
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, dept_id)
) COMMENT '角色自定义数据范围';

CREATE TABLE sys_permission (
    code        VARCHAR(128) NOT NULL PRIMARY KEY COMMENT '权限标识',
    module_code VARCHAR(32)  NOT NULL,
    group_code  VARCHAR(64)  NOT NULL,
    group_name  VARCHAR(64)  NOT NULL,
    group_sort  INT          NOT NULL DEFAULT 0,
    name        VARCHAR(64)  NOT NULL,
    perm_type   VARCHAR(16)  NOT NULL COMMENT 'MENU/BUTTON/FIELD',
    depends_on  VARCHAR(512) NULL COMMENT '依赖的权限点，逗号分隔',
    sort        INT          NOT NULL DEFAULT 0,
    active      TINYINT      NOT NULL DEFAULT 1 COMMENT '声明已移除时为 0',
    updated_at  DATETIME     NOT NULL
) COMMENT '权限点目录（启动时由模块声明同步）';

-- ==================== 数据字典 ====================
CREATE TABLE sys_dict_type (
    id          BIGINT       NOT NULL PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    module_code VARCHAR(32)  NOT NULL,
    is_builtin  TINYINT      NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL,
    remark      VARCHAR(256) NULL,
    version     INT          NOT NULL DEFAULT 0,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_dict_type_code UNIQUE (code)
) COMMENT '字典类型';

CREATE TABLE sys_dict_item (
    id          BIGINT       NOT NULL PRIMARY KEY,
    type_code   VARCHAR(64)  NOT NULL,
    item_value  VARCHAR(32)  NOT NULL,
    label       VARCHAR(64)  NOT NULL,
    label_en    VARCHAR(128) NULL,
    tag_type    VARCHAR(16)  NOT NULL DEFAULT 'DEFAULT',
    sort        INT          NOT NULL DEFAULT 0,
    is_default  TINYINT      NOT NULL DEFAULT 0,
    is_builtin  TINYINT      NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL,
    remark      VARCHAR(256) NULL,
    version     INT          NOT NULL DEFAULT 0,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_dict_item_value UNIQUE (type_code, item_value)
) COMMENT '字典项';

CREATE TABLE sys_dict_version (
    id      INT    NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL
) COMMENT '字典版本号（任意变更 +1，前端据此刷新缓存）';
INSERT INTO sys_dict_version (id, version) VALUES (1, 1);

-- ==================== 编码规则（补充字段） ====================
ALTER TABLE sys_code_rule ADD COLUMN module_code VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE sys_code_rule ADD COLUMN seq_separator VARCHAR(4) NOT NULL DEFAULT '' COMMENT '日期与流水号之间的分隔符';
ALTER TABLE sys_code_rule ADD COLUMN allow_manual TINYINT NOT NULL DEFAULT 0;
ALTER TABLE sys_code_rule ADD COLUMN allowed_vars VARCHAR(256) NOT NULL DEFAULT '' COMMENT '前缀可用变量，逗号分隔（由模块声明同步）';
ALTER TABLE sys_code_seq MODIFY COLUMN reset_key VARCHAR(128) NOT NULL;
ALTER TABLE sys_code_seq ADD COLUMN updated_at DATETIME NULL;

-- ==================== 计量单位 ====================
CREATE TABLE sys_uom (
    id            BIGINT      NOT NULL PRIMARY KEY,
    code          VARCHAR(16) NOT NULL,
    name          VARCHAR(16) NOT NULL,
    name_en       VARCHAR(32) NULL,
    category      VARCHAR(16) NOT NULL COMMENT 'COUNT/WEIGHT/LENGTH/AREA/VOLUME/TIME',
    qty_precision INT         NOT NULL DEFAULT 0 COMMENT '数量小数位 0~4',
    sort          INT         NOT NULL DEFAULT 0,
    is_builtin    TINYINT     NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL,
    version       INT         NOT NULL DEFAULT 0,
    created_by    BIGINT      NULL,
    created_at    DATETIME    NOT NULL,
    updated_by    BIGINT      NULL,
    updated_at    DATETIME    NOT NULL,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_uom_code UNIQUE (code),
    CONSTRAINT uk_sys_uom_name UNIQUE (name)
) COMMENT '计量单位';

CREATE TABLE sys_uom_conversion (
    id         BIGINT         NOT NULL PRIMARY KEY,
    from_uom   VARCHAR(16)    NOT NULL,
    to_uom     VARCHAR(16)    NOT NULL,
    rate       DECIMAL(24, 10) NOT NULL COMMENT '1 源单位 = rate 目标单位',
    version    INT            NOT NULL DEFAULT 0,
    created_by BIGINT         NULL,
    created_at DATETIME       NOT NULL,
    updated_by BIGINT         NULL,
    updated_at DATETIME       NOT NULL,
    deleted    TINYINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_uom_conversion UNIQUE (from_uom, to_uom)
) COMMENT '通用单位换算';

INSERT INTO sys_uom (id, code, name, name_en, category, qty_precision, sort, is_builtin, status, created_at, updated_at) VALUES
(201, 'PCS', '个', 'pcs', 'COUNT', 0, 10, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(202, 'SET', '套', 'set', 'COUNT', 0, 20, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(203, 'PAIR', '对', 'pair', 'COUNT', 0, 30, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(204, 'BOX', '箱', 'box', 'COUNT', 0, 40, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(205, 'CTN', '件', 'carton', 'COUNT', 0, 50, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(206, 'BAG', '包', 'bag', 'COUNT', 0, 60, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(207, 'ROLL', '卷', 'roll', 'COUNT', 0, 70, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(208, 'SHEET', '张', 'sheet', 'COUNT', 0, 80, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(209, 'REEL', '盘', 'reel', 'COUNT', 0, 90, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(210, 'KG', '千克', 'kg', 'WEIGHT', 3, 100, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(211, 'G', '克', 'g', 'WEIGHT', 2, 110, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(212, 'T', '吨', 't', 'WEIGHT', 4, 120, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(213, 'M', '米', 'm', 'LENGTH', 3, 130, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(214, 'CM', '厘米', 'cm', 'LENGTH', 2, 140, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(215, 'MM', '毫米', 'mm', 'LENGTH', 1, 150, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(216, 'M2', '平方米', 'm²', 'AREA', 4, 160, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(217, 'L', '升', 'L', 'VOLUME', 3, 170, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(218, 'ML', '毫升', 'mL', 'VOLUME', 1, 180, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(219, 'H', '小时', 'h', 'TIME', 2, 190, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(220, 'MIN', '分钟', 'min', 'TIME', 0, 200, 1, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sys_uom_conversion (id, from_uom, to_uom, rate, created_at, updated_at) VALUES
(251, 'KG', 'G', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(252, 'T', 'KG', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(253, 'M', 'CM', 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(254, 'M', 'MM', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(255, 'CM', 'MM', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(256, 'L', 'ML', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(257, 'H', 'MIN', 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ==================== 币别与汇率 ====================
CREATE TABLE sys_currency (
    id               BIGINT      NOT NULL PRIMARY KEY,
    code             VARCHAR(3)  NOT NULL,
    name             VARCHAR(16) NOT NULL,
    name_en          VARCHAR(32) NULL,
    symbol           VARCHAR(4)  NULL,
    amount_precision INT         NOT NULL DEFAULT 2,
    is_base          TINYINT     NOT NULL DEFAULT 0,
    sort             INT         NOT NULL DEFAULT 0,
    status           VARCHAR(16) NOT NULL,
    version          INT         NOT NULL DEFAULT 0,
    created_by       BIGINT      NULL,
    created_at       DATETIME    NOT NULL,
    updated_by       BIGINT      NULL,
    updated_at       DATETIME    NOT NULL,
    deleted          TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_currency_code UNIQUE (code)
) COMMENT '币别';

INSERT INTO sys_currency (id, code, name, name_en, symbol, amount_precision, is_base, sort, status, created_at, updated_at) VALUES
(301, 'CNY', '人民币', 'Chinese Yuan', '¥', 2, 1, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(302, 'USD', '美元', 'US Dollar', '$', 2, 0, 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(303, 'EUR', '欧元', 'Euro', '€', 2, 0, 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(304, 'HKD', '港币', 'Hong Kong Dollar', 'HK$', 2, 0, 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(305, 'JPY', '日元', 'Japanese Yen', '¥', 0, 0, 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(306, 'GBP', '英镑', 'British Pound', '£', 2, 0, 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE sys_exchange_rate (
    id             BIGINT         NOT NULL PRIMARY KEY,
    currency       VARCHAR(3)     NOT NULL,
    rate_type      VARCHAR(16)    NOT NULL COMMENT 'DAILY/MONTH_END',
    effective_date DATE           NOT NULL,
    rate           DECIMAL(18, 6) NOT NULL COMMENT '1 外币 = rate 本位币',
    source         VARCHAR(16)    NOT NULL COMMENT 'MANUAL/IMPORT',
    remark         VARCHAR(128)   NULL,
    version        INT            NOT NULL DEFAULT 0,
    created_by     BIGINT         NULL,
    created_at     DATETIME       NOT NULL,
    updated_by     BIGINT         NULL,
    updated_at     DATETIME       NOT NULL,
    deleted        TINYINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_exchange_rate UNIQUE (currency, rate_type, effective_date)
) COMMENT '汇率';

-- ==================== 付款条件 ====================
CREATE TABLE sys_payment_term (
    id                BIGINT       NOT NULL PRIMARY KEY,
    code              VARCHAR(32)  NOT NULL,
    name              VARCHAR(64)  NOT NULL,
    name_en           VARCHAR(128) NULL,
    settlement_method VARCHAR(32)  NOT NULL COMMENT '字典 sys_settlement_method',
    term_usage        VARCHAR(16)  NOT NULL COMMENT 'SALES/PURCHASE/BOTH',
    status            VARCHAR(16)  NOT NULL,
    remark            VARCHAR(256) NULL,
    version           INT          NOT NULL DEFAULT 0,
    created_by        BIGINT       NULL,
    created_at        DATETIME     NOT NULL,
    updated_by        BIGINT       NULL,
    updated_at        DATETIME     NOT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_payment_term_code UNIQUE (code)
) COMMENT '付款条件';

CREATE TABLE sys_payment_term_node (
    id         BIGINT        NOT NULL PRIMARY KEY,
    term_id    BIGINT        NOT NULL,
    seq        INT           NOT NULL,
    name       VARCHAR(32)   NOT NULL,
    percent    DECIMAL(9, 6) NOT NULL COMMENT '比例（小数）',
    base_event VARCHAR(32)   NOT NULL,
    days       INT           NOT NULL
) COMMENT '付款节点';
CREATE INDEX idx_sys_payment_term_node_term ON sys_payment_term_node (term_id);

INSERT INTO sys_payment_term (id, code, name, name_en, settlement_method, term_usage, status, created_at, updated_at) VALUES
(401, 'PREPAID', '100% 预付', '100% T/T in advance', 'TT', 'BOTH', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(402, 'TT30-70BS', '30% 定金，70% 出货前付清', '30% T/T deposit, 70% before shipment', 'TT', 'BOTH', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(403, 'TT30-70BL', '30% 定金，70% 见提单副本', '30% T/T in advance, 70% against B/L copy', 'TT', 'BOTH', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(404, 'NET30', '出货后 30 天', 'Net 30 days after shipment', 'TT', 'BOTH', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(405, 'MONTHLY60', '月结 60 天', '60 days after end of month', 'TT', 'BOTH', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(406, 'PUR_MONTHLY30', '采购月结 30 天', '30 days after end of month', 'TT', 'PURCHASE', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sys_payment_term_node (id, term_id, seq, name, percent, base_event, days) VALUES
(451, 401, 1, '预付', 1, 'ORDER_DATE', 0),
(452, 402, 1, '定金', 0.3, 'ORDER_DATE', 0),
(453, 402, 2, '尾款', 0.7, 'BEFORE_SHIPMENT', 0),
(454, 403, 1, '定金', 0.3, 'ORDER_DATE', 0),
(455, 403, 2, '尾款', 0.7, 'BL_DATE', 0),
(456, 404, 1, '货款', 1, 'SHIPMENT', 30),
(457, 405, 1, '货款', 1, 'MONTH_END', 60),
(458, 406, 1, '货款', 1, 'MONTH_END', 30);

-- ==================== 系统参数 ====================
CREATE TABLE sys_param (
    id            BIGINT        NOT NULL PRIMARY KEY,
    param_key     VARCHAR(128)  NOT NULL,
    module_code   VARCHAR(32)   NOT NULL,
    group_name    VARCHAR(32)   NOT NULL,
    name          VARCHAR(64)   NOT NULL,
    value_type    VARCHAR(16)   NOT NULL,
    options       VARCHAR(2000) NULL COMMENT 'ENUM 选项 JSON',
    min_value     VARCHAR(32)   NULL,
    max_value     VARCHAR(32)   NULL,
    param_value   VARCHAR(1024) NULL,
    default_value VARCHAR(1024) NULL,
    description   VARCHAR(512)  NOT NULL,
    sort          INT           NOT NULL DEFAULT 0,
    active        TINYINT       NOT NULL DEFAULT 1,
    version       INT           NOT NULL DEFAULT 0,
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME      NOT NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_param_key UNIQUE (param_key)
) COMMENT '系统参数（启动时由模块声明同步）';

-- ==================== 日志 ====================
CREATE TABLE sys_login_log (
    id         BIGINT       NOT NULL PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NULL,
    real_name  VARCHAR(64)  NULL,
    log_type   VARCHAR(16)  NOT NULL COMMENT 'LOGIN/LOGOUT/REFRESH',
    result     VARCHAR(32)  NOT NULL COMMENT 'SUCCESS/BAD_CREDENTIALS/LOCKED/DISABLED/CAPTCHA_ERROR/EXPIRED',
    ip         VARCHAR(64)  NULL,
    user_agent VARCHAR(512) NULL,
    browser    VARCHAR(64)  NULL,
    os         VARCHAR(64)  NULL,
    created_at DATETIME     NOT NULL
) COMMENT '登录日志';
CREATE INDEX idx_sys_login_log_created ON sys_login_log (created_at);
CREATE INDEX idx_sys_login_log_user ON sys_login_log (user_id, created_at);

CREATE TABLE sys_oper_log (
    id          BIGINT        NOT NULL PRIMARY KEY,
    trace_id    VARCHAR(64)   NULL,
    module_code VARCHAR(32)   NULL,
    action      VARCHAR(64)   NULL,
    method      VARCHAR(8)    NOT NULL,
    path        VARCHAR(256)  NOT NULL,
    params      TEXT          NULL,
    result      VARCHAR(16)   NOT NULL COMMENT 'SUCCESS/BIZ_ERROR/SYSTEM_ERROR',
    error_code  INT           NULL,
    error_msg   VARCHAR(512)  NULL,
    duration_ms INT           NOT NULL,
    user_id     BIGINT        NULL,
    username    VARCHAR(64)   NULL,
    real_name   VARCHAR(64)   NULL,
    ip          VARCHAR(64)   NULL,
    created_at  DATETIME      NOT NULL
) COMMENT '操作日志';
CREATE INDEX idx_sys_oper_log_created ON sys_oper_log (created_at);
CREATE INDEX idx_sys_oper_log_user ON sys_oper_log (user_id, created_at);
CREATE INDEX idx_sys_oper_log_trace ON sys_oper_log (trace_id);

CREATE TABLE sys_doc_log (
    id            BIGINT       NOT NULL PRIMARY KEY,
    biz_type      VARCHAR(64)  NOT NULL,
    biz_id        BIGINT       NOT NULL,
    biz_no        VARCHAR(64)  NULL,
    action        VARCHAR(32)  NOT NULL,
    action_name   VARCHAR(32)  NOT NULL,
    from_status   VARCHAR(32)  NULL,
    to_status     VARCHAR(32)  NULL,
    reason        VARCHAR(500) NULL,
    operator_id   BIGINT       NULL,
    operator_name VARCHAR(64)  NULL,
    created_at    DATETIME     NOT NULL
) COMMENT '单据操作日志';
CREATE INDEX idx_sys_doc_log_biz ON sys_doc_log (biz_type, biz_id);
