package com.erp.module.inventory.service.doc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialStockAttr;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.inventory.dal.dataobject.PeriodBalanceDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.PeriodBalanceMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.inventory.service.WarehouseService;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 出入库单据共用的工具：编号、日志、物料/仓库/用户名称、单位换算、参考单价、序列号文本 */
@Component
public class DocSupport {

    public static final Set<WarehouseType> AVAILABLE = EnumSet.of(WarehouseType.RAW, WarehouseType.SEMI, WarehouseType.FG, WarehouseType.FPC,
            WarehouseType.ELEC, WarehouseType.PKG, WarehouseType.AUX);

    private final CodeRuleApi codeRuleApi;
    private final DocLogApi docLogApi;
    private final UserApi userApi;
    private final MaterialApi materialApi;
    private final WarehouseService warehouseService;
    private final StockTxnMapper txnMapper;
    private final PeriodBalanceMapper periodBalanceMapper;

    public DocSupport(CodeRuleApi codeRuleApi, DocLogApi docLogApi, UserApi userApi, MaterialApi materialApi, WarehouseService warehouseService,
                      StockTxnMapper txnMapper, PeriodBalanceMapper periodBalanceMapper) {
        this.codeRuleApi = codeRuleApi;
        this.docLogApi = docLogApi;
        this.userApi = userApi;
        this.materialApi = materialApi;
        this.warehouseService = warehouseService;
        this.txnMapper = txnMapper;
        this.periodBalanceMapper = periodBalanceMapper;
    }

    public String nextNo(String rule) {
        return codeRuleApi.nextCode(rule);
    }

    public void log(String bizType, Long id, String no, InvDocAction action, String label, DocStatus from, DocStatus to, String reason) {
        docLogApi.record(bizType, id, no, action.name(), label == null ? action.label() : label, from == null ? null : from.name(),
                to == null ? null : to.name(), reason);
    }

    public void log(String bizType, Long id, String no, String action, String label, String from, String to, String reason) {
        docLogApi.record(bizType, id, no, action, label, from, to, reason);
    }

    public Map<Long, MaterialDTO> materials(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Map.of();
        return materialApi.getMaterials(set).stream().collect(Collectors.toMap(MaterialDTO::id, Function.identity()));
    }

    public MaterialStockAttr stockAttr(Long materialId) {
        return materialApi.getStockAttr(materialId);
    }

    public Map<Long, UserDTO> users(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : userApi.list(set);
    }

    public static String name(Map<Long, UserDTO> users, Long id) {
        return id == null || !users.containsKey(id) ? null : users.get(id).realName();
    }

    public WarehouseService warehouses() {
        return warehouseService;
    }

    public Long currentUser() {
        return SecurityUtils.getLoginUserIdOrNull();
    }

    /** 业务单位换算为基本单位 */
    public BigDecimal toBase(MaterialDTO m, BigDecimal qty, String uom) {
        if (qty == null) return null;
        if (!StringUtils.hasText(uom) || uom.equalsIgnoreCase(m.baseUom())) return qty;
        try {
            return materialApi.convertToBase(m.id(), qty, uom);
        } catch (BizException e) {
            throw BizException.of(InventoryErrorCodes.UOM_NO_CONVERSION, m.code(), uom);
        }
    }

    /** 参考单价：最近一个已结账期间的期末加权平均单价，没有时取最近一次入库单价（需求 08-02 第 5 节） */
    public BigDecimal refCost(Long materialId) {
        PeriodBalanceDO pb = periodBalanceMapper.selectOne(new LambdaQueryWrapper<PeriodBalanceDO>().eq(PeriodBalanceDO::getMaterialId, materialId)
                .isNotNull(PeriodBalanceDO::getAvgCost).orderByDesc(PeriodBalanceDO::getPeriod).last("LIMIT 1"));
        if (pb != null) return pb.getAvgCost();
        StockTxnDO t = txnMapper.selectOne(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getMaterialId, materialId)
                .eq(StockTxnDO::getDirection, "IN").isNotNull(StockTxnDO::getUnitCost).orderByDesc(StockTxnDO::getBizDate)
                .orderByDesc(StockTxnDO::getId).last("LIMIT 1"));
        return t == null ? null : t.getUnitCost();
    }

    public static List<String> serials(String text) {
        if (!StringUtils.hasText(text)) return List.of();
        return Arrays.stream(text.split("[,，\\s]+")).map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }

    public static String serialText(List<String> serials) {
        if (serials == null || serials.isEmpty()) return null;
        return String.join(",", serials.stream().map(String::trim).filter(StringUtils::hasText).distinct().toList());
    }

    public static String typeNames(Set<WarehouseType> types) {
        if (types.containsAll(AVAILABLE)) {
            String others = types.stream().filter(t -> !AVAILABLE.contains(t)).map(WarehouseType::label).collect(Collectors.joining("、"));
            return others.isEmpty() ? "可用仓" : "可用仓、" + others;
        }
        return types.stream().map(WarehouseType::label).collect(Collectors.joining("、"));
    }

    public static Set<WarehouseType> with(Set<WarehouseType> base, WarehouseType... more) {
        EnumSet<WarehouseType> s = EnumSet.copyOf(base);
        s.addAll(Arrays.asList(more));
        return s;
    }

    /** 单据日期：不能晚于今天，不能早于来源单据日期（INV-IN-R11） */
    public static void checkDate(LocalDate date, LocalDate sourceDate, String docName) {
        if (date.isAfter(LocalDate.now())) throw new BizException(InventoryErrorCodes.DOC_DATE_FUTURE);
        if (sourceDate != null && date.isBefore(sourceDate)) throw BizException.of(InventoryErrorCodes.DOC_DATE_BEFORE_SOURCE, docName, sourceDate);
    }

    public static BigDecimal amount(BigDecimal qty, BigDecimal price) {
        return price == null || qty == null ? null : Decimals.multiplyAmount(qty, price);
    }

    public WarehouseDO warehouse(Long id) {
        return warehouseService.getOrThrow(id);
    }

    public static String summary(List<String> codes, Map<Long, MaterialDTO> materials, List<Long> materialIds) {
        if (materialIds.isEmpty()) return "";
        MaterialDTO first = materials.get(materialIds.get(0));
        String head = first == null ? "" : first.code() + " " + first.name();
        long n = materialIds.stream().distinct().count();
        return n > 1 ? head + " 等 " + n + " 项" : head;
    }
}
