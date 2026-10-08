import { http, type PageParam, type PageResult } from '@/api/http'
import type { Option, StatusMap } from '@/components'

/** 工作台接口（需求 02-工作台）：首页、待办、消息、公告、预警 */

export const labelOf = (options: Option[], v?: string) => options.find((o) => o.value === v)?.label ?? v ?? ''

export const TODO_CATEGORY: StatusMap = { APPROVAL: { label: '审批', type: 'primary' }, TASK: { label: '任务', type: 'info' } }
export const TODO_PRIORITY: StatusMap = {
  HIGH: { label: '高', type: 'danger' }, NORMAL: { label: '中', type: 'info', plain: true }, LOW: { label: '低', type: 'info', plain: true }
}
export const TODO_STATUS: StatusMap = {
  PENDING: { label: '待处理', type: 'warning' }, DONE: { label: '已完成', type: 'success' }, CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const MSG_TYPES: Option[] = [
  { value: 'APPROVAL_RESULT', label: '审批结果' }, { value: 'TASK_DONE', label: '任务' }, { value: 'REMIND', label: '提醒' },
  { value: 'SYSTEM', label: '系统' }, { value: 'NOTICE', label: '公告' }
]
export const ALERT_LEVEL: StatusMap = {
  CRITICAL: { label: '严重', type: 'danger' }, WARNING: { label: '警告', type: 'warning' }, INFO: { label: '提示', type: 'info' }
}
export const ALERT_STATUS: StatusMap = {
  OPEN: { label: '未处理', type: 'warning' }, HANDLED: { label: '已处理', type: 'success' }, IGNORED: { label: '已忽略', type: 'info', plain: true },
  RESOLVED: { label: '已消除', type: 'info' }
}
export const ALERT_TYPES: Option[] = [
  { value: 'STOCK_LOW', label: '低于安全库存' }, { value: 'STOCK_HIGH', label: '超过最高库存' }, { value: 'BATCH_EXPIRING', label: '批次临期' },
  { value: 'BATCH_EXPIRED', label: '批次过期' }, { value: 'QC_OVERDUE', label: '待检超时' }, { value: 'DELIVERY_DELAY', label: '交期延期' },
  { value: 'PO_OVERDUE', label: '采购逾期未到货' }, { value: 'CERT_EXPIRING', label: '证书到期' }, { value: 'SUPPLIER_CERT_EXPIRING', label: '供应商资质到期' },
  { value: 'TOOLING_LIFE', label: '工装寿命' }, { value: 'AR_OVERDUE', label: '应收逾期' }, { value: 'RATE_MISSING', label: '汇率未维护' },
  { value: 'DEFECT_SPIKE', label: '不良突增' }
]
export const WF_INSTANCE_STATUS: StatusMap = {
  RUNNING: { label: '审批中', type: 'warning' }, APPROVED: { label: '已通过', type: 'success' }, REJECTED: { label: '已驳回', type: 'danger' },
  WITHDRAWN: { label: '已撤回', type: 'info', plain: true }, TERMINATED: { label: '已终止', type: 'info' }
}
export const WF_TASK_STATUS: StatusMap = {
  APPROVED: { label: '通过', type: 'success' }, REJECTED: { label: '驳回', type: 'danger' }, TRANSFERRED: { label: '转交', type: 'info' },
  CANCELED: { label: '已取消', type: 'info', plain: true }, AUTO_PASSED: { label: '自动通过', type: 'info', plain: true }, PENDING: { label: '待处理', type: 'warning' }
}
export const NOTICE_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' }, PUBLISHED: { label: '已发布', type: 'success' }, WITHDRAWN: { label: '已撤回', type: 'info', plain: true }
}

// ==================== 首页 ====================

export interface Summary { approvals: number; tasks: number; alerts: number; unreadMessages: number; pollSeconds: number }
export interface CardVO { code: string; name: string; type: 'METRIC' | 'CHART'; route?: string; visible: boolean }
export interface CardPoint { label: string; value: string }
export interface CardData {
  code: string; value?: string; unit: 'AMOUNT' | 'COUNT' | 'PERCENT'; changePct?: string; compareLabel?: string; costLike: boolean; subText?: string
  series: CardPoint[]; updatedAt: string
}

export const homeApi = {
  summary: () => http.get<Summary>('/workbench/summary', undefined, { silent: true }),
  /** 首页天气（Open-Meteo，后端缓存 30 分钟） */
  weather: () => http.get<Weather>('/workbench/weather', undefined, { silent: true }),
  cards: () => http.get<CardVO[]>('/workbench/cards'),
  cardData: (code: string, refresh = false) => http.get<CardData>(`/workbench/cards/${code}/data`, { refresh }, { silent: true }),
  saveLayout: (items: { code: string; visible: boolean }[]) => http.put<void>('/workbench/layout', { items }),
  resetLayout: () => http.post<void>('/workbench/layout/reset'),
  shortcuts: () => http.get<string[]>('/workbench/shortcuts'),
  saveShortcuts: (routes: string[]) => http.put<void>('/workbench/shortcuts', { routes })
}

// ==================== 待办 ====================

export interface Todo {
  id: string; todoKey: string; category: string; bizType?: string; bizNo?: string; bizId?: string; title: string; route?: string; priority: string
  dueTime?: string; overdue: boolean; status: string; createdAt: string; doneAt?: string; taskId?: string; manual: boolean; link?: string
}
export type TodoQuery = PageParam & { status?: string; category?: string; bizType?: string; keyword?: string; overdue?: boolean }
/** 审批流（系统管理）：我已处理、我发起的 */
export interface MyTask {
  taskId: string; instanceId: string; bizType: string; bizTypeName?: string; bizId: string; bizNo?: string; title?: string; nodeName?: string
  status: string; initiatorName?: string; startedAt?: string; createdAt?: string; handledAt?: string; detailRoute?: string
}
export interface MyInstance {
  id: string; bizType: string; bizTypeName?: string; bizId: string; bizNo?: string; title?: string; status: string; currentNodeName?: string
  startedAt: string; finishedAt?: string; detailRoute?: string
}
export interface BatchApproveResult { taskId: string; bizNo?: string; success: boolean; message?: string }

