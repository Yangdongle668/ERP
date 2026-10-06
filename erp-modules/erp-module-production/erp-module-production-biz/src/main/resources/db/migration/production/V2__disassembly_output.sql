-- 拆解订单产出（需求 09-01 1.1 DISASSEMBLY）：下达时按 BOM 展开一层固化，子件以“拆解入库”退料单入库
CREATE TABLE mfg_prod_order_output (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    prod_order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    component_id BIGINT NOT NULL,
    qty_per DECIMAL(18,6) NOT NULL COMMENT '每拆解 1 个产品产出的子件数量',
    expected_qty DECIMAL(18,4) NOT NULL COMMENT '预计产出 = 订单数量 × 单位产出',
    received_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已入库',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '拆解订单产出';
CREATE INDEX idx_mfg_pout_order ON mfg_prod_order_output (prod_order_id);
