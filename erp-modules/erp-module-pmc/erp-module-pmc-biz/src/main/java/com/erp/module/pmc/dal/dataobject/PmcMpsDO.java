package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** MPS 主生产计划（pmc_mps） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pmc_mps", autoResultMap = true)
public class PmcMpsDO extends BaseDocDO {

    private String title;
    /** ISO 周 2026-W40 */
    private String startWeek;
    private String endWeek;
    /** DRAFT/PUBLISHED/CLOSED */
    private String mpsStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime publishedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long copiedFromId;
}
