import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import { optionsOf } from './common'

/** 供应商评估（需求 07-10） */
export const SCORE_STATUS: StatusMap = {
  CALCULATED: { label: '已计算', type: 'warning' },
  SCORED: { label: '已评分', type: 'primary' },
  PUBLISHED: { label: '已发布', type: 'success' }
}
export const SCORE_STATUS_OPTIONS = optionsOf(SCORE_STATUS)

export const GRADE_OPTIONS = ['A', 'B', 'C', 'D'].map((g) => ({ value: g, label: g }))

export interface ScoreRow {
  id: string
  supplierId: string
  supplierCode: string
  supplierName: string
  period: string
  lotCount: number
  lotPassCount: number
  qualityScore?: string
  dueLineCount: number
  ontimeLineCount: number
  deliveryScore?: string
  priceScore?: string
  serviceScore?: string
  totalScore?: string
  grade?: string
  status: string
  comment?: string
  publishedAt?: string
  version: number
}

export interface ScoreQuery extends PageParam {
  period?: string
  supplierId?: string
  grade?: string
  statuses?: string
}

export interface ScoreDetail {
  id: string
  period: string
  lots: { receiptId: string; receiptNo: string; materialCode: string; materialName: string; qty: string; inspectStatus: string; inspectionNo?: string;
    judgedDate?: string }[]
  delayedLines: { orderId: string; orderNo: string; lineNo: number; materialCode: string; materialName: string; qty: string; dueDate: string;
    firstReceivedDate?: string; receivedQty: string }[]
}

const BASE = '/purchase/scores'

export const scoreApi = {
  page: (q: ScoreQuery) => http.get<PageResult<ScoreRow>>(BASE, q),
  calculate: (period: string) => http.post<{ count: number; ids: string[] }>(`${BASE}/calculate`, { period }),
  update: (id: string, data: { priceScore?: string; serviceScore?: string; comment?: string; version?: number }) =>
    http.put<ScoreRow>(`${BASE}/${id}`, data),
  publish: (ids: string[]) => http.post<number>(`${BASE}/publish`, { ids }),
  unpublish: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/unpublish`, { reason }),
  details: (id: string) => http.get<ScoreDetail>(`${BASE}/${id}/details`),
  trend: (supplierId: string) => http.get<{ period: string; totalScore?: string; grade?: string }[]>(`${BASE}/trend`, { supplierId })
}
