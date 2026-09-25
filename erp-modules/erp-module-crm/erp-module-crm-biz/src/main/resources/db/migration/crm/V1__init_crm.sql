-- CRM 模块（需求 03-CRM）：客户（联系人、地址、银行、转移记录）、客户料号对照、信用、跟进记录、商机

CREATE TABLE crm_customer (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    name_en VARCHAR(256) NULL,
    short_name VARCHAR(32) NOT NULL,
    name_key VARCHAR(256) NOT NULL COMMENT '名称比较键：小写、去空格和标点（R04）',
    name_core VARCHAR(256) NOT NULL COMMENT '查重键：再去掉公司后缀（R01）',
    customer_type VARCHAR(32) NOT NULL COMMENT '字典 crm_customer_type',
    customer_level VARCHAR(8) NOT NULL DEFAULT 'C' COMMENT '字典 crm_customer_level',
    customer_status VARCHAR(16) NOT NULL COMMENT 'PROSPECT/PENDING/ACTIVE/DISABLED/BLACKLIST',
    status_before_blacklist VARCHAR(16) NULL COMMENT '加入黑名单前的状态，移出时恢复',
    is_foreign TINYINT NOT NULL DEFAULT 0,
    country VARCHAR(2) NOT NULL DEFAULT 'CN',
    province VARCHAR(64) NULL,
    city VARCHAR(64) NULL,
    address VARCHAR(256) NULL,
    industry VARCHAR(32) NULL,
    source VARCHAR(32) NULL,
    website VARCHAR(128) NULL,
    website_domain VARCHAR(128) NULL,
    phone VARCHAR(64) NULL,
    email VARCHAR(128) NULL,
    tax_no VARCHAR(32) NULL,
    owner_id BIGINT NOT NULL COMMENT '负责业务员',
    org_id BIGINT NULL,
    dept_id BIGINT NULL COMMENT '负责部门（数据权限）',
    currency VARCHAR(3) NOT NULL,
    payment_term_id BIGINT NULL,
    trade_term VARCHAR(16) NULL,
    sales_tax_rate DECIMAL(9,4) NOT NULL DEFAULT 0.13,
    credit_limit DECIMAL(20,4) NULL COMMENT '信用额度（本位币），只能通过信用调整修改',
    credit_days INT NULL,
    credit_control VARCHAR(16) NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT/NONE/WARN/BLOCK',
    blacklist_reason VARCHAR(512) NULL,
    first_order_date DATE NULL,
    last_order_date DATE NULL,
    remark VARCHAR(1000) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_crm_customer_code UNIQUE (code)
) COMMENT '客户';
CREATE INDEX idx_crm_customer_owner ON crm_customer (owner_id);
CREATE INDEX idx_crm_customer_short ON crm_customer (short_name);
CREATE INDEX idx_crm_customer_key ON crm_customer (name_key);
CREATE INDEX idx_crm_customer_core ON crm_customer (name_core);
CREATE INDEX idx_crm_customer_tax ON crm_customer (tax_no);
CREATE INDEX idx_crm_customer_dept ON crm_customer (dept_id);

