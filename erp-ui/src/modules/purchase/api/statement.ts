import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import { DOC_STATUS, optionsOf, type DocResult, type DocStatus } from './common'

/** 供应商对账（需求 07-09） */
export const STATEMENT_STATUS: StatusMap = {
  DRAFT: DOC_STATUS.DRAFT,
  PENDING_APPROVAL: DOC_STATUS.PENDING_APPROVAL,
  APPROVED: { label: '待供应商确认', type: 'primary' },
  COMPLETED: { label: '已确认', type: 'success' },
  VOIDED: DOC_STATUS.VOIDED
}
export const STATEMENT_STATUS_OPTIONS = optionsOf(STATEMENT_STATUS)

export const LINE_TYPE_OPTIONS = [
  { value: 'GOODS', label: '货款' },
  { value: 'PROCESS_FEE', label: '加工费' },
  { value: 'RETURN', label: '退货' },
  { value: 'ADJUST', label: '加扣款' }
]

export interface StatementRow {
  id: string
  docNo: string
  supplierId: string
  supplierName: string
  periodFrom: string
  periodTo: string
  currency: string
  goodsAmount?: string
  returnAmount?: string
  adjustAmount?: string
  totalAmount?: string
  status: DocStatus
  supplierConfirmedAt?: string
  ownerName?: string
}

export interface StatementQuery extends PageParam {
  docNo?: string
  supplierId?: string
  statuses?: string
  ownerId?: string
  periodFrom?: string
  periodTo?: string
}

export interface StatementLine {
  id?: string
  lineNo?: number
  lineType: string
  sourceType?: string
  sourceId?: string
  sourceLineId?: string
  sourceNo?: string
  orderNo?: string
  materialId?: string
  materialCode?: string
  materialName?: string
  baseUom?: string
  bizDate?: string
  qty?: string
  remainingQty?: string
  priceInclTax?: string
  taxRate?: string
  amount?: string
  taxAmount?: string
  totalAmount?: string
  remark?: string
}

export interface StatementDetail {
  id: string
  docNo: string
  docDate: string
  status: DocStatus
  supplierId: string
  supplierName: string
  periodFrom: string
  periodTo: string
  currency: string
  exchangeRate: string
  goodsAmount?: string
  returnAmount?: string
  adjustAmount?: string
  totalAmount?: string
  taxAmount?: string
  supplierConfirmedAt?: string
  supplierConfirmer?: string
  remark?: string
  ownerId?: string
  ownerName?: string
  createdAt: string
  version: number
  priceVisible: boolean
  lines: StatementLine[]
}

export interface StatementSave {
  supplierId: string
  currency?: string
  periodFrom: string
  periodTo: string
  remark?: string
  lines: { lineType: string; sourceType?: string; sourceLineId?: string; qty?: string; totalAmount?: string; taxRate?: string; bizDate?: string;
    remark?: string }[]
  fileIds?: string[]
  version?: number
}

const BASE = '/purchase/statements'

export const statementApi = {
  page: (q: StatementQuery) => http.get<PageResult<StatementRow>>(BASE, q),
  candidates: (supplierId: string, from: string, to: string, currency?: string) =>
    http.get<StatementLine[]>(`${BASE}/candidates`, { supplierId, from, to, currency }),
  batchGenerate: (periodFrom: string, periodTo: string) => http.post<string[]>(`${BASE}/batch-generate`, { periodFrom, periodTo }),
  get: (id: string) => http.get<StatementDetail>(`${BASE}/${id}`),
  create: (data: StatementSave) => http.post<string>(BASE, data),
  update: (id: string, data: StatementSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  void: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/void`, { reason }),
  submit: (id: string) => http.post<DocResult>(`${BASE}/${id}/submit`),
  unapprove: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/unapprove`, { reason }),
  confirm: (id: string, data: { confirmer: string; confirmedAt?: string; fileIds?: string[] }) => http.post<void>(`${BASE}/${id}/confirm`, data),
  unconfirm: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/unconfirm`, { reason })
}
