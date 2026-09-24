package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import com.erp.module.inventory.api.doc.TransferType;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 调拨单 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_transfer")
public class TransferDO extends BaseDocDO {

    private TransferType transferType;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;
    private Long inspectionId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long confirmedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime confirmedAt;
}
