import { http, type PageParam, type PageResult } from '@/api/http'

export interface LoginLogRow {
  id: string
  createdAt: string
  username: string
  realName?: string
  type: string
  result: string
  ip?: string
  browser?: string
  os?: string
}

export interface OperLogRow {
  id: string
  createdAt: string
  userId?: string
  username?: string
  realName?: string
  moduleCode?: string
  moduleName?: string
  action: string
  method: string
  path: string
  result: string
  errorCode?: number
  errorMsg?: string
  durationMs: number
  ip?: string
  traceId?: string
  params?: string
}

export interface LogQuery extends PageParam {
  time?: [string, string]
  [key: string]: unknown
}

export function logQueryParams(q: LogQuery) {
  const { time, ...rest } = q
  return { ...rest, timeFrom: time?.[0], timeTo: time?.[1] }
}

export const logApi = {
  loginLogs: (q: LogQuery) => http.get<PageResult<LoginLogRow>>('/system/login-logs', logQueryParams(q)),
  operLogs: (q: LogQuery) => http.get<PageResult<OperLogRow>>('/system/oper-logs', logQueryParams(q)),
  operLog: (id: string) => http.get<OperLogRow>(`/system/oper-logs/${id}`)
}
