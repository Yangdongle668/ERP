package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.module.system.api.paymentterm.BaseEvent;
import lombok.Data;

import java.math.BigDecimal;

/** 付款节点（付款条件的子表，保存时整体替换，没有审计字段） */
@Data
@TableName("sys_payment_term_node")
public class PaymentTermNodeDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long termId;
    private Integer seq;
    private String name;
    private BigDecimal percent;
    private BaseEvent baseEvent;
    private Integer days;
}
