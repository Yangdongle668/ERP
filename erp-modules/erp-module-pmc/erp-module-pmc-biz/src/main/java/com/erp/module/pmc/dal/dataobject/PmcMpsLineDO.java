package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** MPS 明细（物料 × 周）（pmc_mps_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_mps_line")
public class PmcMpsLineDO extends BaseDO {

    private Long mpsId;
    private Long materialId;
    private String week;
    private BigDecimal demandQty;
    /** 在制完工 */
    private BigDecimal wipQty;
    private BigDecimal plannedQty;
    private BigDecimal projectedOnHand;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
