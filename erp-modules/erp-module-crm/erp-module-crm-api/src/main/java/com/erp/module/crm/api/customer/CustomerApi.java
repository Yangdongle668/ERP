package com.erp.module.crm.api.customer;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 客户主数据（需求 03 README 第 9 节）。查询不受数据权限限制，调用方按自己的规则过滤。 */
public interface CustomerApi {

    Optional<CustomerDTO> getCustomer(Long id);

    Map<Long, CustomerDTO> getCustomers(Collection<Long> ids);

    /**
     * 校验客户可以下正式订单（状态为 ACTIVE）。
     *
     * @throws com.erp.common.exception.BizException 不存在、非正式客户、停用或黑名单
     */
    CustomerDTO validateCanOrder(Long id);

    /** 校验客户可以询价、报价、送样（潜在、审批中、正式客户） */
    CustomerDTO validateCanQuote(Long id);

    /** 校验客户可以出货：正式客户；停用客户允许出在途订单；潜在客户、黑名单不允许 */
    CustomerDTO validateCanShip(Long id);

    /** @param type SHIP_TO / BILL_TO / NOTIFY，为空返回全部 */
    List<AddressDTO> getAddresses(Long customerId, String type);

    Optional<AddressDTO> getDefaultAddress(Long customerId, String type);

    /** 在职联系人，主联系人在前 */
    List<ContactDTO> getContacts(Long customerId);

    /** @param statuses 为空不限 */
    List<CustomerDTO> search(String keyword, Collection<CustomerStatus> statuses, int limit);

    /** 销售订单审核后由销售模块调用，回写首次/最近下单日期（需求 03-01 R10） */
    void recordOrder(Long customerId, LocalDate orderDate);
}
