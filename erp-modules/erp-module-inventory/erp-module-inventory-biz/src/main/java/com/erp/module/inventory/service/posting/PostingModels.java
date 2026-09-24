package com.erp.module.inventory.service.posting;

import com.erp.module.inventory.api.stock.StockDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 过账引擎的输入输出 */
public final class PostingModels {

    private PostingModels() {
    }

    /** 仓库单据类型（流水 doc_type） */
    public static final String DOC_STOCK_IN = "STOCK_IN";
    public static final String DOC_STOCK_OUT = "STOCK_OUT";
    public static final String DOC_TRANSFER = "TRANSFER";

    /**
     * 一张仓库单据的一次过账。
     *
     * @param bizType          出入库类型（PURCHASE_IN、PRODUCTION_ISSUE、TRANSFER_NORMAL…）
     * @param skipCountFreeze  盘点单自身的盘盈盘亏调整不受盘点冻结限制
     * @param opening          期初入库：不要求期初已完成，日期可以早于启用期间
     * @param customerId       销售出库时记录到序列号上
     */
    public record PostCommand(String docType, String bizType, Long docId, String docNo, String sourceType, Long sourceId, String sourceNo,
                              LocalDate bizDate, List<PostLine> lines, boolean skipCountFreeze, boolean opening, Long customerId) {
    }

    /**
     * 过账行（基本单位）。
     *
     * @param allowFrozen 冻结、过期批次也可以出库（报废出库、退供应商出库、检验调拨）
     * @param unitCost    入库成本（本位币、不含税、基本单位），出库时为空
     */
    public record PostLine(Long docLineId, Long sourceLineId, StockDirection direction, Long materialId, Long warehouseId, Long locationId,
                           String batchNo, BigDecimal qty, BigDecimal unitCost, List<String> serialNos, boolean allowFrozen,
                           BatchAttrs batchAttrs) {
    }

    /** 入库时新建批次档案用的属性 */
    public record BatchAttrs(Long supplierId, String supplierBatchNo, LocalDate productionDate, LocalDate expireDate) {
    }

    /** 每行过账后的流水 ID 与余额 */
    public record PostedLine(Long docLineId, Long txnId, BigDecimal balanceQty) {
    }
}
