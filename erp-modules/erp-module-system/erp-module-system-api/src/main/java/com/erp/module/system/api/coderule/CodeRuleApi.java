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

    /**
     * 带前缀变量、指定流水号位数生成（如物料编码段：泡棉、电芯等类别 3 位流水，其他 5 位）。
     * {@code seqLength} 为空时按规则配置；计数与 {@link #nextCode(String, Map)} 相同（按前缀取值独立计数）。
     */
    default String nextCode(String bizCode, Map<String, String> vars, Integer seqLength) {
        return nextCode(bizCode, vars);
    }

    /**
     * 手工输入（含导入）的编码符合规则格式（前缀 + 指定位数的流水号，规则不含日期）时，把该前缀的流水号推进到不小于它，
     * 避免之后自动生成的编码与已有编码重复。如导入 LD-B-0011 后，下一个自动编码为 LD-B-0012。
     *
     * @param seqLength 流水号位数，空则按规则配置
     */
    default void observeManualCode(String bizCode, Map<String, String> vars, Integer seqLength, String code) {
    }

    /**
     * 预览下一个编码（不占用流水号），如新建客户时显示“保存后编码为 LD-B-0012”。并发新建时实际编码可能更大。
     *
     * @return 规则不存在或前缀变量缺失时为 null
     */
    default String peekNextCode(String bizCode, Map<String, String> vars) {
        return null;
    }

    /** 是否允许手工输入编码（SYS-COD-R06）：为 false 时业务接口应忽略前端传入的编码，始终自动生成 */
    boolean isManualAllowed(String bizCode);
}
