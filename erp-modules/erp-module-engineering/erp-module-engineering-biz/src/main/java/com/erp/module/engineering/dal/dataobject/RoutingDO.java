package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 工艺路线头 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_routing")
public class RoutingDO extends BaseDocDO {

    /** 自制件 */
    private Long materialId;
    /** 版本号，同一物料从 1 递增 */
    private Integer routingVersion;
    private Boolean isDefault;
    /** 成为默认版本的日期 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate effectiveDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private Long copiedFromId;
    private Integer stepCount;
    private BigDecimal totalSetupMinutes;
    /** 总标准工时（秒/件） */
    private BigDecimal totalRunSeconds;
}
