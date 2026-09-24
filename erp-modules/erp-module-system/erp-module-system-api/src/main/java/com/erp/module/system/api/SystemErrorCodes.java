package com.erp.module.system.api;

import com.erp.common.exception.ErrorCode;

/** 系统管理模块错误码，号段 1_001_000_000 ~ 1_001_999_999。提示文字与需求文档“提示原文”一致。 */
public interface SystemErrorCodes {

    // ========== 认证与个人中心 1_001_001_xxx ==========
    ErrorCode AUTH_BAD_CREDENTIALS = new ErrorCode(1_001_001_000, "用户名或密码错误");
    ErrorCode AUTH_USER_DISABLED = new ErrorCode(1_001_001_001, "账号已停用，请联系管理员");
    ErrorCode AUTH_USER_LOCKED = new ErrorCode(1_001_001_002, "密码错误次数过多，账号已锁定，请 {} 分钟后再试");
    ErrorCode AUTH_REFRESH_TOKEN_INVALID = new ErrorCode(1_001_001_003, "登录已过期，请重新登录");
    ErrorCode AUTH_CAPTCHA_ERROR = new ErrorCode(1_001_001_004, "验证码错误");
    ErrorCode AUTH_CAPTCHA_EXPIRED = new ErrorCode(1_001_001_005, "验证码已过期，请刷新");
    ErrorCode AUTH_OLD_PASSWORD_WRONG = new ErrorCode(1_001_001_006, "原密码不正确");
    ErrorCode AUTH_PASSWORD_TOO_SHORT = new ErrorCode(1_001_001_007, "密码长度不能少于 {} 位");
    ErrorCode AUTH_PASSWORD_LETTER_DIGIT = new ErrorCode(1_001_001_008, "密码必须同时包含字母和数字");
    ErrorCode AUTH_PASSWORD_STRONG = new ErrorCode(1_001_001_009, "密码必须包含大写字母、小写字母、数字、特殊字符中的至少三种");
    ErrorCode AUTH_PASSWORD_REUSED = new ErrorCode(1_001_001_010, "新密码不能与最近 {} 次使用的密码相同");
    ErrorCode AUTH_PASSWORD_CONTAINS_USERNAME = new ErrorCode(1_001_001_011, "密码不能包含用户名");
    ErrorCode AUTH_PASSWORD_TOO_LONG = new ErrorCode(1_001_001_012, "密码长度不能超过 64 位");

    // ========== 编码规则 1_001_002_xxx ==========
    ErrorCode CODE_RULE_NOT_FOUND = new ErrorCode(1_001_002_000, "未配置编码规则【{}】");
    ErrorCode CODE_RULE_VAR_INVALID = new ErrorCode(1_001_002_001, "前缀中的变量「{}」不可用，可用变量：{}");
    ErrorCode CODE_RULE_RESET_MISMATCH = new ErrorCode(1_001_002_002, "重置周期为“{}”时，日期格式必须包含{}");
    ErrorCode CODE_RULE_SEQ_TOO_SMALL = new ErrorCode(1_001_002_003, "新值必须大于当前值 {}");
    ErrorCode CODE_RULE_TOO_LONG = new ErrorCode(1_001_002_004, "生成的编码过长，请检查编码规则【{}】");
    ErrorCode CODE_RULE_VAR_MISSING = new ErrorCode(1_001_002_005, "生成编码【{}】缺少变量「{}」");
    ErrorCode CODE_RULE_PREFIX_INVALID = new ErrorCode(1_001_002_006, "前缀只能包含字母、数字、- _ / 和变量 {变量名}");
    ErrorCode CODE_RULE_DATE_INVALID = new ErrorCode(1_001_002_007, "不支持的日期格式「{}」");

