package com.erp.module.engineering.api;

import com.erp.common.exception.ErrorCode;

/** 研发工程模块错误码，号段 1_005_000_000 ~ 1_005_999_999。提示原文与需求文档一致。 */
public interface EngineeringErrorCodes {

    // ========== 物料 1_005_001_xxx ==========
    ErrorCode MATERIAL_NOT_EXISTS = new ErrorCode(1_005_001_000, "物料不存在");
    ErrorCode MATERIAL_CODE_DUPLICATE = new ErrorCode(1_005_001_001, "物料编码「{}」已存在");
    ErrorCode MATERIAL_NOT_ENABLED = new ErrorCode(1_005_001_002, "物料「{}」未启用，不能使用");
    ErrorCode MATERIAL_NOT_EDITABLE = new ErrorCode(1_005_001_003, "物料「{}」已启用，只能修改名称、规格等描述信息");
    ErrorCode MATERIAL_NOT_DELETABLE = new ErrorCode(1_005_001_004, "只有草稿状态的物料可以删除，已启用的物料请停用");
    ErrorCode MATERIAL_DUPLICATE_SUSPECT = new ErrorCode(1_005_001_005, "发现疑似重复物料：{}");
    ErrorCode MATERIAL_CATEGORY_NOT_LEAF = new ErrorCode(1_005_001_006, "请选择末级物料类别");
    ErrorCode MATERIAL_FIXED_LOT_REQUIRED = new ErrorCode(1_005_001_007, "批量规则为“固定批量”时必须填写固定批量");
    ErrorCode MATERIAL_PERIOD_REQUIRED = new ErrorCode(1_005_001_008, "批量规则为“按周期”时必须填写合并周期天数");
    ErrorCode MATERIAL_INCOMPLETE = new ErrorCode(1_005_001_009, "物料「{}」缺少{}，不能启用");
    ErrorCode MATERIAL_UOM_USED = new ErrorCode(1_005_001_010, "单位换算已被单据使用，不能修改");
    ErrorCode MATERIAL_IN_USE = new ErrorCode(1_005_001_011, "该物料已被使用，不能删除");
    ErrorCode MATERIAL_TRACKING_LOCKED = new ErrorCode(1_005_001_012, "物料有库存或未完成单据，不能修改库存管理方式");
    ErrorCode MATERIAL_UOM_NO_CONVERSION = new ErrorCode(1_005_001_013, "{}{} 没有与基本单位的换算关系");
    ErrorCode MATERIAL_UOM_SAME_AS_BASE = new ErrorCode(1_005_001_014, "辅助单位 {} 不能与基本单位相同");
    ErrorCode MATERIAL_UOM_DUPLICATE = new ErrorCode(1_005_001_015, "辅助单位 {} 重复");
    ErrorCode MATERIAL_PHANTOM_MUST_MAKE = new ErrorCode(1_005_001_016, "虚拟件的取得方式只能是“自制”");
    ErrorCode MATERIAL_FEFO_SHELF_LIFE = new ErrorCode(1_005_001_017, "出库规则为“先到期先出”时必须填写保质期");
    ErrorCode MATERIAL_MIN_LIFE_SHELF_LIFE = new ErrorCode(1_005_001_018, "填写最小剩余保质期前请先填写保质期");
    ErrorCode MATERIAL_GROSS_LT_NET = new ErrorCode(1_005_001_019, "单位毛重不能小于单位净重");
    ErrorCode MATERIAL_MAX_LT_SAFETY = new ErrorCode(1_005_001_020, "最高库存不能小于安全库存");
    ErrorCode MATERIAL_CONVERT_FAILED = new ErrorCode(1_005_001_021, "物料「{}」的单位 {} 与基本单位 {} 之间没有换算关系");
    ErrorCode MATERIAL_APPROVAL_RUNNING = new ErrorCode(1_005_001_022, "物料「{}」正在审批中");

