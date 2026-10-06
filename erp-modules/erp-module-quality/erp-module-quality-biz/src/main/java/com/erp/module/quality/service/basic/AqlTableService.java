package com.erp.module.quality.service.basic;

import org.springframework.context.event.EventListener;
import com.erp.framework.maintenance.DataRestoredEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.controller.vo.BasicVOs.AqlCodeRow;
import com.erp.module.quality.controller.vo.BasicVOs.AqlCodeSave;
import com.erp.module.quality.controller.vo.BasicVOs.AqlRow;
import com.erp.module.quality.controller.vo.BasicVOs.AqlTableSave;
import com.erp.module.quality.controller.vo.BasicVOs.AqlTableView;
import com.erp.module.quality.dal.dataobject.QcAqlCodeDO;
import com.erp.module.quality.dal.dataobject.QcAqlTableDO;
import com.erp.module.quality.dal.mapper.QcAqlCodeMapper;
import com.erp.module.quality.dal.mapper.QcAqlTableMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GB/T 2828.1（ISO 2859-1）一次正常检验抽样表（10-01 第 2、3 节）：样本量字码表（qc_aql_code）与主表（qc_aql_table，箭头规则已展开）。
 * 数据由迁移脚本初始化，可在“检验基础数据 → AQL 抽样表”维护；查询结果缓存在内存，修改后刷新。
 */
@Service
public class AqlTableService {

    /** 检验水平：特殊 S1～S4、一般 I～III */
    public static final List<String> LEVELS = List.of("S1", "S2", "S3", "S4", "I", "II", "III");

    /** 一个等级的抽样结果 */
    public record Plan(String letter, int n, int ac, int re) {
    }

    private record Snapshot(List<QcAqlCodeDO> codes, Map<String, QcAqlTableDO> main, Set<String> aqls, Set<String> letters) {
    }

    private final QcAqlCodeMapper codeMapper;
    private final QcAqlTableMapper tableMapper;
    private volatile Snapshot snapshot;

    public AqlTableService(QcAqlCodeMapper codeMapper, QcAqlTableMapper tableMapper) {
        this.codeMapper = codeMapper;
        this.tableMapper = tableMapper;
    }

    /** 加严检验（QC-SCAR-R04）：检验水平提高一级（S1→S2→S3→S4，I→II→III；S4、III 不变） */
    public static String tighten(String level) {
        return switch (level == null ? "" : level) {
            case "S1" -> "S2";
            case "S2" -> "S3";
            case "S3" -> "S4";
            case "I" -> "II";
            case "II" -> "III";
            default -> level;
        };
    }

    /** 字码 */
    public String codeLetter(long lotQty, String level) {
        if (!LEVELS.contains(level)) throw new IllegalArgumentException("检验水平不正确：" + level);
        long lot = Math.max(lotQty, 2);
        QcAqlCodeDO last = null;
        for (QcAqlCodeDO c : data().codes()) {
            if (!level.equals(c.getInspectionLevel())) continue;
            last = c;
            if (lot >= c.getLotMin() && (c.getLotMax() == null || lot <= c.getLotMax())) return c.getCodeLetter();
        }
        if (last == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "检验水平 " + level + " 的样本量字码");
        return last.getCodeLetter();
    }

    public boolean validAql(String aql) {
        String key = normalize(aql);
        return key != null && data().aqls().contains(key);
    }

    /** 批量 + 检验水平 + AQL → 样本量与 Ac/Re（未截断到批量） */
    public Plan lookup(long lotQty, String level, String aql) {
        String letter = codeLetter(lotQty, level);
        QcAqlTableDO row = data().main().get(letter + "|" + normalize(aql));
        if (row == null) throw BizException.of(QualityErrorCodes.SAMPLING_AQL_INVALID, aql);
        return new Plan(row.getSampleLetter(), row.getSampleSize(), row.getAc(), row.getRe());
    }

    // ==================== 维护 ====================

