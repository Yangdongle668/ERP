package com.erp.module.system.api.org;

/** 组织（公司 / 部门） */
public record OrgDTO(Long id, Long parentId, String code, String name, String shortName, String orgType,
                     Long leaderUserId, String nameEn, String address, String addressEn, String taxNo, String phone,
                     boolean enabled) {

    public boolean isCompany() {
        return "COMPANY".equals(orgType);
    }
}
