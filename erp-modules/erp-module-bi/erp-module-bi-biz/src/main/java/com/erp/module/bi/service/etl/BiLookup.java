package com.erp.module.bi.service.etl;

import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 一次汇总运行内的主数据缓存：物料类别、客户国家 / 业务员、供应商采购员、用户部门、部门所属公司 */
final class BiLookup {

    private final MaterialApi materialApi;
    private final CustomerApi customerApi;
    private final SupplierApi supplierApi;
    private final UserApi userApi;
    private final OrgApi orgApi;

    private final Map<Long, MaterialDTO> materials = new HashMap<>();
    private final Map<Long, CustomerDTO> customers = new HashMap<>();
    private final Map<Long, Optional<SupplierDTO>> suppliers = new HashMap<>();
    private final Map<Long, UserDTO> users = new HashMap<>();
    private final Set<Long> loadedUsers = new HashSet<>();
    private final Map<Long, Optional<Long>> companies = new HashMap<>();

    BiLookup(MaterialApi materialApi, CustomerApi customerApi, SupplierApi supplierApi, UserApi userApi, OrgApi orgApi) {
        this.materialApi = materialApi;
        this.customerApi = customerApi;
        this.supplierApi = supplierApi;
        this.userApi = userApi;
        this.orgApi = orgApi;
    }

    void preloadMaterials(Collection<Long> ids) {
        Set<Long> missing = new HashSet<>();
        for (Long id : ids) if (id != null && !materials.containsKey(id)) missing.add(id);
        if (!missing.isEmpty()) materialApi.getMaterials(missing).forEach(m -> materials.put(m.id(), m));
    }

    void preloadCustomers(Collection<Long> ids) {
        Set<Long> missing = new HashSet<>();
        for (Long id : ids) if (id != null && !customers.containsKey(id)) missing.add(id);
        if (!missing.isEmpty()) customers.putAll(customerApi.getCustomers(missing));
    }

    void preloadUsers(Collection<Long> ids) {
        Set<Long> missing = new HashSet<>();
        for (Long id : ids) if (id != null && loadedUsers.add(id)) missing.add(id);
        if (!missing.isEmpty()) users.putAll(userApi.list(missing));
    }

    Long categoryOf(Long materialId) {
        MaterialDTO m = materialId == null ? null : materials.get(materialId);
        return m == null ? null : m.categoryId();
    }

    CustomerDTO customer(Long id) {
        return id == null ? null : customers.get(id);
    }

    SupplierDTO supplier(Long id) {
        if (id == null) return null;
        return suppliers.computeIfAbsent(id, supplierApi::getSupplier).orElse(null);
    }

    Long deptOfUser(Long userId) {
        if (userId == null) return null;
        preloadUsers(Set.of(userId));
        UserDTO u = users.get(userId);
        return u == null ? null : u.deptId();
    }

    Long companyOf(Long deptId) {
        if (deptId == null) return null;
        return companies.computeIfAbsent(deptId, d -> orgApi.getCompanyOf(d).map(OrgDTO::id)).orElse(null);
    }

    /** 优先使用事实自带的负责人 / 部门，缺失时从客户档案补齐 */
    Long[] customerOwnerDept(Long customerId, Long ownerId, Long deptId) {
        CustomerDTO c = customer(customerId);
        Long owner = ownerId != null ? ownerId : c == null ? null : c.ownerId();
        Long dept = deptId;
        if (dept == null && owner != null) dept = deptOfUser(owner);
        if (dept == null && c != null) dept = c.deptId();
        return new Long[]{owner, dept};
    }

    static boolean same(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
