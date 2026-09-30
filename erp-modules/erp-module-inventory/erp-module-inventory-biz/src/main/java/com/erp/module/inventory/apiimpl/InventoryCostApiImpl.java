package com.erp.module.inventory.apiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.erp.common.util.Decimals;
import com.erp.module.inventory.api.cost.InventoryCostApi;
import com.erp.module.inventory.dal.dataobject.PeriodBalanceDO;
import com.erp.module.inventory.dal.dataobject.PeriodDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.mapper.PeriodBalanceMapper;
import com.erp.module.inventory.dal.mapper.PeriodMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.inventory.service.posting.PeriodGuard;
import com.erp.module.inventory.service.posting.PostingModels;
import com.erp.module.inventory.service.posting.StockPostingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 成本核算用库存数据（财务 12-07） */
@Service
public class InventoryCostApiImpl implements InventoryCostApi {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final StockTxnMapper txnMapper;
    private final PeriodMapper periodMapper;
    private final PeriodBalanceMapper balanceMapper;

    public InventoryCostApiImpl(StockTxnMapper txnMapper, PeriodMapper periodMapper, PeriodBalanceMapper balanceMapper) {
        this.txnMapper = txnMapper;
        this.periodMapper = periodMapper;
        this.balanceMapper = balanceMapper;
    }

    @Override
    public boolean isPeriodClosed(String period) {
        PeriodDO p = periodMapper.selectOne(new LambdaQueryWrapper<PeriodDO>().eq(PeriodDO::getPeriod, period));
        if (p != null) return PeriodGuard.CLOSED.equals(p.getPeriodStatus());
        // 早于系统启用期间：库存未启用，没有业务，视为已结账
        return periodMapper.selectList(new LambdaQueryWrapper<PeriodDO>().orderByAsc(PeriodDO::getPeriod).last("LIMIT 1")).stream()
                .findFirst().map(first -> period.compareTo(first.getPeriod()) < 0).orElse(false);
    }

    @Override
    public List<CostTxn> getPeriodTxns(String period) {
        return txnMapper.selectList(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getPeriod, period)
                        .ne(StockTxnDO::getDocType, PostingModels.DOC_TRANSFER).orderByAsc(StockTxnDO::getBizDate).orderByAsc(StockTxnDO::getId))
                .stream().map(InventoryCostApiImpl::txn).toList();
    }

    private static CostTxn txn(StockTxnDO t) {
        return new CostTxn(t.getId(), t.getDocType(), t.getBizType(), t.getDirection(), Boolean.TRUE.equals(t.getIsReversal()), t.getReversedTxnId(),
                t.getMaterialId(), t.getWarehouseId(), t.getQty(), t.getUnitCost(), t.getAmount(), t.getBizDate(), t.getSourceType(), t.getSourceId(),
                t.getSourceLineId(), t.getSourceNo(), t.getDocNo());
    }

    @Override
    public List<MaterialBalance> getOpeningBalances(String period) {
        String prev = YearMonth.parse(period, YM).minusMonths(1).format(YM);
        List<PeriodBalanceDO> prevRows = balanceMapper.selectList(new LambdaQueryWrapper<PeriodBalanceDO>().eq(PeriodBalanceDO::getPeriod, prev));
        if (!prevRows.isEmpty() && prevRows.stream().allMatch(b -> b.getAmount() != null)) {
            Map<Long, BigDecimal[]> sums = new HashMap<>();
            for (PeriodBalanceDO b : prevRows) {
                BigDecimal[] s = sums.computeIfAbsent(b.getMaterialId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                s[0] = s[0].add(b.getQty());
                s[1] = s[1].add(b.getAmount());
            }
            List<MaterialBalance> result = new ArrayList<>();
            sums.forEach((m, s) -> result.add(new MaterialBalance(m, s[0], s[1])));
            return result;
        }
        // 上期未计算成本：按期间之前的全部流水（含期初导入）汇总
        List<StockTxnDO> txns = txnMapper.selectList(new LambdaQueryWrapper<StockTxnDO>().ne(StockTxnDO::getDocType, PostingModels.DOC_TRANSFER)
                .and(w -> w.eq(StockTxnDO::getPeriod, StockPostingService.OPENING_PERIOD).or().lt(StockTxnDO::getPeriod, period)));
        Map<Long, BigDecimal[]> sums = new HashMap<>();
        for (StockTxnDO t : txns) {
            if (!t.getPeriod().equals(StockPostingService.OPENING_PERIOD) && t.getPeriod().compareTo(period) >= 0) continue;
            // [数量, 入库数量, 入库金额, 已计价出库金额, 未计价出库数量]
            BigDecimal[] s = sums.computeIfAbsent(t.getMaterialId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            boolean in = "IN".equals(t.getDirection());
            s[0] = in ? s[0].add(t.getQty()) : s[0].subtract(t.getQty());
            if (in) {
                s[1] = s[1].add(t.getQty());
                s[2] = s[2].add(t.getAmount() == null ? BigDecimal.ZERO : t.getAmount());
            } else if (t.getAmount() != null) {
                s[3] = s[3].add(t.getAmount());
            } else {
                s[4] = s[4].add(t.getQty());
            }
        }
        List<MaterialBalance> result = new ArrayList<>();
        sums.forEach((m, s) -> {
            BigDecimal avg = s[1].signum() == 0 ? BigDecimal.ZERO : s[2].divide(s[1], 6, RoundingMode.HALF_UP);
            BigDecimal amount = s[2].subtract(s[3]).subtract(s[4].multiply(avg));
            result.add(new MaterialBalance(m, s[0], Decimals.amount(amount)));
        });
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyCosts(String period, Map<Long, BigDecimal> unitCostByTxn) {
        for (Map.Entry<Long, BigDecimal> e : unitCostByTxn.entrySet()) {
            StockTxnDO t = txnMapper.selectById(e.getKey());
            if (t == null || !period.equals(t.getPeriod())) continue;
            BigDecimal unit = Decimals.price(e.getValue());
            txnMapper.update(null, new LambdaUpdateWrapper<StockTxnDO>().set(StockTxnDO::getUnitCost, unit)
                    .set(StockTxnDO::getAmount, Decimals.multiplyAmount(t.getQty(), unit)).eq(StockTxnDO::getId, t.getId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveClosingCosts(String period, Map<Long, BigDecimal> unitCostByMaterial) {
        for (PeriodBalanceDO b : balanceMapper.selectList(new LambdaQueryWrapper<PeriodBalanceDO>().eq(PeriodBalanceDO::getPeriod, period))) {
            BigDecimal unit = unitCostByMaterial.get(b.getMaterialId());
            if (unit == null) continue;
            balanceMapper.update(null, new LambdaUpdateWrapper<PeriodBalanceDO>().set(PeriodBalanceDO::getAvgCost, Decimals.price(unit))
                    .set(PeriodBalanceDO::getAmount, Decimals.multiplyAmount(b.getQty(), unit)).eq(PeriodBalanceDO::getId, b.getId()));
        }
    }
}
