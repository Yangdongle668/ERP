package com.erp.module.engineering.api.bom;

/**
 * BOM 版本是否被业务单据使用（ENG-BOM-R09）：生产模块实现（生产订单引用了该 BOM 版本时返回 true），
 * 被使用的版本不能反审核。
 */
@FunctionalInterface
public interface BomReferenceChecker {

    boolean isUsed(Long bomId);
}
