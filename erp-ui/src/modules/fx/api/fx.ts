import { http } from '@/api/http'

export type FxPairCode = 'USD_CNY' | 'EUR_CNY' | 'EUR_USD'

export interface FxQuoteRow {
  pair: FxPairCode
  label: string
  rate?: string
  publishTime?: string
  fetchedAt?: string
  stale: boolean
  todayAverage?: string
}

export interface FxStatus {
  enabled: boolean
  polling: boolean
  lastSuccessAt?: string
  lastAttemptAt?: string
  lastError?: string
  consecutiveFailures: number
  nextRunAt?: string
  pushTarget?: string
  quotes: FxQuoteRow[]
}

export interface FxDailyRow {
  pair: FxPairCode
  rateDate: string
  avgRate: string
  minRate: string
  maxRate: string
  sampleCount: number
  finalized: boolean
}

export interface FxMonthlyRow {
  pair: FxPairCode
  rateMonth: string
  avgRate: string
  dayCount: number
}

export const FX_PAIRS: { value: FxPairCode; label: string }[] = [
  { value: 'USD_CNY', label: '美元 / 人民币' },
  { value: 'EUR_CNY', label: '欧元 / 人民币' },
  { value: 'EUR_USD', label: '欧元 / 美元' }
]

export const fxApi = {
  status: () => http.get<FxStatus>('/fx/status'),
  refresh: () => http.post<FxStatus>('/fx/refresh'),
  daily: (q: { pair?: string; from?: string; to?: string }) => http.get<FxDailyRow[]>('/fx/daily', q),
  monthly: (limit = 36) => http.get<FxMonthlyRow[]>('/fx/monthly', { limit })
}
