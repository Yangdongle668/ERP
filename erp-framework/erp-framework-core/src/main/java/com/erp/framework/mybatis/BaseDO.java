package com.erp.framework.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 所有数据表实体的基类：雪花 ID、审计字段、逻辑删除、乐观锁。
 *
 * <p>对应的表必须包含：id, version, created_by, created_at, updated_by, updated_at, deleted。
 */
@Data
public abstract class BaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 乐观锁版本号。updateById 时自动比较并 +1；更新行数为 0 表示并发冲突。 */
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean deleted;
}
