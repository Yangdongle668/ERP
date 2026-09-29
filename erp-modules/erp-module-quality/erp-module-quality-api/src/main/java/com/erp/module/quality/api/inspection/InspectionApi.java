package com.erp.module.quality.api.inspection;

import java.math.BigDecimal;
import java.util.List;

/**
 * 业务模块发起检验（需在调用方事务内调用）。IQC / FQC / 退货检验 / 复检由品质监听仓库事件自动生成，不经过本接口。
 */
public interface InspectionApi {

    /**
     * 出货检验（OQC）：出货通知装箱完成后调用，每行生成一张 OQC 检验单（同一通知行不重复生成）。
     * 判定结果以 {@link InspectionJudgedEvent}（inspectType = OQC，upstreamType = SHP_NOTICE）通知。
     *
     * @return 检验单 ID
     */
    List<Long> requestOqc(OqcRequest request);

    /** 出货通知取消 / 退回时调用：未判定的 OQC 检验单自动取消 */
    void cancelOqc(Long noticeId);

    /**
     * @param warehouseId 被检物所在仓库（成品仓，已拣货）
     */
    record OqcRequest(Long noticeId, String noticeNo, Long customerId, Long warehouseId, List<Line> lines) {
        /** @param qty 基本单位数量 */
        public record Line(Long noticeLineId, Long materialId, String batchNo, BigDecimal qty) {
        }
    }
}
