package com.erp.module.bi.service.query;

import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.category.MaterialCategoryDTO;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.purchase.api.supplier.SupplierApi;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.user.UserApi;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 维度取值 → 显示名称（客户简称、物料编码 名称、用户姓名等） */
@Component
public class BiLabelResolver {

    public static final String NONE = "（未指定）";

    static final Map<String, String> INSPECT_TYPES = Map.of("IQC", "来料检验", "IPQC", "制程检验", "FQC", "成品检验", "OQC", "出货检验",
            "RETURN", "退货检验", "NCR", "不合格品", "COMPLAINT", "客诉");

    private final CustomerApi customerApi;
    private final SupplierApi supplierApi;
    private final MaterialApi materialApi;
    private final MaterialCategoryApi categoryApi;
    private final WarehouseApi warehouseApi;
    private final UserApi userApi;
    private final OrgApi orgApi;

    public BiLabelResolver(CustomerApi customerApi, SupplierApi supplierApi, MaterialApi materialApi, MaterialCategoryApi categoryApi,
                           WarehouseApi warehouseApi, UserApi userApi, OrgApi orgApi) {
        this.customerApi = customerApi;
        this.supplierApi = supplierApi;
        this.materialApi = materialApi;
        this.categoryApi = categoryApi;
        this.warehouseApi = warehouseApi;
        this.userApi = userApi;
        this.orgApi = orgApi;
    }

    /** 维度的取值集合 → 名称；未知取值使用原值 */
    public Map<String, String> resolve(String dim, Collection<String> values) {
        Map<String, String> labels = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        for (String v : values) {
            if (v == null) continue;
            if (isIdDim(dim)) {
                try {
                    ids.add(Long.parseLong(v));
                } catch (NumberFormatException ignored) {
                    // 保留原值
                }
            }
        }
        switch (dim) {
            case "customer" -> customerApi.getCustomers(ids).forEach((id, c) -> labels.put(String.valueOf(id), customerName(c)));
            case "supplier" -> ids.forEach(id -> supplierApi.getSupplier(id).ifPresent(s -> labels.put(String.valueOf(id), s.name())));
            case "material" -> materialApi.getMaterials(ids).forEach(m -> labels.put(String.valueOf(m.id()), m.code() + " " + m.name()));
            case "category" -> {
                for (MaterialCategoryDTO c : categoryApi.listAll()) {
                    if (ids.contains(c.id())) labels.put(String.valueOf(c.id()), c.name());
                }
            }
            case "warehouse" -> ids.forEach(id -> warehouseApi.get(id).ifPresent(w -> labels.put(String.valueOf(id), w.name())));
            case "owner" -> userApi.list(ids).forEach((id, u) -> labels.put(String.valueOf(id), u.realName()));
            case "dept" -> orgApi.list(ids).forEach((id, o) -> labels.put(String.valueOf(id), o.name()));
            case "warehouse_type" -> values.stream().filter(Objects::nonNull).forEach(v -> {
                try {
                    labels.put(v, WarehouseType.valueOf(v).label());
                } catch (IllegalArgumentException ignored) {
                    labels.put(v, v);
                }
            });
            case "inspect_type" -> values.stream().filter(Objects::nonNull).forEach(v -> labels.put(v, INSPECT_TYPES.getOrDefault(v, v)));
            default -> {
                // 日期、国家：原值
            }
        }
        return labels;
    }

    public String label(Map<String, String> labels, String value) {
        if (value == null) return NONE;
        return labels.getOrDefault(value, value);
    }

    static boolean isIdDim(String dim) {
        return switch (dim) {
            case "customer", "supplier", "material", "category", "warehouse", "owner", "dept" -> true;
            default -> false;
        };
    }

    private static String customerName(CustomerDTO c) {
        return c.shortName() != null && !c.shortName().isBlank() ? c.shortName() : c.name();
    }
}
