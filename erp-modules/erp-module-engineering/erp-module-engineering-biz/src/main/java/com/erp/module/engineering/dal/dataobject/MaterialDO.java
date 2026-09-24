package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.engineering.api.material.IssueRule;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.OrderPolicy;
import com.erp.module.engineering.api.material.SourceType;
import com.erp.module.engineering.api.material.Tracking;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 物料（需求 05-02 第 2 节）。可为空的字段使用 ALWAYS 更新策略，保证界面清空后能写回 NULL。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_material")
public class MaterialDO extends BaseDO {

    // ---------- 基本信息 ----------
    private String code;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nameEn;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spec;
    private MaterialType materialType;
    private Long categoryId;
    private String baseUom;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String drawingNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String revision;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String brand;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String manufacturer;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mpn;
    /** 查重键：制造商料号去空格大写 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mpnKey;
    /** 查重键：名称 + 规格去空格大写 */
    private String dupKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String hsCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal unitNetWeight;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal unitGrossWeight;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long imageFileId;
    private MaterialStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    // ---------- 计划属性 ----------
    private SourceType sourceType;
    private Integer leadTimeDays;
    private BigDecimal safetyStock;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal maxStock;
    private OrderPolicy orderPolicy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal fixedLotQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer periodDays;
    private BigDecimal moq;
    private BigDecimal mpq;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long plannerId;
    private Integer lowLevelCode;

    // ---------- 采购属性 ----------
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long buyerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String purchaseUom;
    private BigDecimal overReceivePct;

    // ---------- 库存属性 ----------
    private Tracking tracking;
    private IssueRule issueRule;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer shelfLifeDays;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal minRemainingLifePct;

    // ---------- 质量属性 ----------
    private Boolean iqcRequired;
    private Boolean fqcRequired;
    private Boolean oqcRequired;

    // ---------- 财务与销售属性 ----------
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal standardCost;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String salesUom;
    private BigDecimal purchaseTaxRate;
    private BigDecimal salesTaxRate;
}
