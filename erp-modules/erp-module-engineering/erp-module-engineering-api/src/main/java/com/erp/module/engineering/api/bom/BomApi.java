package com.erp.module.engineering.api.bom;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** BOM 查询（PMC、生产、销售报价、财务使用）。只返回已审核的版本。 */
public interface BomApi {

    /** 父件的默认 BOM（已审核且为默认版本）；date 预留按生效日期取版本，当前取现行默认版本 */
    Optional<BomDTO> getDefaultBom(Long materialId, LocalDate date);

    Optional<BomDTO> getBom(Long bomId);

    /**
     * 多级展开（MRP 口径）：从父件默认 BOM 开始逐层计算需求量；虚拟件不出现在结果中，其子件直接挂到上一层（透过）。
     *
     * @param levels 展开层数，≤0 表示全部（受参数 eng.bom.max-level 限制）
     */
    List<BomExplodeLine> explode(Long materialId, BigDecimal qty, LocalDate date, int levels);

    /** 直接使用该子件的已审核 BOM（单层反查） */
    List<BomDTO> whereUsed(Long componentId);

    /** 全部物料的低位码（物料 ID → 低位码；未出现在任何 BOM 中的物料为 0） */
    Map<Long, Integer> getLowLevelCodes();
}
