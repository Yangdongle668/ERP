package com.erp.module.asset.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 固定资产（需求 15-固定资产） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_asset")
public class AssetDO extends BaseDO {

    private String code;
    private String companyNo;
    private String assetClass;
    private String name;
    private String nameAbbr;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spec;
    private LocalDate purchaseDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long custodianId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String location;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal originalValue;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer usefulLifeMonths;
    private String assetStatus;
    private LocalDate scrappedDate;
    private String scrapReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
