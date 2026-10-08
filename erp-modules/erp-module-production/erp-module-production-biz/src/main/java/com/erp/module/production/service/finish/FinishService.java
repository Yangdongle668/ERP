package com.erp.module.production.service.finish;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockInConfirmedEvent;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.production.api.finish.ProductionFinishApi;
import com.erp.module.production.config.ProductionModuleConfig;
import com.erp.module.production.controller.vo.FinishVOs.FinishQuery;
import com.erp.module.production.controller.vo.FinishVOs.FinishReq;
import com.erp.module.production.controller.vo.FinishVOs.FinishRow;
import com.erp.module.production.dal.dataobject.MfgFinishDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.mapper.MfgFinishMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.service.FinishStatus;
import com.erp.module.production.service.MfgAction;
import com.erp.module.production.service.MfgStateMachines;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import com.erp.module.production.service.order.OrderProgressService;
import com.erp.module.production.service.order.ProdOrderService;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 完工入库（需求 09-05）：申请 → 仓库入库确认（免检直接合格）→ FQC 判定回写（{@link ProductionFinishApi}）→ 订单完工判断。
 */
@Service("mfgFinishService")
public class FinishService implements ProductionFinishApi {

    public static final String BIZ_TYPE = ProductionModuleConfig.FINISH;

    private final MfgFinishMapper mapper;
    private final MfgProdOrderMapper orderMapper;
    private final OrderProgressService progress;
    private final MfgSupport support;
    private final InventoryDocApi inventoryDocApi;

    public FinishService(MfgFinishMapper mapper, MfgProdOrderMapper orderMapper, OrderProgressService progress, MfgSupport support,
                         InventoryDocApi inventoryDocApi) {
        this.mapper = mapper;
        this.orderMapper = orderMapper;
        this.progress = progress;
        this.support = support;
        this.inventoryDocApi = inventoryDocApi;
    }

    public MfgFinishDO getOrThrow(Long id) {
        MfgFinishDO f = id == null ? null : mapper.selectById(id);
        if (f == null) throw new BizException(ProductionErrorCodes.FINISH_NOT_EXISTS);
        return f;
    }

