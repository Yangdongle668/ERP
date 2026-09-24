/** 公共组件的配置类型 */

export type TagColor = '' | 'primary' | 'success' | 'warning' | 'danger' | 'info'

/** 状态 → 标签文字与颜色（StatusTag、ErpTable 状态列） */
export type StatusMap = Record<string, { label: string; type: TagColor; plain?: boolean }>

export interface Option {
  value: string | number | boolean
  label: string
}

/** ErpTable 列定义 */
export interface TableColumn<T = any> {
  /** 字段名；也是列设置中的唯一键（无 prop 的列请给 key） */
  prop?: string
  key?: string
  label: string
  width?: number | string
  minWidth?: number | string
  /** 默认按 type 决定：数字类右对齐，状态/日期/是否类居中，其他左对齐 */
  align?: 'left' | 'center' | 'right'
  /**
   * 显示格式：
   * text 文本；link 蓝色链接（点击触发 onClick）；qty 数量（precision 或 uomProp 决定小数位）；
   * amount 金额（currencyProp 决定精度）；price 单价；rate 汇率；percent 百分比；
   * date 日期；datetime 日期时间（到分钟）；dict 字典标签；status 状态标签；enum 枚举文字；bool 是/否
   */
  type?: 'text' | 'link' | 'qty' | 'amount' | 'price' | 'rate' | 'percent' | 'date' | 'datetime' | 'dict' | 'status' | 'enum' | 'bool'
  dictType?: string
  statusMap?: StatusMap
  options?: Option[]
  precision?: number
  /** 数量列：取行上的单位字段决定精度 */
  uomProp?: string
  /** 金额列：取行上的币别字段决定精度 */
  currencyProp?: string
  sortable?: boolean
  fixed?: 'left' | 'right'
  /** 默认隐藏（列设置中可打开） */
  hidden?: boolean
  /** 显示合计（当前页合计） */
  summary?: boolean
  /** 自定义显示文本 */
  formatter?: (row: T) => string
  /** 使用具名插槽 `col-<prop>` 自定义单元格 */
  slot?: boolean
  /** link 类型的点击 */
  onClick?: (row: T) => void
  /** 不显示悬停提示 */
  noTooltip?: boolean
}

/** ErpSearchForm 查询条件定义 */
export interface SearchField {
  prop: string
  label: string
  /**
   * input 文本；number 数字；select 下拉（options）；dict 字典下拉；date 日期；
   * daterange / datetimerange 区间（prop 对应数组 [start, end]）；user 用户；org 部门；
   * uom 单位；currency 币别；slot 自定义（插槽 `field-<prop>`）
   */
  type?: 'input' | 'number' | 'select' | 'dict' | 'date' | 'daterange' | 'datetimerange' | 'user' | 'org' | 'uom' | 'currency' | 'slot'
  options?: Option[]
  dictType?: string
  placeholder?: string
  /** 输入框值自动转大写（编码、单号） */
  upper?: boolean
  multiple?: boolean
  clearable?: boolean
}

/** 单据页头按钮（DocPageHeader） */
export interface DocAction {
  key: string
  label: string
  type?: 'primary' | 'default' | 'danger'
  permission?: string
  /** 返回 false 时隐藏（按状态） */
  visible?: () => boolean
  /** 二次确认文案 */
  confirm?: string
  /** 需要填写原因（打开 ReasonDialog） */
  reasonRequired?: boolean
  reasonTitle?: string
  /** 常用原因 */
  reasonOptions?: string[]
  /** 执行动作，reason 为原因弹窗输入 */
  handler: (reason?: string) => Promise<unknown> | unknown
}

/** LinesEditor 列定义 */
export interface LineColumn<R = any> {
  prop: string
  label: string
  width?: number
  minWidth?: number
  /**
   * material 物料编码单元格（输入编码回车带出，或打开选择器）；
   * text 文本；qty 数量；price 单价；amount 金额；number 数字；date 日期；select 下拉；dict 字典；
   * readonly 只读文本（带出字段、计算字段）
   */
  type: 'material' | 'text' | 'qty' | 'price' | 'amount' | 'number' | 'date' | 'select' | 'dict' | 'readonly'
  required?: boolean
  options?: Option[]
  dictType?: string
  precision?: number
  /** 数量列：取行上的单位字段决定精度 */
  uomProp?: string
  min?: number
  /** 允许负数（默认不允许） */
  allowNegative?: boolean
  /** 行级可编辑判断 */
  editable?: (row: R) => boolean
  /** 自定义校验，返回错误信息 */
  validate?: (value: unknown, row: R) => string | undefined
  /** 底部合计 */
  summary?: boolean
  /** 只读列的显示 */
  formatter?: (row: R) => string
  /** 物料列：可选的物料类型过滤 */
  materialTypes?: string[]
}

/** 表格行操作（RowActions） */
export interface RowAction {
  label: string
  permission?: string
  /** 返回 false 时隐藏（按状态）。不可用按钮隐藏而不是置灰 */
  visible?: boolean
  danger?: boolean
  /** 二次确认文案 */
  confirm?: string
  handler: () => unknown
}

/** 审批弹窗结果（ApproveDialog） */
export interface ApproveResult {
  result: 'APPROVE' | 'REJECT'
  comment: string
}

/** 关联单据（RelatedDocs） */
export interface RelatedDoc {
  direction: 'UP' | 'DOWN'
  docTypeName: string
  docNo: string
  docDate?: string
  status?: string
  statusLabel?: string
  /** 前端详情路由，如 /sales/order/123 */
  route?: string
}

/** 审批记录（ApprovalTimeline / ApprovalActions，数据来自 /system/workflow/instances/by-biz） */
export interface WfTask {
  id: string
  nodeName: string
  assigneeName: string
  status: string
  comment?: string
  autoReason?: string
  transferToName?: string
  handledByName?: string
  createdAt: string
  finishedAt?: string
}
export interface WfInstance {
  id: string
  status: string
  initiatorName: string
  startedAt: string
  finishedAt?: string
  resultComment?: string
  tasks: WfTask[]
}
export interface ByBiz {
  instances: WfInstance[]
  myPendingTaskId?: string
  canWithdraw?: boolean
  runningInstanceId?: string
}
