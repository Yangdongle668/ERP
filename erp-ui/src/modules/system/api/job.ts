import { http, type PageResult } from '@/api/http'

export interface JobRow {
  code: string
  name: string
  moduleCode: string
  moduleName: string
  cron: string
  defaultCron: string
  enabled: boolean
  lastRunAt?: string
  lastResult?: 'SUCCESS' | 'FAILED'
  lastMessage?: string
  nextRunAt?: string
  running: boolean
}

export interface JobLogRow {
  id: string
  jobCode: string
  startedAt: string
  finishedAt?: string
  durationMs?: number
  result: 'RUNNING' | 'SUCCESS' | 'FAILED'
  message?: string
  triggerType: 'SCHEDULE' | 'MANUAL'
  operatorName?: string
}

export const jobApi = {
  list: () => http.get<JobRow[]>('/system/jobs'),
  preview: (cron: string) => http.get<string[]>('/system/jobs/cron-preview', { cron }, { silent: true }),
  updateCron: (code: string, cron: string) => http.put<void>(`/system/jobs/${code}/cron`, { cron }),
  resetCron: (code: string) => http.post<void>(`/system/jobs/${code}/reset-cron`),
  enable: (code: string) => http.post<void>(`/system/jobs/${code}/enable`),
  disable: (code: string) => http.post<void>(`/system/jobs/${code}/disable`),
  run: (code: string) => http.post<void>(`/system/jobs/${code}/run`),
  logs: (code: string, pageNo: number, pageSize: number) => http.get<PageResult<JobLogRow>>(`/system/jobs/${code}/logs`, { pageNo, pageSize })
}
