package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 询价物料（表 pur_rfq_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_rfq_line")
public class RfqLineDO extends BaseDO {

    private Long rfqId;

    private Integer lineNo;

    private Long materialId;

    private BigDecimal qty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate requiredDate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
