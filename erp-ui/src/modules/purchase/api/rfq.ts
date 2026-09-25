import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import { optionsOf } from './common'
import type { SupplierMaterial } from './supplier'

/** 询价比价（需求 07-04） */
export type RfqStatus = 'DRAFT' | 'QUOTING' | 'COMPARING' | 'AWARDED' | 'CANCELED'

export const RFQ_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  QUOTING: { label: '报价中', type: 'warning' },
  COMPARING: { label: '比价中', type: 'primary' },
  AWARDED: { label: '已定标', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const RFQ_STATUS_OPTIONS = optionsOf(RFQ_STATUS)

export interface RfqRow {
  id: string
  docNo: string
  title: string
  currency: string
  materialCount: number
  supplierCount: number
  quotedCount: number
  quoteDeadline: string
  overdue: boolean
  status: RfqStatus
  ownerName?: string
  docDate: string
}

export interface RfqQuery extends PageParam {
  docNo?: string
  title?: string
  statuses?: string
  materialId?: string
  supplierId?: string
  deadlineFrom?: string
  deadlineTo?: string
}

export interface RfqLine {
  id?: string
  lineNo?: number
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  baseUom?: string
  qty?: string
  requiredDate?: string
  remark?: string
}

export interface RfqSupplier {
  id: string
  supplierId: string
  supplierCode: string
  supplierName: string
  supplierStatus: string
  sentAt?: string
  quoted: boolean
}

export interface RfqDetail {
  id: string
  docNo: string
  docDate: string
  status: RfqStatus
  title: string
  currency: string
  quoteDeadline: string
  cancelReason?: string
  remark?: string
  ownerId?: string
  ownerName?: string
  createdAt: string
  version: number
  lines: RfqLine[]
  suppliers: RfqSupplier[]
  adjustIds: string[]
}

export interface RfqSave {
  title: string
  currency?: string
  quoteDeadline: string
  remark?: string
  lines: { materialId: string; qty?: string; requiredDate?: string; remark?: string }[]
  supplierIds: string[]
  fileIds?: string[]
  version?: number
}

export interface QuoteCell {
  supplierId: string
  price?: string
  taxRate?: string
  moq?: string
  leadTimeDays?: number
  validUntil?: string
  awarded: boolean
  awardQtyPct?: string
  remark?: string
  lowest: boolean
}

export interface QuoteRow {
  rfqLineId: string
  materialId: string
  materialCode: string
  materialName: string
  baseUom: string
  qty?: string
  currentPrice?: string
  lowestPrice?: string
  diffPct?: string
  cells: QuoteCell[]
}

export interface SupplierTotal {
  supplierId: string
  supplierName: string
  totalAmount?: string
  complete: boolean
}

export interface QuoteMatrix {
  rfqId: string
  status: RfqStatus
  currency: string
  quoteDeadline: string
  pastDeadline: boolean
  suppliers: RfqSupplier[]
  rows: QuoteRow[]
  totals: SupplierTotal[]
}

export interface QuoteSave {
  rfqLineId: string
  supplierId: string
  price?: string
  taxRate?: string
  moq?: string
  leadTimeDays?: number
  validUntil?: string
  remark?: string
}

export interface AwardLine {
  rfqLineId: string
  awards: { supplierId: string; pct?: string }[]
}

const BASE = '/purchase/rfqs'

export const rfqApi = {
  page: (q: RfqQuery) => http.get<PageResult<RfqRow>>(BASE, q),
  get: (id: string) => http.get<RfqDetail>(`${BASE}/${id}`),
  defaultSuppliers: (materialIds: string[]) => http.get<SupplierMaterial[]>(`${BASE}/default-suppliers`, { materialIds: materialIds.join(',') }),
  create: (data: RfqSave) => http.post<string>(BASE, data),
  update: (id: string, data: RfqSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  send: (id: string) => http.post<void>(`${BASE}/${id}/send`),
  quotes: (id: string) => http.get<QuoteMatrix>(`${BASE}/${id}/quotes`),
  saveQuotes: (id: string, quotes: QuoteSave[]) => http.put<{ saved: number; warnings: string[] }>(`${BASE}/${id}/quotes`, { quotes }),
  endQuote: (id: string) => http.post<void>(`${BASE}/${id}/end-quote`),
  award: (id: string, lines: AwardLine[]) => http.post<{ adjustIds: string[] }>(`${BASE}/${id}/award`, { lines }),
  cancel: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/cancel`, { reason })
}
