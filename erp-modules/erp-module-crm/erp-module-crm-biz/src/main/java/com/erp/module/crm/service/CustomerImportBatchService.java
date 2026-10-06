package com.erp.module.crm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.erp.common.exception.BizException;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.crm.api.CrmErrorCodes;
import com.erp.module.crm.dal.dataobject.CrmImportBatchDO;
import com.erp.module.crm.dal.dataobject.CrmImportItemDO;
import com.erp.module.crm.dal.dataobject.CustomerDO;
import com.erp.module.crm.dal.mapper.CrmImportBatchMapper;
import com.erp.module.crm.dal.mapper.CrmImportItemMapper;
import com.erp.module.crm.dal.mapper.CustomerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户导入批次与回滚（需求 03-01 3.5）：回滚时物理删除本批新增的客户（编码可重新导入）；
 * 任一客户导入后已有业务数据时整批不回滚。
 */
@Service
public class CustomerImportBatchService {

    static final String DONE = "DONE";
    static final String ROLLED_BACK = "ROLLED_BACK";

    private final CrmImportBatchMapper batchMapper;
    private final CrmImportItemMapper itemMapper;
    private final CustomerMapper customerMapper;
    private final CustomerService customerService;

    public CustomerImportBatchService(CrmImportBatchMapper batchMapper, CrmImportItemMapper itemMapper, CustomerMapper customerMapper,
                                      CustomerService customerService) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.customerMapper = customerMapper;
        this.customerService = customerService;
    }

    public record BatchRow(Long id, String fileName, int totalCount, int createdCount, int updatedCount, String status, LocalDateTime createdAt,
                           Long createdBy, LocalDateTime rolledBackAt, Long rolledBackBy) {
    }

    public CrmImportBatchDO begin(String fileName, int total) {
        CrmImportBatchDO b = new CrmImportBatchDO();
        b.setId(IdWorker.getId());
        b.setFileName(fileName == null ? null : fileName.length() > 256 ? fileName.substring(0, 256) : fileName);
        b.setTotalCount(total);
        b.setCreatedCount(0);
        b.setBatchStatus(DONE);
        batchMapper.insert(b);
        return b;
    }

    public void created(CrmImportBatchDO batch, Long customerId, String code, int seq) {
        CrmImportItemDO i = new CrmImportItemDO();
        i.setId(IdWorker.getId());
        i.setBatchId(batch.getId());
        i.setCustomerId(customerId);
        i.setCode(code);
        i.setSeq(seq);
        itemMapper.insert(i);
    }

    public void finish(CrmImportBatchDO batch, int created) {
        if (created == 0) {
            batchMapper.deleteById(batch.getId());
            return;
        }
        CrmImportBatchDO b = batchMapper.selectById(batch.getId());
        b.setCreatedCount(created);
        batchMapper.updateByIdOrFail(b);
    }

    public List<BatchRow> list() {
        return batchMapper.selectRecent(50).stream().map(b -> new BatchRow(b.getId(), b.getFileName(), b.getTotalCount(), b.getCreatedCount(), 0,
                b.getBatchStatus(), b.getCreatedAt(), b.getCreatedBy(), b.getRolledBackAt(), b.getRolledBackBy())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long batchId) {
        CrmImportBatchDO b = batchMapper.selectById(batchId);
        if (b == null) throw new BizException(CrmErrorCodes.IMPORT_BATCH_NOT_EXISTS);
        if (ROLLED_BACK.equals(b.getBatchStatus())) throw new BizException(CrmErrorCodes.IMPORT_BATCH_ROLLED_BACK);
        List<CrmImportItemDO> items = itemMapper.selectByBatch(batchId);
        List<String> blocked = new ArrayList<>();
        for (CrmImportItemDO i : items) {
            CustomerDO c = customerMapper.selectById(i.getCustomerId());
            if (c != null && customerService.hasBusinessData(c.getId())) blocked.add(i.getCode());
        }
        if (!blocked.isEmpty()) {
            throw BizException.of(CrmErrorCodes.IMPORT_ROLLBACK_BLOCKED,
                    blocked.size() <= 10 ? String.join("、", blocked) : String.join("、", blocked.subList(0, 10)) + " 等 " + blocked.size() + " 个");
        }
        for (CrmImportItemDO i : items) customerService.hardDelete(i.getCustomerId());
        b.setBatchStatus(ROLLED_BACK);
        b.setRolledBackAt(LocalDateTime.now());
        b.setRolledBackBy(SecurityUtils.getLoginUserIdOrNull());
        batchMapper.updateByIdOrFail(b);
    }
}
