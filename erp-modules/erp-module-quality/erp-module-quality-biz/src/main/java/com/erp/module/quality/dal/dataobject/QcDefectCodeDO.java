package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 缺陷代码（qc_defect_code） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_defect_code")
public class QcDefectCodeDO extends BaseDO {

    private String code;
    private String name;
    /** 字典 qc_defect_category */
    private String category;
    /** CR/MA/MI */
    private String defaultLevel;
    /** ENABLED/DISABLED */
    private String codeStatus;
}
