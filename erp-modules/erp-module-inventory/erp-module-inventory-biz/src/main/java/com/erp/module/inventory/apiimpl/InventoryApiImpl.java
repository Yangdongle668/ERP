package com.erp.module.inventory.apiimpl;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.module.inventory.api.stock.InventoryApi;
import com.erp.module.inventory.api.stock.StockPostingRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 占位实现：保证依赖本契约的模块可以先编译、启动。
 * TODO(inventory): 由仓库模块开发时替换为真实实现（库存余额 + 流水 + 并发扣减）。
 */
@Service
public class InventoryApiImpl implements InventoryApi {

    @Override
    public void post(StockPostingRequest request) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "库存过账");
    }

    @Override
    public void reverse(String bizType, Long bizId) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "库存冲销");
    }

    @Override
    public BigDecimal getAvailableQty(Long materialId) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "可用库存查询");
    }
}
