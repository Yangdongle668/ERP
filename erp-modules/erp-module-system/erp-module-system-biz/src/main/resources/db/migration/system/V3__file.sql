-- 系统管理第 2 步：附件（需求 01-系统管理/12 第 1 节）
CREATE TABLE sys_file (
    id           BIGINT       NOT NULL PRIMARY KEY,
    biz_type     VARCHAR(64)  NULL COMMENT '所属业务类型；上传后未绑定前为空',
    biz_id       BIGINT       NULL COMMENT '所属业务 ID',
    category     VARCHAR(32)  NULL COMMENT '附件分类（业务自定义）',
    file_name    VARCHAR(255) NOT NULL COMMENT '原始文件名',
    ext          VARCHAR(16)  NOT NULL COMMENT '扩展名（小写）',
    content_type VARCHAR(128) NOT NULL,
    file_size    BIGINT       NOT NULL COMMENT '字节',
    sha256       VARCHAR(64)  NOT NULL COMMENT '内容哈希',
    storage      VARCHAR(16)  NOT NULL COMMENT 'LOCAL/S3',
    path         VARCHAR(512) NOT NULL COMMENT '存储路径 {yyyy}/{MM}/{uuid}.{ext}',
    bound        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已绑定业务',
    version      INT          NOT NULL DEFAULT 0,
    created_by   BIGINT       NULL,
    created_at   DATETIME     NOT NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0
) COMMENT '附件';
CREATE INDEX idx_sys_file_biz ON sys_file (biz_type, biz_id);
CREATE INDEX idx_sys_file_unbound ON sys_file (bound, created_at);
