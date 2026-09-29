package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 推送记录（催料、例外，同一对象一天只推一次）（pmc_push_log） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmc_push_log")
public class PmcPushLogDO extends BaseDO {

    private String pushType;
    private String refKey;
    private LocalDate pushDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String userIds;
}
