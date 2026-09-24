package com.erp.module.system.api.org;

/** 扩展点：删除组织前检查是否被业务数据引用（SYS-ORG-R07），由业务模块实现。 */
public interface OrgReferenceChecker {

    boolean isReferenced(Long orgId);
}
