package com.erp.module.production.service.order;

import com.erp.common.exception.BizException;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.sample.SampleApi;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.production.api.order.ProductionOrderCompletedEvent;
import com.erp.module.production.api.order.ProductionProgressEvent;
import com.erp.module.production.config.ProductionModuleConfig;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderOperationDO;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderOperationMapper;
import com.erp.module.production.service.MfgAction;
import com.erp.module.production.service.MfgStateMachines;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产订单状态与进度（各单据共用，只依赖 Mapper，避免服务间循环依赖）：
 * 状态流转、首次领料/报工进入生产中、完工判断（R06）、进度事件。
 */
@Service("mfgOrderProgressService")
public class OrderProgressService {

    public static final String SAMPLE_SOURCE = "ENG_SAMPLE";

    private final MfgProdOrderMapper mapper;
    private final MfgProdOrderOperationMapper operationMapper;
    private final MfgSupport support;
    private final DomainEventPublisher eventPublisher;
    private final ObjectProvider<SampleApi> sampleApi;

    public OrderProgressService(MfgProdOrderMapper mapper, MfgProdOrderOperationMapper operationMapper, MfgSupport support,
                                DomainEventPublisher eventPublisher, ObjectProvider<SampleApi> sampleApi) {
        this.mapper = mapper;
        this.operationMapper = operationMapper;
        this.support = support;
        this.eventPublisher = eventPublisher;
        this.sampleApi = sampleApi;
    }

    public MfgProdOrderDO getOrThrow(Long id) {
        MfgProdOrderDO o = id == null ? null : mapper.selectById(id);
        if (o == null) throw new BizException(ProductionErrorCodes.ORDER_NOT_EXISTS);
        return o;
    }

    public static ProdStatus status(MfgProdOrderDO o) {
        return ProdStatus.of(o.getProdStatus());
    }

