package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 工装使用与维护记录 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_tooling_record")
public class ToolingRecordDO extends BaseDO {

    private Long toolingId;
    /** LEND/RETURN/USAGE/MAINTAIN/REPAIR_START/REPAIR_END/SCRAP/ADJUST */
    private String recordType;
    /** USAGE 本次次数；ADJUST 调整后次数 */
    private Integer recordCount;
    /** 经办人 */
    private Long userId;
    private String sourceDocNo;
    private String content;
    private BigDecimal cost;
    private LocalDateTime occurredAt;
}
