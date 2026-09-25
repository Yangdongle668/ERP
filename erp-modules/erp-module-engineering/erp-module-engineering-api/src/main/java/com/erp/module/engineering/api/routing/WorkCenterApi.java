package com.erp.module.engineering.api.routing;

import java.util.List;
import java.util.Optional;

/** 工作中心查询（PMC 排产、生产派工、财务成本） */
public interface WorkCenterApi {

    Optional<WorkCenterDTO> get(Long id);

    /** 全部启用的工作中心 */
    List<WorkCenterDTO> list();
}
