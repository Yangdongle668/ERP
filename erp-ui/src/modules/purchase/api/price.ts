import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import { DOC_STATUS, optionsOf, type DocResult, type DocStatus } from './common'

/** 采购价格与调价单（需求 07-02） */
export const PRICE_STATUS: StatusMap = {
  EFFECTIVE: { label: '生效', type: 'success' },
  EXPIRED: { label: '已过期', type: 'info', plain: true },
  REPLACED: { label: '已替代', type: 'info', plain: true }
}
export const PRICE_STATUS_OPTIONS = optionsOf(PRICE_STATUS)

export const ADJUST_STATUS: StatusMap = {
  DRAFT: DOC_STATUS.DRAFT,
  PENDING_APPROVAL: DOC_STATUS.PENDING_APPROVAL,
  APPROVED: { label: '已生效', type: 'success' },
  VOIDED: DOC_STATUS.VOIDED
}
export const ADJUST_STATUS_OPTIONS = optionsOf(ADJUST_STATUS)

export const ADJUST_SOURCE_OPTIONS = [
  { value: 'MANUAL', label: '手工' },
  { value: 'RFQ', label: '询价定标' },
  { value: 'IMPORT', label: '导入' }
]

export interface PriceRow {
  id: string
  supplierId: string
  supplierCode: string
  supplierName: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  currency: string
  minQty: string
  price: string
  taxRate: string
  priceInclTax: string
  effectiveFrom: string
  effectiveTo?: string
  priceStatus: string
  adjustId?: string
  adjustNo?: string
  updatedAt: string
}

export interface PriceQuery extends PageParam {
  supplierId?: string
  materialId?: string
  keyword?: string
  categoryId?: string
  statuses?: string
  dateFrom?: string
  dateTo?: string
}

export interface EffectivePrice {
  priceId: string
  price: string
  priceInclTax: string
  taxRate: string
  basePrice: string
  minQty: string
  currency: string
  effectiveFrom: string
  effectiveTo?: string
}

export interface AdjustRow {
  id: string
  docNo: string
  docDate: string
  supplierId: string
  supplierName: string
  currency: string
  adjustReason: string
  source: string
  lineCount: number
  maxChangePct?: string
  status: DocStatus
  ownerName?: string
  createdAt: string
}

export interface AdjustQuery extends PageParam {
  docNo?: string
  supplierId?: string
  materialId?: string
  statuses?: string
  dateFrom?: string
  dateTo?: string
}

export interface AdjustLine {
  id?: string
  lineNo?: number
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  baseUom?: string
  minQty?: string
  oldPrice?: string
  newPrice?: string
  taxRate?: string
  changePct?: string
  effectiveFrom?: string
  effectiveTo?: string
  remark?: string
  overThreshold?: boolean
}

export interface AdjustDetail {
  id: string
  docNo: string
  docDate: string
  status: DocStatus
  supplierId: string
  supplierName: string
  supplierStatus: string
  currency: string
  adjustReason: string
  source: string
  rfqId?: string
  rfqNo?: string
  remark?: string
  ownerName?: string
  createdAt: string
  version: number
  lines: AdjustLine[]
}

export interface AdjustSave {
  supplierId: string
  currency?: string
  adjustReason: string
  remark?: string
  lines: { materialId: string; minQty?: string; newPrice: string; taxRate?: string; effectiveFrom?: string; effectiveTo?: string; remark?: string }[]
  fileIds?: string[]
  version?: number
}

export const priceApi = {
  page: (q: PriceQuery) => http.get<PageResult<PriceRow>>('/purchase/prices', q),
  history: (supplierId: string, materialId: string) => http.get<PriceRow[]>('/purchase/prices/history', { supplierId, materialId }),
  effective: (p: { supplierId: string; materialId: string; currency: string; qty?: string; uom?: string; date?: string }) =>
    http.get<EffectivePrice | null>('/purchase/prices/effective', p, { silent: true })
}

const BASE = '/purchase/price-adjusts'

export const adjustApi = {
  page: (q: AdjustQuery) => http.get<PageResult<AdjustRow>>(BASE, q),
  get: (id: string) => http.get<AdjustDetail>(`${BASE}/${id}`),
  create: (data: AdjustSave) => http.post<string>(BASE, data),
  update: (id: string, data: AdjustSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  submit: (id: string) => http.post<DocResult>(`${BASE}/${id}/submit`),
  void: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/void`, { reason })
}
