-- 系统管理第 2 步：审批流（需求 01-系统管理/08）
-- 公共审计列：version（乐观锁）、created_by/at、updated_by/at、deleted

CREATE TABLE wf_biz_type (
    id           BIGINT       NOT NULL PRIMARY KEY,
    biz_type     VARCHAR(64)  NOT NULL COMMENT '单据类型编码',
    name         VARCHAR(64)  NOT NULL,
    module_code  VARCHAR(32)  NOT NULL,
    detail_route VARCHAR(128) NOT NULL COMMENT '前端详情页路由模板，如 /sales/order/{id}',
    fields       TEXT         NULL COMMENT '条件字段 JSON：[{code,name,type,options,dictType}]',
    user_fields  TEXT         NULL COMMENT '单据用户字段 JSON：[{code,name}]',
    active       TINYINT      NOT NULL DEFAULT 1 COMMENT '声明已从代码中移除时为 0',
    version      INT          NOT NULL DEFAULT 0,
    created_by   BIGINT       NULL,
    created_at   DATETIME     NOT NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_wf_biz_type UNIQUE (biz_type)
) COMMENT '可审批的单据类型';

CREATE TABLE wf_definition (
    id              BIGINT       NOT NULL PRIMARY KEY,
    biz_type        VARCHAR(64)  NOT NULL,
    def_version     INT          NOT NULL COMMENT '流程版本号，从 1 递增',
    status          VARCHAR(16)  NOT NULL COMMENT 'DRAFT/ACTIVE/ARCHIVED',
    enabled         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用审批（ACTIVE 版本有效）',
    skip_initiator  TINYINT      NOT NULL DEFAULT 1,
    skip_duplicate  TINYINT      NOT NULL DEFAULT 1,
    empty_policy    VARCHAR(16)  NOT NULL DEFAULT 'TO_ADMIN' COMMENT 'AUTO_PASS/TO_ADMIN',
    based_on        INT          NULL COMMENT '草稿基于的版本号',
    published_at    DATETIME     NULL,
    published_by    BIGINT       NULL,
    remark          VARCHAR(256) NULL,
    version         INT          NOT NULL DEFAULT 0,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0
) COMMENT '审批流程定义（每个版本一行）';
CREATE INDEX idx_wf_definition_biz ON wf_definition (biz_type, status);

CREATE TABLE wf_branch (
    id            BIGINT      NOT NULL PRIMARY KEY,
    definition_id BIGINT      NOT NULL,
    priority      INT         NOT NULL COMMENT '越小越先匹配；其他情况固定 9999',
    name          VARCHAR(64) NOT NULL,
    is_default    TINYINT     NOT NULL DEFAULT 0 COMMENT '其他情况',
    conditions    TEXT        NULL COMMENT '条件 JSON：[{field,op,value}]',
    version       INT         NOT NULL DEFAULT 0,
    created_by    BIGINT      NULL,
    created_at    DATETIME    NOT NULL,
    updated_by    BIGINT      NULL,
    updated_at    DATETIME    NOT NULL,
    deleted       TINYINT     NOT NULL DEFAULT 0
) COMMENT '审批分支';
CREATE INDEX idx_wf_branch_def ON wf_branch (definition_id);

CREATE TABLE wf_node (
    id             BIGINT      NOT NULL PRIMARY KEY,
    branch_id      BIGINT      NOT NULL,
    seq            INT         NOT NULL COMMENT '顺序，从 1 开始',
    name           VARCHAR(32) NOT NULL,
    approver_type  VARCHAR(32) NOT NULL COMMENT 'USER/ROLE/DEPT_LEADER/UPPER_DEPT_LEADER/SUPERIOR/BIZ_USER',
    approver_value TEXT        NULL COMMENT 'USER：用户 ID 数组；ROLE：{roleId,sameCompany}；BIZ_USER：字段编码',
    multi_mode     VARCHAR(8)  NOT NULL DEFAULT 'ANY' COMMENT 'ANY 或签 / ALL 会签',
    version        INT         NOT NULL DEFAULT 0,
    created_by     BIGINT      NULL,
    created_at     DATETIME    NOT NULL,
    updated_by     BIGINT      NULL,
    updated_at     DATETIME    NOT NULL,
    deleted        TINYINT     NOT NULL DEFAULT 0
) COMMENT '审批节点';
CREATE INDEX idx_wf_node_branch ON wf_node (branch_id);

CREATE TABLE wf_instance (
    id                BIGINT        NOT NULL PRIMARY KEY,
    biz_type          VARCHAR(64)   NOT NULL,
    biz_id            BIGINT        NOT NULL,
    biz_no            VARCHAR(64)   NOT NULL,
    title             VARCHAR(128)  NOT NULL COMMENT '待办标题',
    definition_id     BIGINT        NOT NULL COMMENT '发起时的流程版本',
    branch_id         BIGINT        NOT NULL COMMENT '命中的分支',
    initiator_id      BIGINT        NOT NULL,
    initiator_dept_id BIGINT        NULL COMMENT '发起人主部门（快照）',
    variables         TEXT          NULL COMMENT '条件字段值快照 JSON',
    biz_users         TEXT          NULL COMMENT '单据用户字段快照 JSON',
    status            VARCHAR(16)   NOT NULL COMMENT 'RUNNING/APPROVED/REJECTED/WITHDRAWN/TERMINATED',
    current_seq       INT           NULL,
    current_node_name VARCHAR(32)   NULL,
    node_started_at   DATETIME      NULL COMMENT '当前节点开始时间（停留时长）',
    started_at        DATETIME      NOT NULL,
    finished_at       DATETIME      NULL,
    result_comment    VARCHAR(500)  NULL COMMENT '驳回意见 / 终止原因',
    version           INT           NOT NULL DEFAULT 0,
    created_by        BIGINT        NULL,
    created_at        DATETIME      NOT NULL,
    updated_by        BIGINT        NULL,
    updated_at        DATETIME      NOT NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0
) COMMENT '审批实例';
CREATE INDEX idx_wf_instance_biz ON wf_instance (biz_type, biz_id);
CREATE INDEX idx_wf_instance_initiator ON wf_instance (initiator_id, status);

CREATE TABLE wf_task (
    id             BIGINT       NOT NULL PRIMARY KEY,
    instance_id    BIGINT       NOT NULL,
    node_seq       INT          NOT NULL,
    node_name      VARCHAR(32)  NOT NULL,
    multi_mode     VARCHAR(8)   NOT NULL,
    assignee_id    BIGINT       NULL COMMENT '处理人（节点无审批人且自动通过时为空）',
    status         VARCHAR(16)  NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/TRANSFERRED/CANCELED/AUTO_PASSED',
    comment_text   VARCHAR(500) NULL COMMENT '审批意见',
    transfer_to_id BIGINT       NULL,
    auto_reason    VARCHAR(64)  NULL COMMENT '自动通过原因 / 系统转交说明',
    handled_at     DATETIME     NULL,
    handled_by     BIGINT       NULL COMMENT '实际操作人（流程管理员转交时不是处理人本人）',
    version        INT          NOT NULL DEFAULT 0,
    created_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL,
    updated_by     BIGINT       NULL,
    updated_at     DATETIME     NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0
) COMMENT '审批任务';
CREATE INDEX idx_wf_task_assignee ON wf_task (assignee_id, status);
CREATE INDEX idx_wf_task_instance ON wf_task (instance_id);