    // ========== 组织 1_001_003_xxx ==========
    ErrorCode ORG_NOT_EXISTS = new ErrorCode(1_001_003_000, "组织不存在");
    ErrorCode ORG_DEPT_CANNOT_HAVE_COMPANY = new ErrorCode(1_001_003_001, "部门下不能创建公司");
    ErrorCode ORG_ROOT_MUST_BE_COMPANY = new ErrorCode(1_001_003_002, "顶级组织必须是公司");
    ErrorCode ORG_PARENT_CYCLE = new ErrorCode(1_001_003_003, "上级组织不能是自己或自己的下级");
    ErrorCode ORG_TOO_DEEP = new ErrorCode(1_001_003_004, "组织层级不能超过 8 级");
    ErrorCode ORG_CODE_DUPLICATE = new ErrorCode(1_001_003_005, "组织编码「{}」已存在");
    ErrorCode ORG_HAS_ENABLED_CHILDREN = new ErrorCode(1_001_003_006, "请先停用下级组织");
    ErrorCode ORG_HAS_ENABLED_USERS = new ErrorCode(1_001_003_007, "该部门下还有 {} 个启用的用户，请先调整用户部门或停用用户");
    ErrorCode ORG_PARENT_DISABLED = new ErrorCode(1_001_003_008, "上级组织已停用，请先启用上级组织");
    ErrorCode ORG_HAS_CHILDREN = new ErrorCode(1_001_003_009, "该组织存在下级组织，不能删除");
    ErrorCode ORG_HAS_USERS = new ErrorCode(1_001_003_010, "该组织下存在用户，不能删除");
    ErrorCode ORG_REFERENCED = new ErrorCode(1_001_003_011, "该组织已被业务数据使用，只能停用");
    ErrorCode ORG_NAME_DUPLICATE = new ErrorCode(1_001_003_012, "同一上级下已存在名称为「{}」的组织");
    ErrorCode ORG_LAST_COMPANY = new ErrorCode(1_001_003_013, "至少需要保留一个启用的公司");
    ErrorCode ORG_DISABLED = new ErrorCode(1_001_003_014, "部门「{}」已停用");

    // ========== 用户 1_001_004_xxx ==========
    ErrorCode USER_NOT_EXISTS = new ErrorCode(1_001_004_000, "用户不存在");
    ErrorCode USER_USERNAME_DUPLICATE = new ErrorCode(1_001_004_001, "用户名「{}」已存在");
    ErrorCode USER_EMPLOYEE_NO_DUPLICATE = new ErrorCode(1_001_004_002, "工号「{}」已被用户「{}」使用");
    ErrorCode USER_MOBILE_DUPLICATE = new ErrorCode(1_001_004_003, "手机号已被用户「{}」使用");
    ErrorCode USER_SUPERIOR_CYCLE = new ErrorCode(1_001_004_004, "直属上级不能是自己或自己的下属");
    ErrorCode USER_ROLE_REQUIRED = new ErrorCode(1_001_004_005, "请至少分配一个角色");
    ErrorCode USER_SUPER_ADMIN_ROLE_ONLY = new ErrorCode(1_001_004_006, "只有超级管理员可以分配超级管理员角色");
    ErrorCode USER_CANNOT_DISABLE_SELF = new ErrorCode(1_001_004_007, "不能停用当前登录的账号");
    ErrorCode USER_CANNOT_DISABLE_ADMIN = new ErrorCode(1_001_004_008, "内置超级管理员不能停用");
    ErrorCode USER_INIT_PASSWORD_REQUIRED = new ErrorCode(1_001_004_009, "请先在系统参数中设置用户初始密码");
    ErrorCode USER_USERNAME_INVALID = new ErrorCode(1_001_004_010, "用户名为 4～32 位，字母开头，只能包含字母、数字、. _ -");
    ErrorCode USER_PART_DEPT_INVALID = new ErrorCode(1_001_004_011, "兼职部门不能包含主部门，且最多 10 个");

    // ========== 角色与权限 1_001_005_xxx ==========
    ErrorCode ROLE_NOT_EXISTS = new ErrorCode(1_001_005_000, "角色不存在");
    ErrorCode ROLE_CODE_DUPLICATE = new ErrorCode(1_001_005_001, "角色编码「{}」已存在");
    ErrorCode ROLE_NAME_DUPLICATE = new ErrorCode(1_001_005_002, "角色名称「{}」已存在");
    ErrorCode ROLE_BUILTIN = new ErrorCode(1_001_005_003, "内置角色不能修改");
    ErrorCode ROLE_DISABLE_ORPHAN_USERS = new ErrorCode(1_001_005_004, "停用后以下用户将没有任何可用角色：{}，请先为他们分配其他角色");
    ErrorCode ROLE_HAS_USERS = new ErrorCode(1_001_005_005, "该角色下还有 {} 个用户，请先移除");
    ErrorCode ROLE_PERMISSION_NOT_EXISTS = new ErrorCode(1_001_005_006, "权限点「{}」不存在");
    ErrorCode ROLE_MEMBER_LAST_ROLE = new ErrorCode(1_001_005_007, "用户「{}」只有这一个角色，不能移除");
    ErrorCode ROLE_CUSTOM_DEPT_REQUIRED = new ErrorCode(1_001_005_008, "数据范围为自定义时请选择部门");
    ErrorCode ROLE_DISABLED = new ErrorCode(1_001_005_009, "角色「{}」已停用");

