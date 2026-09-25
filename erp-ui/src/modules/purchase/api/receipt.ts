import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import { DOC_STATUS, optionsOf, type DocResult, type DocStatus, type RelatedDoc, type SaveResult } from './common'

/** 到货（需求 07-06）与采购退货（需求 07-08） */
export const RECEIPT_STATUS: StatusMap = {
  DRAFT: DOC_STATUS.DRAFT,
  APPROVED: DOC_STATUS.APPROVED,
  COMPLETED: DOC_STATUS.COMPLETED
}
export const RECEIPT_STATUS_OPTIONS = optionsOf(RECEIPT_STATUS)

export const RECEIPT_TYPE_OPTIONS = [
  { value: 'PURCHASE', label: '采购到货' },
  { value: 'OUTSOURCE', label: '委外收货' },
  { value: 'SAMPLE', label: '样品到货' }
]

export const STOCK_STATUS: StatusMap = {
  NONE: { label: '未入库', type: 'warning' },
  PARTIAL: { label: '部分入库', type: 'primary' },
  ALL: { label: '已入库', type: 'success' }
}

export const RETURN_STATUS: StatusMap = {
  DRAFT: DOC_STATUS.DRAFT,
  PENDING_APPROVAL: DOC_STATUS.PENDING_APPROVAL,
  APPROVED: { label: '待出库', type: 'primary' },
  COMPLETED: DOC_STATUS.COMPLETED,
  VOIDED: DOC_STATUS.VOIDED
}
export const RETURN_STATUS_OPTIONS = optionsOf(RETURN_STATUS)

export const HANDLING_OPTIONS = [
  { value: 'REPLACE', label: '换货' },
  { value: 'REFUND', label: '退款' }
]

export const OUT_STATUS: StatusMap = {
  NONE: { label: '-', type: 'info', plain: true },
  PENDING: { label: '待出库', type: 'warning' },
  DONE: { label: '已出库', type: 'success' }
}

export interface ReceiptRow {
  id: string
  docNo: string
  receiptType: string
  supplierId: string
  supplierName: string
  deliveryNoteNo?: string
  arrivalAt?: string
  materialSummary?: string
  lineCount: number
  inspectSummary?: string
  stockStatus: string
  receiverName?: string
  status: DocStatus
}

export interface ReceiptQuery extends PageParam {
  docNo?: string
  supplierId?: string
  deliveryNoteNo?: string
  orderNo?: string
  materialId?: string
  inspectStatus?: string
  receiptType?: string
  statuses?: string
  dateFrom?: string
  dateTo?: string
}

export interface ReceiptLine {
  id?: string
  lineNo?: number
  orderId?: string
  orderNo?: string
  orderLineId?: string
  orderLineNo?: number
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  baseUom?: string
  uom?: string
  qty?: string
  baseQty?: string
  openQty?: string
  supplierBatchNo?: string
  productionDate?: string
  inspectRequired?: boolean
  targetWarehouseId?: string
  targetWarehouseName?: string
  stockInId?: string
  stockInNo?: string
  stockedQty?: string
  stockedDate?: string
  batchNo?: string
  inspectStatus?: string
  qualifiedQty?: string
  concessionQty?: string
  rejectedQty?: string
  returnedQty?: string
  statementQty?: string
  inspectionNo?: string
  rejectReason?: string
  remark?: string
  /** 编辑页：保质期（天），提示需要生产日期 */
  shelfLifeDays?: number
}

export interface ReceiptDetail {
  id: string
  docNo: string
  docDate: string
  status: DocStatus
  receiptType: string
  supplierId: string
  supplierName: string
  supplierStatus: string
  deliveryNoteNo?: string
  arrivalAt?: string
  receiverId?: string
  receiverName?: string
  remark?: string
  ownerId?: string
  createdByName?: string
  createdAt: string
  version: number
  lines: ReceiptLine[]
  related: RelatedDoc[]
}

export interface ReceiptSave {
  receiptType: string
  supplierId: string
  deliveryNoteNo?: string
  arrivalAt?: string
  receiverId?: string
  remark?: string
  lines: { orderLineId?: string; orderId?: string; qty: string; supplierBatchNo?: string; productionDate?: string; remark?: string }[]
  fileIds?: string[]
  version?: number
}

export interface ReturnableLine {
  id: string
  receiptId: string
  receiptNo: string
  arrivalDate?: string
  orderId: string
  orderNo?: string
  orderLineId?: string
  materialId: string
  materialCode: string
  materialName: string
  baseUom: string
  batchNo?: string
  currency?: string
  baseQty: string
  stockedQty: string
  rejectedQty: string
  returnedQty: string
  returnableQty: string
  priceInclTax?: string
  inspectStatus?: string
}

