import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'

/** 工装（需求 05-08） */
export const TOOLING_STATUS: StatusMap = {
  IN_STOCK: { label: '在库', type: 'success' },
  IN_USE: { label: '使用中', type: 'primary' },
  LENT: { label: '借出', type: 'warning' },
  REPAIRING: { label: '维修中', type: 'danger' },
  SCRAPPED: { label: '报废', type: 'info', plain: true }
}
export const TOOLING_STATUS_OPTIONS = Object.entries(TOOLING_STATUS).map(([value, s]) => ({ value, label: s.label }))
export const OWNERSHIP_OPTIONS = [
  { value: 'OWN', label: '自有' },
  { value: 'CUSTOMER', label: '客户资产' }
]
export const RECORD_TYPE_OPTIONS = [
  { value: 'LEND', label: '借出' },
  { value: 'RETURN', label: '归还' },
  { value: 'MAINTAIN', label: '保养' },
  { value: 'REPAIR_START', label: '送修' },
  { value: 'REPAIR_END', label: '修复' },
  { value: 'SCRAP', label: '报废' },
  { value: 'ADJUST', label: '次数调整' },
  { value: 'USAGE', label: '使用' }
]

export interface MaterialRef { id: string; code: string; name: string }

export interface ToolingRow {
  id: string
  code: string
  name: string
  toolingType: string
  spec?: string
  ownership: string
  customerId?: string
  customerName?: string
  cavity: number
  designLife?: number
  usedCount: number
  /** 0.9 = 90% */
  lifePct?: string
  lifeWarn: boolean
  maintainCycle?: number
  lastMaintainCount: number
  toMaintain?: number
  location?: string
  toolingStatus: string
  holderId?: string
  holderName?: string
  supplierName?: string
  purchaseDate?: string
  purchaseAmount?: string
  allowOverLife: boolean
  overLifeReason?: string
  materialIds: string[]
  materials: MaterialRef[]
  remark?: string
  version: number
}

export interface ToolingSave {
  code?: string
  name: string
  toolingType?: string
  spec?: string
  ownership: string
  customerId?: string
  cavity?: number
  designLife?: number
  initialUsedCount?: number
  maintainCycle?: number
  location?: string
  supplierName?: string
  purchaseDate?: string
  purchaseAmount?: string
  allowOverLife?: boolean
  overLifeReason?: string
  materialIds: string[]
  remark?: string
  version?: number
}

export interface RecordReq {
  userId?: string
  content?: string
  vendor?: string
  cost?: string
  date?: string
  expectedReturn?: string
  count?: number
}

export interface RecordRow {
  id: string
  recordType: string
  count?: number
  userId?: string
  userName?: string
  sourceDocNo?: string
  content?: string
  cost?: string
  occurredAt: string
  createdByName?: string
}

export interface ToolingQuery extends PageParam {
  code?: string
  name?: string
  toolingType?: string
  ownership?: string
  toolingStatus?: string
  materialId?: string
  lifeWarning?: boolean
}

export type RecordAction = 'lend' | 'return' | 'maintain' | 'repair-start' | 'repair-end' | 'scrap' | 'adjust'

const BASE = '/engineering/toolings'
export const toolingApi = {
  page: (q: ToolingQuery) => http.get<PageResult<ToolingRow>>(BASE, q),
  get: (id: string) => http.get<ToolingRow>(`${BASE}/${id}`),
  records: (id: string) => http.get<RecordRow[]>(`${BASE}/${id}/records`),
  create: (data: ToolingSave) => http.post<string>(BASE, data),
  update: (id: string, data: ToolingSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  record: (id: string, action: RecordAction, data: RecordReq) => http.post<void>(`${BASE}/${id}/${action}`, data)
}
