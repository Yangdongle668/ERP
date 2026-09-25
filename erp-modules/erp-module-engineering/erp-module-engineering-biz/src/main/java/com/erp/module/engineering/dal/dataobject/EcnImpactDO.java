package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/** ECN 影响分析 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_ecn_impact")
public class EcnImpactDO extends BaseDO {

    private Long ecnId;
    private Long materialId;
    /** STOCK/PURCHASE/WIP/SALES */
    private String impactType;
    private String docNo;
    private BigDecimal qty;
    /** 处理方式 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handling;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handlingRemark;
}
