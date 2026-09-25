package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 信用额度调整单 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_credit_change")
public class CreditChangeDO extends BaseDocDO {

    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal oldLimit;
    private BigDecimal newLimit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer oldDays;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer newDays;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String oldControl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String newControl;
    /** 临时额度到期日 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expireDate;
    private String reason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedAt;
    /** 临时额度已恢复 */
    private Boolean restored;
}
