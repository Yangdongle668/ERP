package com.erp.module.system.api.uom;

import java.math.BigDecimal;
import java.util.Optional;

/** 计量单位与通用换算（01-06 计量单位）。 */
public interface UomApi {

    Optional<UomDTO> get(String code);

    /** 校验单位存在且启用，否则抛出“计量单位「x」不存在或已停用” */
    UomDTO validate(String code);

    /**
     * 通用换算（SYS-UOM-R07）：相同单位直接返回；有直接或反向换算按其计算；否则经同类别的一个中间单位换算一次；
     * 都没有时抛出“单位 {from} 与 {to} 之间没有换算关系”。结果按目标单位精度舍入。
     */
    BigDecimal convert(BigDecimal qty, String fromUom, String toUom);

    /** 按单位精度 HALF_UP 舍入（SYS-UOM-R08），业务保存数量前必须调用 */
    BigDecimal round(BigDecimal qty, String uom);

    int precision(String uom);
}
