package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_import_item")
public class CrmImportItemDO extends BaseDO {

    private Long batchId;
    private Long customerId;
    private String code;
    private Integer seq;
}
