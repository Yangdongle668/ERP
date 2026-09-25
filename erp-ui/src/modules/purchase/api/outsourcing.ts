import { http, type PageParam, type PageResult } from '@/api/http'
import { DOC_STATUS, optionsOf, type DocResult, type DocStatus, type RelatedDoc } from './common'

/** 委外加工（需求 07-07） */
export const OS_STATUS = DOC_STATUS
export const OS_STATUS_OPTIONS = optionsOf(OS_STATUS)

export const TXN_TYPE_OPTIONS = [
  { value: 'ISSUE', label: '发料' },
  { value: 'RETURN', label: '余料退回' }
]

export interface OsRow {
  id: string
  docNo: string
  docDate: string
  supplierId: string
  supplierName: string
  materialId: string
  materialCode: string
  materialName: string
  uom: string
  qty: string
  processPrice?: string
  currency: string
  requiredDate: string
  issuePct?: string
  receivedQty: string
  qualifiedQty: string
  status: DocStatus
  ownerName?: string
}

export interface OsQuery extends PageParam {
  docNo?: string
  supplierId?: string
  materialId?: string
  statuses?: string
  requiredFrom?: string
  requiredTo?: string
}

export interface OsMaterial {
  id?: string
  lineNo?: number
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  uom?: string
  qtyPer?: string
  requiredQty?: string
  issuedQty?: string
  returnedQty?: string
  consumedQty?: string
  lossQty?: string
  lossReason?: string
  adjustReason?: string
  availableQty?: string
  previewConsumed?: string
  previewLoss?: string
}

export interface BomPreview {
  bomId: string
  bomNo: string
  bomVersion: number
  materials: OsMaterial[]
}

export interface OsReceiptLine {
  receiptId: string
  receiptNo: string
  arrivalAt?: string
  receiptStatus: string
  qty: string
  stockedQty: string
  inspectStatus?: string
  qualifiedQty: string
  rejectedQty: string
}

export interface OsTxn {
  id: string
  txnType: string
  stockDocId: string
  stockDocNo: string
  materialCode: string
  materialName: string
  qty: string
  reversed: boolean
  createdAt: string
}

export interface OsDetail {
  id: string
  docNo: string
  docDate: string
  status: DocStatus
  supplierId: string
  supplierName: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  uom: string
  bomId?: string
  bomNo?: string
  bomVersion?: number
  qty: string
  processPrice?: string
  taxRate: string
  currency: string
  exchangeRate: string
  amount?: string
  taxAmount?: string
  totalAmount?: string
  requiredDate: string
  receivedQty: string
  qualifiedQty: string
  kitQty: string
  mrpResultId?: string
  closeReason?: string
  remark?: string
  ownerId?: string
  ownerName?: string
  createdAt: string
  version: number
  priceVisible: boolean
  materials: OsMaterial[]
  receipts: OsReceiptLine[]
  txns: OsTxn[]
  related: RelatedDoc[]
}

export interface OsSave {
  supplierId: string
  materialId: string
  bomId?: string
  qty: string
  processPrice?: string
  taxRate?: string
  currency?: string
  exchangeRate?: string
  requiredDate: string
  remark?: string
  materials?: { materialId: string; requiredQty?: string; adjustReason?: string }[]
  fileIds?: string[]
  version?: number
}

const BASE = '/purchase/outsourcings'

export const outsourcingApi = {
  page: (q: OsQuery) => http.get<PageResult<OsRow>>(BASE, q),
  preview: (materialId: string, qty?: string, bomId?: string) => http.get<BomPreview>(`${BASE}/bom-preview`, { materialId, qty, bomId }),
  get: (id: string) => http.get<OsDetail>(`${BASE}/${id}`),
  create: (data: OsSave) => http.post<string>(BASE, data),
  update: (id: string, data: OsSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  void: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/void`, { reason }),
  submit: (id: string) => http.post<DocResult>(`${BASE}/${id}/submit`),
  unapprove: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/unapprove`, { reason }),
  close: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/close`, { reason }),
  issue: (id: string, lines: { outsourcingMaterialId: string; qty: string }[]) => http.post<{ stockDocIds: string[] }>(`${BASE}/${id}/issue`, { lines }),
  returnMaterial: (id: string, lines: { outsourcingMaterialId: string; qty: string; defective?: boolean }[]) =>
    http.post<{ stockDocIds: string[] }>(`${BASE}/${id}/return-material`, { lines }),
  settle: (id: string, lines: { outsourcingMaterialId: string; lossReason?: string }[]) => http.post<void>(`${BASE}/${id}/settle`, { lines })
}
