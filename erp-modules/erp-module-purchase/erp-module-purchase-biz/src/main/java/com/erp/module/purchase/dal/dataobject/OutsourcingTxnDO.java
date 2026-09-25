package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 委外发料/退料记录（表 pur_outsourcing_txn）：仓库确认后写入，反确认后标记，已发、退回数量据此汇总 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_outsourcing_txn")
public class OutsourcingTxnDO extends BaseDO {

    private Long outsourcingId;

    private Long outsourcingMaterialId;

    /** ISSUE 发料 / RETURN 余料退回 */
    private String txnType;

    private Long stockDocId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockDocNo;

    /** 基本单位 */
    private BigDecimal qty;

    /** 仓库已反确认 */
    private Boolean reversed;
}
