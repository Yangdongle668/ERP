package com.erp.module.production.service;

import com.erp.common.enums.DocStatus;
import com.erp.common.statemachine.StateMachine;

import java.util.EnumSet;
import java.util.Set;

/** 生产订单状态（需求 09-01 第 4 节） */
public enum ProdStatus implements StateMachine.Labeled {
    DRAFT("草稿", DocStatus.DRAFT),
    PENDING("待审批", DocStatus.PENDING_APPROVAL),
    PLANNED("已计划", DocStatus.APPROVED),
    RELEASED("已下达", DocStatus.IN_PROGRESS),
    IN_PROGRESS("生产中", DocStatus.IN_PROGRESS),
    SUSPENDED("暂停", DocStatus.IN_PROGRESS),
    COMPLETED("已完工", DocStatus.COMPLETED),
    CLOSED("已关闭", DocStatus.CLOSED),
    VOIDED("已作废", DocStatus.VOIDED);

    /** 已下达未关闭（在制）：可领料、退料、完工入库、关闭 */
    public static final Set<ProdStatus> ACTIVE = EnumSet.of(RELEASED, IN_PROGRESS, SUSPENDED, COMPLETED);
    /** 可以生产：领料、派工、报工 */
    public static final Set<ProdStatus> RUNNING = EnumSet.of(RELEASED, IN_PROGRESS);

    private final String label;
    private final DocStatus docStatus;

    ProdStatus(String label, DocStatus docStatus) {
        this.label = label;
        this.docStatus = docStatus;
    }

    @Override
    public String label() {
        return label;
    }

    public DocStatus docStatus() {
        return docStatus;
    }

    public static ProdStatus of(String s) {
        return valueOf(s);
    }
}