    // ========== 数据字典 1_001_006_xxx ==========
    ErrorCode DICT_TYPE_NOT_EXISTS = new ErrorCode(1_001_006_000, "字典类型不存在");
    ErrorCode DICT_TYPE_DUPLICATE = new ErrorCode(1_001_006_001, "字典类型「{}」已存在");
    ErrorCode DICT_VALUE_DUPLICATE = new ErrorCode(1_001_006_002, "字典值「{}」已存在");
    ErrorCode DICT_LABEL_DUPLICATE = new ErrorCode(1_001_006_003, "字典标签「{}」已存在");
    ErrorCode DICT_TYPE_BUILTIN = new ErrorCode(1_001_006_004, "内置字典不能删除");
    ErrorCode DICT_TYPE_HAS_ITEMS = new ErrorCode(1_001_006_005, "请先删除该类型下的字典项");
    ErrorCode DICT_ITEM_REFERENCED = new ErrorCode(1_001_006_006, "该字典项已被使用，只能停用");
    ErrorCode DICT_VALUE_INVALID = new ErrorCode(1_001_006_007, "{}的值「{}」无效");
    ErrorCode DICT_ITEM_NOT_EXISTS = new ErrorCode(1_001_006_008, "字典项不存在");
    ErrorCode DICT_ITEM_BUILTIN = new ErrorCode(1_001_006_009, "内置字典项不能删除、停用或修改值");

    // ========== 计量单位 1_001_007_xxx ==========
    ErrorCode UOM_NOT_EXISTS = new ErrorCode(1_001_007_000, "计量单位「{}」不存在或已停用");
    ErrorCode UOM_CODE_DUPLICATE = new ErrorCode(1_001_007_001, "单位编码「{}」已存在");
    ErrorCode UOM_NAME_DUPLICATE = new ErrorCode(1_001_007_002, "单位名称「{}」已存在");
    ErrorCode UOM_REFERENCED = new ErrorCode(1_001_007_003, "该单位已被使用，只能停用");
    ErrorCode UOM_CATEGORY_LOCKED = new ErrorCode(1_001_007_004, "该单位已被使用，不能修改类别");
    ErrorCode UOM_PRECISION_DECREASE = new ErrorCode(1_001_007_005, "单位精度只能调大");
    ErrorCode UOM_CONVERSION_CATEGORY = new ErrorCode(1_001_007_006, "只能在同类别单位之间换算");
    ErrorCode UOM_CONVERSION_DUPLICATE = new ErrorCode(1_001_007_007, "该换算已存在");
    ErrorCode UOM_NO_CONVERSION = new ErrorCode(1_001_007_008, "单位 {} 与 {} 之间没有换算关系");
    ErrorCode UOM_BUILTIN = new ErrorCode(1_001_007_009, "内置单位不能删除");
    ErrorCode UOM_CONVERSION_SAME = new ErrorCode(1_001_007_010, "源单位与目标单位不能相同");

    // ========== 币别汇率 1_001_008_xxx ==========
    ErrorCode CURRENCY_NOT_EXISTS = new ErrorCode(1_001_008_000, "币别「{}」不存在或已停用");
    ErrorCode CURRENCY_DUPLICATE = new ErrorCode(1_001_008_001, "币别「{}」已存在");
    ErrorCode CURRENCY_BASE_CANNOT_DISABLE = new ErrorCode(1_001_008_002, "本位币不能停用");
    ErrorCode CURRENCY_BASE_LOCKED = new ErrorCode(1_001_008_003, "系统中已有业务数据，不能修改本位币");
    ErrorCode RATE_DUPLICATE = new ErrorCode(1_001_008_004, "{} 在 {} 已有{}");
    ErrorCode RATE_NOT_FOUND = new ErrorCode(1_001_008_005, "未维护币别 {} 在 {} 及之前的汇率，请先维护汇率");
    ErrorCode RATE_INVALID = new ErrorCode(1_001_008_006, "汇率必须大于 0");
    ErrorCode RATE_BASE_CURRENCY = new ErrorCode(1_001_008_007, "本位币不需要维护汇率");
    ErrorCode RATE_NOT_EXISTS = new ErrorCode(1_001_008_008, "汇率记录不存在");
    ErrorCode CURRENCY_PRECISION_LOCKED = new ErrorCode(1_001_008_009, "系统中已有业务数据，不能修改金额精度");