    // ========== 物料类别 1_005_002_xxx ==========
    ErrorCode CATEGORY_NOT_EXISTS = new ErrorCode(1_005_002_000, "物料类别不存在");
    ErrorCode CATEGORY_CODE_DUPLICATE = new ErrorCode(1_005_002_001, "类别编码「{}」已存在");
    ErrorCode CATEGORY_PARENT_CYCLE = new ErrorCode(1_005_002_002, "上级类别不能是自己或自己的下级");
    ErrorCode CATEGORY_TOO_DEEP = new ErrorCode(1_005_002_003, "物料类别不能超过 5 级");
    ErrorCode CATEGORY_HAS_MATERIAL = new ErrorCode(1_005_002_004, "该类别下已有物料，不能新增下级类别");
    ErrorCode CATEGORY_NOT_DELETABLE = new ErrorCode(1_005_002_005, "该类别下有下级类别或物料，不能删除");
    ErrorCode CATEGORY_HAS_ENABLED_CHILD = new ErrorCode(1_005_002_006, "请先停用下级类别");
    ErrorCode CATEGORY_CODE_LOCKED = new ErrorCode(1_005_002_007, "该类别已被物料使用，不能修改编码");
    ErrorCode CATEGORY_NAME_DUPLICATE = new ErrorCode(1_005_002_008, "同一上级下已有名称为「{}」的类别");
    ErrorCode CATEGORY_DISABLED = new ErrorCode(1_005_002_009, "物料类别「{}」已停用");

    // ========== BOM 1_005_003_xxx ==========
    ErrorCode BOM_NOT_EXISTS = new ErrorCode(1_005_003_000, "BOM 不存在");
    ErrorCode BOM_PARENT_TYPE = new ErrorCode(1_005_003_001, "原材料、包材、辅料不能作为 BOM 父件");
    ErrorCode BOM_COMPONENT_SELF = new ErrorCode(1_005_003_002, "子件不能与父件相同");
    ErrorCode BOM_COMPONENT_DUPLICATE = new ErrorCode(1_005_003_003, "子件「{}」重复，请合并为一行");
    ErrorCode BOM_CYCLE = new ErrorCode(1_005_003_004, "存在循环引用：{}");
    ErrorCode BOM_COMPONENT_NOT_ENABLED = new ErrorCode(1_005_003_005, "子件「{}」未启用，不能提交");
    ErrorCode BOM_NO_LINES = new ErrorCode(1_005_003_006, "请至少添加一行明细");
    ErrorCode BOM_TOO_DEEP = new ErrorCode(1_005_003_007, "BOM 展开超过 {} 层，请检查数据");
    ErrorCode BOM_NOT_EDITABLE = new ErrorCode(1_005_003_008, "只有草稿状态的 BOM 可以修改");
    ErrorCode BOM_DEFAULT_UNAPPROVE = new ErrorCode(1_005_003_009, "默认版本不能反审核");
    ErrorCode BOM_USED_UNAPPROVE = new ErrorCode(1_005_003_010, "该 BOM 已被生产订单使用，不能反审核，请新建版本");
    ErrorCode BOM_DEFAULT_DISABLE = new ErrorCode(1_005_003_011, "默认版本不能停用");
    ErrorCode BOM_PHANTOM_NO_BOM = new ErrorCode(1_005_003_012, "虚拟件「{}」没有已审核的 BOM");
    ErrorCode BOM_PARENT_NOT_USABLE = new ErrorCode(1_005_003_013, "父件「{}」已停用，不能提交");
    ErrorCode BOM_SUBSTITUTE_INVALID = new ErrorCode(1_005_003_014, "第 {} 行的替代料不能是主料本身或本 BOM 的其他主料");
    ErrorCode BOM_SET_DEFAULT_STATUS = new ErrorCode(1_005_003_015, "只有已审核的版本可以设为默认");
    ErrorCode BOM_PENDING_NOT_DELETABLE = new ErrorCode(1_005_003_016, "只有草稿状态的 BOM 可以删除");
    ErrorCode BOM_DESCRIPTION_REQUIRED = new ErrorCode(1_005_003_017, "新建版本时请填写版本说明");
    ErrorCode BOM_NO_DEFAULT = new ErrorCode(1_005_003_018, "物料「{}」没有默认 BOM");
    ErrorCode BOM_SUBSTITUTE_NOT_ENABLED = new ErrorCode(1_005_003_019, "替代料「{}」未启用，不能提交");
    ErrorCode BOM_COMPONENT_DRAFT_NOT_ALLOWED = new ErrorCode(1_005_003_020, "子件「{}」未启用");
}
