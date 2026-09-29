package com.erp.module.shipping.service.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.pmc.api.shipping.ShippingPlanApi;
import com.erp.module.sales.api.order.SalesOrderWritebackApi;
import com.erp.module.shipping.api.ShippingErrorCodes;
import com.erp.module.shipping.api.notice.ShipmentNoticeChangedEvent;
import com.erp.module.shipping.config.ShippingModuleConfig;
import com.erp.module.shipping.dal.dataobject.ShpNoticeDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeLineDO;
import com.erp.module.shipping.dal.dataobject.ShpPickingDO;
import com.erp.module.shipping.dal.mapper.ShpNoticeLineMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeMapper;
import com.erp.module.shipping.dal.mapper.ShpPickingMapper;
import com.erp.module.shipping.service.NoticeStatus;
import com.erp.module.shipping.service.PickingStatus;
import com.erp.module.shipping.service.ShpAction;
import com.erp.module.shipping.service.ShpStateMachines;
import com.erp.module.shipping.service.ShpSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 出货通知的状态流转与对订单 / 出货计划的回写（通知、拣货、装箱、出货各服务共用） */
@Component
public class NoticeFlow {

    private final ShpNoticeMapper mapper;
    private final ShpNoticeLineMapper lineMapper;
    private final ShpPickingMapper pickingMapper;
    private final SalesOrderWritebackApi writebackApi;
    private final ShippingPlanApi shippingPlanApi;
    private final DomainEventPublisher eventPublisher;
    private final ShpSupport support;

    public NoticeFlow(ShpNoticeMapper mapper, ShpNoticeLineMapper lineMapper, ShpPickingMapper pickingMapper, SalesOrderWritebackApi writebackApi,
                      ShippingPlanApi shippingPlanApi, DomainEventPublisher eventPublisher, ShpSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.pickingMapper = pickingMapper;
        this.writebackApi = writebackApi;
        this.shippingPlanApi = shippingPlanApi;
        this.eventPublisher = eventPublisher;
        this.support = support;
    }

    public ShpNoticeDO get(Long id) {
        ShpNoticeDO n = id == null ? null : mapper.selectById(id);
        if (n == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "出货通知");
        return n;
    }

    /** 事件监听用：不存在时返回 null */
    public ShpNoticeDO find(Long id) {
        return id == null ? null : mapper.selectById(id);
    }

    public static NoticeStatus status(ShpNoticeDO n) {
        return NoticeStatus.valueOf(n.getNoticeStatus());
    }

    public void fire(ShpNoticeDO n, ShpAction action, String reason) {
        NoticeStatus from = status(n);
        NoticeStatus to = ShpStateMachines.NOTICE.next(from, action)
                .orElseThrow(() -> BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, from.label(), action.label()));
        n.setNoticeStatus(to.name());
        n.setStatus(to.docStatus());
        mapper.updateByIdOrFail(n);
        support.log(ShippingModuleConfig.NOTICE, n.getId(), n.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    /** 当前有效拣货单（未取消） */
    public ShpPickingDO activePicking(Long noticeId) {
        return pickingMapper.selectList(new LambdaQueryWrapper<ShpPickingDO>().eq(ShpPickingDO::getNoticeId, noticeId)
                .ne(ShpPickingDO::getPickingStatus, PickingStatus.CANCELED.name()).orderByDesc(ShpPickingDO::getId)).stream().findFirst().orElse(null);
    }

    /** 通知行有效数量（基本单位）= 通知数量 − 按实拣完成的缺货 */
    public static BigDecimal effectiveQty(ShpNoticeLineDO l) {
        return Decimals.qty(l.getBaseQty().subtract(ShpSupport.nz(l.getShortageQty())));
    }

    /**
     * SHP-SN-R02：回写订单已通知数量（正数占用，负数释放）并发布 ShipmentNoticeChangedEvent；
     * planDeltas 同步回写 PMC 出货计划已通知数量。
     */
    public void writeBack(ShpNoticeDO n, Map<Long, BigDecimal> orderDeltas, Map<Long, BigDecimal> planDeltas) {
        List<ShipmentNoticeChangedEvent.Line> lines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : orderDeltas.entrySet()) {
            BigDecimal d = Decimals.qty(e.getValue());
            if (d.signum() == 0) continue;
            writebackApi.onNoticeChanged(e.getKey(), d, n.getId(), n.getDocNo());
            lines.add(new ShipmentNoticeChangedEvent.Line(e.getKey(), d));
        }
        for (Map.Entry<Long, BigDecimal> e : planDeltas.entrySet()) {
            BigDecimal d = Decimals.qty(e.getValue());
            if (e.getKey() != null && d.signum() != 0) shippingPlanApi.onNoticed(e.getKey(), d);
        }
        if (!lines.isEmpty()) {
            eventPublisher.publish(new ShipmentNoticeChangedEvent(n.getId(), n.getDocNo(), n.getCustomerId(), n.getNoticeStatus(), lines));
        }
    }

    /** 释放未出货数量（关闭通知 / 按实拣完成的缺货）：key = 通知行，value = 释放的基本单位数量 */
    public void release(ShpNoticeDO n, Map<ShpNoticeLineDO, BigDecimal> releases) {
        Map<Long, BigDecimal> orders = new LinkedHashMap<>();
        Map<Long, BigDecimal> plans = new LinkedHashMap<>();
        releases.forEach((l, q) -> {
            if (q.signum() <= 0) return;
            orders.merge(l.getOrderLineId(), q.negate(), BigDecimal::add);
            if (l.getShippingPlanLineId() != null) plans.merge(l.getShippingPlanLineId(), q.negate(), BigDecimal::add);
        });
        writeBack(n, orders, plans);
    }

    /** 出货确认后：全部已装箱数量都出货时通知“已出货” */
    public void refreshShipped(Long noticeId) {
        ShpNoticeDO n = get(noticeId);
        NoticeStatus st = status(n);
        List<ShpNoticeLineDO> lines = lineMapper.selectByParent(noticeId);
        boolean all = !lines.isEmpty() && lines.stream().allMatch(l -> ShpSupport.nz(l.getShippedQty()).compareTo(effectiveQty(l)) >= 0);
        if (all && (st == NoticeStatus.PACKED || st == NoticeStatus.READY)) {
            fire(n, ShpAction.SHIP_ALL, null);
        } else if (!all && st == NoticeStatus.SHIPPED) {
            fire(n, ShpAction.UNSHIP, "出货冲回");
            if ("PASSED".equals(n.getOqcResult())) fire(get(noticeId), ShpAction.OQC_PASS, "OQC 已合格");
        }
    }
}