    public AqlTableView view() {
        Snapshot s = data();
        List<AqlCodeRow> codes = s.codes().stream().map(c -> new AqlCodeRow(c.getId(), c.getLotMin(), c.getLotMax(), c.getInspectionLevel(),
                c.getCodeLetter(), c.getVersion())).toList();
        List<AqlRow> rows = s.main().values().stream()
                .sorted(Comparator.comparing(QcAqlTableDO::getCodeLetter).thenComparing(r -> new BigDecimal(r.getAql())))
                .map(r -> new AqlRow(r.getId(), r.getCodeLetter(), r.getAql(), r.getSampleLetter(), r.getSampleSize(), r.getAc(), r.getRe(), r.getVersion()))
                .toList();
        return new AqlTableView(LEVELS, s.letters().stream().sorted().toList(),
                s.aqls().stream().sorted(Comparator.comparing(BigDecimal::new)).toList(), codes, rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCode(Long id, AqlCodeSave req) {
        QcAqlCodeDO c = codeMapper.selectById(id);
        if (c == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "样本量字码");
        String letter = req.codeLetter() == null ? null : req.codeLetter().trim().toUpperCase();
        if (letter == null || !data().letters().contains(letter)) throw BizException.of(QualityErrorCodes.AQL_LETTER_INVALID, req.codeLetter());
        if (req.version() != null) c.setVersion(req.version());
        c.setCodeLetter(letter);
        codeMapper.updateByIdOrFail(c);
        refreshAfterCommit();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateTable(Long id, AqlTableSave req) {
        QcAqlTableDO r = tableMapper.selectById(id);
        if (r == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "AQL 主表");
        String letter = req.sampleLetter() == null ? null : req.sampleLetter().trim().toUpperCase();
        if (letter == null || !data().letters().contains(letter)) throw BizException.of(QualityErrorCodes.AQL_LETTER_INVALID, req.sampleLetter());
        if (req.sampleSize() == null || req.sampleSize() < 1 || req.ac() == null || req.re() == null || req.ac() < 0 || req.re() <= req.ac()) {
            throw new BizException(QualityErrorCodes.AQL_PLAN_INVALID);
        }
        if (req.version() != null) r.setVersion(req.version());
        r.setSampleLetter(letter);
        r.setSampleSize(req.sampleSize());
        r.setAc(req.ac());
        r.setRe(req.re());
        tableMapper.updateByIdOrFail(r);
        refreshAfterCommit();
    }

    /** 系统数据恢复后重新读取 */
    @EventListener(DataRestoredEvent.class)
    public void onRestored() {
        snapshot = null;
    }

    private void refreshAfterCommit() {
        snapshot = null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    snapshot = null;
                }
            });
        }
    }

    private Snapshot data() {
        Snapshot s = snapshot;
        if (s != null) return s;
        List<QcAqlCodeDO> codes = codeMapper.selectList(new LambdaQueryWrapper<QcAqlCodeDO>().orderByAsc(QcAqlCodeDO::getLotMin)
                .orderByAsc(QcAqlCodeDO::getId));
        Map<String, QcAqlTableDO> main = new HashMap<>();
        Set<String> aqls = new LinkedHashSet<>();
        Set<String> letters = new LinkedHashSet<>();
        for (QcAqlTableDO r : tableMapper.selectList(new LambdaQueryWrapper<QcAqlTableDO>().orderByAsc(QcAqlTableDO::getId))) {
            String key = normalize(r.getAql());
            main.put(r.getCodeLetter() + "|" + key, r);
            aqls.add(key);
            letters.add(r.getCodeLetter());
        }
        s = new Snapshot(codes, main, aqls, letters);
        snapshot = s;
        return s;
    }

    /** AQL 统一写法（0.65 与 0.650 视为同一值）；非数字返回 null */
    static String normalize(String aql) {
        if (aql == null || aql.isBlank()) return null;
        try {
            BigDecimal v = new BigDecimal(aql.trim());
            return v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
