package com.erp.module.quality.api.inspection;

import java.util.List;

/** 检验单查询（资材、生产、出货、销售使用） */
public interface InspectionQueryApi {

    /**
     * 上游业务单据的检验单（不含已取消）。
     *
     * @param upstreamType 如 PUR_RECEIPT、MFG_FINISH、SAL_RETURN、SHP_NOTICE、MFG_REPORT
     */
    List<InspectionDTO> getByBiz(String upstreamType, Long upstreamId);

    /** 出货通知的 OQC 是否全部通过（存在 OQC 且全部已判定、无拒收）；不需要 OQC 时由出货模块自行判断 */
    boolean isOqcPassed(Long noticeId);

    /**
     * 首件检验（QC-INS-R09）：参数 qc.ipqc.first-article 打开且该生产订单没有判定合格（或特采）的首件检验时抛出
     * “首件检验未通过，不能报工”。参数关闭时直接返回。
     */
    void checkFirstArticle(Long prodOrderId);
}
