package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 询价供应商（表 pur_rfq_supplier） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_rfq_supplier")
public class RfqSupplierDO extends BaseDO {

    private Long rfqId;

    private Long supplierId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime sentAt;

    private Boolean quoted;
}
