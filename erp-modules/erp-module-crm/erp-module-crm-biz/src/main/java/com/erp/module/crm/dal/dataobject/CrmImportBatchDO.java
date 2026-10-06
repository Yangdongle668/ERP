package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_import_batch")
public class CrmImportBatchDO extends BaseDO {

    private String fileName;
    private Integer totalCount;
    private Integer createdCount;
    private String batchStatus;
    private LocalDateTime rolledBackAt;
    private Long rolledBackBy;
}
