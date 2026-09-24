package com.erp.module.system.api.org;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 组织查询（带缓存）。 */
public interface OrgApi {

    Optional<OrgDTO> get(Long id);

    Map<Long, OrgDTO> list(Collection<Long> ids);

    /** 本节点及全部下级的 ID */
    Set<Long> getSelfAndChildrenIds(Long id);

    /** 部门所属公司：沿上级查找第一个类型为公司的节点（部门本身是公司时返回自己） */
    Optional<OrgDTO> getCompanyOf(Long deptId);
}
