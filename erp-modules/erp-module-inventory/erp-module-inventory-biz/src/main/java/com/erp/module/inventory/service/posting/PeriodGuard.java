package com.erp.module.inventory.service.posting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.dal.dataobject.PeriodDO;
import com.erp.module.inventory.dal.mapper.PeriodMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 过账前的期间校验（INV-PST-R01、INV-PRD-R01～R03） */
@Component
public class PeriodGuard {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final PeriodMapper periodMapper;

    public PeriodGuard(PeriodMapper periodMapper) {
        this.periodMapper = periodMapper;
    }

    public static String periodOf(LocalDate date) {
        return date.format(YM);
    }

    public PeriodDO openingPeriod() {
        return periodMapper.selectOne(new LambdaQueryWrapper<PeriodDO>().eq(PeriodDO::getIsOpening, true));
    }

    /**
     * @param opening 期初入库：只要求已设置启用期间且期初未完成
     */
    public void checkPostable(LocalDate bizDate, boolean opening) {
        PeriodDO start = openingPeriod();
        if (start == null) throw new BizException(InventoryErrorCodes.OPENING_NOT_COMPLETED);
        if (opening) {
            if (Boolean.TRUE.equals(start.getOpeningCompleted())) throw new BizException(InventoryErrorCodes.OPENING_COMPLETED);
            return;
        }
        if (!Boolean.TRUE.equals(start.getOpeningCompleted())) throw new BizException(InventoryErrorCodes.OPENING_NOT_COMPLETED);
        if (bizDate.isBefore(start.getStartDate())) throw BizException.of(InventoryErrorCodes.BEFORE_OPENING_PERIOD, start.getPeriod());
        checkNotClosed(bizDate);
    }

    /** 该日期所在期间未结账（反确认、冲销时也要校验） */
    public void checkNotClosed(LocalDate bizDate) {
        String p = periodOf(bizDate);
        PeriodDO row = periodMapper.selectOne(new LambdaQueryWrapper<PeriodDO>().eq(PeriodDO::getPeriod, p));
        if (row != null && CLOSED.equals(row.getPeriodStatus())) throw BizException.of(InventoryErrorCodes.PERIOD_CLOSED, p);
    }
}
