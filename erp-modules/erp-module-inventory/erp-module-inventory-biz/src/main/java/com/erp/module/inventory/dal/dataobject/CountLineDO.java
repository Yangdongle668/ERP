package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 盘点明细 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_count_line")
public class CountLineDO extends BaseDO {

    private Long countId;
    private Integer lineNo;
    private Long warehouseId;
    private Long locationId;
    private Long materialId;
    private String batchNo;
    private BigDecimal bookQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal countQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal recountQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal finalQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal diffQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal refCost;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal diffAmount;
    private Boolean needRecount;
    private Boolean isAdded;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long counterId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime countedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    /** 序列号物料：实盘 / 复盘序列号清单与比对出的盘盈、盘亏序列号（逗号分隔） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String countSerials;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String recountSerials;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String gainSerials;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lossSerials;
}
