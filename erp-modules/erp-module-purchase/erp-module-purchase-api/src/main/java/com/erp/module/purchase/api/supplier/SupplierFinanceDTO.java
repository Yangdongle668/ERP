package com.erp.module.purchase.api.supplier;

import java.util.List;

/**
 * 供应商的财务信息（财务付款申请、应付到期日）。
 *
 * @param paymentTermId 付款条件（应付到期日按其节点计算）
 * @param supplierLevel 供应商等级（付款申请审批条件）
 * @param invoiceType   发票类型（VAT_SPECIAL / VAT_NORMAL …）
 * @param banks         供应商收款账户
 */
public record SupplierFinanceDTO(Long id, String code, String name, String shortName, String currency, Long paymentTermId, String supplierLevel,
                                 String invoiceType, List<Bank> banks) {

    public record Bank(Long id, String bankName, String accountName, String accountNo, String swift, String currency, boolean isDefault) {
    }
}
