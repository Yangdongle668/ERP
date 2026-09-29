package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** NCR 处置明细（qc_ncr_disposition） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_ncr_disposition")
public class QcNcrDispositionDO extends BaseDO {

    private Long ncrId;
    private Integer seq;
    /** RETURN/CONCESSION/SORT/REWORK/SCRAP */
    private String disposition;
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String followDocNo;
    private Boolean done;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime doneAt;
}
