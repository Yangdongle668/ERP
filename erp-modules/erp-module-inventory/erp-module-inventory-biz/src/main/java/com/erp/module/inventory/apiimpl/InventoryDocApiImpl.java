package com.erp.module.inventory.apiimpl;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockOutRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 占位实现：保证依赖本契约的模块可以先编译、启动。
 * TODO(inventory): 仓库模块开发入库单/出库单时替换为真实实现。
 */
@Service
public class InventoryDocApiImpl implements InventoryDocApi {

    @Override
    public List<Long> createStockIn(StockInRequest request) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "生成入库单");
    }

    @Override
    public List<Long> createStockOut(StockOutRequest request) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "生成出库单");
    }

    @Override
    public void cancelBySource(String sourceType, Long sourceId) {
        throw BizException.of(GlobalErrorCodes.NOT_IMPLEMENTED, "撤销来源单据生成的仓库单据");
    }
}
