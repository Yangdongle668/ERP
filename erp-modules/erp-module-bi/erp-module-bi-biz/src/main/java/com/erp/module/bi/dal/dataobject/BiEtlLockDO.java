package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 数据任务互斥锁（bi_etl_lock） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_etl_lock")
public class BiEtlLockDO extends BaseDO {

    private String lockKey;
    private LocalDateTime lockedUntil;
    private String lockedBy;
}
