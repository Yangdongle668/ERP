package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.inventory.api.doc.JudgeResult;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 调拨单行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_transfer_line")
public class TransferLineDO extends BaseDO {

    private Long transferId;
    private Integer lineNo;
    private Long materialId;
    private BigDecimal qty;
    private String batchNo;
    private Long fromLocationId;
    private Long toLocationId;
    private String serialNos;
    private JudgeResult judgeResult;
    private Long sourceLineId;
    private String remark;
}
