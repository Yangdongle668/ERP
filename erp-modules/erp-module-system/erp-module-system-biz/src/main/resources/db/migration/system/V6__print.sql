-- 系统管理第 2 步：打印模板（需求 01-系统管理/09）
CREATE TABLE sys_print_biz (
    id          BIGINT       NOT NULL PRIMARY KEY,
    biz_type    VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    module_code VARCHAR(32)  NOT NULL,
    data_api    VARCHAR(128) NOT NULL COMMENT '打印数据接口，如 /sales/orders/{id}/print-data',
    variables   TEXT         NULL COMMENT '变量说明 JSON：[{path,name,type}]',
    sample_data MEDIUMTEXT   NULL COMMENT '示例数据 JSON',
    active      TINYINT      NOT NULL DEFAULT 1,
    version     INT          NOT NULL DEFAULT 0,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_print_biz UNIQUE (biz_type)
) COMMENT '可打印单据类型';

CREATE TABLE sys_print_template (
    id           BIGINT       NOT NULL PRIMARY KEY,
    biz_type     VARCHAR(64)  NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    language     VARCHAR(8)   NOT NULL DEFAULT 'zh-CN' COMMENT 'zh-CN/en',
    paper        VARCHAR(16)  NOT NULL DEFAULT 'A4_P' COMMENT '字典 sys_print_paper',
    paper_width  INT          NULL COMMENT '自定义纸张宽（mm）',
    paper_height INT          NULL COMMENT '自定义纸张高（mm）',
    margin       VARCHAR(32)  NOT NULL DEFAULT '10mm 10mm 10mm 10mm',
    content      MEDIUMTEXT   NOT NULL COMMENT 'HTML 模板（Handlebars 语法），≤ 200KB',
    is_default   TINYINT      NOT NULL DEFAULT 0,
    is_builtin   TINYINT      NOT NULL DEFAULT 0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    remark       VARCHAR(256) NULL,
    version      INT          NOT NULL DEFAULT 0,
    created_by   BIGINT       NULL,
    created_at   DATETIME     NOT NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0
) COMMENT '打印模板';
CREATE INDEX idx_sys_print_template_biz ON sys_print_template (biz_type, language);

CREATE TABLE sys_print_log (
    id          BIGINT      NOT NULL PRIMARY KEY,
    biz_type    VARCHAR(64) NOT NULL,
    biz_id      BIGINT      NOT NULL,
    template_id BIGINT      NULL,
    printed_by  BIGINT      NULL,
    printed_at  DATETIME    NOT NULL
) COMMENT '打印记录';
CREATE INDEX idx_sys_print_log_biz ON sys_print_log (biz_type, biz_id);
