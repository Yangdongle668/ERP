package com.erp.module.asset.api;

import com.erp.common.exception.ErrorCode;

/** 固定资产模块错误码，号段 1_015_000_000 ~ 1_015_999_999。 */
public interface AssetErrorCodes {

    ErrorCode ASSET_NOT_EXISTS = new ErrorCode(1_015_001_000, "固定资产不存在");
    ErrorCode ASSET_CODE_DUPLICATE = new ErrorCode(1_015_001_001, "资产编码「{}」已存在");
    ErrorCode ASSET_STATUS = new ErrorCode(1_015_001_002, "资产「{}」当前状态为{}，不能{}");
    ErrorCode ASSET_FIELD_INVALID = new ErrorCode(1_015_001_003, "{}");
    ErrorCode ASSET_SEQ_OVERFLOW = new ErrorCode(1_015_001_004, "编码「{}」的流水号已用完（001～999）");
    ErrorCode ASSET_CODE_LOCKED = new ErrorCode(1_015_001_005, "资产编码已生成，所属公司、分类、名称缩写、购置日期不能修改");
    ErrorCode ASSET_SCRAPPED = new ErrorCode(1_015_001_006, "已报废的资产不能修改");
}