    // ========== 付款条件 1_001_009_xxx ==========
    ErrorCode PAYMENT_TERM_NOT_EXISTS = new ErrorCode(1_001_009_000, "付款条件不存在或已停用");
    ErrorCode PAYMENT_TERM_CODE_DUPLICATE = new ErrorCode(1_001_009_001, "付款条件编码「{}」已存在");
    ErrorCode PAYMENT_TERM_PERCENT_SUM = new ErrorCode(1_001_009_002, "付款节点比例合计必须等于 100%");
    ErrorCode PAYMENT_TERM_REFERENCED = new ErrorCode(1_001_009_003, "该付款条件已被使用，只能停用");
    ErrorCode PAYMENT_TERM_USAGE_MISMATCH = new ErrorCode(1_001_009_004, "付款条件「{}」不适用于{}");

    // ========== 系统参数 1_001_010_xxx ==========
    ErrorCode PARAM_NOT_EXISTS = new ErrorCode(1_001_010_000, "系统参数「{}」不存在");
    ErrorCode PARAM_OUT_OF_RANGE = new ErrorCode(1_001_010_001, "参数「{}」的值必须在 {}～{} 之间");
    ErrorCode PARAM_TYPE_INVALID = new ErrorCode(1_001_010_002, "参数「{}」的值格式不正确：{}");

    // ========== 日志 1_001_011_xxx ==========
    ErrorCode LOG_RANGE_TOO_LARGE = new ErrorCode(1_001_011_000, "查询时间范围不能超过 3 个月");

    // ========== 附件 1_001_012_xxx ==========
    ErrorCode FILE_NOT_EXISTS = new ErrorCode(1_001_012_000, "附件不存在或已删除");
    ErrorCode FILE_TOO_LARGE = new ErrorCode(1_001_012_001, "文件大小不能超过 {}MB");
    ErrorCode FILE_TYPE_NOT_ALLOWED = new ErrorCode(1_001_012_002, "不支持的文件类型：{}");
    ErrorCode FILE_ACCESS_DENIED = new ErrorCode(1_001_012_003, "没有权限访问该附件");
    ErrorCode FILE_EMPTY = new ErrorCode(1_001_012_004, "不能上传空文件");
    ErrorCode FILE_STORAGE_FAILED = new ErrorCode(1_001_012_005, "附件保存失败，请稍后重试");

    // ========== 后台任务 1_001_013_xxx ==========
    ErrorCode TASK_NOT_EXISTS = new ErrorCode(1_001_013_000, "任务不存在");
    ErrorCode TASK_CANNOT_CANCEL = new ErrorCode(1_001_013_001, "只能取消等待中的任务");
    ErrorCode TASK_INTERRUPTED = new ErrorCode(1_001_013_002, "服务重启，任务中断，请重新提交");

    // ========== 定时任务 1_001_014_xxx ==========
    ErrorCode JOB_NOT_EXISTS = new ErrorCode(1_001_014_000, "定时任务「{}」不存在");
    ErrorCode JOB_CRON_INVALID = new ErrorCode(1_001_014_001, "Cron 表达式不正确");
    ErrorCode JOB_RUNNING = new ErrorCode(1_001_014_002, "任务正在执行中");

