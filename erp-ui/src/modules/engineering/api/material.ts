import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'

export type MaterialType = 'RAW' | 'SEMI_FINISHED' | 'FINISHED' | 'PACKAGING' | 'AUXILIARY' | 'PHANTOM'
export type MaterialStatus = 'DRAFT' | 'PENDING' | 'ENABLED' | 'DISABLED'
export type SourceType = 'PURCHASE' | 'MAKE' | 'OUTSOURCE'
export type Tracking = 'NONE' | 'BATCH' | 'SERIAL'
export type OrderPolicy = 'LOT_FOR_LOT' | 'FIXED_QTY' | 'PERIOD'
export type IssueRule = 'FIFO' | 'FEFO'

export const MATERIAL_TYPE_OPTIONS: { value: MaterialType; label: string }[] = [
  { value: 'RAW', label: '原材料' },
  { value: 'SEMI_FINISHED', label: '半成品' },
  { value: 'FINISHED', label: '成品' },
  { value: 'PACKAGING', label: '包材' },
  { value: 'AUXILIARY', label: '辅料' },
  { value: 'PHANTOM', label: '虚拟件' }
]
export const SOURCE_TYPE_OPTIONS: { value: SourceType; label: string }[] = [
  { value: 'PURCHASE', label: '采购' },
  { value: 'MAKE', label: '自制' },
  { value: 'OUTSOURCE', label: '委外' }
]
export const TRACKING_OPTIONS: { value: Tracking; label: string }[] = [
  { value: 'NONE', label: '不管理' },
  { value: 'BATCH', label: '批次' },
  { value: 'SERIAL', label: '序列号' }
]
export const ORDER_POLICY_OPTIONS: { value: OrderPolicy; label: string }[] = [
  { value: 'LOT_FOR_LOT', label: '按需' },
  { value: 'FIXED_QTY', label: '固定批量' },
  { value: 'PERIOD', label: '按周期' }
]
export const ISSUE_RULE_OPTIONS: { value: IssueRule; label: string }[] = [
  { value: 'FIFO', label: '先进先出' },
  { value: 'FEFO', label: '先到期先出' }
]
export const MATERIAL_STATUS_OPTIONS: { value: MaterialStatus; label: string }[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待审批' },
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '停用' }
]

/** 物料状态：草稿灰、待审批橙、启用绿、停用灰 plain */
export const MATERIAL_STATUS: StatusMap & Record<MaterialStatus, StatusMap[string]> = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  ENABLED: { label: '启用', type: 'success' },
  DISABLED: { label: '停用', type: 'info', plain: true }
}

export const labelOf = (options: { value: string; label: string }[], v?: string) => options.find((o) => o.value === v)?.label ?? v ?? ''

/** 默认取得方式：半成品、成品、虚拟件自制，其余采购 */
export function defaultSource(type?: MaterialType): SourceType {
  return type === 'SEMI_FINISHED' || type === 'FINISHED' || type === 'PHANTOM' ? 'MAKE' : 'PURCHASE'
}

export interface UomRow {
  id?: string
  uom: string
  rate?: string
  remark?: string
  /** 已被单据使用：不能删除、不能改换算率 */
  used?: boolean
}

export interface Material {
  id: string
  code: string
  name: string
  nameEn?: string
  spec?: string
  materialType: MaterialType
  categoryId?: string
  categoryCode?: string
  categoryName?: string
  baseUom: string
  drawingNo?: string
  revision?: string
  brand?: string
  manufacturer?: string
  mpn?: string
  hsCode?: string
  unitNetWeight?: string
  unitGrossWeight?: string
  imageFileId?: string
  status: MaterialStatus
  remark?: string
  sourceType?: SourceType
  leadTimeDays?: number
  safetyStock?: string
  maxStock?: string
  orderPolicy?: OrderPolicy
  fixedLotQty?: string
  periodDays?: number
  moq?: string
  mpq?: string
  plannerId?: string
  plannerName?: string
  lowLevelCode?: number
  buyerId?: string
  buyerName?: string
  purchaseUom?: string
  overReceivePct?: string
  tracking?: Tracking
  issueRule?: IssueRule
  shelfLifeDays?: number
  minRemainingLifePct?: string
  iqcRequired?: boolean
  fqcRequired?: boolean
  oqcRequired?: boolean
  /** 无字段权限 eng:material:cost 时为空 */
  standardCost?: string
  salesUom?: string
  purchaseTaxRate?: string
  salesTaxRate?: string
  version: number
  createdBy?: string
  createdByName?: string
  createdAt: string
  updatedAt: string
  uoms?: UomRow[]
}

export interface MaterialQuery extends PageParam {
  code?: string
  name?: string
  types?: string
  categoryId?: string
  status?: MaterialStatus
  mpn?: string
  sourceType?: SourceType
  buyerId?: string
  tracking?: Tracking
  createdFrom?: string
  createdTo?: string
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

export type MaterialSave = Omit<Partial<Material>, 'id' | 'status' | 'createdAt' | 'updatedAt' | 'uoms' | 'version'> & {
  name: string
  baseUom: string
  uoms?: { uom: string; rate: string; remark?: string }[]
  version?: number
}

export interface Suspect {
  id: string
  code: string
  name: string
  spec?: string
  mpn?: string
  status: MaterialStatus
  reason: 'NAME_SPEC' | 'MPN'
}

export interface MaterialSettings {
  enableApproval: boolean
  manualCodeAllowed: boolean
  duplicateCheck: 'OFF' | 'WARN' | 'BLOCK'
  canViewCost: boolean
}

export interface BatchResult {
  success: number
  failures: { id: string; code: string; message: string }[]
}

export interface BomBrief {
  bomId: string
  docNo: string
  materialId: string
  materialCode: string
  materialName: string
  version: number
  isDefault: boolean
  status: string
  qtyPer?: string
  uom?: string
}

const BASE = '/engineering/materials'

export const materialApi = {
  page: (q: MaterialQuery) => http.get<PageResult<Material>>(BASE, q),
  get: (id: string) => http.get<Material>(`${BASE}/${id}`),
  settings: () => http.get<MaterialSettings>(`${BASE}/settings`),
  create: (data: MaterialSave) => http.post<string>(BASE, data),
  update: (id: string, data: MaterialSave) => http.put<void>(`${BASE}/${id}`, data),
  enable: (id: string) => http.post<MaterialStatus>(`${BASE}/${id}/enable`),
  disable: (id: string) => http.post<void>(`${BASE}/${id}/disable`),
  batchEnable: (ids: string[]) => http.post<BatchResult>(`${BASE}/batch-enable`, { ids }),
  batchDisable: (ids: string[]) => http.post<BatchResult>(`${BASE}/batch-disable`, { ids }),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  references: (id: string) => http.get<{ stockQty: string; openDocCount: number; bomCount: number }>(`${BASE}/${id}/references`),
  duplicateCheck: (data: { id?: string; categoryId?: string; name?: string; spec?: string; mpn?: string }) =>
    http.post<{ mode: string; suspects: Suspect[] }>(`${BASE}/duplicate-check`, data, { silent: true }),
  boms: (id: string) => http.get<{ asParent: BomBrief[]; asComponent: BomBrief[] }>(`${BASE}/${id}/boms`)
}
