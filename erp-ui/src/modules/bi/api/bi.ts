import { http, postSse, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'

/** BI / AI 接口（需求 13-BI与AI）：通用查询、驾驶舱、指标库、数据任务、AI 分析 */

export type MetricUnit = 'AMOUNT' | 'PERCENT' | 'QTY' | 'COUNT' | 'DAYS' | 'PRICE'

// ==================== 通用查询 ====================

export interface BiQuery {
  metrics: string[]
  dimensions?: string[]
  filters?: Record<string, string[]>
  from?: string
  to?: string
  granularity?: 'day' | 'month' | 'quarter' | 'year'
  sort?: string
  order?: 'asc' | 'desc'
  limit?: number
}
export interface BiColumn { key: string; label: string; kind: 'DIMENSION' | 'METRIC'; unit?: MetricUnit }
export interface MetricMeta { code: string; name: string; unit: MetricUnit; description: string; source: string; sensitive: boolean }
export type BiRow = Record<string, string | number | null>
export interface BiResult {
  columns: BiColumn[]; rows: BiRow[]; metrics: MetricMeta[]; from: string; to: string; granularity?: string; truncated: boolean; dataUpdatedAt?: string
}

export const queryApi = {
  query: (q: BiQuery) => http.post<BiResult>('/bi/query', q)
}

// ==================== 驾驶舱 ====================

export type PeriodCode = 'THIS_MONTH' | 'LAST_MONTH' | 'THIS_QUARTER' | 'THIS_YEAR' | 'CUSTOM'
export type CompareCode = 'MOM' | 'YOY'
export const PERIODS: { value: PeriodCode; label: string }[] = [
  { value: 'THIS_MONTH', label: '本月' }, { value: 'LAST_MONTH', label: '上月' }, { value: 'THIS_QUARTER', label: '本季' },
  { value: 'THIS_YEAR', label: '本年' }, { value: 'CUSTOM', label: '自定义' }
]
export const COMPARES: { value: CompareCode; label: string }[] = [{ value: 'MOM', label: '环比' }, { value: 'YOY', label: '同比' }]

export interface Kpi {
  code: string; name: string; unit: MetricUnit; value?: number; compareValue?: number; changePct?: number; changePt?: number
  extraName?: string; extraUnit?: MetricUnit; extra?: number; route?: string
}
export interface Share { id?: string; label: string; value: number; share?: number }
export interface Dashboard {
  from: string; to: string; compareFrom: string; compareTo: string; compare: CompareCode; finance: boolean; kpis: Kpi[]
  trend: { month: string; order: number; ship: number; receipt: number }[]
  topCustomers: Share[]; categories: Share[]
  delivery: { onTimeRate?: number; openOrderAmount: number; overdueLines: number }
  quality: { month: string; iqcPassRate?: number; fpy?: number; complaints: number }[]
  inventory: { type: string; label: string; amount: number }[]; slowMovingAmount?: number; dataUpdatedAt?: string
}

export const dashboardApi = {
  get: (p: { period: PeriodCode; from?: string; to?: string; compare: CompareCode }) => http.get<Dashboard>('/bi/dashboard', p)
}

// ==================== 指标库、专题页、数据任务 ====================

export interface Metric {
  code: string; name: string; defaultName: string; topic: string; unit: MetricUnit; description: string; source: string
  dimensions: { code: string; label: string }[]; permission: string; sensitive: boolean; derived: boolean; remark?: string; ownerName?: string
  lastCalculatedAt?: string
}
export interface PageConfig { code: string; name: string; metrics: Metric[]; dimensions: { code: string; label: string }[]; dataUpdatedAt?: string }
export interface EtlJob {
  id: string; code: string; name: string; jobStatus: 'IDLE' | 'RUNNING'; lastStartedAt?: string; lastFinishedAt?: string; lastResult?: 'SUCCESS' | 'FAILED'
  lastRows?: number; lastDiffRows?: number; lastDurationMs?: number; lastMessage?: string
}
export interface EtlLog { id: string; jobCode: string; startedAt: string; finishedAt?: string; result: string; rowCount: number; diffRows: number; message?: string }

export const ETL_RESULT: StatusMap = { SUCCESS: { label: '成功', type: 'success' }, FAILED: { label: '失败', type: 'danger' } }
export const ETL_STATUS: StatusMap = { IDLE: { label: '空闲', type: 'info', plain: true }, RUNNING: { label: '运行中', type: 'warning' } }
export const UNIT_LABEL: Record<MetricUnit, string> = { AMOUNT: '金额', PERCENT: '百分比', QTY: '数量', COUNT: '个数', DAYS: '天数', PRICE: '单价' }
export const TOPIC_LABEL: Record<string, string> = {
  sales: '销售', purchase: '采购', inventory: '库存', production: '生产', quality: '品质', finance: '财务'
}

export const metricApi = {
  list: () => http.get<Metric[]>('/bi/metrics'),
  visible: () => http.get<Metric[]>('/bi/metrics/visible'),
  update: (code: string, body: { displayName?: string; description?: string; ownerName?: string }) => http.put<Metric>(`/bi/metrics/${code}`, body),
  pageConfig: (code: string) => http.get<PageConfig>(`/bi/pages/${code}/config`)
}

export const etlApi = {
  jobs: () => http.get<EtlJob[]>('/bi/etl/jobs'),
  logs: (jobCode?: string) => http.get<EtlLog[]>('/bi/etl/logs', { jobCode, limit: 50 }),
  run: (code: string) => http.post<EtlJob>(`/bi/etl/jobs/${code}/run`, undefined, { timeout: 600000 })
}

// ==================== AI 分析 ====================

export interface AiStatus { enabled: boolean; configured: boolean; model: string; quota: number; usedToday: number; message?: string }
export interface Conversation { id: string; title: string; createdAt: string; updatedAt: string }
export interface AiMessage {
  id: string; role: 'USER' | 'ASSISTANT'; content: string; chart?: 'line' | 'bar' | 'pie' | 'table'
  toolCalls: { tool: string; input: BiQuery; error?: string; rows?: number }[]; results: BiResult[]; tokens?: number
  feedback?: 'UP' | 'DOWN'; feedbackRemark?: string; createdAt: string
}
export interface Anomaly {
  id: string; detectDate: string; metricCode: string; dimension?: string; dimValue?: string; dimLabel?: string; currentValue?: number
  baseValue?: number; changePct: number; method: 'SIGMA' | 'MOM'; level: 'INFO' | 'WARNING'; explanation?: string
}
export interface WeeklyReport { id: string; weekStart: string; title: string; dataJson?: string; summary?: string; createdAt: string }
export interface AiSettings { enabled: boolean; provider: string; baseUrl?: string; model?: string; maskedKey?: string; keySource: string; mask: boolean; quota: number }
export interface UsageRow { date: string; userId: string; userName?: string; questions: number; tokens: number; failures: number }
export interface AiLog {
  id: string; userId: string; userName?: string; question: string; toolCalls?: string; resultRows: number; latencyMs: number; tokens: number
  success: boolean; error?: string; feedback?: string; createdAt: string
}

export const AI_PROVIDER: Record<string, string> = { DEEPSEEK: 'DeepSeek', QWEN: '通义千问（阿里云百炼）', OPENAI_COMPATIBLE: '其他 OpenAI 兼容接口' }
export const ANOMALY_LEVEL: StatusMap = { WARNING: { label: '警告', type: 'warning' }, INFO: { label: '提示', type: 'info' } }
export const ANOMALY_METHOD: Record<string, string> = { SIGMA: '同星期 3σ', MOM: '近 30 天环比' }

export const aiApi = {
  status: () => http.get<AiStatus>('/bi/ai/status'),
  conversations: () => http.get<Conversation[]>('/bi/ai/conversations'),
  create: (title?: string) => http.post<Conversation>('/bi/ai/conversations', { title }),
  rename: (id: string, title: string) => http.put<void>(`/bi/ai/conversations/${id}`, { title }),
  remove: (id: string) => http.delete<void>(`/bi/ai/conversations/${id}`),
  messages: (id: string) => http.get<AiMessage[]>(`/bi/ai/conversations/${id}/messages`),
  ask: (id: string, question: string) => http.post<AiMessage>(`/bi/ai/conversations/${id}/messages`, { question }, { timeout: 300000 }),
  /** 流式提问（SSE）：onDelta 逐段收到模型输出，onStatus 收到进度提示，返回最终消息 */
  askStream: async (id: string, question: string, onDelta: (text: string) => void, onStatus?: (text: string) => void, signal?: AbortSignal) => {
    let done: AiMessage | undefined
    await postSse(`/bi/ai/conversations/${id}/messages/stream`, { question }, (event, data) => {
      if (event === 'delta') onDelta((data as { text: string }).text)
      else if (event === 'status') onStatus?.((data as { text: string }).text)
      else if (event === 'done') done = data as AiMessage
    }, { signal })
    return done
  },
  feedback: (id: string, feedback: 'UP' | 'DOWN' | null, remark?: string) => http.post<void>(`/bi/ai/messages/${id}/feedback`, { feedback, remark }),
  anomalies: (from?: string, to?: string) => http.get<Anomaly[]>('/bi/ai/anomalies', { from, to }),
  detect: () => http.post<number>('/bi/ai/anomalies/detect', undefined, { timeout: 300000 }),
  weeklyReports: () => http.get<WeeklyReport[]>('/bi/ai/weekly-reports'),
  generateWeekly: () => http.post<WeeklyReport>('/bi/ai/weekly-reports/generate', undefined, { timeout: 300000 }),
  settings: () => http.get<AiSettings>('/bi/ai/settings'),
  usage: (from?: string, to?: string) => http.get<UsageRow[]>('/bi/ai/usage', { from, to }),
  logs: (q: PageParam & { userId?: string; success?: boolean; feedback?: string }) => http.get<PageResult<AiLog>>('/bi/ai/logs', q)
}

// ---------- 销售预测建议（需求 13-04 2.4） ----------
export interface ForecastSuggestion {
  materialId: string
  materialLabel: string
  historyMonths: number
  history: { month: string; qty: string }[]
  forecast: Record<string, string>
  method: 'TREND' | 'TREND_SEASONAL'
  mape?: number
}
export interface ForecastResult { startPeriod: string; endPeriod: string; months: number; suggestions: ForecastSuggestion[] }
export const forecastApi = {
  suggestions: (months: number) => http.get<ForecastResult>('/bi/forecast/suggestions', { months }, { timeout: 120000 }),
  generate: (materialIds: string[], months: number) => http.post<{ forecastId: string; materialCount: number }>('/bi/forecast/generate', { materialIds, months }, { timeout: 120000 })
}
