package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import com.erp.module.purchase.service.rfq.RfqStatus;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 询价单（表 pur_rfq） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_rfq", autoResultMap = true)
public class RfqDO extends BaseDocDO {

    private String title;

    private String currency;

    private LocalDate quoteDeadline;

    /** DRAFT/QUOTING/COMPARING/AWARDED/CANCELED */
    private RfqStatus rfqStatus;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cancelReason;
}
