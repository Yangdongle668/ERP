package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 库存预留 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_reservation")
public class ReservationDO extends BaseDO {

    private Long materialId;
    private Long warehouseId;
    private String batchNo;
    private BigDecimal qty;
    private BigDecimal releasedQty;
    private String bizType;
    private Long bizId;
    private Long bizLineId;
    private String bizNo;
    private String status;
}
