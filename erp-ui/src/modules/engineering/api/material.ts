import { http, type PageParam, type PageResult } from '@/api/http'

export type MaterialType = 'RAW' | 'SEMI_FINISHED' | 'FINISHED' | 'PACKAGING' | 'AUXILIARY' | 'PHANTOM'
export type MaterialStatus = 'DRAFT' | 'ENABLED' | 'DISABLED'

export const MATERIAL_TYPE_OPTIONS: { value: MaterialType; label: string }[] = [
  { value: 'RAW', label: '原材料' },
  { value: 'SEMI_FINISHED', label: '半成品' },
  { value: 'FINISHED', label: '成品' },
  { value: 'PACKAGING', label: '包材' },
  { value: 'AUXILIARY', label: '辅料' },
  { value: 'PHANTOM', label: '虚拟件' }
]

export const MATERIAL_STATUS: Record<MaterialStatus, { label: string; type: 'info' | 'success' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  ENABLED: { label: '启用', type: 'success' },
  DISABLED: { label: '停用', type: 'danger' }
}

export interface Material {
  id: string
  code: string
  name: string
  nameEn?: string
  spec?: string
  materialType: MaterialType
  categoryId?: string
  baseUom: string
  status: MaterialStatus
  remark?: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface MaterialQuery extends PageParam {
  code?: string
  name?: string
  materialType?: MaterialType
  status?: MaterialStatus
}

export interface MaterialSave {
  code?: string
  name: string
  nameEn?: string
  spec?: string
  materialType?: MaterialType
  categoryId?: string
  baseUom: string
  remark?: string
  version?: number
}

const BASE = '/engineering/materials'

export const materialApi = {
  page: (q: MaterialQuery) => http.get<PageResult<Material>>(BASE, q),
  get: (id: string) => http.get<Material>(`${BASE}/${id}`),
  create: (data: MaterialSave) => http.post<string>(BASE, data),
  update: (id: string, data: MaterialSave) => http.put<void>(`${BASE}/${id}`, data),
  enable: (id: string) => http.post<void>(`${BASE}/${id}/enable`),
  disable: (id: string) => http.post<void>(`${BASE}/${id}/disable`),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`)
}
