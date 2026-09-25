package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** ECN 工程变更 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_ecn")
public class EcnDO extends BaseDocDO {

    private String title;
    /** 字典 eng_ecn_type */
    private String ecnType;
    /** 字典 eng_ecn_reason */
    private String reasonType;
    private String reason;
    /** NORMAL/URGENT */
    private String urgency;
    /** IMMEDIATE/DATE/USE_UP */
    private String effectiveMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate effectiveDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    /** 已完成影响分析 */
    private Boolean analyzed;
    /** 涉及关键件 */
    private Boolean keyPart;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedAt;
    /** 实际生效时间 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime effectedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
}
