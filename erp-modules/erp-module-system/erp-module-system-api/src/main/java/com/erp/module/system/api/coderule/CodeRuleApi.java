package com.erp.module.system.api.coderule;

/**
 * 编码规则：生成单据编号、主数据编码（需求文档 00 第 4.4 节）。
 *
 * <p>并发安全、不重号；允许因事务回滚产生跳号。
 */
public interface CodeRuleApi {

    /**
     * 生成下一个编码。
     *
     * @param bizCode 业务编码，如 {@code MATERIAL}、{@code SALES_ORDER}
     * @throws com.erp.common.exception.BizException 规则未配置且没有模块声明默认规则时
     */
    String nextCode(String bizCode);
}