    // ========== 打印 1_001_015_xxx ==========
    ErrorCode PRINT_TEMPLATE_NOT_EXISTS = new ErrorCode(1_001_015_000, "打印模板不存在");
    ErrorCode PRINT_TEMPLATE_SCRIPT = new ErrorCode(1_001_015_001, "模板中不允许包含脚本");
    ErrorCode PRINT_TEMPLATE_TOO_LARGE = new ErrorCode(1_001_015_002, "模板内容不能超过 200KB");
    ErrorCode PRINT_TEMPLATE_BUILTIN = new ErrorCode(1_001_015_003, "内置模板不能修改或删除，请复制后修改");
    ErrorCode PRINT_TEMPLATE_DEFAULT = new ErrorCode(1_001_015_004, "默认模板不能停用或删除");
    ErrorCode PRINT_NO_TEMPLATE = new ErrorCode(1_001_015_005, "单据类型「{}」没有可用的打印模板");
    ErrorCode PRINT_BIZ_NOT_EXISTS = new ErrorCode(1_001_015_006, "打印单据类型「{}」不存在");

    // ========== 审批流 1_001_016_xxx ==========
    ErrorCode WF_BIZ_TYPE_NOT_EXISTS = new ErrorCode(1_001_016_000, "单据类型「{}」不存在或未声明审批");
    ErrorCode WF_DEFINITION_NOT_EXISTS = new ErrorCode(1_001_016_001, "流程版本不存在");
    ErrorCode WF_NOT_DRAFT = new ErrorCode(1_001_016_002, "只能修改草稿版本");
    ErrorCode WF_NO_DEFAULT_BRANCH = new ErrorCode(1_001_016_003, "流程必须有“其他情况”分支");
    ErrorCode WF_BRANCH_NO_NODE = new ErrorCode(1_001_016_004, "分支「{}」至少需要一个审批节点");
    ErrorCode WF_BRANCH_NO_CONDITION = new ErrorCode(1_001_016_005, "分支「{}」至少需要一个条件");
    ErrorCode WF_CONDITION_INCOMPLETE = new ErrorCode(1_001_016_006, "分支「{}」的条件不完整");
    ErrorCode WF_CONDITION_FIELD = new ErrorCode(1_001_016_007, "分支「{}」的条件字段「{}」不存在");
    ErrorCode WF_NODE_INCOMPLETE = new ErrorCode(1_001_016_008, "节点「{}」的审批人未设置");
    ErrorCode WF_NODE_USER_DISABLED = new ErrorCode(1_001_016_009, "节点「{}」的审批人「{}」已停用");
    ErrorCode WF_NODE_ROLE_INVALID = new ErrorCode(1_001_016_010, "节点「{}」的角色不存在或已停用");
    ErrorCode WF_NODE_BIZ_FIELD = new ErrorCode(1_001_016_011, "节点「{}」的单据字段「{}」不存在");
    ErrorCode WF_NO_ACTIVE_VERSION = new ErrorCode(1_001_016_012, "该单据还没有已发布的审批流程");
    ErrorCode WF_ALREADY_RUNNING = new ErrorCode(1_001_016_013, "该单据正在审批中，不能重复提交");
    ErrorCode WF_TASK_NOT_HANDLER = new ErrorCode(1_001_016_014, "你不是该任务的处理人或任务已处理");
    ErrorCode WF_REJECT_COMMENT_REQUIRED = new ErrorCode(1_001_016_015, "请填写驳回意见");
    ErrorCode WF_COMMENT_TOO_LONG = new ErrorCode(1_001_016_016, "审批意见不能超过 500 字");
    ErrorCode WF_WITHDRAW_NOT_INITIATOR = new ErrorCode(1_001_016_017, "只有发起人可以撤回");
    ErrorCode WF_NOT_RUNNING = new ErrorCode(1_001_016_018, "审批已结束，不能撤回");
    ErrorCode WF_TRANSFER_SELF = new ErrorCode(1_001_016_019, "不能转交给自己");
    ErrorCode WF_TRANSFER_DUPLICATE = new ErrorCode(1_001_016_020, "{} 已是该节点的审批人");
    ErrorCode WF_TRANSFER_TARGET_INVALID = new ErrorCode(1_001_016_021, "转交对象不存在或已停用");
    ErrorCode WF_INSTANCE_NOT_EXISTS = new ErrorCode(1_001_016_022, "审批实例不存在");
    ErrorCode WF_TERMINATE_REASON_REQUIRED = new ErrorCode(1_001_016_023, "请填写终止原因");
    ErrorCode WF_INSTANCE_FINISHED = new ErrorCode(1_001_016_024, "审批已结束");
}
