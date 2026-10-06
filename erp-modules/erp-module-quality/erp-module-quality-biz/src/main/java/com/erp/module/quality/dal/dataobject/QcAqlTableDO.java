package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AQL 主表（qc_aql_table）：字码 × AQL → 样本量、Ac、Re（箭头规则已展开） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_aql_table")
public class QcAqlTableDO extends BaseDO {

    private String codeLetter;
    /** 0 表示零缺陷 */
    private String aql;
    /** 箭头指向后实际使用的字码 */
    private String sampleLetter;
    private Integer sampleSize;
    private Integer ac;
    private Integer re;
}
