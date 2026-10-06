-- 客户导入批次与回滚（需求 03-01 3.x 导入）：回滚时删除本批新增、且之后没有业务数据的客户
CREATE TABLE crm_import_batch (
    id             BIGINT       NOT NULL PRIMARY KEY,
    file_name      VARCHAR(256) NULL,
    total_count    INT          NOT NULL DEFAULT 0,
    created_count  INT          NOT NULL DEFAULT 0,
    batch_status   VARCHAR(16)  NOT NULL COMMENT 'DONE 已导入 / ROLLED_BACK 已回滚',
    rolled_back_at DATETIME     NULL,
    rolled_back_by BIGINT       NULL,
    version        INT          NOT NULL DEFAULT 0,
    created_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL,
    updated_by     BIGINT       NULL,
    updated_at     DATETIME     NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0
) COMMENT '客户导入批次';

CREATE TABLE crm_import_item (
    id          BIGINT      NOT NULL PRIMARY KEY,
    batch_id    BIGINT      NOT NULL,
    customer_id BIGINT      NOT NULL,
    code        VARCHAR(32) NOT NULL,
    seq         INT         NOT NULL DEFAULT 0,
    version     INT         NOT NULL DEFAULT 0,
    created_by  BIGINT      NULL,
    created_at  DATETIME    NOT NULL,
    updated_by  BIGINT      NULL,
    updated_at  DATETIME    NOT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0
) COMMENT '客户导入明细';
CREATE INDEX idx_crm_import_item_batch ON crm_import_item (batch_id);
