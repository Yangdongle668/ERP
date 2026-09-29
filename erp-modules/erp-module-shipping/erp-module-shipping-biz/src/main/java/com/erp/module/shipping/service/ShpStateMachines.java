package com.erp.module.shipping.service;

import com.erp.common.statemachine.StateMachine;

import static com.erp.module.shipping.service.ShpAction.*;

/** 出货状态机 */
public final class ShpStateMachines {

    private ShpStateMachines() {
    }

    /** 出货通知（11-01 第 3.3 节、11-02、11-03） */
    public static final StateMachine<NoticeStatus, ShpAction> NOTICE;

    static {
        StateMachine.Builder<NoticeStatus, ShpAction> b = StateMachine.builder(NoticeStatus.class, ShpAction.class)
                .transition(NoticeStatus.DRAFT, SUBMIT, NoticeStatus.PENDING)
                .transition(NoticeStatus.PENDING, WITHDRAW, NoticeStatus.DRAFT)
                .transition(NoticeStatus.PENDING, REJECT, NoticeStatus.DRAFT)
                .transition(NoticeStatus.PENDING, APPROVE, NoticeStatus.APPROVED)
                .transition(NoticeStatus.APPROVED, UNAPPROVE, NoticeStatus.DRAFT)
                .transition(NoticeStatus.APPROVED, START_PICK, NoticeStatus.PICKING)
                // 未启用拣货：审核后直接已装箱
                .transition(NoticeStatus.APPROVED, PACK, NoticeStatus.PACKED)
                .transition(NoticeStatus.PICKING, PACK, NoticeStatus.PACKED)
                .transition(NoticeStatus.PACKED, UNPACK, NoticeStatus.PICKING)
                .transition(NoticeStatus.OQC, UNPACK, NoticeStatus.PICKING)
                .transition(NoticeStatus.READY, UNPACK, NoticeStatus.PICKING)
                .transition(NoticeStatus.PACKED, REQUEST_OQC, NoticeStatus.OQC)
                .transition(NoticeStatus.OQC, OQC_PASS, NoticeStatus.READY)
                .transition(NoticeStatus.OQC, OQC_REJECT, NoticeStatus.PACKED)
                .transition(NoticeStatus.PACKED, SHIP_ALL, NoticeStatus.SHIPPED)
                .transition(NoticeStatus.READY, SHIP_ALL, NoticeStatus.SHIPPED)
                .transition(NoticeStatus.SHIPPED, UNSHIP, NoticeStatus.PACKED)
                // 出货冲回后 OQC 已合格的通知回到待出货
                .transition(NoticeStatus.PACKED, OQC_PASS, NoticeStatus.READY)
                .transition(NoticeStatus.DRAFT, VOID, NoticeStatus.VOIDED);
        for (NoticeStatus s : new NoticeStatus[]{NoticeStatus.APPROVED, NoticeStatus.PICKING, NoticeStatus.PACKED, NoticeStatus.OQC, NoticeStatus.READY}) {
            b.transition(s, CLOSE, NoticeStatus.CLOSED);
        }
        NOTICE = b.build();
    }

    /** 拣货单 */
    public static final StateMachine<PickingStatus, ShpAction> PICKING = StateMachine.builder(PickingStatus.class, ShpAction.class)
            .transition(PickingStatus.WAITING, START_PICK, PickingStatus.PICKING)
            .transition(PickingStatus.PICKING, COMPLETE_PICK, PickingStatus.DONE)
            .transition(PickingStatus.WAITING, CANCEL, PickingStatus.CANCELED)
            .transition(PickingStatus.PICKING, CANCEL, PickingStatus.CANCELED)
            .transition(PickingStatus.DONE, CANCEL, PickingStatus.CANCELED)
            .build();

    /** 出货单（11-03 第 4 节） */
    public static final StateMachine<ShipmentStatus, ShpAction> SHIPMENT = StateMachine.builder(ShipmentStatus.class, ShpAction.class)
            .transition(ShipmentStatus.DRAFT, SUBMIT, ShipmentStatus.PENDING)
            .transition(ShipmentStatus.PENDING, WITHDRAW, ShipmentStatus.DRAFT)
            .transition(ShipmentStatus.PENDING, REJECT, ShipmentStatus.DRAFT)
            .transition(ShipmentStatus.PENDING, APPROVE, ShipmentStatus.SUBMITTED)
            .transition(ShipmentStatus.SUBMITTED, WITHDRAW, ShipmentStatus.DRAFT)
            .transition(ShipmentStatus.SUBMITTED, OUT_REJECTED, ShipmentStatus.DRAFT)
            .transition(ShipmentStatus.SUBMITTED, CONFIRM_OUT, ShipmentStatus.SHIPPED)
            .transition(ShipmentStatus.SHIPPED, REVERSE, ShipmentStatus.SUBMITTED)
            .transition(ShipmentStatus.COMPLETED, REVERSE, ShipmentStatus.SUBMITTED)
            .transition(ShipmentStatus.SHIPPED, COMPLETE, ShipmentStatus.COMPLETED)
            .transition(ShipmentStatus.DRAFT, VOID, ShipmentStatus.VOIDED)
            .build();
}
