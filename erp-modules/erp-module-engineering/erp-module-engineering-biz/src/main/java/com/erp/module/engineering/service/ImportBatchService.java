package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.erp.common.exception.BizException;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.dal.dataobject.ImportBatchDO;
import com.erp.module.engineering.dal.dataobject.ImportItemDO;
import com.erp.module.engineering.dal.dataobject.MaterialCategoryDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.dataobject.MaterialUomDO;
import com.erp.module.engineering.dal.mapper.BomLineMapper;
import com.erp.module.engineering.dal.mapper.CodeSegmentMapper;
import com.erp.module.engineering.dal.mapper.CodeSegmentValueMapper;
import com.erp.module.engineering.dal.mapper.ImportBatchMapper;
import com.erp.module.engineering.dal.mapper.ImportItemMapper;
import com.erp.module.engineering.dal.mapper.MaterialCategoryMapper;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.engineering.dal.mapper.MaterialUomMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导入批次与回滚（需求 05-02 3.5、05-01 第 9 节）：每次导入记录新增和更新的数据；回滚时删除本批新增的（物理删除，编码可重新导入），
 * 把本批更新的恢复为导入前的内容。全部成功或全部不做；本批数据之后已被使用（库存、单据、BOM、下级类别等）或被之后的批次修改过时不能回滚。
 */
@Service
public class ImportBatchService {

    public static final String MATERIAL = "MATERIAL";
    public static final String CATEGORY = "CATEGORY";
    static final String CREATE = "CREATE";
    static final String UPDATE = "UPDATE";
    static final String DONE = "DONE";
    static final String ROLLED_BACK = "ROLLED_BACK";

    /** 快照专用：不受全局 Long → 字符串等序列化配置影响 */
    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private final ImportBatchMapper batchMapper;
    private final ImportItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final MaterialUomMapper uomMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final CodeSegmentMapper segmentMapper;
    private final CodeSegmentValueMapper segmentValueMapper;
    private final BomLineMapper bomLineMapper;
    private final MaterialService materialService;

