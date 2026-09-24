package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 盘点单 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_count")
public class CountDO extends BaseDocDO {

    private String countType;
    private String warehouseIds;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String categoryIds;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String locationIds;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String materialIds;
    private Boolean includeZero;
    private Boolean blindCount;
    private LocalDateTime snapshotAt;
    private String countStatus;
    private Long gainInId;
    private Long lossOutId;
}
