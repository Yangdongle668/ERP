package com.erp.module.system.api.workflow;

import java.util.ArrayList;
import java.util.List;

/**
 * 可审批的单据类型声明（需求 01-系统管理/08 第 2 节 wf_biz_type、README 第 5 节）。业务模块在配置类中声明为 Bean，
 * 启动时同步到 wf_biz_type；管理员据此配置分支条件和“单据指定字段”审批人。
 *
 * <pre>
 * &#64;Bean
 * public ApprovalBizDefinition salesOrderApproval() {
 *     return ApprovalBizDefinition.of("SAL_ORDER", "销售订单", "sales", "/sales/order/{id}")
 *             .numberField("amountBase", "订单金额(本位币)")
 *             .dictField("customerLevel", "客户等级", "crm_customer_level")
 *             .deptField("deptId", "业务部门")
 *             .userField("salespersonId", "业务员");
 * }
 * </pre>
 *
 * 提交审批时 {@code WorkflowApi.start} 的 variables 按这里的字段编码提供值，bizUsers 提供用户字段的用户 ID。
 */
public final class ApprovalBizDefinition {

    public enum FieldType { NUMBER, STRING, ENUM, DICT, DEPT, USER, BOOL }

    public record Option(String value, String label) {
    }

    /** 条件字段 */
    public record Field(String code, String name, FieldType type, List<Option> options, String dictType) {
    }

    /** 可作为“单据指定字段”审批人的用户字段 */
    public record UserField(String code, String name) {
    }

    private final String bizType;
    private final String name;
    private final String moduleCode;
    private final String detailRoute;
    private final List<Field> fields = new ArrayList<>();
    private final List<UserField> userFields = new ArrayList<>();

    private ApprovalBizDefinition(String bizType, String name, String moduleCode, String detailRoute) {
        this.bizType = bizType;
        this.name = name;
        this.moduleCode = moduleCode;
        this.detailRoute = detailRoute;
    }

    /**
     * @param bizType     单据类型编码，与编码规则的业务编码一致，如 SAL_ORDER
     * @param detailRoute 前端详情页路由模板，如 /sales/order/{id}（待办点击跳转）
     */
    public static ApprovalBizDefinition of(String bizType, String name, String moduleCode, String detailRoute) {
        return new ApprovalBizDefinition(bizType, name, moduleCode, detailRoute);
    }

    public ApprovalBizDefinition numberField(String code, String name) {
        fields.add(new Field(code, name, FieldType.NUMBER, List.of(), null));
        return this;
    }

    public ApprovalBizDefinition stringField(String code, String name) {
        fields.add(new Field(code, name, FieldType.STRING, List.of(), null));
        return this;
    }

    public ApprovalBizDefinition enumField(String code, String name, List<Option> options) {
        fields.add(new Field(code, name, FieldType.ENUM, List.copyOf(options), null));
        return this;
    }

    public ApprovalBizDefinition dictField(String code, String name, String dictType) {
        fields.add(new Field(code, name, FieldType.DICT, List.of(), dictType));
        return this;
    }

    public ApprovalBizDefinition deptField(String code, String name) {
        fields.add(new Field(code, name, FieldType.DEPT, List.of(), null));
        return this;
    }

    public ApprovalBizDefinition boolField(String code, String name) {
        fields.add(new Field(code, name, FieldType.BOOL, List.of(), null));
        return this;
    }

    /** 用户字段：既可作为条件（在所选用户中），也可作为“单据指定字段”审批人 */
    public ApprovalBizDefinition userField(String code, String name) {
        fields.add(new Field(code, name, FieldType.USER, List.of(), null));
        userFields.add(new UserField(code, name));
        return this;
    }

    public String bizType() {
        return bizType;
    }

    public String name() {
        return name;
    }

    public String moduleCode() {
        return moduleCode;
    }

    public String detailRoute() {
        return detailRoute;
    }

    public List<Field> fields() {
        return List.copyOf(fields);
    }

    public List<UserField> userFields() {
        return List.copyOf(userFields);
    }
}