export const todoApi = {
  page: (params: TodoQuery) => http.get<PageResult<Todo>>('/workbench/todos', params),
  count: () => http.get<number>('/workbench/todos/count', undefined, { silent: true }),
  done: (id: string) => http.post<void>(`/workbench/todos/${id}/done`),
  approve: (taskId: string, comment?: string) => http.post<void>(`/system/workflow/tasks/${taskId}/approve`, { comment }),
  reject: (taskId: string, comment: string) => http.post<void>(`/system/workflow/tasks/${taskId}/reject`, { comment }),
  batchApprove: (taskIds: string[], comment?: string) => http.post<BatchApproveResult[]>('/system/workflow/tasks/batch-approve', { taskIds, comment }),
  myDone: (params: PageParam) => http.get<PageResult<MyTask>>('/system/workflow/tasks/my', { ...params, status: 'DONE' }),
  myStarted: (params: PageParam & { status?: string }) => http.get<PageResult<MyInstance>>('/system/workflow/instances/my', params),
  withdraw: (instanceId: string) => http.post<void>(`/system/workflow/instances/${instanceId}/withdraw`)
}

// ==================== 消息 ====================

export interface Message { id: string; msgType: string; title: string; content?: string; route?: string; read: boolean; readAt?: string; createdAt: string }
export interface UnreadCount { total: number; byType: Record<string, number> }

export const messageApi = {
  page: (params: PageParam & { type?: string; read?: boolean }) => http.get<PageResult<Message>>('/workbench/messages', params),
  unread: () => http.get<UnreadCount>('/workbench/messages/unread-count', undefined, { silent: true }),
  read: (id: string) => http.post<void>(`/workbench/messages/${id}/read`),
  readAll: (type?: string) => http.post<{ count: number }>(`/workbench/messages/read-all${type ? `?type=${type}` : ''}`),
  deleteRead: () => http.delete<{ count: number }>('/workbench/messages/read')
}

// ==================== 公告 ====================

export interface NoticeRow {
  id: string; title: string; scope: string; deptIds: string[]; deptNames?: string; important: boolean; publishAt: string; expireAt?: string
  status: string; readCount: number; targetCount: number; publisherName?: string; createdAt: string
}
export interface NoticeFile { id: string; fileName: string; ext?: string; size: number; createdAt?: string }
export interface NoticeDetail { header: NoticeRow; content: string; read: boolean; files: NoticeFile[] }
export interface NoticeSave {
  title: string; content: string; scope: string; deptIds?: string[]; important: boolean; publishAt?: string; expireAt?: string; fileIds?: string[]
}
export interface ActiveNotice { id: string; title: string; content: string; important: boolean; publishAt: string; read: boolean }
export interface NoticeReader { userId: string; userName?: string; deptName?: string; readAt: string }

export const noticeApi = {
  page: (params: PageParam & { keyword?: string; status?: string }) => http.get<PageResult<NoticeRow>>('/workbench/notices', params),
  get: (id: string) => http.get<NoticeDetail>(`/workbench/notices/${id}`),
  create: (data: NoticeSave) => http.post<string>('/workbench/notices', data),
  update: (id: string, data: NoticeSave) => http.put<void>(`/workbench/notices/${id}`, data),
  remove: (id: string) => http.delete<void>(`/workbench/notices/${id}`),
  publish: (id: string) => http.post<void>(`/workbench/notices/${id}/publish`),
  withdraw: (id: string) => http.post<void>(`/workbench/notices/${id}/withdraw`),
  readers: (id: string) => http.get<NoticeReader[]>(`/workbench/notices/${id}/readers`),
  active: (importantUnread = false) => http.get<ActiveNotice[]>('/workbench/notices/active', { importantUnread }, { silent: true }),
  read: (id: string) => http.post<void>(`/workbench/notices/${id}/read`)
}

// ==================== 预警 ====================

export interface Alert {
  id: string; alertKey: string; alertType: string; level: string; bizType?: string; bizId?: string; title: string; content?: string; route?: string
  status: string; firstRaisedAt: string; lastRaisedAt: string; handledByName?: string; handledAt?: string; handleRemark?: string
}
export interface AlertStats { critical: number; warning: number; info: number }

export const alertApi = {
  page: (params: PageParam & { level?: string; alertType?: string; statuses?: string }) => http.get<PageResult<Alert>>('/workbench/alerts', params),
  stats: () => http.get<AlertStats>('/workbench/alerts/stats'),
  handle: (id: string, remark: string) => http.post<void>(`/workbench/alerts/${id}/handle`, { remark }),
  ignore: (id: string, remark: string) => http.post<void>(`/workbench/alerts/${id}/ignore`, { remark })
}

/** 首页天气预报；available=false 表示暂时取不到，stale=true 表示显示的是上次成功获取的数据 */
export interface WeatherDay { date: string; code: number; text: string; icon: string; min?: string; max?: string; rainProbability?: number }
export interface Weather {
  enabled: boolean
  available: boolean
  stale: boolean
  city: string
  temperature?: string
  humidity?: number
  windSpeed?: string
  code: number
  text?: string
  icon?: string
  days: WeatherDay[]
  updatedAt?: string
}
