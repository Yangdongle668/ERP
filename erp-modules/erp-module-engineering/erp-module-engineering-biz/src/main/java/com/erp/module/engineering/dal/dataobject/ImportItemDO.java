package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_import_item")
public class ImportItemDO extends BaseDO {

    private Long batchId;
    private Long targetId;
    private String targetCode;
    private String action;
    private String snapshot;
    private Integer seq;
}
