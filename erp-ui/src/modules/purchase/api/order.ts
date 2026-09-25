import { http, type PageParam, type PageResult } from '@/api/http'
import { DOC_STATUS, optionsOf, type DocResult, type DocStatus, type RelatedDoc } from './common'

/** 采购订单与订单变更（需求 07-05） */
export const ORDER_STATUS = DOC_STATUS
export const ORDER_STATUS_OPTIONS = optionsOf(ORDER_STATUS)

export const ORDER_TYPE_OPTIONS = [
  { value: 'STANDARD', label: '标准采购' },
  { value: 'SAMPLE', label: '样品采购' }
]

export const CHANGE_TYPE_OPTIONS = [
  { value: 'MODIFY', label: '修改' },
  { value: 'ADD', label: '新增' },
  { value: 'CANCEL', label: '取消' }
]

export interface OrderRow {
  id: string
  docNo: string
  docDate: string
  orderType: string
  supplierId: string
  supplierName: string
  ownerId?: string
  ownerName?: string
  currency: string
  totalAmount?: string
  earliestDate?: string
  orderedQty: string
  receivedQty: string
  overdueLines: number
  delayedLines: number
  sentAt?: string
  orderVersion: number
  hasPriceOverrun: boolean
  status: DocStatus
}

export interface OrderQuery extends PageParam {
  docNo?: string
  supplierId?: string
  ownerId?: string
  statuses?: string
  materialId?: string
  dateFrom?: string
  dateTo?: string
  requiredFrom?: string
  requiredTo?: string
  orderType?: string
  overdue?: boolean
}

export interface OrderLine {
  id?: string
  lineNo?: number
  materialId?: string
  materialCode?: string
  materialName?: string
  materialNameEn?: string
  materialSpec?: string
  baseUom?: string
  supplierPartNo?: string
  uom?: string
  qty?: string
  baseQty?: string
  price?: string
  priceInclTax?: string
  taxRate?: string
  amount?: string
  taxAmount?: string
  totalAmount?: string
  listPrice?: string
  priceOverrun?: boolean
  requiredDate?: string
  confirmedDate?: string
  receivedQty?: string
  stockedQty?: string
  qualifiedQty?: string
  returnedQty?: string
  replaceQty?: string
  statementQty?: string
  openQty?: string
  firstReceivedDate?: string
  requisitionLineId?: string
  requisitionNo?: string
  requisitionId?: string
  lineStatus?: string
  delayed?: boolean
  overdue?: boolean
  lastFollowUp?: string
  followUpAt?: string
  remark?: string
}

export interface OrderDetail {
  id: string
  docNo: string
  docDate: string
  status: DocStatus
  orderType: string
  supplierId: string
  supplierCode: string
  supplierName: string
  supplierLevel?: string
  supplierContactId?: string
  contactName?: string
  currency: string
  exchangeRate: string
  paymentTermId?: string
  paymentTermName?: string
  tradeTerm?: string
  taxIncluded: boolean
  deliveryAddress?: string
  amount?: string
  taxAmount?: string
  totalAmount?: string
  totalAmountBase?: string
  orderVersion: number
  hasPriceOverrun: boolean
  sentAt?: string
  closeReason?: string
  ownerId?: string
  ownerName?: string
  remark?: string
  priceVisible: boolean
  hasReceipt: boolean
  runningChangeId?: string
  createdByName?: string
  createdAt: string
  version: number
  lines: OrderLine[]
  related: RelatedDoc[]
}

export interface OrderLineSave {
  id?: string
  materialId: string
  uom?: string
  qty: string
  price?: string
  priceInclTax?: string
  taxRate?: string
  requiredDate: string
  requisitionLineId?: string
  remark?: string
}

export interface OrderSave {
  orderType?: string
  supplierId: string
  supplierContactId?: string
  currency?: string
  exchangeRate?: string
  paymentTermId?: string
  tradeTerm?: string
  taxIncluded?: boolean
  deliveryAddress?: string
  ownerId?: string
  remark?: string
  lines: OrderLineSave[]
  fileIds?: string[]
  version?: number
}