CREATE TABLE crm_contact (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    gender VARCHAR(8) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'MALE/FEMALE/UNKNOWN',
    title VARCHAR(64) NULL,
    contact_role VARCHAR(32) NULL COMMENT '字典 crm_contact_role',
    email VARCHAR(128) NULL,
    phone VARCHAR(32) NULL,
    mobile VARCHAR(32) NULL,
    im VARCHAR(64) NULL,
    birthday DATE NULL,
    is_primary TINYINT NOT NULL DEFAULT 0,
    contact_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/LEFT',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '客户联系人';
CREATE INDEX idx_crm_contact_customer ON crm_contact (customer_id);

CREATE TABLE crm_address (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    address_type VARCHAR(16) NOT NULL COMMENT 'SHIP_TO/BILL_TO/NOTIFY',
    company_name VARCHAR(256) NOT NULL,
    contact_name VARCHAR(64) NULL,
    phone VARCHAR(32) NULL,
    country VARCHAR(2) NOT NULL,
    province VARCHAR(64) NULL,
    city VARCHAR(64) NULL,
    zip VARCHAR(16) NULL,
    address_line VARCHAR(512) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '客户地址';
CREATE INDEX idx_crm_address_customer ON crm_address (customer_id);

CREATE TABLE crm_customer_bank (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    bank_name VARCHAR(128) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    account_no VARCHAR(64) NOT NULL,
    swift VARCHAR(16) NULL,
    currency VARCHAR(3) NULL,
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '客户银行信息';
CREATE INDEX idx_crm_bank_customer ON crm_customer_bank (customer_id);

CREATE TABLE crm_customer_transfer_log (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    from_owner_id BIGINT NULL,
    to_owner_id BIGINT NOT NULL,
    transfer_docs TINYINT NOT NULL DEFAULT 0,
    reason VARCHAR(512) NOT NULL,
    operator_id BIGINT NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '客户转移记录';
CREATE INDEX idx_crm_transfer_customer ON crm_customer_transfer_log (customer_id);

CREATE TABLE crm_customer_part (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    customer_part_no VARCHAR(64) NOT NULL,
    customer_part_name VARCHAR(256) NULL,
    customer_part_spec VARCHAR(512) NULL,
    material_id BIGINT NOT NULL,
    customer_revision VARCHAR(16) NULL,
    part_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    remark VARCHAR(256) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '客户料号对照';
CREATE INDEX idx_crm_part_customer ON crm_customer_part (customer_id, customer_part_no);
CREATE INDEX idx_crm_part_material ON crm_customer_part (material_id);

CREATE TABLE crm_customer_credit (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    receivable_balance DECIMAL(20,4) NOT NULL DEFAULT 0,
    overdue_amount DECIMAL(20,4) NOT NULL DEFAULT 0,
    open_order_amount DECIMAL(20,4) NOT NULL DEFAULT 0,
    refreshed_at DATETIME NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_crm_credit_customer UNIQUE (customer_id)
) COMMENT '客户信用占用（由各模块回写/定时重算）';

CREATE TABLE crm_credit_change (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    doc_no VARCHAR(64) NOT NULL,
    doc_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    org_id BIGINT NULL,
    dept_id BIGINT NULL,
    owner_id BIGINT NULL,
    source_type VARCHAR(32) NULL,
    source_id BIGINT NULL,
    source_no VARCHAR(64) NULL,
    remark VARCHAR(1000) NULL,
    customer_id BIGINT NOT NULL,
    old_limit DECIMAL(20,4) NULL,
    new_limit DECIMAL(20,4) NOT NULL,
    old_days INT NULL,
    new_days INT NULL,
    old_control VARCHAR(16) NULL,
    new_control VARCHAR(16) NULL,
    expire_date DATE NULL COMMENT '临时额度到期日',
    reason VARCHAR(512) NOT NULL,
    approved_at DATETIME NULL,
    restored TINYINT NOT NULL DEFAULT 0 COMMENT '临时额度已恢复',
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '信用额度调整单';
CREATE INDEX idx_crm_credit_change_customer ON crm_credit_change (customer_id);

CREATE TABLE crm_followup (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    contact_id BIGINT NULL,
    opportunity_id BIGINT NULL,
    followup_type VARCHAR(32) NOT NULL COMMENT '字典 crm_followup_type',
    followup_at DATETIME NOT NULL,
    subject VARCHAR(128) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    next_followup_at DATE NULL,
    next_plan VARCHAR(512) NULL,
    owner_id BIGINT NOT NULL,
    reminded TINYINT NOT NULL DEFAULT 0,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0
) COMMENT '跟进记录';
CREATE INDEX idx_crm_followup_customer ON crm_followup (customer_id, followup_at);
CREATE INDEX idx_crm_followup_next ON crm_followup (next_followup_at);
CREATE INDEX idx_crm_followup_opp ON crm_followup (opportunity_id);

CREATE TABLE crm_opportunity (
    id                 BIGINT        NOT NULL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    customer_id BIGINT NOT NULL,
    contact_id BIGINT NULL,
    stage VARCHAR(16) NOT NULL COMMENT 'CONTACT/REQUIREMENT/QUOTATION/NEGOTIATION/WON/LOST',
    opp_status VARCHAR(16) NOT NULL COMMENT 'OPEN/WON/LOST/SHELVED',
    amount DECIMAL(20,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    win_rate DECIMAL(9,4) NOT NULL,
    expected_date DATE NOT NULL,
    products VARCHAR(512) NULL,
    competitor VARCHAR(256) NULL,
    owner_id BIGINT NOT NULL,
    org_id BIGINT NULL,
    dept_id BIGINT NULL,
    lost_reason VARCHAR(32) NULL,
    lost_remark VARCHAR(512) NULL,
    won_order_no VARCHAR(64) NULL,
    closed_at DATETIME NULL,
    remark VARCHAR(1000) NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL,
    updated_by         BIGINT        NULL,
    updated_at         DATETIME      NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_crm_opportunity_code UNIQUE (code)
) COMMENT '商机';
CREATE INDEX idx_crm_opp_customer ON crm_opportunity (customer_id);
CREATE INDEX idx_crm_opp_owner ON crm_opportunity (owner_id);
