package com.erp.module.workbench.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 首页布局（wb_layout） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_layout")
public class WbLayoutDO extends BaseDO {

    private Long userId;
    private String layoutJson;
}
