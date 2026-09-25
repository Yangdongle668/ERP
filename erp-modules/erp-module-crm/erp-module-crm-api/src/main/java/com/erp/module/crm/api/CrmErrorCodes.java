package com.erp.module.crm.api;

import com.erp.common.exception.ErrorCode;

/** CRM模块错误码，号段 1_003_000_000 ~ 1_003_999_999。 */
public interface CrmErrorCodes {

    /** 占位示例，保留 */
    ErrorCode CRM_PLACEHOLDER = new ErrorCode(1_003_000_000, "CRM模块错误");

    // ========== 客户 1_003_001_xxx ==========
    ErrorCode CUSTOMER_NOT_EXISTS = new ErrorCode(1_003_001_000, "客户不存在");
    ErrorCode CUSTOMER_DUPLICATE = new ErrorCode(1_003_001_001, "发现疑似重复客户：{}");
    ErrorCode CUSTOMER_NAME_EN_REQUIRED = new ErrorCode(1_003_001_002, "外销客户必须填写英文名称");
    ErrorCode CUSTOMER_ACTIVATE_MISSING = new ErrorCode(1_003_001_003, "转正式客户需要：{}");
    ErrorCode CUSTOMER_NAME_EXISTS = new ErrorCode(1_003_001_004, "该国家已存在名称为「{}」的客户");
    ErrorCode CONTACT_WAY_REQUIRED = new ErrorCode(1_003_001_005, "联系人「{}」至少需要填写邮箱、电话、手机中的一项");
    ErrorCode BLACKLIST_REASON_REQUIRED = new ErrorCode(1_003_001_006, "请填写加入黑名单的原因");
    ErrorCode CUSTOMER_HAS_DATA = new ErrorCode(1_003_001_007, "客户已有业务数据，不能删除");
    ErrorCode CUSTOMER_STATUS = new ErrorCode(1_003_001_008, "客户当前状态【{}】不允许{}");
    ErrorCode CUSTOMER_NOT_ACTIVE = new ErrorCode(1_003_001_009, "客户「{}」不是正式客户，不能下单");
    ErrorCode CUSTOMER_BLACKLISTED = new ErrorCode(1_003_001_010, "客户「{}」在黑名单中，不能{}");
    ErrorCode CUSTOMER_DISABLED = new ErrorCode(1_003_001_011, "客户「{}」已停用，不能{}");
    ErrorCode CUSTOMER_CODE_EXISTS = new ErrorCode(1_003_001_012, "客户编码「{}」已存在");
    ErrorCode CUSTOMER_OWNER_FORBIDDEN = new ErrorCode(1_003_001_013, "没有客户转移权限，负责人只能是自己");
    ErrorCode TRANSFER_REASON_REQUIRED = new ErrorCode(1_003_001_014, "请填写转移原因");
    ErrorCode CUSTOMER_FIELD_INVALID = new ErrorCode(1_003_001_015, "{}");

    // ========== 客户料号 1_003_002_xxx ==========
    ErrorCode PART_NOT_EXISTS = new ErrorCode(1_003_002_000, "客户料号对照不存在");
    ErrorCode PART_DUPLICATE = new ErrorCode(1_003_002_001, "客户料号「{}」已存在，对应物料 {}");
    ErrorCode PART_USED = new ErrorCode(1_003_002_002, "该客户料号已被订单使用，只能停用");
    ErrorCode PART_MATERIAL_INVALID = new ErrorCode(1_003_002_003, "本厂物料必须是启用的半成品或成品");

    // ========== 信用 1_003_003_xxx ==========
    ErrorCode CREDIT_CHANGE_NOT_EXISTS = new ErrorCode(1_003_003_000, "信用额度调整单不存在");
    ErrorCode CREDIT_CHANGE_STATUS = new ErrorCode(1_003_003_001, "调整单当前状态【{}】不允许该操作");
    ErrorCode CREDIT_EXPIRE_DATE = new ErrorCode(1_003_003_002, "临时额度到期日必须晚于今天");
    ErrorCode CREDIT_CHANGE_PENDING = new ErrorCode(1_003_003_003, "客户「{}」已有未完成的额度调整单 {}");

    // ========== 跟进 1_003_004_xxx ==========
    ErrorCode FOLLOWUP_NOT_EXISTS = new ErrorCode(1_003_004_000, "跟进记录不存在");
    ErrorCode FOLLOWUP_FUTURE = new ErrorCode(1_003_004_001, "跟进时间不能晚于当前时间");
    ErrorCode FOLLOWUP_NEXT_DATE = new ErrorCode(1_003_004_002, "下次跟进日期不能早于今天");
    ErrorCode FOLLOWUP_LOCKED = new ErrorCode(1_003_004_003, "跟进记录超过 24 小时，不能修改");
    ErrorCode FOLLOWUP_NOT_MINE = new ErrorCode(1_003_004_004, "只有记录人本人可以修改、删除跟进记录");

    // ========== 商机 1_003_005_xxx ==========
    ErrorCode OPP_NOT_EXISTS = new ErrorCode(1_003_005_000, "商机不存在");
    ErrorCode OPP_LOST_REASON = new ErrorCode(1_003_005_001, "请选择输单原因");
    ErrorCode OPP_CLOSED = new ErrorCode(1_003_005_002, "商机已结束，不能修改");
    ErrorCode OPP_STATUS = new ErrorCode(1_003_005_003, "商机当前状态【{}】不允许{}");
}
