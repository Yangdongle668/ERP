import { http } from '@/api/http'

export type FxPairCode = 'USD_CNY' | 'EUR_CNY' | 'JPY_CNY' | 'KRW_CNY' | 'AUD_CNY'

export interface FxQuoteRow {
  pair: FxPairCode
  label: string
  rate?: string
  publishTime?: string
  fetchedAt?: string
  stale: boolean
  todayAverage?: string
  /** 保存历史并推送系统汇率表（仅美元）；其他币别只有实时报价 */
  persisted: boolean
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
  { value: 'JPY_CNY', label: '日元 / 人民币' },
  { value: 'KRW_CNY', label: '韩元 / 人民币' },
  { value: 'AUD_CNY', label: '澳元 / 人民币' }
]

export const fxApi = {
  status: () => http.get<FxStatus>('/fx/status'),
  refresh: () => http.post<FxStatus>('/fx/refresh'),
  daily: (q: { pair?: string; from?: string; to?: string }) => http.get<FxDailyRow[]>('/fx/daily', q),
  monthly: (limit = 36) => http.get<FxMonthlyRow[]>('/fx/monthly', { limit })
}
