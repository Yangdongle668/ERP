import { http, type PageParam, type PageResult } from '@/api/http'

export interface CurrencyRow {
  id: string
  code: string
  name: string
  nameEn?: string
  symbol?: string
  amountPrecision: number
  base: boolean
  sort: number
  status: 'ENABLED' | 'DISABLED'
  version: number
}

export interface CurrencySave {
  code: string
  name: string
  nameEn?: string
  symbol?: string
  amountPrecision: number
  sort: number
  version?: number
}

export interface RateRow {
  id: string
  currency: string
  rateType: 'DAILY' | 'MONTH_END'
  effectiveDate: string
  rate: string
  source: 'MANUAL' | 'IMPORT'
  remark?: string
  updatedByName?: string
  updatedAt: string
  version: number
}

export interface RateQuery extends PageParam {
  currency?: string
  rateType?: string
  dateRange?: [string, string]
  sortOrder?: string
}

export interface RateSave {
  currency: string
  rateType: 'DAILY' | 'MONTH_END'
  effectiveDate: string
  rate: string
  remark?: string
  version?: number
}

export const RATE_TYPE_OPTIONS = [
  { value: 'DAILY', label: '日汇率' },
  { value: 'MONTH_END', label: '月末汇率' }
]

export function rateQueryParams(q: RateQuery) {
  const { dateRange, ...rest } = q
  return { ...rest, dateFrom: dateRange?.[0], dateTo: dateRange?.[1] }
}

export const currencyApi = {
  list: () => http.get<CurrencyRow[]>('/system/currencies'),
  create: (data: CurrencySave) => http.post<string>('/system/currencies', data),
  update: (id: string, data: CurrencySave) => http.put<void>(`/system/currencies/${id}`, data),
  enable: (id: string) => http.post<void>(`/system/currencies/${id}/enable`),
  disable: (id: string) => http.post<void>(`/system/currencies/${id}/disable`),
  setBase: (id: string) => http.post<void>(`/system/currencies/${id}/set-base`),
  rates: (q: RateQuery) => http.get<PageResult<RateRow>>('/system/exchange-rates', rateQueryParams(q)),
  createRate: (data: RateSave) => http.post<string>('/system/exchange-rates', data),
  updateRate: (id: string, data: RateSave) => http.put<void>(`/system/exchange-rates/${id}`, data),
  removeRate: (id: string) => http.delete<void>(`/system/exchange-rates/${id}`),
  batch: (data: { rateType: string; effectiveDate: string; lines: { currency: string; rate: string }[] }) =>
    http.post<number>('/system/exchange-rates/batch', data),
  lookup: (currency: string, date: string, type = 'DAILY') =>
    http.get<{ rate: string; effectiveDate: string }>('/system/exchange-rates/lookup', { currency, date, type }, { silent: true })
}
