import { http } from '@/api/http'

export const UOM_CATEGORY_OPTIONS = [
  { value: 'COUNT', label: '数量' },
  { value: 'WEIGHT', label: '重量' },
  { value: 'LENGTH', label: '长度' },
  { value: 'AREA', label: '面积' },
  { value: 'VOLUME', label: '体积' },
  { value: 'TIME', label: '时间' }
]

export interface UomRow {
  id: string
  code: string
  name: string
  nameEn?: string
  category: string
  precision: number
  sort: number
  builtin: boolean
  status: 'ENABLED' | 'DISABLED'
  version: number
}

export interface UomSave {
  code: string
  name: string
  nameEn?: string
  category: string
  precision: number
  sort: number
  version?: number
}

export interface ConversionRow {
  id: string
  fromUom: string
  toUom: string
  rate: string
  version: number
}

export const uomApi = {
  list: (params: { keyword?: string; category?: string; status?: string }) => http.get<UomRow[]>('/system/uoms', params),
  create: (data: UomSave) => http.post<string>('/system/uoms', data),
  update: (id: string, data: UomSave) => http.put<void>(`/system/uoms/${id}`, data),
  enable: (id: string) => http.post<void>(`/system/uoms/${id}/enable`),
  disable: (id: string) => http.post<void>(`/system/uoms/${id}/disable`),
  remove: (id: string) => http.delete<void>(`/system/uoms/${id}`),
  conversions: () => http.get<ConversionRow[]>('/system/uom-conversions'),
  createConversion: (data: { fromUom: string; toUom: string; rate: string }) => http.post<string>('/system/uom-conversions', data),
  updateConversion: (id: string, data: { fromUom: string; toUom: string; rate: string; version?: number }) =>
    http.put<void>(`/system/uom-conversions/${id}`, data),
  removeConversion: (id: string) => http.delete<void>(`/system/uom-conversions/${id}`)
}
