import { http, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'

export type AssetStatus = 'IN_USE' | 'IDLE' | 'REPAIRING' | 'SCRAPPED'

export interface AssetRow {
  id: string
  code: string
  companyNo: string
  assetClass: string
  name: string
  nameAbbr: string
  spec?: string
  purchaseDate: string
  deptId?: string
  deptName?: string
  custodianId?: string
  custodianName?: string
  location?: string
  supplierName?: string
  customerName?: string
  originalValue?: string
  usefulLifeMonths?: number
  assetStatus: AssetStatus
  scrappedDate?: string
  scrapReason?: string
  remark?: string
  createdByName?: string
  createdAt: string
  version: number
}

export interface AssetQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  assetClass?: string
  companyNo?: string
  assetStatus?: string
  deptId?: string
}

export type AssetSave = Pick<AssetRow, 'companyNo' | 'assetClass' | 'name' | 'nameAbbr' | 'purchaseDate'> &
  Partial<Pick<AssetRow, 'spec' | 'deptId' | 'custodianId' | 'location' | 'supplierName' | 'customerName' | 'originalValue' | 'usefulLifeMonths' | 'remark' | 'version'>>

export const ASSET_STATUS: StatusMap = {
  IN_USE: { label: '在用', type: 'success' },
  IDLE: { label: '闲置', type: 'info' },
  REPAIRING: { label: '维修中', type: 'warning' },
  SCRAPPED: { label: '已报废', type: 'danger' }
}
export const ASSET_STATUS_OPTIONS = Object.entries(ASSET_STATUS).map(([value, s]) => ({ value, label: s.label }))

const BASE = '/asset/assets'

export const assetApi = {
  page: (q: AssetQuery) => http.get<PageResult<AssetRow>>(BASE, q),
  get: (id: string) => http.get<AssetRow>(`${BASE}/${id}`),
  preview: (q: { companyNo?: string; assetClass?: string; nameAbbr?: string; purchaseDate?: string }) =>
    http.get<{ prefix?: string; example?: string }>(`${BASE}/code-preview`, q, { silent: true }),
  create: (d: AssetSave) => http.post<string>(BASE, d),
  update: (id: string, d: AssetSave) => http.put<void>(`${BASE}/${id}`, d),
  status: (id: string, op: 'USE' | 'IDLE' | 'REPAIR' | 'REPAIR_END', reason?: string) => http.post<void>(`${BASE}/${id}/status`, { op, reason }),
  scrap: (id: string, scrappedDate: string, reason: string) => http.post<void>(`${BASE}/${id}/scrap`, { scrappedDate, reason }),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`)
}
