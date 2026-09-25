import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'

/** 研发项目（需求 05-06） */
export const PROJECT_STATUS: StatusMap = {
  PLANNING: { label: '计划中', type: 'info' },
  IN_PROGRESS: { label: '进行中', type: 'primary' },
  ON_HOLD: { label: '已暂停', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const PROJECT_STATUS_OPTIONS = Object.entries(PROJECT_STATUS).map(([value, s]) => ({ value, label: s.label }))
export const TASK_STATUS: StatusMap = {
  TODO: { label: '未开始', type: 'info' },
  DOING: { label: '进行中', type: 'primary' },
  DONE: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const PROJECT_TYPE_OPTIONS = [
  { value: 'NPI', label: '新产品导入' },
  { value: 'IMPROVEMENT', label: '产品改进' },
  { value: 'CUSTOMER_CUSTOM', label: '客户定制' }
]
export const PRIORITY_OPTIONS = [
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' }
]
/** 阶段（字典 eng_project_stage 的内置项，顺序固定） */
export const STAGE_OPTIONS = [
  { value: 'CONCEPT', label: '概念' },
  { value: 'DESIGN', label: '设计' },
  { value: 'EVT', label: '工程验证' },
  { value: 'DVT', label: '设计验证' },
  { value: 'PVT', label: '生产验证' },
  { value: 'MP', label: '量产' }
]

export interface ProjectRow {
  id: string
  docNo: string
  name: string
  projectType: string
  customerId?: string
  customerName?: string
  productMaterialId?: string
  productCode?: string
  productName?: string
  pmUserId: string
  pmName?: string
  stage: string
  progressPct: string
  planStart: string
  planEnd: string
  overdue: boolean
  priority: string
  projectStatus: string
}

export interface MemberRow { userId: string; name?: string; deptName?: string; role?: string }
export interface TaskRow {
  id: string
  stage: string
  name: string
  ownerId: string
  ownerName?: string
  planStart: string
  planEnd: string
  actualEnd?: string
  taskStatus: string
  deliverable?: string
  weight: number
  remark?: string
  overdue: boolean
  fileCount: number
  canUpdate: boolean
}
export interface RelatedRow { docType: string; id: string; docNo: string; title?: string; status?: string }

export interface ProjectDetail extends Omit<ProjectRow, 'overdue'> {
  actualStart?: string
  actualEnd?: string
  description?: string
  cancelReason?: string
  members: MemberRow[]
  tasks: TaskRow[]
  related: RelatedRow[]
  undoneInStage: number
  undoneTotal: number
  canManage: boolean
  createdByName?: string
  createdAt: string
  version: number
}

export interface ProjectSave {
  name: string
  projectType?: string
  customerId?: string
  productMaterialId?: string
  pmUserId?: string
  priority?: string
  planStart?: string
  planEnd?: string
  description?: string
  members: { userId: string; role?: string }[]
  version?: number
}

export interface TaskSave {
  stage?: string
  name: string
  ownerId?: string
  planStart?: string
  planEnd?: string
  deliverable?: string
  weight?: number
  remark?: string
}

export interface ProjectQuery extends PageParam {
  docNo?: string
  name?: string
  customerId?: string
  pmUserId?: string
  stage?: string
  statuses?: string
  planEndFrom?: string
  planEndTo?: string
}

const BASE = '/engineering/projects'
export const projectApi = {
  page: (q: ProjectQuery) => http.get<PageResult<ProjectRow>>(BASE, q),
  get: (id: string) => http.get<ProjectDetail>(`${BASE}/${id}`),
  create: (data: ProjectSave) => http.post<string>(BASE, data),
  update: (id: string, data: ProjectSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  stage: (id: string, stage: string) => http.post<void>(`${BASE}/${id}/stage`, { stage }),
  hold: (id: string) => http.post<void>(`${BASE}/${id}/hold`),
  resume: (id: string) => http.post<void>(`${BASE}/${id}/resume`),
  complete: (id: string) => http.post<void>(`${BASE}/${id}/complete`),
  cancel: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/cancel`, { reason }),
  createTask: (id: string, data: TaskSave) => http.post<string>(`${BASE}/${id}/tasks`, data),
  updateTask: (id: string, taskId: string, data: TaskSave) => http.put<void>(`${BASE}/${id}/tasks/${taskId}`, data),
  removeTask: (id: string, taskId: string) => http.delete<void>(`${BASE}/${id}/tasks/${taskId}`),
  taskStatus: (id: string, taskId: string, status: string, deliverable?: string) =>
    http.post<void>(`${BASE}/${id}/tasks/${taskId}/status`, { status, deliverable })
}
