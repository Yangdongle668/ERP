package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/** ECN 变更明细 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_ecn_line")
public class EcnLineDO extends BaseDO {

    private Long ecnId;
    private Integer lineNo;
    /** 被变更的 BOM 版本 */
    private Long bomId;
    /** ADD/REMOVE/REPLACE/CHANGE_QTY */
    private String action;
    private Long oldComponentId;
    private Long newComponentId;
    private BigDecimal oldQtyPer;
    private BigDecimal newQtyPer;
    private BigDecimal oldScrapRate;
    private BigDecimal newScrapRate;
    /** 新位号 */
    private String positionNo;
    /** 审批后生成的新 BOM 版本 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long newBomId;
    private String remark;
}
