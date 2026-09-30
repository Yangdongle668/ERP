package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 品质日汇总（bi_agg_quality_daily） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_agg_quality_daily")
public class BiAggQualityDO extends BaseDO {

    private LocalDate statDate;
    private String period;
    /** IQC/IPQC/FQC/OQC/RETURN/NCR/COMPLAINT */
    private String inspectType;
    private Long supplierId;
    private Long customerId;
    private Long materialId;
    private Long categoryId;
    private Integer lotCount;
    private Integer passCount;
    private Integer concessionCount;
    private Integer rejectCount;
    private Integer defectCount;
    private Integer ncrCount;
    private Integer complaintCount;
}
