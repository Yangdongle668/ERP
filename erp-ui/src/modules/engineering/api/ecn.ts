import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'

/** ECN 工程变更（需求 05-05）。已审核 APPROVED、已生效 IN_PROGRESS、已关闭 COMPLETED */
export type EcnStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'IN_PROGRESS' | 'COMPLETED' | 'VOIDED'
export const ECN_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_APPROVAL: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已审核', type: 'primary' },
  IN_PROGRESS: { label: '已生效', type: 'success' },
  COMPLETED: { label: '已关闭', type: 'info', plain: true },
  VOIDED: { label: '已作废', type: 'danger', plain: true }
}
export const ECN_STATUS_OPTIONS = Object.entries(ECN_STATUS).map(([value, s]) => ({ value, label: s.label }))
export const ACTION_OPTIONS = [
  { value: 'ADD', label: '新增子件' },
  { value: 'REMOVE', label: '删除子件' },
  { value: 'REPLACE', label: '替换子件' },
  { value: 'CHANGE_QTY', label: '修改用量' }
]
export const MODE_OPTIONS = [
  { value: 'IMMEDIATE', label: '审批后立即生效' },
  { value: 'DATE', label: '指定日期生效' },
  { value: 'USE_UP', label: '旧料用完后手工切换' }
]
export const URGENCY_OPTIONS = [
  { value: 'NORMAL', label: '普通' },
  { value: 'URGENT', label: '紧急' }
]
export const IMPACT_TYPE_OPTIONS = [
  { value: 'STOCK', label: '库存' },
  { value: 'PURCHASE', label: '在途采购' },
  { value: 'WIP', label: '在制生产订单' },
  { value: 'SALES', label: '未完成销售订单' }
]
/** 处理方式可选项按影响类型 */
export const HANDLING_OPTIONS: Record<string, { value: string; label: string }[]> = {
  STOCK: [{ value: 'CONTINUE_USE', label: '继续使用' }, { value: 'REWORK', label: '返工' }, { value: 'SCRAP', label: '报废' }, { value: 'RETURN_SUPPLIER', label: '退供应商' }],
  PURCHASE: [{ value: 'CANCEL', label: '取消' }, { value: 'KEEP', label: '保留' }],
  WIP: [{ value: 'UPDATE_WIP', label: '更新用料' }, { value: 'KEEP', label: '保留原用料' }],
  SALES: [{ value: 'NO_ACTION', label: '无需处理' }]
}
export const DEPT_ROLE_OPTIONS = [
  { value: 'WAREHOUSE', label: '仓库' },
  { value: 'PURCHASE', label: '采购' },
  { value: 'PRODUCTION', label: '生产' },
  { value: 'QUALITY', label: '品质' },
  { value: 'PMC', label: 'PMC' },
  { value: 'CERT', label: '认证' }
]

export interface EcnRow {
  id: string
  docNo: string
  title: string
  ecnType: string
  reasonType: string
  urgency: string
  effectiveMode: string
  effectiveDate?: string
  status: EcnStatus
  createdByName?: string
  docDate: string
}

export interface EcnLine {
  id?: string
  lineNo?: number
  bomId?: string
  bomNo?: string
  parentId?: string
  parentCode?: string
  parentName?: string
  action?: string
  oldComponentId?: string
  oldCode?: string
  oldName?: string
  newComponentId?: string
  newCode?: string
  newName?: string
  uom?: string
  oldQtyPer?: string
  newQtyPer?: string
  oldScrapRate?: string
  newScrapRate?: string
  positionNo?: string
  newBomId?: string
  newBomNo?: string
  remark?: string
}

export interface EcnImpactRow {
  id: string
  materialId: string
  materialCode?: string
  materialName?: string
  uom?: string
  impactType: string
  docNo?: string
  qty: string
  handling?: string
  handlingRemark?: string
}

export interface EcnTaskRow {
  id?: string
  deptRole: string
  assigneeId?: string
  assigneeName?: string
  content: string
  taskStatus?: string
  doneRemark?: string
  doneByName?: string
  doneAt?: string
  mine?: boolean
}

export interface EcnDetail {
  id: string
  docNo: string
  docDate: string
  title: string
  ecnType: string
  reasonType: string
  reason: string
  urgency: string
  effectiveMode: string
  effectiveDate?: string
  customerId?: string
  customerName?: string
  analyzed: boolean
  keyPart: boolean
  status: EcnStatus
  approvedAt?: string
  effectedAt?: string
  closedAt?: string
  createdByName?: string
  createdAt: string
  version: number
  lines: EcnLine[]
  impacts: EcnImpactRow[]
  tasks: EcnTaskRow[]
  pendingTasks: number
}

export interface EcnSave {
  title: string
  ecnType?: string
  reasonType?: string
  reason: string
  urgency: string
  effectiveMode: string
  effectiveDate?: string
  customerId?: string
  lines: { bomId: string; action: string; oldComponentId?: string; newComponentId?: string; newQtyPer?: string; newScrapRate?: string; positionNo?: string; remark?: string }[]
  impacts?: { id: string; handling?: string; handlingRemark?: string }[]
  tasks?: { deptRole: string; assigneeId?: string; content: string }[]
  fileIds?: string[]
  version?: number
}

export interface EcnQuery extends PageParam {
  docNo?: string
  title?: string
  ecnType?: string
  statuses?: string
  materialId?: string
  dateFrom?: string
  dateTo?: string
}

const BASE = '/engineering/ecns'
export const ecnApi = {
  page: (q: EcnQuery) => http.get<PageResult<EcnRow>>(BASE, q),
  get: (id: string) => http.get<EcnDetail>(`${BASE}/${id}`),
  create: (data: EcnSave) => http.post<string>(BASE, data),
  update: (id: string, data: EcnSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  batchReplacePreview: (oldComponentId: string, newComponentId: string, newQtyPer?: string) =>
    http.post<EcnLine[]>(`${BASE}/batch-replace-preview`, { oldComponentId, newComponentId, newQtyPer }),
  analyze: (id: string) => http.post<void>(`${BASE}/${id}/analyze`),
  submit: (id: string) => http.post<{ status: EcnStatus; keyPartWarning?: string }>(`${BASE}/${id}/submit`),
  void: (id: string, reason?: string) => http.post<void>(`${BASE}/${id}/void`, { reason }),
  effect: (id: string) => http.post<void>(`${BASE}/${id}/effect`),
  close: (id: string) => http.post<void>(`${BASE}/${id}/close`),
  taskDone: (id: string, taskId: string, remark?: string) => http.post<void>(`${BASE}/${id}/tasks/${taskId}/done`, { remark })
}
