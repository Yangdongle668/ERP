package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 到货单（表 pur_receipt） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_receipt", autoResultMap = true)
public class ReceiptDO extends BaseDocDO {

    private Long supplierId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String deliveryNoteNo;

    private LocalDateTime arrivalAt;

    /** PURCHASE/OUTSOURCE/SAMPLE */
    private String receiptType;

    private Long receiverId;
}
