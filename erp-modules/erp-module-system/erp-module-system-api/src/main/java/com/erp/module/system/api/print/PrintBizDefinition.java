package com.erp.module.system.api.print;

import java.util.ArrayList;
import java.util.List;

/**
 * 可打印的单据类型声明（需求 01-系统管理/09 第 2 节 sys_print_biz）。业务模块在配置类中声明为 Bean，启动时同步；
 * 内置模板放在模块资源 {@code print-templates/<bizType>-zh-CN.html}（对外单据另有 {@code -en.html}），
 * 不存在时导入，已存在的不覆盖（R05）。
 *
 * <pre>
 * &#64;Bean
 * public PrintBizDefinition salesOrderPrint() {
 *     return PrintBizDefinition.of("SAL_ORDER", "销售订单", "sales", "/sales/orders/{id}/print-data")
 *             .variable("docNo", "单号", "string")
 *             .variable("customer.nameEn", "客户英文名", "string")
 *             .variable("lines", "明细", "array")
 *             .variable("lines.qty", "数量", "qty")
 *             .sampleData("{\"docNo\":\"SO-202609-0001\",\"lines\":[{\"qty\":10}]}");
 * }
 * </pre>
 *
 * 打印数据接口 {@code GET dataApi}（{id} 替换为单据 ID）按当前用户权限返回 JSON；草稿单据返回 status=DRAFT 时打印页加“草稿”水印，
 * status=CANCELED 时不允许打印。
 */
public final class PrintBizDefinition {

    /**
     * 模板变量说明（模板编辑器的变量面板）。
     *
     * @param path 如 docNo、customer.nameEn、lines（数组）、lines.qty（数组元素字段）
     * @param type string / number / qty / amount / price / date / datetime / array / object / image
     */
    public record Variable(String path, String name, String type) {
    }

    private final String bizType;
    private final String name;
    private final String moduleCode;
    private final String dataApi;
    private final List<Variable> variables = new ArrayList<>();
    private String sampleData = "{}";

    private PrintBizDefinition(String bizType, String name, String moduleCode, String dataApi) {
        this.bizType = bizType;
        this.name = name;
        this.moduleCode = moduleCode;
        this.dataApi = dataApi;
    }

    /** @param dataApi 打印数据接口（前端调用，不含 /api 前缀），如 /sales/orders/{id}/print-data */
    public static PrintBizDefinition of(String bizType, String name, String moduleCode, String dataApi) {
        return new PrintBizDefinition(bizType, name, moduleCode, dataApi);
    }

    public PrintBizDefinition variable(String path, String name, String type) {
        variables.add(new Variable(path, name, type));
        return this;
    }

    /** 示例数据（JSON），编辑器预览用 */
    public PrintBizDefinition sampleData(String json) {
        this.sampleData = json;
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

    public String dataApi() {
        return dataApi;
    }

    public List<Variable> variables() {
        return List.copyOf(variables);
    }

    public String sampleData() {
        return sampleData;
    }
}
