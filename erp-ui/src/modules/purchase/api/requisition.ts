import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import { DOC_STATUS, optionsOf, type DocResult, type DocStatus, type RelatedDoc } from './common'

/** 采购申请（需求 07-03） */
export const REQ_STATUS: StatusMap = {
  DRAFT: DOC_STATUS.DRAFT,
  PENDING_APPROVAL: DOC_STATUS.PENDING_APPROVAL,
  APPROVED: DOC_STATUS.APPROVED,
  IN_PROGRESS: { label: '部分转订单', type: 'primary' },
  COMPLETED: { label: '已转订单', type: 'success' },
  CLOSED: DOC_STATUS.CLOSED,
  VOIDED: DOC_STATUS.VOIDED
}
export const REQ_STATUS_OPTIONS = optionsOf(REQ_STATUS)

export interface ReqRow {
  id: string
  docNo: string
  docDate: string
  requisitionType: string
  requestDeptId?: string
  requestDeptName?: string
  ownerId?: string
  ownerName?: string
  materialSummary?: string
  lineCount: number
  orderedLineCount: number
  urgent: boolean
  earliestRequiredDate?: string
  status: DocStatus
}

export interface ReqQuery extends PageParam {
  docNo?: string
  requisitionType?: string
  statuses?: string
  requestDeptId?: string
  ownerId?: string
  materialId?: string
  requiredFrom?: string
  requiredTo?: string
  urgent?: boolean
}

export interface ReqLine {
  id?: string
  lineNo?: number
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  baseUom?: string
  uom?: string
  qty?: string
  baseQty?: string
  requiredDate?: string
  suggestedSupplierId?: string
  suggestedSupplierName?: string
  referencePrice?: string
  purpose?: string
  orderedQty?: string
  lineStatus?: string
  mrpResultId?: string
  sourceDemand?: string
  remark?: string
}

export interface ReqDetail {
  id: string
  docNo: string
  docDate: string
  status: DocStatus
  requisitionType: string
  requestDeptId?: string
  requestDeptName?: string
  urgent: boolean
  mrpRunId?: string
  ownerId?: string
  ownerName?: string
  remark?: string
  createdAt: string
  version: number
  priceVisible: boolean
  lines: ReqLine[]
  related: RelatedDoc[]
}

export interface ReqSave {
  requisitionType?: string
  requestDeptId?: string
  urgent?: boolean
  remark?: string
  lines: { materialId: string; uom?: string; qty: string; requiredDate: string; suggestedSupplierId?: string; purpose?: string; remark?: string }[]
  fileIds?: string[]
  version?: number
}

export interface PendingLine {
  id: string
  requisitionId: string
  docNo: string
  lineNo: number
  requisitionType: string
  urgent: boolean
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  qty: string
  orderedQty: string
  pendingQty: string
  requiredDate: string
  leadTimeDays?: number
  suggestedSupplierId?: string
  suggestedSupplierName?: string
  referencePrice?: string
  ownerName?: string
  sourceDemand?: string
}

export interface PendingQuery extends PageParam {
  docNo?: string
  supplierId?: string
  materialId?: string
  requiredFrom?: string
  requiredTo?: string
  lineIds?: string
}

const BASE = '/purchase/requisitions'

export const requisitionApi = {
  page: (q: ReqQuery) => http.get<PageResult<ReqRow>>(BASE, q),
  get: (id: string) => http.get<ReqDetail>(`${BASE}/${id}`),
  create: (data: ReqSave) => http.post<string>(BASE, data),
  update: (id: string, data: ReqSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  submit: (id: string) => http.post<DocResult>(`${BASE}/${id}/submit`),
  unapprove: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/unapprove`, { reason }),
  close: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/close`, { reason }),
  void: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/void`, { reason }),
  pending: (q: PendingQuery) => http.get<PageResult<PendingLine>>('/purchase/requisition-lines/pending', q)
}
