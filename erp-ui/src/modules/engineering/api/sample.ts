import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'

/** 样品单（需求 05-07） */
export const SAMPLE_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已审批', type: 'primary' },
  MAKING: { label: '制作中', type: 'primary' },
  READY: { label: '待寄出', type: 'warning' },
  SHIPPED: { label: '已寄出', type: 'primary' },
  FEEDBACK: { label: '已反馈', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true },
  VOIDED: { label: '已作废', type: 'danger', plain: true }
}
export const SAMPLE_STATUS_OPTIONS = Object.entries(SAMPLE_STATUS).map(([value, s]) => ({ value, label: s.label }))
export const SAMPLE_TYPE_OPTIONS = [
  { value: 'CUSTOMER', label: '客户样' },
  { value: 'ENGINEERING', label: '工程验证样' },
  { value: 'CERTIFICATION', label: '认证送样' }
]
export const MAKE_METHOD_OPTIONS = [
  { value: 'PRODUCE', label: '生产制作' },
  { value: 'FROM_STOCK', label: '从库存领取' }
]
export const FEEDBACK_STATUS: StatusMap = {
  APPROVED: { label: '承认', type: 'success' },
  CONDITIONAL: { label: '有条件承认', type: 'warning' },
  REJECTED: { label: '不承认', type: 'danger' }
}
export const FEEDBACK_OPTIONS = Object.entries(FEEDBACK_STATUS).map(([value, s]) => ({ value, label: s.label }))

export interface SampleRow {
  id: string
  docNo: string
  sampleType: string
  customerId?: string
  customerName?: string
  materialId: string
  materialCode?: string
  materialName?: string
  uom?: string
  qty: string
  requiredDate: string
  overdue: boolean
  makeMethod: string
  sampleStatus: string
  feedbackResult?: string
  createdByName?: string
  createdAt: string
}

export interface SampleDetail {
  id: string
  docNo: string
  sampleType: string
  customerId?: string
  customerName?: string
  contactId?: string
  projectId?: string
  projectNo?: string
  projectName?: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  materialStatus: string
  uom: string
  customerPartNo?: string
  qty: string
  requiredDate: string
  makeMethod: string
  purpose: string
  requirements?: string
  shipAddress?: string
  prodOrderId?: string
  prodOrderNo?: string
  stockOutId?: string
  stockOutNo?: string
  stockOutDone: boolean
  shipDate?: string
  courier?: string
  trackingNo?: string
  feedbackResult?: string
  feedbackDate?: string
  feedbackContent?: string
  sampleStatus: string
  status: string
  closeReason?: string
  productionAvailable: boolean
  createdByName?: string
  createdAt: string
  version: number
}

export interface SampleSave {
  sampleType?: string
  customerId?: string
  contactId?: string
  projectId?: string
  materialId?: string
  customerPartNo?: string
  qty?: string
  requiredDate?: string
  makeMethod: string
  purpose: string
  requirements?: string
  shipAddress?: string
  fileIds?: string[]
  version?: number
}

export interface SampleQuery extends PageParam {
  docNo?: string
  customerId?: string
  materialId?: string
  sampleType?: string
  statuses?: string
  requiredFrom?: string
  requiredTo?: string
  projectId?: string
}

const BASE = '/engineering/samples'
export const sampleApi = {
  page: (q: SampleQuery) => http.get<PageResult<SampleRow>>(BASE, q),
  get: (id: string) => http.get<SampleDetail>(`${BASE}/${id}`),
  create: (data: SampleSave) => http.post<string>(BASE, data),
  update: (id: string, data: SampleSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  submit: (id: string) => http.post<string>(`${BASE}/${id}/submit`),
  createProdOrder: (id: string) => http.post<void>(`${BASE}/${id}/create-prod-order`),
  requestStockOut: (id: string) => http.post<string>(`${BASE}/${id}/request-stock-out`),
  ship: (id: string, data: { shipDate: string; courier: string; trackingNo: string; shipAddress?: string }) => http.post<void>(`${BASE}/${id}/ship`, data),
  feedback: (id: string, data: { result: string; feedbackDate: string; content?: string; fileIds?: string[] }) => http.post<void>(`${BASE}/${id}/feedback`, data),
  close: (id: string, reason?: string) => http.post<void>(`${BASE}/${id}/close`, { reason }),
  void: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/void`, { reason })
}
