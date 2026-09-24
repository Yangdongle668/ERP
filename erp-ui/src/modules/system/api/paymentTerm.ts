import { http } from '@/api/http'

export const BASE_EVENT_OPTIONS = [
  { value: 'ORDER_DATE', label: '下单日' },
  { value: 'BEFORE_SHIPMENT', label: '出货前' },
  { value: 'SHIPMENT', label: '出货日' },
  { value: 'BL_DATE', label: '提单日' },
  { value: 'INVOICE_DATE', label: '开票日' },
  { value: 'RECEIPT_DATE', label: '到货日' },
  { value: 'MONTH_END', label: '月结' }
]

export const USAGE_OPTIONS = [
  { value: 'BOTH', label: '销售与采购' },
  { value: 'SALES', label: '销售' },
  { value: 'PURCHASE', label: '采购' }
]

export interface TermNode {
  name: string
  /** 比例（小数，0.3 表示 30%） */
  percent: string
  baseEvent: string
  days: number
}

export interface TermRow {
  id: string
  code: string
  name: string
  nameEn?: string
  settlementMethod: string
  usage: string
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  nodes: TermNode[]
  nodeSummary: string
  version: number
}

export interface TermSave {
  code: string
  name: string
  nameEn?: string
  settlementMethod: string
  usage: string
  remark?: string
  nodes: TermNode[]
  version?: number
}

export const paymentTermApi = {
  list: (params: { keyword?: string; usage?: string; status?: string }) => http.get<TermRow[]>('/system/payment-terms', params),
  create: (data: TermSave) => http.post<string>('/system/payment-terms', data),
  update: (id: string, data: TermSave) => http.put<void>(`/system/payment-terms/${id}`, data),
  enable: (id: string) => http.post<void>(`/system/payment-terms/${id}/enable`),
  disable: (id: string) => http.post<void>(`/system/payment-terms/${id}/disable`),
  remove: (id: string) => http.delete<void>(`/system/payment-terms/${id}`)
}
