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

    // ========== 工作中心与工艺路线 1_005_004_xxx ==========
    ErrorCode WORK_CENTER_NOT_EXISTS = new ErrorCode(1_005_004_000, "工作中心不存在");
    ErrorCode WORK_CENTER_CODE_DUPLICATE = new ErrorCode(1_005_004_001, "工作中心编码「{}」已存在");
    ErrorCode WORK_CENTER_IN_USE = new ErrorCode(1_005_004_002, "该工作中心已被使用，只能停用");
    ErrorCode WORK_CENTER_DISABLED = new ErrorCode(1_005_004_003, "工作中心「{}」已停用");
    ErrorCode ROUTING_NOT_EXISTS = new ErrorCode(1_005_004_010, "工艺路线不存在");
    ErrorCode ROUTING_SEQ_DUPLICATE = new ErrorCode(1_005_004_011, "工序号 {} 重复");
    ErrorCode ROUTING_LAST_REPORT_POINT = new ErrorCode(1_005_004_012, "最后一道工序必须是报工点");
    ErrorCode ROUTING_OUTSOURCE_WC = new ErrorCode(1_005_004_013, "委外工序必须选择委外类型的工作中心");
    ErrorCode ROUTING_USED_UNAPPROVE = new ErrorCode(1_005_004_014, "该工艺路线已被生产订单使用，不能反审核");
    ErrorCode ROUTING_NOT_EDITABLE = new ErrorCode(1_005_004_015, "只有草稿状态的工艺路线可以修改");
    ErrorCode ROUTING_NO_STEPS = new ErrorCode(1_005_004_016, "请至少添加一道工序");
    ErrorCode ROUTING_MATERIAL_TYPE = new ErrorCode(1_005_004_017, "只有半成品、成品可以建立工艺路线");
    ErrorCode ROUTING_DEFAULT_UNAPPROVE = new ErrorCode(1_005_004_018, "默认版本不能反审核");
    ErrorCode ROUTING_DEFAULT_DISABLE = new ErrorCode(1_005_004_019, "默认版本不能停用");
    ErrorCode ROUTING_SET_DEFAULT_STATUS = new ErrorCode(1_005_004_020, "只有已审核的版本可以设为默认");
    ErrorCode ROUTING_RUN_SECONDS = new ErrorCode(1_005_004_021, "第 {} 行标准工时必须大于 0");

    // ========== ECN 1_005_005_xxx ==========
    ErrorCode ECN_NOT_EXISTS = new ErrorCode(1_005_005_000, "ECN 不存在");
    ErrorCode ECN_BOM_NOT_DEFAULT = new ErrorCode(1_005_005_001, "BOM「{}」不是当前默认版本，请重新选择");
    ErrorCode ECN_COMPONENT_MULTI = new ErrorCode(1_005_005_002, "BOM「{}」中子件「{}」存在多个变更");
    ErrorCode ECN_IMPACT_REQUIRED = new ErrorCode(1_005_005_003, "请完成影响分析并选择处理方式");
    ErrorCode ECN_BOM_LOCKED = new ErrorCode(1_005_005_004, "BOM「{}」正在 ECN「{}」中变更");
    ErrorCode ECN_BOM_CHANGED = new ErrorCode(1_005_005_005, "BOM「{}」在审批期间已变更，请驳回后重新发起");
    ErrorCode ECN_TASKS_UNDONE = new ErrorCode(1_005_005_006, "还有 {} 项执行任务未完成");
    ErrorCode ECN_NO_LINES = new ErrorCode(1_005_005_007, "请至少添加一行变更明细");
    ErrorCode ECN_NOT_EDITABLE = new ErrorCode(1_005_005_008, "只有草稿状态的 ECN 可以修改");
    ErrorCode ECN_LINE_INVALID = new ErrorCode(1_005_005_009, "第 {} 行：{}");
    ErrorCode ECN_EFFECTIVE_DATE = new ErrorCode(1_005_005_010, "生效方式为指定日期时，请填写不早于今天的生效日期");
    ErrorCode ECN_TASK_NOT_MINE = new ErrorCode(1_005_005_011, "只有任务负责人可以确认完成");
    ErrorCode ECN_STATUS = new ErrorCode(1_005_005_012, "ECN 当前状态【{}】不允许该操作");
    ErrorCode ECN_RESULT_DUPLICATE = new ErrorCode(1_005_005_013, "BOM「{}」变更后子件「{}」重复");

    // ========== 研发项目 1_005_006_xxx ==========
    ErrorCode PROJECT_NOT_EXISTS = new ErrorCode(1_005_006_000, "项目不存在");
    ErrorCode PROJECT_DATE_RANGE = new ErrorCode(1_005_006_001, "计划结束日期不能早于开始日期");
    ErrorCode PROJECT_TASK_OWNER_NOT_MEMBER = new ErrorCode(1_005_006_002, "任务负责人必须是项目成员");
    ErrorCode PROJECT_TASKS_UNDONE = new ErrorCode(1_005_006_003, "还有 {} 个未完成任务");
    ErrorCode PROJECT_NO_PERMISSION = new ErrorCode(1_005_006_004, "只有项目经理或任务负责人可以操作");
    ErrorCode PROJECT_STATUS = new ErrorCode(1_005_006_005, "项目当前状态【{}】不允许该操作");
    ErrorCode PROJECT_TASK_NOT_EXISTS = new ErrorCode(1_005_006_006, "任务不存在");
    ErrorCode PROJECT_CANCEL_REASON = new ErrorCode(1_005_006_007, "请填写取消原因");
    ErrorCode PROJECT_STAGE_INVALID = new ErrorCode(1_005_006_008, "只能推进到后续阶段");

    // ========== 样品 1_005_007_xxx ==========
    ErrorCode SAMPLE_NOT_EXISTS = new ErrorCode(1_005_007_000, "样品单不存在");
    ErrorCode SAMPLE_CUSTOMER_REQUIRED = new ErrorCode(1_005_007_001, "客户样必须选择客户");
    ErrorCode SAMPLE_NO_BOM = new ErrorCode(1_005_007_002, "物料「{}」没有已审核的 BOM，不能生成生产订单");
    ErrorCode SAMPLE_NOT_OUT = new ErrorCode(1_005_007_003, "样品尚未出库");
    ErrorCode SAMPLE_STATUS = new ErrorCode(1_005_007_004, "样品单当前状态【{}】不允许{}");
    ErrorCode SAMPLE_REQUIRED_DATE = new ErrorCode(1_005_007_005, "要求日期不能早于今天");
    ErrorCode SAMPLE_PRODUCTION_UNAVAILABLE = new ErrorCode(1_005_007_006, "生产模块尚未提供样品生产订单，请选择“从库存领取”");
    ErrorCode SAMPLE_MATERIAL_DRAFT = new ErrorCode(1_005_007_007, "物料「{}」尚未启用，请先启用物料");
    ErrorCode SAMPLE_REASON_REQUIRED = new ErrorCode(1_005_007_008, "请填写原因");

    // ========== 工装 1_005_008_xxx ==========
    ErrorCode TOOLING_NOT_EXISTS = new ErrorCode(1_005_008_000, "工装不存在");
    ErrorCode TOOLING_CODE_DUPLICATE = new ErrorCode(1_005_008_001, "工装编号「{}」已存在");
    ErrorCode TOOLING_CUSTOMER_REQUIRED = new ErrorCode(1_005_008_002, "客户资产必须选择客户");
    ErrorCode TOOLING_LIFE_EXCEEDED = new ErrorCode(1_005_008_003, "工装「{}」已达到设计寿命，不能继续使用");
    ErrorCode TOOLING_STATUS = new ErrorCode(1_005_008_004, "工装「{}」当前状态为{}，不能{}");
    ErrorCode TOOLING_HAS_USAGE = new ErrorCode(1_005_008_005, "工装已有使用记录，不能删除");
    ErrorCode TOOLING_FIELD_REQUIRED = new ErrorCode(1_005_008_006, "请填写{}");

    // ========== 认证 1_005_009_xxx ==========
    ErrorCode CERT_NOT_EXISTS = new ErrorCode(1_005_009_000, "证书不存在");
    ErrorCode CERT_DUPLICATE = new ErrorCode(1_005_009_001, "该证书已存在");
    ErrorCode CERT_DATE_RANGE = new ErrorCode(1_005_009_002, "到期日期不能早于发证日期");
    ErrorCode CERT_FILE_REQUIRED = new ErrorCode(1_005_009_003, "请上传证书文件");
}
