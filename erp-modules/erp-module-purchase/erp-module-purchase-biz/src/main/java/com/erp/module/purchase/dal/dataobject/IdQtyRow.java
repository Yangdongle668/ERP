package com.erp.module.purchase.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;

/** 汇总查询结果：ID + 数量 */
@Data
public class IdQtyRow {

    private Long id;
    private BigDecimal qty;
}
