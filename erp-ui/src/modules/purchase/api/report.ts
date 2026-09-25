import { http, type PageParam, type PageResult } from '@/api/http'

/** 采购报表（需求 07-11） */
export interface TrackingRow {
  orderLineId: string
  orderId: string
  orderNo: string
  lineNo: number
  supplierId: string
  supplierName: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  qty: string
  receivedQty: string
  openQty: string
  requiredDate: string
  confirmedDate?: string
  overdueDays: number
  demand?: string
  ownerId?: string
  ownerName?: string
  lastFollowUp?: string
  followUpAt?: string
}

export interface TrackingQuery extends PageParam {
  supplierId?: string
  ownerId?: string
  materialId?: string
  requiredFrom?: string
  requiredTo?: string
  confirmedFrom?: string
  confirmedTo?: string
  overdueOnly?: boolean
  unconfirmedOnly?: boolean
}

export interface ExecutionRow {
  orderId: string
  orderNo: string
  docDate: string
  supplierId: string
  supplierName: string
  lineNo: number
  materialId: string
  materialCode: string
  materialName: string
  baseUom: string
  qty: string
  receivedQty: string
  stockedQty: string
  qualifiedQty: string
  returnedQty: string
  statementQty: string
  openQty: string
  currency: string
  priceInclTax?: string
  totalAmount?: string
  lineStatus: string
}

export interface ExecutionQuery extends PageParam {
  docNo?: string
  supplierId?: string
  ownerId?: string
  materialId?: string
  statuses?: string
  dateFrom?: string
  dateTo?: string
}

export interface PriceTrendRow {
  month: string
  materialId: string
  materialCode: string
  materialName: string
  supplierId: string
  supplierName: string
  qty: string
  avgPrice?: string
  maxPrice?: string
  minPrice?: string
}

export interface PriceTrend {
  months: string[]
  series: { materialId: string; materialCode: string; materialName: string; points: { month: string; avgPrice?: string }[] }[]
  rows: PriceTrendRow[]
}

export interface SummaryRow {
  key1: string
  label1: string
  key2?: string
  label2?: string
  orderAmount?: string
  receivedAmount?: string
  qualifiedAmount?: string
  returnAmount?: string
  orderLineCount: number
  ontimeRate?: string
  lotPassRate?: string
}

export const SUMMARY_DIM_OPTIONS = [
  { value: 'SUPPLIER', label: '供应商' },
  { value: 'CATEGORY', label: '物料类别' },
  { value: 'MATERIAL', label: '物料' },
  { value: 'BUYER', label: '采购员' },
  { value: 'MONTH', label: '月份' }
]

const BASE = '/purchase/reports'

export const reportApi = {
  tracking: (q: TrackingQuery) => http.get<PageResult<TrackingRow>>(`${BASE}/delivery-tracking`, q),
  followUp: (orderLineId: string, content: string, newDate?: string) =>
    http.post<void>(`${BASE}/delivery-tracking/${orderLineId}/follow-up`, { content, newDate }),
  execution: (q: ExecutionQuery) => http.get<PageResult<ExecutionRow>>(`${BASE}/order-execution`, q),
  priceTrend: (q: { materialIds: string; dateFrom?: string; dateTo?: string }) => http.get<PriceTrend>(`${BASE}/price-trend`, q),
  summary: (q: { dim1: string; dim2?: string; dateFrom?: string; dateTo?: string }) => http.get<SummaryRow[]>(`${BASE}/summary`, q)
}
