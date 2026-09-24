package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 出库单行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock_out_line")
public class StockOutLineDO extends BaseDO {

    private Long stockOutId;
    private Integer lineNo;
    private Long materialId;
    private String uom;
    private BigDecimal requestQty;
    private BigDecimal qty;
    private BigDecimal baseQty;
    private Long locationId;
    private String batchNo;
    private String serialNos;
    private Long sourceLineId;
    private String remark;
}
