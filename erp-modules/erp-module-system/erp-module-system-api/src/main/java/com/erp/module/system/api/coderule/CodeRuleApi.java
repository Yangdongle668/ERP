package com.erp.module.system.api.coderule;

import java.util.Map;

/**
 * 编码规则：生成单据编号、主数据编码（01-05 编码规则）。
 *
 * <p>并发安全、不重号；允许因事务回滚产生跳号。
 */
public interface CodeRuleApi {

    /**
     * 生成下一个编码。
     *
     * @param bizCode 业务编码，如 {@code SAL_ORDER}
     * @throws com.erp.common.exception.BizException 规则未配置且没有模块声明默认规则时
     */
    String nextCode(String bizCode);

    /**
     * 带前缀变量生成，如物料编码 {@code nextCode("ENG_MATERIAL", Map.of("categoryPrefix", "FPC"))}。
     * 不同的前缀取值独立计数。
     */
    String nextCode(String bizCode, Map<String, String> vars);

    /** 是否允许手工输入编码（SYS-COD-R06）：为 false 时业务接口应忽略前端传入的编码，始终自动生成 */
    boolean isManualAllowed(String bizCode);
}
