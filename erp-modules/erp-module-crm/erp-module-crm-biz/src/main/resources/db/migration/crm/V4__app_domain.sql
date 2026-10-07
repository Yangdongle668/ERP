-- 应用领域（《编码规则管理制度》LD-QA-MS-001 5.1）：领域字母即客户编码 LD-字母-流水号 中的字母，各领域独立计流水号
CREATE TABLE crm_app_domain (
    id          BIGINT       NOT NULL PRIMARY KEY,
    code        VARCHAR(1)   NOT NULL COMMENT '领域字母 A～Z',
    name        VARCHAR(64)  NOT NULL,
    name_en     VARCHAR(128) NULL,
    sort        INT          NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    remark      VARCHAR(256) NULL,
    version     INT          NOT NULL DEFAULT 0,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0
) COMMENT '应用领域';
CREATE UNIQUE INDEX uk_crm_app_domain_code ON crm_app_domain (code);

INSERT INTO crm_app_domain (id, code, name, name_en, sort, status, created_at, updated_at) VALUES
    (3001, 'A', '智能医疗', 'Smart medical', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3002, 'B', '智能穿戴（戒指、眼镜、手表、耳机等）', 'Smart wearables', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3003, 'C', '消费电子', 'Consumer electronics', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3004, 'D', '低空设备', 'Low-altitude equipment', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3005, 'E', '物联网（智能家居等）', 'IoT', 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3007, 'G', '电子产品', 'Electronics', 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3015, 'O', '枪械运动', 'Shooting sports', 150, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 编码为 LD-字母-流水号 的已有客户：应用领域以编码字母为准
UPDATE crm_customer SET app_domain = SUBSTRING(code, 4, 1)
 WHERE code LIKE 'LD-_-%' AND SUBSTRING(code, 4, 1) BETWEEN 'A' AND 'Z';

-- 客户已使用、但不在上面列表中的领域字母补成领域（名称暂用字母，可在「CRM / 应用领域」中改名）
INSERT INTO crm_app_domain (id, code, name, sort, status, created_at, updated_at)
SELECT 3000 + ASCII(d.app_domain) - 64, d.app_domain, d.app_domain, (ASCII(d.app_domain) - 64) * 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM (SELECT DISTINCT app_domain FROM crm_customer WHERE app_domain IS NOT NULL AND LENGTH(app_domain) = 1) d
 WHERE NOT EXISTS (SELECT 1 FROM crm_app_domain a WHERE a.code = d.app_domain);
