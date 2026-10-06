package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_import_batch")
public class ImportBatchDO extends BaseDO {

    private String bizType;
    private String fileName;
    private String importMode;
    private Integer totalCount;
    private Integer createdCount;
    private Integer updatedCount;
    private String batchStatus;
    private LocalDateTime rolledBackAt;
    private Long rolledBackBy;
}
