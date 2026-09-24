package com.erp.module.inventory.apiimpl;

import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.TransferRequest;
import com.erp.module.inventory.service.doc.StockInService;
import com.erp.module.inventory.service.doc.StockOutService;
import com.erp.module.inventory.service.doc.TransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 业务模块生成仓库单据的入口（在调用方事务内执行） */
@Service
public class InventoryDocApiImpl implements InventoryDocApi {

    private final StockInService stockInService;
    private final StockOutService stockOutService;
    private final TransferService transferService;

    public InventoryDocApiImpl(StockInService stockInService, StockOutService stockOutService, TransferService transferService) {
        this.stockInService = stockInService;
        this.stockOutService = stockOutService;
        this.transferService = transferService;
    }

    @Override
    public List<Long> createStockIn(StockInRequest request) {
        return stockInService.createFromSource(request, request.docDate());
    }

    @Override
    public List<Long> createStockOut(StockOutRequest request) {
        return stockOutService.createFromSource(request, request.docDate());
    }

    @Override
    public List<Long> createTransfer(TransferRequest request) {
        return transferService.createFromSource(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelBySource(String sourceType, Long sourceId) {
        stockInService.cancelBySource(sourceType, sourceId);
        stockOutService.cancelBySource(sourceType, sourceId);
        transferService.cancelBySource(sourceType, sourceId);
    }
}
