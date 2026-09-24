package com.erp.framework.mybatis;

import com.erp.common.enums.DocStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 业务单据单头基类（需求文档 00 第 4.1 节）。
 *
 * <p>状态只能通过 {@link com.erp.common.statemachine.StateMachine} 计算后设置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseDocDO extends BaseDO {

    private String docNo;
    private LocalDate docDate;
    private DocStatus status;
    private Long orgId;
    private Long deptId;
    private Long ownerId;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private String remark;
}
