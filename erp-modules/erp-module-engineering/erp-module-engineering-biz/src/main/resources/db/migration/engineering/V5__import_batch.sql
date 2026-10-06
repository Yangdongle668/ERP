-- 导入批次与回滚（需求 05-02 3.5、05-01 第 9 节）：记录每次导入新增 / 更新的数据，回滚时删除新增的、恢复更新前的内容
CREATE TABLE eng_import_batch (
    id             BIGINT       NOT NULL PRIMARY KEY,
    biz_type       VARCHAR(16)  NOT NULL COMMENT 'MATERIAL 物料 / CATEGORY 物料类别',
    file_name      VARCHAR(256) NULL,
    import_mode    VARCHAR(16)  NULL COMMENT '编码已存在：SKIP / UPDATE',
    total_count    INT          NOT NULL DEFAULT 0,
    created_count  INT          NOT NULL DEFAULT 0,
    updated_count  INT          NOT NULL DEFAULT 0,
    batch_status   VARCHAR(16)  NOT NULL COMMENT 'DONE 已导入 / ROLLED_BACK 已回滚',
    rolled_back_at DATETIME     NULL,
    rolled_back_by BIGINT       NULL,
    version        INT          NOT NULL DEFAULT 0,
    created_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL,
    updated_by     BIGINT       NULL,
    updated_at     DATETIME     NOT NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0
) COMMENT '导入批次';
CREATE INDEX idx_eng_import_batch_type ON eng_import_batch (biz_type, created_at);

CREATE TABLE eng_import_item (
    id          BIGINT      NOT NULL PRIMARY KEY,
    batch_id    BIGINT      NOT NULL,
    target_id   BIGINT      NOT NULL COMMENT '物料 / 类别 ID',
    target_code VARCHAR(64) NOT NULL,
    action      VARCHAR(16) NOT NULL COMMENT 'CREATE 新增 / UPDATE 更新',
    snapshot    TEXT        NULL COMMENT '更新前的数据（JSON），回滚时恢复',
    seq         INT         NOT NULL DEFAULT 0,
    version     INT         NOT NULL DEFAULT 0,
    created_by  BIGINT      NULL,
    created_at  DATETIME    NOT NULL,
    updated_by  BIGINT      NULL,
    updated_at  DATETIME    NOT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0
) COMMENT '导入明细';
CREATE INDEX idx_eng_import_item_batch ON eng_import_item (batch_id);
CREATE INDEX idx_eng_import_item_target ON eng_import_item (target_id);
