import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import type { MaterialStatus, MaterialType, SourceType } from './material'

export type BomStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'CLOSED' | 'VOIDED'
export type IssueMethod = 'PICK' | 'BACKFLUSH'

/** BOM 状态：CLOSED 显示为“停用” */
export const BOM_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_APPROVAL: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已审核', type: 'success' },
  CLOSED: { label: '停用', type: 'info', plain: true },
  VOIDED: { label: '已作废', type: 'danger', plain: true }
}
export const BOM_STATUS_OPTIONS = Object.entries(BOM_STATUS).filter(([k]) => k !== 'VOIDED').map(([value, s]) => ({ value, label: s.label }))
export const ISSUE_METHOD_OPTIONS: { value: IssueMethod; label: string }[] = [
  { value: 'PICK', label: '领料' },
  { value: 'BACKFLUSH', label: '倒冲' }
]
/** BOM 父件可选类型 */
export const PARENT_TYPES: MaterialType[] = ['SEMI_FINISHED', 'FINISHED', 'PHANTOM']

export interface BomRow {
  id: string
  docNo: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  materialType: MaterialType
  uom: string
  version: number
  baseQty: string
  isDefault: boolean
  lineCount: number
  description?: string
  status: BomStatus
  updatedByName?: string
  updatedAt: string
}

export interface SubstituteRow {
  substituteId: string
  code?: string
  name?: string
  spec?: string
  uom?: string
  status?: MaterialStatus
  priority: number
  ratio: string
  remark?: string
}

export interface BomLine {
  id?: string
  lineNo?: number
  componentId?: string
  componentCode?: string
  componentName?: string
  componentSpec?: string
  componentType?: MaterialType
  componentStatus?: MaterialStatus
  uom?: string
  qtyPer?: string
  /** 小数（0.02 = 2%） */
  scrapRate?: string
  positionNo?: string
  issueMethod?: IssueMethod
  operationSeq?: number
  isKey?: boolean
  remark?: string
  substitutes: SubstituteRow[]
}

export interface BomDetail {
  id: string
  docNo: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  materialType: MaterialType
  materialStatus: MaterialStatus
  uom: string
  version: number
  baseQty: string
  isDefault: boolean
  effectiveDate?: string
  description?: string
  remark?: string
  status: BomStatus
  copiedFromId?: string
  copiedFromNo?: string
  createdByName?: string
  createdAt: string
  updatedByName?: string
  updatedAt: string
  rowVersion: number
  lines: BomLine[]
}

export interface BomSave {
  materialId: string
  baseQty: string
  description?: string
  remark?: string
  lines: {
    componentId: string
    qtyPer: string
    scrapRate?: string
    positionNo?: string
    issueMethod?: IssueMethod
    operationSeq?: number
    isKey?: boolean
    remark?: string
    substitutes: { substituteId: string; priority: number; ratio: string; remark?: string }[]
  }[]
  fileIds?: string[]
  rowVersion?: number
}

export interface ExplodeRow {
  key: string
  level: number
  path: string
  parentId: string
  componentId: string
  code: string
  name: string
  spec?: string
  materialType: MaterialType
  sourceType?: SourceType
  uom: string
  qtyPer: string
  scrapRate?: string
  totalQtyPer: string
  requiredQty: string
  issueMethod?: IssueMethod
  phantom: boolean
  bomId?: string
  bomVersion?: number
  unitCost?: string
  costAmount?: string
  costMissing: boolean
  children?: ExplodeRow[]
}

export interface WhereUsedRow {
  key: string
  level: number
  bomId: string
  docNo: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  materialType: MaterialType
  version: number
  isDefault: boolean
  status: BomStatus
  qtyPer: string
  uom: string
  top: boolean
  children?: WhereUsedRow[]
}

export interface CompareSide {
  qtyPer: string
  scrapRate?: string
  positionNo?: string
  issueMethod?: IssueMethod
  isKey: boolean
  substituteCount: number
}

export interface CompareRow {
  componentId: string
  code: string
  name: string
  spec?: string
  uom: string
  change: 'ADDED' | 'REMOVED' | 'CHANGED' | 'SAME'
  changedFields: string[]
  left?: CompareSide
  right?: CompareSide
}

export interface BomQuery extends PageParam {
  materialId?: string
  keyword?: string
  statuses?: string
  defaultOnly?: boolean
  componentId?: string
}

const BASE = '/engineering/boms'

export const bomApi = {
  page: (q: BomQuery) => http.get<PageResult<BomRow>>(BASE, q),
  get: (id: string) => http.get<BomDetail>(`${BASE}/${id}`),
  nextVersion: (materialId: string) => http.get<{ materialId: string; version: number }>(`${BASE}/next-version`, { materialId }),
  create: (data: BomSave) => http.post<{ id: string; warnings: string[] }>(BASE, data),
  update: (id: string, data: BomSave) => http.put<{ id: string; warnings: string[] }>(`${BASE}/${id}`, data),
  newVersion: (id: string) => http.post<string>(`${BASE}/${id}/new-version`),
  submit: (id: string) => http.post<BomStatus>(`${BASE}/${id}/submit`),
  unapprove: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/unapprove`, { reason }),
  setDefault: (id: string) => http.post<void>(`${BASE}/${id}/set-default`),
  disable: (id: string) => http.post<void>(`${BASE}/${id}/disable`),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  explode: (id: string, qty = '1', levels = 0) => http.get<ExplodeRow[]>(`${BASE}/${id}/explode`, { qty, levels }),
  whereUsed: (materialId: string) => http.get<WhereUsedRow[]>(`${BASE}/where-used`, { materialId }),
  compare: (leftId: string, rightId: string) =>
    http.get<{ leftId: string; leftNo: string; rightId: string; rightNo: string; rows: CompareRow[] }>(`${BASE}/compare`, { leftId, rightId }),
  cost: (id: string) => http.get<{ total: string; missingCount: number; rows: ExplodeRow[] }>(`${BASE}/${id}/cost`)
}
