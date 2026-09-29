package com.erp.module.shipping.service;

import com.erp.module.shipping.config.ShippingModuleConfig;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.service.shipment.ShipmentService;
import com.erp.module.system.api.job.ErpJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 出货定时任务 */
@Component
public class ShippingJobs {

    /** ETA 已过 N 天仍未到港时提醒船务（SHP-LOG-R03） */
    static final int ETA_OVERDUE_DAYS = 3;

    private final ShipmentService shipmentService;
    private final ShpSupport support;

    public ShippingJobs(ShipmentService shipmentService, ShpSupport support) {
        this.shipmentService = shipmentService;
        this.support = support;
    }

    @ErpJob(code = "SHP_ETA_OVERDUE", name = "出货 ETA 超期未到港提醒", cron = "0 20 8 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public String etaOverdue() {
        List<ShpShipmentDO> list = shipmentService.etaOverdueList(LocalDate.now().minusDays(ETA_OVERDUE_DAYS));
        for (ShpShipmentDO s : list) {
            support.message(List.of(s.getOwnerId()), "出货单 " + s.getDocNo() + " ETA 已过 " + ETA_OVERDUE_DAYS + " 天仍未到港",
                    "ETA " + s.getEta() + "，提单号 " + (s.getBlNo() == null ? "-" : s.getBlNo()) + "，请跟进货代", "/shipping/shipment/" + s.getId());
            shipmentService.markEtaReminded(s);
        }
        return "提醒 " + list.size() + " 张（" + ShippingModuleConfig.MODULE + "）";
    }
}
