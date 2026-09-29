package com.erp.module.pmc.service.demand;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.engineering.api.material.MaterialPurchaseAttr;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.controller.vo.DemandVOs.KitLine;
import com.erp.module.pmc.controller.vo.DemandVOs.KitResult;
import com.erp.module.pmc.controller.vo.DemandVOs.ReplyLine;
import com.erp.module.pmc.controller.vo.DemandVOs.ReplyRow;
import com.erp.module.pmc.dal.dataobject.PmcDemandDO;
import com.erp.module.pmc.dal.mapper.PmcDemandMapper;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PlanningData.Supply;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.sales.api.order.SalesOrderApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 交期回复（需求 06-01 4.2、R02～R05） */
@Service("pmcDeliveryReplyService")
public class DeliveryReplyService {

    private final PmcDemandMapper mapper;
    private final PmcSupport support;
    private final PlanningData data;
    private final SalesOrderApi salesOrderApi;

    public DeliveryReplyService(PmcDemandMapper mapper, PmcSupport support, PlanningData data, SalesOrderApi salesOrderApi) {
        this.mapper = mapper;
        this.support = support;
        this.data = data;
        this.salesOrderApi = salesOrderApi;
    }

    /** 待回复：已审核、没有承诺交期，或要求交期 / 数量变更后需要重新回复的订单行 */
    public List<ReplyRow> pending(Long customerId, Long materialId) {
        List<PmcDemandDO> list = mapper.selectList(new LambdaQueryWrapper<PmcDemandDO>().eq(PmcDemandDO::getDemandType, DemandService.SALES_ORDER)
                .eq(PmcDemandDO::getDemandStatus, DemandService.OPEN)
                .in(PmcDemandDO::getReplyStatus, List.of(DemandService.REPLY_PENDING, DemandService.REPLY_AGAIN))
                .eq(customerId != null, PmcDemandDO::getCustomerId, customerId).eq(materialId != null, PmcDemandDO::getMaterialId, materialId)
                .orderByAsc(PmcDemandDO::getCustomerDate).orderByAsc(PmcDemandDO::getSourceNo).orderByAsc(PmcDemandDO::getSourceLineNo));
        if (list.isEmpty()) return List.of();
        Set<Long> mids = new HashSet<>(list.stream().map(PmcDemandDO::getMaterialId).toList());
        Map<Long, MaterialDTO> ms = support.materials(mids);
        Map<Long, BigDecimal> wip = data.wip(mids);
        Map<Long, BigDecimal> covered = coverage(mids);
        Map<Long, CustomerDTO> cs = data.customers(list.stream().map(PmcDemandDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(PmcDemandDO::getSalesOwnerId).toList());
        Map<Long, BigDecimal> avail = data.available(mids);
        Map<String, KitResult> kitCache = new HashMap<>();
        List<ReplyRow> out = new ArrayList<>();
        for (PmcDemandDO d : list) {
            MaterialDTO m = ms.get(d.getMaterialId());
            BigDecimal free = covered.getOrDefault(d.getId(), BigDecimal.ZERO);
            LocalDate suggested;
            String basis;
            if (free.compareTo(d.getOpenQty()) >= 0) {
                suggested = LocalDate.now().plusDays(1);
                basis = "库存可满足";
            } else {
                KitResult k = kitCache.computeIfAbsent(d.getMaterialId() + "|" + d.getOpenQty(), x -> kit(d.getMaterialId(), d.getOpenQty()));
                suggested = k.suggestedDate();
                basis = "物料齐套 " + k.kitDate() + " + 生产提前期 " + k.leadTimeDays() + " 天";
            }
            out.add(new ReplyRow(d.getId(), d.getSourceId(), d.getSourceNo(), d.getSourceLineNo(), d.getSourceLineId(), d.getCustomerId(),
                    PlanningData.customerName(cs, d.getCustomerId()), d.getSalesOwnerId(), PmcSupport.name(users, d.getSalesOwnerId()), d.getMaterialId(),
                    m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(), d.getQty(),
                    d.getOpenQty(), d.getCustomerDate(), d.getPromisedDate(), avail.getOrDefault(d.getMaterialId(), BigDecimal.ZERO),
                    wip.getOrDefault(d.getMaterialId(), BigDecimal.ZERO), suggested, basis, DemandService.REPLY_AGAIN.equals(d.getReplyStatus())));
        }
        return out;
    }

    /**
     * 可用库存按需求日期先后分配给该物料的全部未满足需求（未被更早需求占用的部分），返回 需求 ID → 可分配数量。
     */
    private Map<Long, BigDecimal> coverage(Set<Long> materialIds) {
        Map<Long, BigDecimal> left = new HashMap<>(data.available(materialIds));
        Map<Long, BigDecimal> out = new HashMap<>();
        for (PmcDemandDO d : mapper.selectList(new LambdaQueryWrapper<PmcDemandDO>().in(PmcDemandDO::getMaterialId, materialIds)
                .eq(PmcDemandDO::getDemandStatus, DemandService.OPEN).ne(PmcDemandDO::getDemandType, DemandService.FORECAST)
                .orderByAsc(PmcDemandDO::getRequiredDate).orderByAsc(PmcDemandDO::getPriority).orderByAsc(PmcDemandDO::getId))) {
            BigDecimal have = left.getOrDefault(d.getMaterialId(), BigDecimal.ZERO);
            BigDecimal take = PmcSupport.min(have, d.getOpenQty());
            out.put(d.getId(), take);
            left.put(d.getMaterialId(), have.subtract(take));
        }
        return out;
    }

    /**
     * 单层 BOM 齐套分析：每个子件需求 = 数量 × 单位用量 × (1 + 损耗)；可用库存够 → 今天；否则按在途（日期先后）覆盖 → 覆盖那笔的日期；
     * 在途也不够 → 今天 + 采购提前期。最晚齐套日期 + 产品生产提前期 + 1 天 = 建议交期（PMC-DMD-R03）。
     */
    public KitResult kit(Long materialId, BigDecimal qty) {
        LocalDate today = LocalDate.now();
        MaterialPlanAttr pa = support.materialApi().getPlanAttr(materialId);
        int lead = pa == null ? 0 : pa.leadTimeDays();
        List<KitLine> lines = new ArrayList<>();
        LocalDate kitDate = today;
        BomDTO bom = data.bomApi().getDefaultBom(materialId, today).orElse(null);
        if (bom != null) {
            Map<Long, BigDecimal> need = new HashMap<>();
            explode(bom, qty, need, 0);
            Map<Long, MaterialDTO> ms = support.materials(need.keySet());
            Map<Long, BigDecimal> avail = data.available(need.keySet());
            Map<Long, List<Supply>> sup = data.supplies(need.keySet());
            Map<Long, MaterialPurchaseAttr> pur = support.purchaseAttrs(need.keySet());
            for (Map.Entry<Long, BigDecimal> e : need.entrySet()) {
                MaterialDTO c = ms.get(e.getKey());
                BigDecimal req = support.roundUp(e.getValue(), c == null ? null : c.baseUom());
                BigDecimal have = avail.getOrDefault(e.getKey(), BigDecimal.ZERO);
                List<Supply> ss = sup.getOrDefault(e.getKey(), List.of());
                BigDecimal transit = PmcSupport.sum(ss.stream().map(Supply::qty).toList());
                LocalDate date;
                String basis;
                if (have.compareTo(req) >= 0) {
                    date = today;
                    basis = "库存充足";
                } else {
                    BigDecimal gap = req.subtract(have);
                    date = null;
                    basis = null;
                    for (Supply s : ss) {
                        gap = gap.subtract(s.qty());
                        if (gap.signum() <= 0) {
                            date = s.date().isBefore(today) ? today : s.date();
                            basis = "在途 " + s.docNo();
                            break;
                        }
                    }
                    if (date == null) {
                        MaterialPurchaseAttr a = pur.get(e.getKey());
                        MaterialPlanAttr cp = support.materialApi().getPlanAttr(e.getKey());
                        int days = a != null && a.leadTimeDays() > 0 ? a.leadTimeDays() : cp == null ? 0 : cp.leadTimeDays();
                        date = today.plusDays(days);
                        basis = "缺 " + PmcSupport.plain(gap) + "，按采购提前期 " + days + " 天";
                    }
                }
                if (date.isAfter(kitDate)) kitDate = date;
                lines.add(new KitLine(e.getKey(), c == null ? null : c.code(), c == null ? null : c.name(), c == null ? null : c.baseUom(), req, have,
                        transit, date, basis));
            }
        }
        LocalDate start = kitDate.isBefore(today) ? today : kitDate;
        return new KitResult(materialId, qty, kitDate, lead, start.plusDays(lead + 1L), lines);
    }

    /** 单层展开（虚拟件透过）：子件需求 = 数量 × 用量 ÷ 基数 × (1 + 损耗) */
    private void explode(BomDTO bom, BigDecimal qty, Map<Long, BigDecimal> need, int depth) {
        if (depth > 10) return;
        BigDecimal base = bom.baseQty() == null || bom.baseQty().signum() == 0 ? BigDecimal.ONE : bom.baseQty();
        Map<Long, MaterialDTO> ms = support.materials(bom.lines().stream().map(BomDTO.Line::componentId).toList());
        for (BomDTO.Line l : bom.lines()) {
            BigDecimal q = qty.multiply(l.qtyPer()).divide(base, 8, RoundingMode.HALF_UP).multiply(BigDecimal.ONE.add(PmcSupport.nz(l.scrapRate())));
            MaterialDTO c = ms.get(l.componentId());
            if (c != null && c.materialType() == MaterialType.PHANTOM) {
                BomDTO sub = data.bomApi().getDefaultBom(c.id(), LocalDate.now()).orElse(null);
                if (sub != null) {
                    explode(sub, q, need, depth + 1);
                    continue;
                }
            }
            need.merge(l.componentId(), q, BigDecimal::add);
        }
    }

    /** 保存回复：写回销售订单承诺交期（R04），需求日期改为承诺交期 */
    @Transactional(rollbackFor = Exception.class)
    public int reply(List<ReplyLine> lines) {
        if (lines == null || lines.isEmpty()) return 0;
        LocalDate today = LocalDate.now();
        int n = 0;
        for (ReplyLine l : lines) {
            if (l.promisedDate() == null || l.promisedDate().isBefore(today)) throw new BizException(PmcErrorCodes.PROMISED_DATE_PAST);
            PmcDemandDO d = mapper.selectById(l.demandId());
            if (d == null || !DemandService.SALES_ORDER.equals(d.getDemandType())) throw new BizException(PmcErrorCodes.DEMAND_NOT_EXISTS);
            d.setReplyStatus(DemandService.REPLY_DONE);
            d.setRepliedQty(d.getQty());
            d.setRepliedCustomerDate(d.getCustomerDate());
            d.setReplyRemark(PmcSupport.trim(l.remark()));
            d.setRepliedBy(support.currentUser());
            d.setRepliedAt(LocalDateTime.now());
            d.setPromisedDate(l.promisedDate());
            d.setRequiredDate(l.promisedDate());
            mapper.updateByIdOrFail(d);
            salesOrderApi.updatePromisedDate(d.getSourceLineId(), l.promisedDate(), PmcSupport.trim(l.remark()));
            n++;
        }
        return n;
    }
}
