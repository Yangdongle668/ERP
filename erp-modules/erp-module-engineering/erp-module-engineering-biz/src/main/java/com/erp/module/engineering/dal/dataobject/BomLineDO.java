package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.engineering.api.bom.IssueMethod;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** BOM 行：用量为每 base_qty 个父件，单位为子件基本单位；损耗率存 0～1 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_bom_line")
public class BomLineDO extends BaseDO {

    private Long bomId;
    private Integer lineNo;
    private Long componentId;
    private BigDecimal qtyPer;
    private String uom;
    private BigDecimal scrapRate;
    private String positionNo;
    private IssueMethod issueMethod;
    private Integer operationSeq;
    private Boolean isKey;
    private String remark;
}