export interface OpenLine {
  id: string
  orderId: string
  orderNo: string
  lineNo: number
  orderType: string
  supplierId: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  uom: string
  qty: string
  receivedQty: string
  openQty: string
  openBaseQty: string
  requiredDate: string
  confirmedDate?: string
  inspectRequired: boolean
  shelfLifeDays?: number
}

export interface ChangeRow {
  id: string
  docNo: string
  docDate: string
  orderId: string
  orderNo: string
  changeReason: string
  newVersion: number
  amountChangeBase?: string
  status: DocStatus
  ownerName?: string
  createdAt: string
}

export interface ChangeLine {
  id?: string
  lineNo?: number
  orderLineId?: string
  orderLineNo?: number
  changeType?: string
  materialId?: string
  materialCode?: string
  materialName?: string
  uom?: string
  oldQty?: string
  newQty?: string
  oldPrice?: string
  newPrice?: string
  taxRate?: string
  oldRequiredDate?: string
  newRequiredDate?: string
  receivedQty?: string
  remark?: string
}

export interface ChangeDetail {
  id: string
  docNo: string
  docDate: string
  status: DocStatus
  orderId: string
  orderNo: string
  orderVersion: number
  changeReason: string
  newVersion: number
  amountChangeBase?: string
  ownerName?: string
  createdAt: string
  version: number
  priceVisible: boolean
  lines: ChangeLine[]
}

export interface ChangeSave {
  orderId: string
  changeReason: string
  lines: { orderLineId?: string; changeType: string; materialId?: string; uom?: string; newQty?: string; newPrice?: string; taxRate?: string;
    newRequiredDate?: string; remark?: string }[]
  fileIds?: string[]
  version?: number
}

export const orderApi = {
  page: (q: OrderQuery) => http.get<PageResult<OrderRow>>('/purchase/orders', q),
  get: (id: string) => http.get<OrderDetail>(`/purchase/orders/${id}`),
  create: (data: OrderSave) => http.post<{ id: string; warnings: string[] }>('/purchase/orders', data),
  update: (id: string, data: OrderSave) => http.put<{ id: string; warnings: string[] }>(`/purchase/orders/${id}`, data),
  remove: (id: string) => http.delete<void>(`/purchase/orders/${id}`),
  fromRequisitions: (lines: { requisitionLineId: string; supplierId: string; qty?: string }[]) =>
    http.post<{ orderIds: string[]; messages: string[] }>('/purchase/orders/from-requisitions', { lines }),
  submit: (id: string) => http.post<DocResult>(`/purchase/orders/${id}/submit`),
  unapprove: (id: string, reason: string) => http.post<void>(`/purchase/orders/${id}/unapprove`, { reason }),
  close: (id: string, reason: string) => http.post<void>(`/purchase/orders/${id}/close`, { reason }),
  void: (id: string, reason: string) => http.post<void>(`/purchase/orders/${id}/void`, { reason }),
  confirmDates: (id: string, lines: { lineId: string; confirmedDate?: string }[]) => http.put<void>(`/purchase/orders/${id}/confirmed-dates`, { lines }),
  sent: (id: string) => http.post<void>(`/purchase/orders/${id}/sent`),
  openLines: (q: PageParam & { supplierId?: string; materialId?: string; orderNo?: string; orderType?: string }) =>
    http.get<PageResult<OpenLine>>('/purchase/order-lines/open', q)
}

export const changeApi = {
  page: (q: PageParam & { orderId?: string; docNo?: string; statuses?: string }) => http.get<PageResult<ChangeRow>>('/purchase/order-changes', q),
  template: (orderId: string) => http.get<OrderLine[]>('/purchase/order-changes/template', { orderId }),
  get: (id: string) => http.get<ChangeDetail>(`/purchase/order-changes/${id}`),
  create: (data: ChangeSave) => http.post<string>('/purchase/order-changes', data),
  update: (id: string, data: ChangeSave) => http.put<void>(`/purchase/order-changes/${id}`, data),
  remove: (id: string) => http.delete<void>(`/purchase/order-changes/${id}`),
  submit: (id: string) => http.post<DocResult>(`/purchase/order-changes/${id}/submit`)
}