    public ImportBatchService(ImportBatchMapper batchMapper, ImportItemMapper itemMapper, MaterialMapper materialMapper, MaterialUomMapper uomMapper,
                              MaterialCategoryMapper categoryMapper, CodeSegmentMapper segmentMapper, CodeSegmentValueMapper segmentValueMapper,
                              BomLineMapper bomLineMapper, MaterialService materialService) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.materialMapper = materialMapper;
        this.uomMapper = uomMapper;
        this.categoryMapper = categoryMapper;
        this.segmentMapper = segmentMapper;
        this.segmentValueMapper = segmentValueMapper;
        this.bomLineMapper = bomLineMapper;
        this.materialService = materialService;
    }

    /** 物料更新前的快照 */
    record MaterialSnapshot(MaterialDO material, List<MaterialUomDO> uoms) {
    }

    public record BatchRow(Long id, String bizType, String fileName, String importMode, int totalCount, int createdCount, int updatedCount,
                           String status, LocalDateTime createdAt, Long createdBy, LocalDateTime rolledBackAt, Long rolledBackBy) {
    }

    // ==================== 记录 ====================

    /** 开始一个批次（导入执行时） */
    public ImportBatchDO begin(String bizType, String fileName, String mode, int total) {
        ImportBatchDO b = new ImportBatchDO();
        b.setId(IdWorker.getId());
        b.setBizType(bizType);
        b.setFileName(fileName == null ? null : fileName.length() > 256 ? fileName.substring(0, 256) : fileName);
        b.setImportMode(mode);
        b.setTotalCount(total);
        b.setCreatedCount(0);
        b.setUpdatedCount(0);
        b.setBatchStatus(DONE);
        batchMapper.insert(b);
        return b;
    }

    /** 记录新增（在该行的事务中调用） */
    public void created(ImportBatchDO batch, Long targetId, String code, int seq) {
        item(batch, targetId, code, CREATE, null, seq);
    }

    /** 记录物料更新：保存更新前的物料与单位换算（在执行更新之前调用） */
    public void beforeMaterialUpdate(ImportBatchDO batch, Long materialId, int seq) {
        MaterialDO m = materialMapper.selectById(materialId);
        try {
            item(batch, materialId, m.getCode(), UPDATE, JSON.writeValueAsString(new MaterialSnapshot(m, uomMapper.selectByMaterial(materialId))), seq);
        } catch (Exception e) {
            throw new IllegalStateException("保存导入快照失败", e);
        }
    }

    private void item(ImportBatchDO batch, Long targetId, String code, String action, String snapshot, int seq) {
        ImportItemDO i = new ImportItemDO();
        i.setId(IdWorker.getId());
        i.setBatchId(batch.getId());
        i.setTargetId(targetId);
        i.setTargetCode(code);
        i.setAction(action);
        i.setSnapshot(snapshot);
        i.setSeq(seq);
        itemMapper.insert(i);
    }

    /** 导入结束：写入新增 / 更新条数；没有任何成功的行时删除批次 */
    public void finish(ImportBatchDO batch) {
        List<ImportItemDO> items = itemMapper.selectByBatch(batch.getId());
        if (items.isEmpty()) {
            batchMapper.deleteById(batch.getId());
            return;
        }
        ImportBatchDO b = batchMapper.selectById(batch.getId());
        b.setCreatedCount((int) items.stream().filter(i -> CREATE.equals(i.getAction())).count());
        b.setUpdatedCount((int) items.stream().filter(i -> UPDATE.equals(i.getAction())).count());
        batchMapper.updateByIdOrFail(b);
    }

    // ==================== 查询 / 回滚 ====================

    public List<BatchRow> list(String bizType) {
        return batchMapper.selectRecent(bizType, 50).stream().map(b -> new BatchRow(b.getId(), b.getBizType(), b.getFileName(), b.getImportMode(),
                b.getTotalCount(), b.getCreatedCount(), b.getUpdatedCount(), b.getBatchStatus(), b.getCreatedAt(), b.getCreatedBy(),
                b.getRolledBackAt(), b.getRolledBackBy())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollback(String bizType, Long batchId) {
        ImportBatchDO b = batchMapper.selectById(batchId);
        if (b == null || !bizType.equals(b.getBizType())) throw new BizException(EngineeringErrorCodes.IMPORT_BATCH_NOT_EXISTS);
        if (ROLLED_BACK.equals(b.getBatchStatus())) throw new BizException(EngineeringErrorCodes.IMPORT_BATCH_ROLLED_BACK);
        List<ImportItemDO> items = itemMapper.selectByBatch(batchId);
        Set<Long> targets = items.stream().map(ImportItemDO::getTargetId).collect(Collectors.toSet());
        if (!targets.isEmpty()) {
            List<Long> later = itemMapper.selectLaterBatches(batchId, targets);
            if (!later.isEmpty()) {
                throw BizException.of(EngineeringErrorCodes.IMPORT_BATCH_LATER, later.stream().map(String::valueOf).collect(Collectors.joining("、")));
            }
        }
        if (MATERIAL.equals(bizType)) rollbackMaterials(items);
        else rollbackCategories(items);
        b.setBatchStatus(ROLLED_BACK);
        b.setRolledBackAt(LocalDateTime.now());
        b.setRolledBackBy(SecurityUtils.getLoginUserIdOrNull());
        batchMapper.updateByIdOrFail(b);
    }

    private void rollbackMaterials(List<ImportItemDO> items) {
        List<String> blocked = new ArrayList<>();
        for (ImportItemDO i : items) {
            if (!CREATE.equals(i.getAction()) || materialMapper.selectById(i.getTargetId()) == null) continue;
            Long id = i.getTargetId();
            if (materialService.usage(id).used() || bomLineMapper.countBomsUsing(id, false) > 0 || bomLineMapper.countBomsAsParent(id) > 0) {
                blocked.add(i.getTargetCode());
            }
        }
        if (!blocked.isEmpty()) throw BizException.of(EngineeringErrorCodes.IMPORT_ROLLBACK_BLOCKED, summary(blocked));
        for (int k = items.size() - 1; k >= 0; k--) {
            ImportItemDO i = items.get(k);
            uomMapper.deleteByMaterial(i.getTargetId());
            materialMapper.hardDelete(i.getTargetId());
            if (UPDATE.equals(i.getAction())) restoreMaterial(i);
        }
    }

    /** 恢复更新前的物料：同一 ID 重新写入快照（含空值），版本号加 1 使打开中的编辑页提示数据已变化 */
    private void restoreMaterial(ImportItemDO i) {
        MaterialSnapshot s;
        try {
            s = JSON.readValue(i.getSnapshot(), MaterialSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("导入快照损坏：" + i.getTargetCode(), e);
        }
        MaterialDO m = s.material();
        m.setVersion((m.getVersion() == null ? 0 : m.getVersion()) + 1);
        materialMapper.insert(m);
        for (MaterialUomDO u : s.uoms()) uomMapper.insert(u);
    }

    private void rollbackCategories(List<ImportItemDO> items) {
        Set<Long> ids = new HashSet<>();
        items.forEach(i -> ids.add(i.getTargetId()));
        List<String> blocked = new ArrayList<>();
        Map<Long, Long> materialCounts = materialMapper.countAllByCategory();
        for (ImportItemDO i : items) {
            MaterialCategoryDO c = categoryMapper.selectById(i.getTargetId());
            if (c == null) continue;
            boolean foreignChild = categoryMapper.selectChildren(c.getId()).stream().anyMatch(x -> !ids.contains(x.getId()));
            if (materialCounts.getOrDefault(c.getId(), 0L) > 0 || foreignChild) blocked.add(i.getTargetCode());
        }
        if (!blocked.isEmpty()) throw BizException.of(EngineeringErrorCodes.IMPORT_ROLLBACK_BLOCKED, summary(blocked) + "（类别下已有物料或下级类别）");
        // 先删下级（按导入顺序倒序）
        for (int k = items.size() - 1; k >= 0; k--) {
            Long id = items.get(k).getTargetId();
            segmentValueMapper.hardDeleteByCategory(id);
            segmentMapper.hardDeleteByCategory(id);
            categoryMapper.hardDelete(id);
        }
    }

    private static String summary(List<String> codes) {
        return codes.size() <= 10 ? String.join("、", codes) : String.join("、", codes.subList(0, 10)) + " 等 " + codes.size() + " 条";
    }
}
