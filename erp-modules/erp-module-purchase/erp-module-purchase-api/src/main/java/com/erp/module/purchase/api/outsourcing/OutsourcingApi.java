package com.erp.module.purchase.api.outsourcing;

import java.util.List;

/** 委外加工（PMC 调用）。 */
public interface OutsourcingApi {

    /**
     * MRP 委外建议生成草稿委外单（每条建议一张），按默认 BOM 生成用料、按采购价格表取加工费。
     *
     * @return 生成的委外单 ID
     */
    List<Long> createFromMrp(List<MrpOutsourceSuggestion> suggestions);
}
