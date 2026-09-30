package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** AI 经营周报（ai_weekly_report） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_weekly_report")
public class AiWeeklyReportDO extends BaseDO {

    private LocalDate weekStart;
    /** 为空表示全公司口径 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long userId;
    private String title;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dataJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String summary;
}