    public PageResult<FinishRow> page(FinishQuery q) {
        LambdaQueryWrapper<MfgFinishDO> w = new LambdaQueryWrapper<MfgFinishDO>().eq(MfgFinishDO::getDeleted, false);
        if (q.getProdOrderId() != null) w.eq(MfgFinishDO::getProdOrderId, q.getProdOrderId());
        if (StringUtils.hasText(q.getProdOrderNo())) w.likeRight(MfgFinishDO::getSourceNo, q.getProdOrderNo().trim());
        if (StringUtils.hasText(q.getDocNo())) w.likeRight(MfgFinishDO::getDocNo, q.getDocNo().trim().toUpperCase());
        if (q.getMaterialId() != null) w.eq(MfgFinishDO::getMaterialId, q.getMaterialId());
        if (StringUtils.hasText(q.getStatuses())) w.in(MfgFinishDO::getFinishStatus, Arrays.asList(q.getStatuses().split(",")));
        if (q.getDateFrom() != null) w.ge(MfgFinishDO::getDocDate, q.getDateFrom());
        if (q.getDateTo() != null) w.le(MfgFinishDO::getDocDate, q.getDateTo());
        w.orderByDesc(MfgFinishDO::getId);
        IPage<MfgFinishDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        List<MfgFinishDO> list = p.getRecords();
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(MfgFinishDO::getMaterialId).toList());
        Map<Long, WarehouseDTO> whs = support.warehouses(list.stream().map(MfgFinishDO::getWarehouseId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(MfgFinishDO::getOwnerId).toList());
        return new PageResult<>(list.stream().map(f -> {
            MaterialDTO m = ms.get(f.getMaterialId());
            WarehouseDTO wh = whs.get(f.getWarehouseId());
            return new FinishRow(f.getId(), f.getDocNo(), f.getProdOrderId(), f.getSourceNo(), f.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), f.getQty(), f.getBatchNo(), Boolean.TRUE.equals(f.getFqcRequired()), f.getWarehouseId(),
                    wh == null ? null : wh.name(), f.getStockInNos(), f.getStockedQty(), f.getQualifiedQty(), f.getRejectedQty(), f.getFinishStatus(),
                    MfgSupport.name(users, f.getOwnerId()), f.getDocDate(), f.getRemark());
        }).toList(), p.getTotal());
    }

    /** 完工入库单打印数据（单行明细，与其他单据模板共用 lines 结构） */
    public Map<String, Object> printData(Long id) {
        MfgFinishDO f = getOrThrow(id);
        MfgProdOrderDO o = f.getProdOrderId() == null ? null : orderMapper.selectById(f.getProdOrderId());
        MaterialDTO m = support.material(f.getMaterialId());
        FinishStatus st = FinishStatus.valueOf(f.getFinishStatus());
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("docNo", f.getDocNo());
        data.put("docDate", f.getDocDate());
        data.put("status", st == FinishStatus.CANCELED ? "CANCELED" : st.docStatus().name());
        data.put("statusName", st.label());
        data.put("orgId", f.getOrgId());
        data.put("prodOrderNo", java.util.Objects.toString(f.getSourceNo(), ""));
        data.put("salesOrderNo", o == null ? "" : java.util.Objects.toString(o.getSalesOrderNo(), ""));
        data.put("prodQty", o == null ? null : o.getQty());
        data.put("warehouseName", java.util.Objects.toString(support.warehouseName(f.getWarehouseId()), ""));
        data.put("fqcRequired", Boolean.TRUE.equals(f.getFqcRequired()) ? "是" : "否");
        data.put("stockInNos", java.util.Objects.toString(f.getStockInNos(), ""));
        data.put("remark", java.util.Objects.toString(f.getRemark(), ""));
        data.put("ownerName", java.util.Objects.toString(support.userName(f.getOwnerId()), ""));
        data.put("createdByName", java.util.Objects.toString(support.userName(f.getCreatedBy()), ""));
        Long dept = f.getDeptId() != null ? f.getDeptId() : o == null ? null : o.getDeptId();
        data.put("deptName", dept == null ? "" : java.util.Objects.toString(support.deptName(dept), ""));
        data.put("auditByName", "");
        Map<String, Object> line = new java.util.LinkedHashMap<>();
        line.put("lineNo", 1);
        line.put("materialCode", m == null ? "" : m.code());
        line.put("materialName", m == null ? "" : m.name());
        line.put("materialSpec", m == null ? "" : java.util.Objects.toString(m.spec(), ""));
        line.put("uom", m == null ? "" : m.baseUom());
        line.put("qty", f.getQty());
        line.put("batchNo", java.util.Objects.toString(f.getBatchNo(), ""));
        line.put("serialNos", java.util.Objects.toString(f.getSerialNos(), ""));
        data.put("lines", List.of(line));
        data.put("totalQty", f.getQty());
        return data;
    }

    /** 申请完工入库（FN-R01、R02）：数量 ≤ 完工 − 已申请；需 FQC 入待检仓，否则入物料默认仓 */
    @Transactional(rollbackFor = Exception.class)
    public Long finish(Long prodOrderId, FinishReq req) {
        MfgProdOrderDO o = progress.getOrThrow(prodOrderId);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        ProdStatus s = OrderProgressService.status(o);
        if (s == ProdStatus.SUSPENDED) throw new BizException(ProductionErrorCodes.ORDER_SUSPENDED);
        if (!ProdStatus.ACTIVE.contains(s)) throw BizException.of(ProductionErrorCodes.ORDER_NOT_RUNNING, s.label());
        if (ProdOrderService.DISASSEMBLY.equals(o.getOrderType())) throw new BizException(ProductionErrorCodes.FINISH_DISASSEMBLY);
        BigDecimal available = MfgSupport.max0(o.getCompletedQty().subtract(o.getFinishedRequestQty()));
        if (req.qty() == null || req.qty().signum() <= 0 || req.qty().compareTo(available) > 0) {
            throw BizException.of(ProductionErrorCodes.FINISH_OVER, MfgSupport.plain(available));
        }
        MaterialDTO m = support.material(o.getMaterialId());
        List<String> serials = req.serialNos() == null ? List.of() : req.serialNos().stream().filter(StringUtils::hasText).map(String::trim).toList();
        if (m.tracking() == Tracking.SERIAL && BigDecimal.valueOf(serials.size()).compareTo(req.qty()) != 0) {
            throw BizException.of(ProductionErrorCodes.FINISH_SERIAL_REQUIRED, m.code(), MfgSupport.plain(req.qty()));
        }
        boolean fqc = support.materialApi().getQualityAttr(m.id()).fqcRequired();
        WarehouseDTO wh = support.warehouseApi().getDefaultWarehouse(m.id(), fqc ? WarehouseType.QC : null);
        MfgFinishDO f = new MfgFinishDO();
        f.setDocNo(support.nextNo(BIZ_TYPE));
        f.setDocDate(LocalDate.now());
        f.setFinishStatus(FinishStatus.SUBMITTED.name());
        f.setStatus(FinishStatus.SUBMITTED.docStatus());
        f.setProdOrderId(o.getId());
        f.setSourceType(ProductionModuleConfig.PROD_ORDER);
        f.setSourceId(o.getId());
        f.setSourceNo(o.getDocNo());
        f.setMaterialId(m.id());
        f.setQty(req.qty());
        f.setBatchNo(StringUtils.hasText(req.batchNo()) ? req.batchNo().trim() : o.getBatchNo());
        f.setFqcRequired(fqc);
        f.setWarehouseId(wh.id());
        f.setSerialNos(serials.isEmpty() ? null : String.join(",", serials));
        f.setStockedQty(BigDecimal.ZERO);
        f.setQualifiedQty(BigDecimal.ZERO);
        f.setRejectedQty(BigDecimal.ZERO);
        f.setRemark(MfgSupport.trim(req.remark()));
        support.fillOwner(f, null, o.getDeptId());
        f.setDeptId(o.getDeptId());
        mapper.insert(f);
        o.setFinishedRequestQty(o.getFinishedRequestQty().add(req.qty()));
        orderMapper.updateByIdOrFail(o);
        support.log(ProductionModuleConfig.PROD_ORDER, o.getId(), o.getDocNo(), "FINISH", "申请完工入库", o.getProdStatus(), o.getProdStatus(),
                f.getDocNo() + " × " + MfgSupport.plain(req.qty()));
        List<Long> ids = inventoryDocApi.createStockIn(new StockInRequest(StockInType.PRODUCTION_IN, new SourceRef(SOURCE_TYPE, f.getId(), f.getDocNo()),
                wh.id(), LocalDate.now(), null, null, List.of(new StockInRequest.Line(f.getId(), m.id(), m.baseUom(), req.qty(), f.getBatchNo(), null,
                LocalDate.now(), null, serials.isEmpty() ? null : serials))));
        MfgFinishDO fresh = getOrThrow(f.getId());
        fresh.setStockInIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        mapper.updateByIdOrFail(fresh);
        return f.getId();
    }

    /** 取消（FN-R05）：入库单未确认时作废入库单，已申请数量扣回 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        MfgFinishDO f = getOrThrow(id);
        DataScopes.check(f.getOrgId(), f.getDeptId(), f.getOwnerId(), "完工入库申请");
        if (!FinishStatus.SUBMITTED.name().equals(f.getFinishStatus())) throw new BizException(ProductionErrorCodes.FINISH_STOCKED);
        try {
            inventoryDocApi.cancelBySource(SOURCE_TYPE, id);
        } catch (BizException e) {
            throw new BizException(ProductionErrorCodes.FINISH_STOCKED);
        }
        doCancel(f, null);
    }

    private void doCancel(MfgFinishDO f, String reason) {
        fire(f, MfgAction.CANCEL, reason);
        MfgProdOrderDO o = progress.getOrThrow(f.getProdOrderId());
        o.setFinishedRequestQty(MfgSupport.max0(o.getFinishedRequestQty().subtract(f.getQty())));
        orderMapper.updateByIdOrFail(o);
    }

    private void fire(MfgFinishDO f, MfgAction action, String reason) {
        FinishStatus from = FinishStatus.valueOf(f.getFinishStatus());
        FinishStatus to = MfgStateMachines.FINISH.fire(from, action);
        f.setFinishStatus(to.name());
        f.setStatus(to.docStatus());
        mapper.updateByIdOrFail(f);
        support.log(BIZ_TYPE, f.getId(), f.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 仓库回写 ====================

    /** 入库确认（FN-R03）：回写已入库；免检时合格 = 入库，订单合格入库增加；完工判断 */
    @EventListener
    public void onStockIn(StockInConfirmedEvent e) {
        if (e.getSource() == null || !SOURCE_TYPE.equals(e.getSource().sourceType())) return;
        MfgFinishDO f = mapper.selectById(e.getSource().sourceId());
        if (f == null || !FinishStatus.SUBMITTED.name().equals(f.getFinishStatus())) return;
        BigDecimal qty = MfgSupport.sum(e.getLines().stream().map(StockInConfirmedEvent.Line::baseQty).toList());
        f.setStockedQty(f.getStockedQty().add(qty));
        f.setStockInNos(append(f.getStockInNos(), e.getStockInNo()));
        if (e.getWarehouseId() != null) f.setWarehouseId(e.getWarehouseId());
        MfgProdOrderDO o = progress.getOrThrow(f.getProdOrderId());
        o.setStockedQty(o.getStockedQty().add(qty));
        boolean fqc = Boolean.TRUE.equals(f.getFqcRequired()) && "QC".equals(e.getWarehouseType());
        if (!fqc) {
            f.setQualifiedQty(f.getQualifiedQty().add(qty));
            o.setQualifiedStockedQty(o.getQualifiedStockedQty().add(qty));
        }
        fire(f, MfgAction.STOCK, e.getStockInNo());
        if (!fqc) fire(f, MfgAction.JUDGE, "免检");
        orderMapper.updateByIdOrFail(o);
        progress.checkCompletion(o);
        progress.publishProgress(o);
    }

    @EventListener
    public void onStockDoc(StockDocEvent e) {
        if (!"STOCK_IN".equals(e.getDocType()) || e.getSource() == null || !SOURCE_TYPE.equals(e.getSource().sourceType())) return;
        MfgFinishDO f = mapper.selectById(e.getSource().sourceId());
        if (f == null) return;
        if (e.getKind() == StockDocEvent.Kind.IN_REVERSING && FinishStatus.JUDGED.name().equals(f.getFinishStatus())
                && Boolean.TRUE.equals(f.getFqcRequired()) && f.getQualifiedQty().add(f.getRejectedQty()).signum() > 0) {
            throw new BizException(ProductionErrorCodes.FINISH_STOCKED);
        }
        if (e.getKind() == StockDocEvent.Kind.IN_REVERSED && !FinishStatus.SUBMITTED.name().equals(f.getFinishStatus())
                && !FinishStatus.CANCELED.name().equals(f.getFinishStatus())) {
            MfgProdOrderDO o = progress.getOrThrow(f.getProdOrderId());
            o.setStockedQty(MfgSupport.max0(o.getStockedQty().subtract(f.getStockedQty())));
            if (!Boolean.TRUE.equals(f.getFqcRequired())) o.setQualifiedStockedQty(MfgSupport.max0(o.getQualifiedStockedQty().subtract(f.getQualifiedQty())));
            if (FinishStatus.JUDGED.name().equals(f.getFinishStatus())) fire(f, MfgAction.UNJUDGE, null);
            f.setStockedQty(BigDecimal.ZERO);
            if (!Boolean.TRUE.equals(f.getFqcRequired())) f.setQualifiedQty(BigDecimal.ZERO);
            fire(f, MfgAction.UNSTOCK, "入库单 " + e.getDocNo() + " 反确认");
            orderMapper.updateByIdOrFail(o);
            progress.checkCompletion(o);
            progress.publishProgress(o);
        } else if (e.getKind() == StockDocEvent.Kind.REJECTED && FinishStatus.SUBMITTED.name().equals(f.getFinishStatus())) {
            doCancel(f, "仓库退回：" + (e.getReason() == null ? "" : e.getReason()));
            support.message(List.of(f.getOwnerId()), "完工入库被仓库退回", "完工入库申请 " + f.getDocNo() + " 被仓库退回：" + (e.getReason() == null ? "" : e.getReason()),
                    "/production/finish");
        }
    }

    /** FQC 判定回写（FN-R04）：合格累加到订单合格入库；不合格记为 FQC 不良 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onFqcJudged(Long finishId, BigDecimal qualifiedQty, BigDecimal rejectedQty) {
        MfgFinishDO f = getOrThrow(finishId);
        BigDecimal q = MfgSupport.nz(qualifiedQty);
        BigDecimal r = MfgSupport.nz(rejectedQty);
        f.setQualifiedQty(MfgSupport.max0(f.getQualifiedQty().add(q)));
        f.setRejectedQty(MfgSupport.max0(f.getRejectedQty().add(r)));
        MfgProdOrderDO o = progress.getOrThrow(f.getProdOrderId());
        o.setQualifiedStockedQty(MfgSupport.max0(o.getQualifiedStockedQty().add(q)));
        o.setFqcRejectedQty(MfgSupport.max0(o.getFqcRejectedQty().add(r)));
        boolean judged = f.getQualifiedQty().add(f.getRejectedQty()).compareTo(f.getStockedQty()) >= 0 && f.getStockedQty().signum() > 0;
        FinishStatus s = FinishStatus.valueOf(f.getFinishStatus());
        if (judged && s == FinishStatus.STOCKED) fire(f, MfgAction.JUDGE, "合格 " + MfgSupport.plain(q) + "，不合格 " + MfgSupport.plain(r));
        else if (!judged && s == FinishStatus.JUDGED) fire(f, MfgAction.UNJUDGE, null);
        else mapper.updateByIdOrFail(f);
        orderMapper.updateByIdOrFail(o);
        progress.checkCompletion(o);
        progress.publishProgress(o);
    }

    public List<MfgFinishDO> listByOrder(Long prodOrderId) {
        return mapper.selectList(new LambdaQueryWrapper<MfgFinishDO>().eq(MfgFinishDO::getProdOrderId, prodOrderId).orderByDesc(MfgFinishDO::getId));
    }

    static String append(String list, String no) {
        if (no == null) return list;
        return list == null || list.isBlank() ? no : list + "," + no;
    }

    static Map<Long, MfgFinishDO> byId(List<MfgFinishDO> list) {
        return list.stream().collect(Collectors.toMap(MfgFinishDO::getId, Function.identity()));
    }
}