    /** 状态流转：状态机 → 乐观锁更新 → 操作日志 */
    public void fire(MfgProdOrderDO o, MfgAction action, String reason) {
        ProdStatus from = status(o);
        ProdStatus to = MfgStateMachines.PROD_ORDER.fire(from, action);
        o.setProdStatus(to.name());
        o.setStatus(to.docStatus());
        mapper.updateByIdOrFail(o);
        support.log(ProductionModuleConfig.PROD_ORDER, o.getId(), o.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    /** 可以生产（领料、派工、报工）：已下达 / 生产中；暂停时提示“生产订单已暂停” */
    public static void requireRunning(MfgProdOrderDO o) {
        ProdStatus s = status(o);
        if (s == ProdStatus.SUSPENDED) throw new BizException(ProductionErrorCodes.ORDER_SUSPENDED);
        if (!ProdStatus.RUNNING.contains(s)) throw BizException.of(ProductionErrorCodes.ORDER_NOT_RUNNING, s.label());
    }

    /** 已下达未关闭（退料、完工入库） */
    public static void requireActive(MfgProdOrderDO o) {
        ProdStatus s = status(o);
        if (!ProdStatus.ACTIVE.contains(s)) throw BizException.of(ProductionErrorCodes.ORDER_NOT_RUNNING, s.label());
    }

    /** 首次领料确认或首次报工：已下达 → 生产中，记录实际开工 */
    public void markStarted(MfgProdOrderDO o) {
        if (o.getActualStart() == null) o.setActualStart(LocalDateTime.now());
        if (status(o) == ProdStatus.RELEASED) fire(o, MfgAction.START, null);
        else mapper.updateByIdOrFail(o);
    }

    /**
     * 完工判断（MFG-FN-R06）：合格入库 + 报废 ≥ 计划数量 → 已完工，发布完工事件（样品订单回调样品单）；
     * 拆解订单没有产品入库，按末道报工（完成 + 报废）判断。冲销后不再满足时已完工 → 生产中。调用方负责先保存数量字段。
     */
    public void checkCompletion(MfgProdOrderDO o) {
        ProdStatus s = status(o);
        BigDecimal output = ProdOrderService.DISASSEMBLY.equals(o.getOrderType()) ? o.getCompletedQty() : o.getQualifiedStockedQty();
        boolean done = MfgSupport.nz(output).add(MfgSupport.nz(o.getScrappedQty())).compareTo(o.getQty()) >= 0;
        if (done && (s == ProdStatus.RELEASED || s == ProdStatus.IN_PROGRESS)) {
            o.setActualEnd(LocalDateTime.now());
            fire(o, MfgAction.COMPLETE, null);
            eventPublisher.publish(new ProductionOrderCompletedEvent(o.getId(), o.getDocNo(), o.getOrderType(), o.getMaterialId(), o.getSourceType(),
                    o.getSourceId(), o.getQualifiedStockedQty()));
            if (SAMPLE_SOURCE.equals(o.getSourceType()) && o.getSourceId() != null) {
                SampleApi api = sampleApi.getIfAvailable();
                if (api != null) api.onProductionCompleted(o.getSourceId());
            }
        } else if (!done && s == ProdStatus.COMPLETED) {
            o.setActualEnd(null);
            fire(o, MfgAction.REOPEN, null);
        }
    }

    public void publishProgress(MfgProdOrderDO o) {
        eventPublisher.publish(new ProductionProgressEvent(o.getId(), o.getDocNo(), o.getMaterialId(), o.getCompletedQty(), o.getStockedQty(),
                o.getQualifiedStockedQty()));
    }

    // ==================== 工序 ====================

    public List<MfgProdOrderOperationDO> operations(Long orderId) {
        return operationMapper.selectByParent(orderId);
    }

    public static MfgProdOrderOperationDO operation(List<MfgProdOrderOperationDO> ops, int seq) {
        return ops.stream().filter(o -> o.getSeq() == seq).findFirst().orElse(null);
    }

    /** 上一个报工点（没有时为 null，即首道报工点） */
    public static MfgProdOrderOperationDO previousReportPoint(List<MfgProdOrderOperationDO> ops, int seq) {
        MfgProdOrderOperationDO prev = null;
        for (MfgProdOrderOperationDO op : ops) {
            if (op.getSeq() >= seq) break;
            if (Boolean.TRUE.equals(op.getIsReportPoint())) prev = op;
        }
        return prev;
    }

    public static MfgProdOrderOperationDO nextReportPoint(List<MfgProdOrderOperationDO> ops, int seq) {
        for (MfgProdOrderOperationDO op : ops) {
            if (op.getSeq() > seq && Boolean.TRUE.equals(op.getIsReportPoint())) return op;
        }
        return null;
    }

    public static MfgProdOrderOperationDO lastReportPoint(List<MfgProdOrderOperationDO> ops) {
        MfgProdOrderOperationDO last = null;
        for (MfgProdOrderOperationDO op : ops) if (Boolean.TRUE.equals(op.getIsReportPoint())) last = op;
        return last;
    }

    /** 工序已投入 = 合格 + 待处理不良 + 报废 */
    public static BigDecimal input(MfgProdOrderOperationDO op) {
        return MfgSupport.nz(op.getGoodQty()).add(MfgSupport.nz(op.getDefectQty())).add(MfgSupport.nz(op.getScrapQty()));
    }

    /** 工序投入上限：首道报工点 = 订单数量 × (1 + 超产比例)；其他 = 上一报工点累计合格 */
    public BigDecimal inputLimit(MfgProdOrderDO o, List<MfgProdOrderOperationDO> ops, int seq) {
        MfgProdOrderOperationDO prev = previousReportPoint(ops, seq);
        if (prev != null) return MfgSupport.nz(prev.getGoodQty());
        return o.getQty().multiply(MfgSupport.onePlusPct(support.paramDecimal(ProductionModuleConfig.P_OVER_PRODUCE_PCT)))
                .setScale(4, java.math.RoundingMode.DOWN);
    }
}
