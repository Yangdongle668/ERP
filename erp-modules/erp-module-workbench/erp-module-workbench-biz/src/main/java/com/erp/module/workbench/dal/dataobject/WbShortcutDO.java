package com.erp.module.workbench.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 快捷入口（wb_shortcut） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_shortcut")
public class WbShortcutDO extends BaseDO {

    private Long userId;
    private String menuRoute;
    private Integer sort;
}
