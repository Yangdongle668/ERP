package com.erp.module.engineering.api;

import com.erp.common.exception.ErrorCode;

/** 研发工程模块错误码，号段 1_005_000_000 ~ 1_005_999_999。 */
public interface EngineeringErrorCodes {

    // ========== 物料 1_005_001_xxx ==========
    ErrorCode MATERIAL_NOT_EXISTS = new ErrorCode(1_005_001_000, "物料不存在");
    ErrorCode MATERIAL_CODE_DUPLICATE = new ErrorCode(1_005_001_001, "物料编码【{}】已存在");
    ErrorCode MATERIAL_NOT_ENABLED = new ErrorCode(1_005_001_002, "物料【{}】未启用，不能使用");
    ErrorCode MATERIAL_NOT_EDITABLE = new ErrorCode(1_005_001_003, "物料【{}】已启用，只能修改名称、规格等描述信息");
    ErrorCode MATERIAL_NOT_DELETABLE = new ErrorCode(1_005_001_004, "只有草稿状态的物料可以删除，已启用的物料请停用");
}
