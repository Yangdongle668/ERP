import { http, type PageParam, type PageResult } from '@/api/http'

export interface DictTypeRow {
  id: string
  code: string
  name: string
  moduleCode: string
  moduleName: string
  builtin: boolean
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  version: number
}

export interface DictItemRow {
  id: string
  typeCode: string
  value: string
  label: string
  labelEn?: string
  tagType: string
  sort: number
  isDefault: boolean
  builtin: boolean
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  version: number
}

export interface DictItemSave {
  typeCode: string
  value: string
  label: string
  labelEn?: string
  tagType: string
  sort: number
  isDefault: boolean
  remark?: string
  version?: number
}

export const dictApi = {
  types: (q: PageParam & { keyword?: string; moduleCode?: string }) => http.get<PageResult<DictTypeRow>>('/system/dict-types', q),
  createType: (data: { code: string; name: string; status?: string; remark?: string }) => http.post<string>('/system/dict-types', data),
  updateType: (id: string, data: { code: string; name: string; status?: string; remark?: string; version?: number }) =>
    http.put<void>(`/system/dict-types/${id}`, data),
  removeType: (id: string) => http.delete<void>(`/system/dict-types/${id}`),
  items: (typeCode: string) => http.get<DictItemRow[]>('/system/dict-items', { typeCode }),
  createItem: (data: DictItemSave) => http.post<string>('/system/dict-items', data),
  updateItem: (id: string, data: DictItemSave) => http.put<void>(`/system/dict-items/${id}`, data),
  enableItem: (id: string) => http.post<void>(`/system/dict-items/${id}/enable`),
  disableItem: (id: string) => http.post<void>(`/system/dict-items/${id}/disable`),
  removeItem: (id: string) => http.delete<void>(`/system/dict-items/${id}`),
  refreshCache: () => http.post<void>('/system/dicts/refresh-cache')
}
