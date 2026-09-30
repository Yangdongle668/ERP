package com.erp.module.workbench.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 预警接收人（wb_alert_user） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_alert_user")
public class WbAlertUserDO extends BaseDO {

    private Long alertId;
    private Long userId;
}
