import { http, type PageParam, type PageResult } from '@/api/http'

export type TaskStatus = 'WAITING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED'

export interface TaskRow {
  id: string
  taskType: string
  name: string
  moduleCode: string
  status: TaskStatus
  progress: number
  resultFileId?: string
  resultExpired: boolean
  resultMessage?: string
  errorMessage?: string
  submittedBy?: string
  submittedByName?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

export interface TaskQuery extends PageParam {
  taskType?: string
  status?: string
  time?: [string, string]
  all?: boolean
}

export const TASK_TYPE_OPTIONS = [
  { value: 'EXPORT', label: '导出' },
  { value: 'IMPORT', label: '导入' },
  { value: 'MRP', label: 'MRP 运算' },
  { value: 'COST_CALC', label: '成本计算' }
]

export function taskQueryParams(q: TaskQuery) {
  const { time, ...rest } = q
  return { ...rest, timeFrom: time?.[0], timeTo: time?.[1] }
}

export const taskApi = {
  page: (q: TaskQuery) => http.get<PageResult<TaskRow>>('/system/tasks', taskQueryParams(q)),
  get: (id: string) => http.get<TaskRow>(`/system/tasks/${id}`),
  cancel: (id: string) => http.post<void>(`/system/tasks/${id}/cancel`)
}
