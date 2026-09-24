package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BOM 头（需求 05-03 第 2 节）。doc_no = 父件编码-V版本；状态使用通用单据状态，CLOSED 在界面上显示为“停用”。
 * 版本号字段为 bomVersion（version 已被乐观锁占用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_bom")
public class BomDO extends BaseDocDO {

    private Long materialId;
    private Integer bomVersion;
    private BigDecimal baseQty;
    private Boolean isDefault;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate effectiveDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private Long ecnId;
    private Long copiedFromId;
    private Integer lineCount;
}
