-- 数据任务的跨实例互斥锁（多实例部署时同一时刻只有一个节点运行数据任务）
CREATE TABLE bi_etl_lock (
    id           BIGINT       NOT NULL PRIMARY KEY,
    lock_key     VARCHAR(32)  NOT NULL COMMENT '锁名称',
    locked_until DATETIME     NULL COMMENT '锁到期时间（节点崩溃时到期后自动释放）',
    locked_by    VARCHAR(64)  NULL COMMENT '持有锁的节点',
    version      INT          NOT NULL DEFAULT 0,
    created_by   BIGINT       NULL,
    created_at   DATETIME     NOT NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     NOT NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_bi_etl_lock UNIQUE (lock_key)
) COMMENT '数据任务互斥锁';

INSERT INTO bi_etl_lock (id, lock_key, version, created_at, updated_at, deleted) VALUES (1, 'ETL', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
