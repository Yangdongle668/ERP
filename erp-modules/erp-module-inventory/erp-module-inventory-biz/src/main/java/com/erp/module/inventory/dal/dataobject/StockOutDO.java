package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import com.erp.module.inventory.api.doc.StockOutType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 出库单 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock_out")
public class StockOutDO extends BaseDocDO {

    private LocalDate sourceDate;
    private StockOutType outType;
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long receiverDeptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long receiverId;
    private Long supplierId;
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;
    private Boolean manual;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long confirmedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectReason;
}
