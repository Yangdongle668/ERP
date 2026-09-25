import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import type { MaterialRef } from './tooling'

/** 认证证书（需求 05-09）。validity：有效 / 长期有效 / 即将到期 / 已过期 / 已撤销 */
export const VALIDITY_STATUS: StatusMap = {
  VALID: { label: '有效', type: 'success' },
  LONG_TERM: { label: '长期有效', type: 'success' },
  EXPIRING: { label: '即将到期', type: 'warning' },
  EXPIRED: { label: '已过期', type: 'danger' },
  REVOKED: { label: '已撤销', type: 'info', plain: true }
}
export const VALIDITY_OPTIONS = [
  { value: 'VALID', label: '有效' },
  { value: 'EXPIRING', label: '即将到期' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'REVOKED', label: '已撤销' }
]

export interface CertRow {
  id: string
  certType: string
  certNo: string
  name: string
  issuingBody: string
  holder?: string
  issueDate: string
  expireDate?: string
  countries: string[]
  scope?: string
  certStatus: string
  validity: string
  daysLeft?: number
  materials: MaterialRef[]
  fileCount: number
  revokeReason?: string
  remark?: string
  version: number
}

export interface CertSave {
  certType?: string
  certNo: string
  name: string
  issuingBody: string
  holder?: string
  issueDate?: string
  expireDate?: string
  countries: string[]
  scope?: string
  materialIds: string[]
  fileIds: string[]
  remark?: string
  version?: number
}

export interface CertQuery extends PageParam {
  certNo?: string
  certType?: string
  materialId?: string
  validity?: string
  expireFrom?: string
  expireTo?: string
}

const BASE = '/engineering/certifications'
export const certApi = {
  page: (q: CertQuery) => http.get<PageResult<CertRow>>(BASE, q),
  byMaterial: (materialId: string) => http.get<CertRow[]>(`${BASE}/by-material/${materialId}`),
  create: (data: CertSave) => http.post<string>(BASE, data),
  update: (id: string, data: CertSave) => http.put<void>(`${BASE}/${id}`, data),
  revoke: (id: string, reason: string) => http.post<void>(`${BASE}/${id}/revoke`, { reason }),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`)
}
