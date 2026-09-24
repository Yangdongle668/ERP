package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 入库单行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock_in_line")
public class StockInLineDO extends BaseDO {

    private Long stockInId;
    private Integer lineNo;
    private Long materialId;
    private String uom;
    private BigDecimal qty;
    private BigDecimal baseQty;
    private Long locationId;
    private String batchNo;
    private String supplierBatchNo;
    private LocalDate productionDate;
    private LocalDate expireDate;
    private String serialNos;
    private BigDecimal unitCost;
    private BigDecimal amount;
    private Long sourceLineId;
    private String remark;
}
