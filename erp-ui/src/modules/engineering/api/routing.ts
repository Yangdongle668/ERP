import { http, type PageParam, type PageResult } from '@/api/http'
import type { BomStatus } from './bom'

/** 工作中心与工艺路线（需求 05-04） */
export const WC_TYPE_OPTIONS = [
  { value: 'LINE', label: '产线' },
  { value: 'MACHINE', label: '设备' },
  { value: 'MANUAL', label: '人工' },
  { value: 'OUTSOURCE', label: '委外' }
]

export interface WorkCenterRow {
  id: string
  code: string
  name: string
  deptId: string
  deptName?: string
  wcType: string
  hoursPerShift: string
  shiftCount: number
  efficiencyPct: string
  capacityHoursPerDay: string
  laborRate?: string
  overheadRate?: string
  rateVisible: boolean
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  version: number
}

export interface WorkCenterSave {
  code: string
  name: string
  deptId?: string
  wcType?: string
  hoursPerShift?: string
  shiftCount?: number
  /** 0.85 = 85% */
  efficiencyPct?: string
  laborRate?: string
  overheadRate?: string
  remark?: string
  version?: number
}

export interface WorkCenterSimple { id: string; code: string; name: string; wcType: string }

export interface RoutingRow {
  id: string
  docNo: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  version: number
  isDefault: boolean
  stepCount: number
  totalRunSeconds: string
  description?: string
  status: BomStatus
  updatedByName?: string
  updatedAt: string
}

export interface StepRow {
  id?: string
  seq?: number
  operation?: string
  workCenterId?: string
  workCenterCode?: string
  workCenterName?: string
  wcType?: string
  setupMinutes?: string
  runSeconds?: string
  isReportPoint?: boolean
  isInspectionPoint?: boolean
  isOutsourced?: boolean
  remark?: string
}

export interface RoutingDetail {
  id: string
  docNo: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  materialType: string
  version: number
  isDefault: boolean
  description?: string
  remark?: string
  status: BomStatus
  copiedFromId?: string
  copiedFromNo?: string
  totalSetupMinutes: string
  totalRunSeconds: string
  byWorkCenter: { workCenterId: string; workCenterName: string; setupMinutes: string; runSeconds: string }[]
  createdByName?: string
  createdAt: string
  updatedByName?: string
  updatedAt: string
  rowVersion: number
  steps: StepRow[]
}

export interface RoutingSave {
  materialId: string
  description?: string
  remark?: string
  steps: StepRow[]
  rowVersion?: number
}

export interface RoutingQuery extends PageParam {
  materialId?: string
  keyword?: string
  statuses?: string
  defaultOnly?: boolean
  workCenterId?: string
}

export const workCenterApi = {
  page: (q: PageParam & { keyword?: string; wcType?: string; status?: string; deptId?: string }) => http.get<PageResult<WorkCenterRow>>('/engineering/work-centers', q),
  simple: () => http.get<WorkCenterSimple[]>('/engineering/work-centers/simple'),
  create: (data: WorkCenterSave) => http.post<string>('/engineering/work-centers', data),
  update: (id: string, data: WorkCenterSave) => http.put<void>(`/engineering/work-centers/${id}`, data),
  enable: (id: string) => http.post<void>(`/engineering/work-centers/${id}/enable`),
  disable: (id: string) => http.post<void>(`/engineering/work-centers/${id}/disable`),
  remove: (id: string) => http.delete<void>(`/engineering/work-centers/${id}`)
}

const BASE = '/engineering/routings'
export const routingApi = {
  page: (q: RoutingQuery) => http.get<PageResult<RoutingRow>>(BASE, q),
  get: (id: string) => http.get<RoutingDetail>(`${BASE}/${id}`),
  create: (data: RoutingSave) => http.post<{ id: string; warnings: string[] }>(BASE, data),
  update: (id: string, data: RoutingSave) => http.put<{ id: string; warnings: string[] }>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  approve: (id: string) => http.post<void>(`${BASE}/${id}/approve`),
  unapprove: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/unapprove`, { reason }),
  setDefault: (id: string) => http.post<void>(`${BASE}/${id}/set-default`),
  newVersion: (id: string) => http.post<string>(`${BASE}/${id}/new-version`),
  disable: (id: string) => http.post<void>(`${BASE}/${id}/disable`)
}
