package com.erp.module.crm.api.part;

/** 客户料号引用检查扩展点（需求 03-02 R04），由销售模块实现：被订单引用的对照不能删除，只能停用。 */
public interface CustomerPartReferenceChecker {

    boolean isUsed(Long customerPartId);
}
