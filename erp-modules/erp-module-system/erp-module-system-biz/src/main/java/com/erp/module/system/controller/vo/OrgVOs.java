package com.erp.module.system.controller.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 组织架构 VO */
public final class OrgVOs {

    private OrgVOs() {
    }

    /** 树形表格节点 */
    public record OrgNode(Long id, Long parentId, String code, String name, String shortName, String orgType,
                          Long leaderUserId, String leaderName, String phone, int userCount, int sort, String status,
                          int level, List<OrgNode> children) {
    }

    /** 精简树（OrgTreeSelect） */
    public record SimpleNode(Long id, Long parentId, String code, String name, String orgType, List<SimpleNode> children) {
    }

    public record OrgResp(Long id, Long parentId, String code, String name, String shortName, String orgType,
                          Long leaderUserId, String leaderName, String phone, String address, String nameEn, String addressEn,
                          String taxNo, Long logoFileId, int sort, String status, String remark, Integer version) {
    }

    public record OrgSave(
            Long parentId,
            @NotBlank(message = "请选择类型") String orgType,
            @NotBlank(message = "请输入编码") @Pattern(regexp = "[A-Za-z0-9_-]{2,32}", message = "编码格式不正确，2～32 位字母、数字、- _") String code,
            @NotBlank(message = "请输入名称") @Size(max = 64, message = "名称不能超过 64 个字") String name,
            @Size(max = 32) String shortName,
            Long leaderUserId,
            @Size(max = 32) String phone,
            @Size(max = 256) String address,
            @Size(max = 128) String nameEn,
            @Size(max = 256) String addressEn,
            @Size(max = 32) String taxNo,
            Long logoFileId,
            @NotNull(message = "请输入排序") @Min(0) @Max(9999) Integer sort,
            @Size(max = 256) String remark,
            Integer version) {
    }
}