export interface ReturnRow {
  id: string
  docNo: string
  docDate: string
  supplierId: string
  supplierName: string
  returnReason: string
  handling: string
  warehouseId: string
  warehouseName?: string
  materialSummary?: string
  currency?: string
  totalAmount?: string
  status: DocStatus
  outStatus: string
  ownerName?: string
}

export interface ReturnQuery extends PageParam {
  docNo?: string
  supplierId?: string
  returnReason?: string
  handling?: string
  statuses?: string
  materialId?: string
  dateFrom?: string
  dateTo?: string
}

export interface ReturnLine {
  id?: string
  lineNo?: number
  receiptLineId?: string
  receiptId?: string
  receiptNo?: string
  orderLineId?: string
  orderNo?: string
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  baseUom?: string
  batchNo?: string
  qty?: string
  returnableQty?: string
  priceInclTax?: string
  taxRate?: string
  totalAmount?: string
  outQty?: string
  outDate?: string
  statementQty?: string
  remark?: string
}

export interface ReturnDetail {
  id: string
  docNo: string
  docDate: string
  status: DocStatus
  supplierId: string
  supplierName: string
  returnReason: string
  handling: string
  warehouseId: string
  warehouseName?: string
  currency?: string
  exchangeRate?: string
  totalAmount?: string
  stockOutId?: string
  stockOutNo?: string
  ncrNo?: string
  voidReason?: string
  remark?: string
  ownerId?: string
  ownerName?: string
  createdAt: string
  version: number
  priceVisible: boolean
  lines: ReturnLine[]
  related: RelatedDoc[]
}

export interface ReturnSave {
  supplierId: string
  returnReason: string
  handling: string
  warehouseId: string
  ncrNo?: string
  remark?: string
  lines: { receiptLineId: string; batchNo?: string; qty: string; remark?: string }[]
  fileIds?: string[]
  version?: number
}

export interface DefectCandidate {
  receiptLineId: string
  supplierId: string
  supplierName?: string
  receiptId: string
  receiptNo: string
  orderNo?: string
  materialId: string
  materialCode: string
  materialName: string
  baseUom: string
  batchNo?: string
  warehouseId: string
  warehouseName: string
  ngQty: string
  rejectedQty: string
  returnableQty: string
  inspectStatus?: string
  inspectionNo?: string
}

export const receiptApi = {
  page: (q: ReceiptQuery) => http.get<PageResult<ReceiptRow>>('/purchase/receipts', q),
  get: (id: string) => http.get<ReceiptDetail>(`/purchase/receipts/${id}`),
  create: (data: ReceiptSave) => http.post<SaveResult>('/purchase/receipts', data),
  update: (id: string, data: ReceiptSave) => http.put<SaveResult>(`/purchase/receipts/${id}`, data),
  remove: (id: string) => http.delete<void>(`/purchase/receipts/${id}`),
  approve: (id: string) => http.post<DocResult>(`/purchase/receipts/${id}/approve`),
  unapprove: (id: string, reason: string) => http.post<void>(`/purchase/receipts/${id}/unapprove`, { reason }),
  returnable: (q: PageParam & { supplierId?: string; materialId?: string; receiptNo?: string }) =>
    http.get<PageResult<ReturnableLine>>('/purchase/receipt-lines/returnable', q)
}

export const returnApi = {
  page: (q: ReturnQuery) => http.get<PageResult<ReturnRow>>('/purchase/returns', q),
  get: (id: string) => http.get<ReturnDetail>(`/purchase/returns/${id}`),
  create: (data: ReturnSave) => http.post<SaveResult>('/purchase/returns', data),
  update: (id: string, data: ReturnSave) => http.put<SaveResult>(`/purchase/returns/${id}`, data),
  remove: (id: string) => http.delete<void>(`/purchase/returns/${id}`),
  submit: (id: string) => http.post<DocResult>(`/purchase/returns/${id}/submit`),
  void: (id: string, reason: string) => http.post<void>(`/purchase/returns/${id}/void`, { reason }),
  defectCandidates: (supplierId?: string) => http.get<DefectCandidate[]>('/purchase/returns/defect-candidates', { supplierId }),
  fromDefects: (items: { receiptLineId: string; warehouseId: string; qty?: string }[]) =>
    http.post<string[]>('/purchase/returns/from-defects', { items })
}
