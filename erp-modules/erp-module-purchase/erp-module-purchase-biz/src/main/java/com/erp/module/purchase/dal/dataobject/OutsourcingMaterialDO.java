package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 委外用料（表 pur_outsourcing_material） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_outsourcing_material")
public class OutsourcingMaterialDO extends BaseDO {

    private Long outsourcingId;

    private Integer lineNo;

    private Long materialId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String uom;

    private BigDecimal qtyPer;

    private BigDecimal requiredQty;

    private BigDecimal issuedQty;

    private BigDecimal returnedQty;

    private BigDecimal consumedQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal lossQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lossReason;

    /** 调整应发数量原因 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String adjustReason;
}
