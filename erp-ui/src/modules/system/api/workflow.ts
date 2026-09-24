import { http, type PageParam, type PageResult } from '@/api/http'

export type FieldType = 'NUMBER' | 'STRING' | 'ENUM' | 'DICT' | 'DEPT' | 'USER' | 'BOOL'
export type ApproverType = 'USER' | 'ROLE' | 'DEPT_LEADER' | 'UPPER_DEPT_LEADER' | 'SUPERIOR' | 'BIZ_USER'

export interface BizField {
  code: string
  name: string
  type: FieldType
  options: { value: string; label: string }[]
  dictType?: string
}

export interface BizType {
  bizType: string
  name: string
  moduleCode: string
  moduleName: string
  /** NONE 未配置 / ENABLED 已启用 / DISABLED 已停用 */
  configStatus: 'NONE' | 'ENABLED' | 'DISABLED'
  hasDraft: boolean
  activeVersion?: number
  fields: BizField[]
  userFields: { code: string; name: string }[]
}

export interface Condition {
  field: string
  op: string
  /** 按字段类型：数字、字符串、布尔，或 ID / 值数组（IN、BETWEEN 等） */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  value: any
}

export interface NodeDef {
  id?: string
  seq?: number
  name: string
  approverType: ApproverType
  approverValue: unknown
  multiMode: 'ANY' | 'ALL'
  approverSummary?: string
}

export interface BranchDef {
  id?: string
  priority?: number
  name: string
  isDefault: boolean
  conditions: Condition[]
  nodes: NodeDef[]
}

export interface Definition {
  id: string
  bizType: string
  defVersion: number
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  enabled: boolean
  skipInitiator: boolean
  skipDuplicate: boolean
  emptyPolicy: 'AUTO_PASS' | 'TO_ADMIN'
  basedOn?: number
  publishedAt?: string
  publishedByName?: string
  remark?: string
  branches: BranchDef[]
}

export interface HistoryRow {
  id: string
  defVersion: number
  status: string
  publishedAt?: string
  publishedByName?: string
  remark?: string
}

export interface MonitorRow {
  id: string
  bizType: string
  bizTypeName: string
  bizId: string
  bizNo: string
  title: string
  initiatorName: string
  startedAt: string
  currentNodeName?: string
  assigneeNames: string[]
  pendingTasks: { taskId: string; assigneeId: string; assigneeName: string }[]
  stayMinutes?: number
  status: string
  detailRoute?: string
}

export interface InstanceQuery extends PageParam {
  bizType?: string
  bizNo?: string
  initiatorId?: string
  assigneeId?: string
  status?: string
  started?: [string, string]
}

export const INSTANCE_STATUS_OPTIONS = [
  { value: 'RUNNING', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'WITHDRAWN', label: '已撤回' },
  { value: 'TERMINATED', label: '已终止' }
]

export const APPROVER_TYPES: { value: ApproverType; label: string; defaultName: string }[] = [
  { value: 'DEPT_LEADER', label: '部门负责人', defaultName: '部门负责人审批' },
  { value: 'UPPER_DEPT_LEADER', label: '上级部门负责人', defaultName: '上级部门负责人审批' },
  { value: 'SUPERIOR', label: '直属上级', defaultName: '直属上级审批' },
  { value: 'USER', label: '指定人员', defaultName: '审批' },
  { value: 'ROLE', label: '指定角色', defaultName: '审批' },
  { value: 'BIZ_USER', label: '单据指定字段', defaultName: '确认' }
]

/** 各字段类型可用的运算符 */
export const OPS: Record<FieldType, { value: string; label: string }[]> = {
  NUMBER: [
    { value: 'GT', label: '大于' }, { value: 'GE', label: '大于等于' }, { value: 'LT', label: '小于' },
    { value: 'LE', label: '小于等于' }, { value: 'EQ', label: '等于' }, { value: 'BETWEEN', label: '介于' }
  ],
  STRING: [{ value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' }, { value: 'IN', label: '属于' }, { value: 'NOT_IN', label: '不属于' }],
  ENUM: [{ value: 'IN', label: '属于' }, { value: 'NOT_IN', label: '不属于' }, { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' }],
  DICT: [{ value: 'IN', label: '属于' }, { value: 'NOT_IN', label: '不属于' }, { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' }],
  DEPT: [{ value: 'IN_TREE', label: '在部门及下级中' }],
  USER: [{ value: 'IN', label: '属于' }],
  BOOL: [{ value: 'EQ', label: '等于' }]
}

export function instanceQueryParams(q: InstanceQuery) {
  const { started, ...rest } = q
  return { ...rest, startedFrom: started?.[0], startedTo: started?.[1] }
}

export const workflowApi = {
  bizTypes: () => http.get<BizType[]>('/system/workflow/biz-types'),
  definitions: (bizType: string) => http.get<{ active?: Definition; draft?: Definition }>('/system/workflow/definitions', { bizType }),
  definition: (id: string) => http.get<Definition>(`/system/workflow/definitions/${id}`),
  history: (bizType: string) => http.get<HistoryRow[]>('/system/workflow/definitions/history', { bizType }),
  draft: (bizType: string) => http.post<Definition>(`/system/workflow/definitions/draft?bizType=${encodeURIComponent(bizType)}`),
  saveDraft: (id: string, data: { skipInitiator: boolean; skipDuplicate: boolean; emptyPolicy: string; branches: BranchDef[] }) =>
    http.put<Definition>(`/system/workflow/definitions/${id}`, data),
  discard: (id: string) => http.delete<void>(`/system/workflow/definitions/${id}`),
  publish: (id: string, remark?: string) => http.post<void>(`/system/workflow/definitions/${id}/publish`, { remark }),
  setEnabled: (bizType: string, enabled: boolean) => http.post<void>(`/system/workflow/biz-types/${bizType}/enabled`, { enabled }),
  instances: (q: InstanceQuery) => http.get<PageResult<MonitorRow>>('/system/workflow/instances', instanceQueryParams(q)),
  terminate: (id: string, reason: string) => http.post<void>(`/system/workflow/instances/${id}/terminate`, { reason }),
  transfer: (taskId: string, toUserId: string, comment?: string) => http.post<void>(`/system/workflow/tasks/${taskId}/transfer`, { toUserId, comment })
}
