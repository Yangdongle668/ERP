package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 打印模板（sys_print_template） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_print_template")
public class PrintTemplateDO extends BaseDO {

    private String bizType;
    private String name;
    private String language;
    private String paper;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer paperWidth;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer paperHeight;
    private String margin;
    private String content;
    /** 每页明细行数：设置后按固定行数分页（针式多联纸），空为浏览器自动分页 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer rowsPerPage;
    /** 联次说明，| 分隔 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String copiesNote;
    /** CARBON 多联纸一次打印 / REPEAT 普通纸逐联打印 */
    private String copyMode;
    /** 可视化版式 JSON（为空表示代码模板）；有版式时 content 由前端按版式生成 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String layout;
    /** 内置模板变体：zh-CN / en / zh-CN-dot */
    private String builtinKey;
    private Boolean isDefault;
    private Boolean isBuiltin;
    private EnableStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
