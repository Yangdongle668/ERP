import { http, type PageParam, type PageResult } from '@/api/http'
import type { Option, StatusMap } from '@/components'

/** PMC 模块接口与公共类型（需求 06-PMC）。数量均为基本单位 */

export const DEMAND_TYPE: StatusMap = {
  SALES_ORDER: { label: '销售订单', type: 'primary' },
  FORECAST: { label: '预测', type: 'warning', plain: true },
  MANUAL: { label: '手工', type: 'info' }
}
export const DEMAND_STATUS: StatusMap = {
  OPEN: { label: '未满足', type: 'warning' },
  CLOSED: { label: '已关闭', type: 'info', plain: true }
}
export const PLAN_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true }
}
export const RUN_STATUS: StatusMap = {
  RUNNING: { label: '运算中', type: 'warning' },
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
}
export const SUGGESTION_STATUS: StatusMap = {
  PENDING: { label: '待处理', type: 'warning' },
  CONVERTED: { label: '已转单', type: 'success' },
  IGNORED: { label: '已忽略', type: 'info', plain: true },
  SUPERSEDED: { label: '已过期', type: 'info', plain: true }
}
export const EXCEPTION_TYPE: StatusMap = {
  EXPEDITE: { label: '提前', type: 'danger' },
  DEFER: { label: '推迟', type: 'primary' },
  CANCEL: { label: '取消', type: 'info' },
  PAST_DUE: { label: '已逾期', type: 'danger', plain: true },
  DISABLED: { label: '物料停用', type: 'warning' }
}
export const ALERT_LEVEL: StatusMap = {
  CRITICAL: { label: '严重', type: 'danger' },
  WARNING: { label: '警告', type: 'warning' },
  INFO: { label: '提示', type: 'primary', plain: true }
}
export const ALERT_STATUS: StatusMap = {
  OPEN: { label: '未处理', type: 'warning' },
  HANDLED: { label: '已处理', type: 'success' },
  IGNORED: { label: '已忽略', type: 'info', plain: true },
  CLOSED: { label: '已消除', type: 'info', plain: true }
}
export const SHIP_LINE_STATUS: StatusMap = {
  PLANNED: { label: '已计划', type: 'primary', plain: true },
  NOTICED: { label: '已通知', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const RUN_TYPE_OPTIONS: Option[] = [
  { value: 'FULL', label: '全量' }, { value: 'NET_CHANGE', label: '净变更' }, { value: 'ORDER', label: '指定订单' }
]
export const SUGGESTION_TYPE_OPTIONS: Option[] = [
  { value: 'PURCHASE', label: '采购' }, { value: 'MAKE', label: '生产' }, { value: 'OUTSOURCE', label: '委外' }
]
export const ALERT_CAUSE_OPTIONS: Option[] = [
  { value: 'NO_STOCK_NO_WO', label: '无库存无生产订单' }, { value: 'MATERIAL_SHORTAGE', label: '缺料' },
  { value: 'CAPACITY', label: '产能' }, { value: 'WO_DELAY', label: '生产进度' }
]
export const PEG_TYPE: Record<string, string> = {
  SALES_ORDER: '销售订单', FORECAST: '预测', MANUAL: '手工需求', MPS: 'MPS', SAFETY_STOCK: '安全库存', PARENT: '上层计划订单', ALLOCATION: '在制分配'
}
export const BALANCE_TYPE: Record<string, string> = {
  ...PEG_TYPE, OPENING: '期初可用', PURCHASE: '在途采购', QC: '待检', WIP: '在制', SUBSTITUTE: '替代料', PLANNED: '计划订单'
}

export const optionsOf = (map: StatusMap, exclude: string[] = []): Option[] =>
  Object.entries(map).filter(([k]) => !exclude.includes(k)).map(([value, s]) => ({ value, label: s.label }))
export const labelOf = (options: Option[], v?: string | null) => (v ? options.find((o) => o.value === v)?.label ?? v : '-')
export const joinList = (v?: string[]) => (v?.length ? v.join(',') : undefined)
export const num = (v?: string | number | null) => (v === undefined || v === null || v === '' ? 0 : Number(v))
export const pct = (v?: string | number | null, digits = 0) => (v === undefined || v === null || v === '' ? '-' : `${(Number(v) * 100).toFixed(digits)}%`)

/** ISO 周（2026-W40） */
export function isoWeek(d: Date): string {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()))
  const day = t.getUTCDay() || 7
  t.setUTCDate(t.getUTCDate() + 4 - day)
  const year = t.getUTCFullYear()
  const week = Math.ceil(((t.getTime() - Date.UTC(year, 0, 1)) / 86400000 + 1) / 7)
  return `${year}-W${String(week).padStart(2, '0')}`
}
export function addWeeks(week: string, n: number): string {
  const m = /^(\d{4})-W(\d{2})$/.exec(week)
  if (!m) return week
  const jan4 = new Date(Number(m[1]), 0, 4)
  const monday = new Date(jan4)
  monday.setDate(jan4.getDate() - ((jan4.getDay() || 7) - 1) + (Number(m[2]) - 1) * 7 + n * 7)
  return isoWeek(monday)
}
/** 该周的周一（YYYY-MM-DD） */
export function weekMonday(week: string): string {
  const m = /^(\d{4})-W(\d{2})$/.exec(week)
  if (!m) return ''
  const jan4 = new Date(Number(m[1]), 0, 4)
  const monday = new Date(jan4)
  monday.setDate(jan4.getDate() - ((jan4.getDay() || 7) - 1) + (Number(m[2]) - 1) * 7)
  return `${monday.getFullYear()}-${String(monday.getMonth() + 1).padStart(2, '0')}-${String(monday.getDate()).padStart(2, '0')}`
}

export interface BatchResult { success: number; errors: string[]; docNos: string[] }

// ==================== 需求池 / 交期回复 ====================

export interface DemandRow {
  id: string; demandType: string; sourceId?: string; sourceNo?: string; sourceLineNo?: number; customerId?: string; customerName?: string
  materialId: string; materialCode: string; materialName: string; materialSpec?: string; baseUom: string; qty: string; fulfilledQty: string
  openQty: string; requiredDate: string; customerDate?: string; promisedDate?: string; availableQty: string; wipQty: string; inTransitQty: string
  priority: number; demandStatus: string; remark?: string; createdByName?: string
}
export interface DemandSave { materialId: string; qty: string; requiredDate: string; priority?: number; customerId?: string; remark: string }
export interface ReplyRow {
  demandId: string; orderId: string; orderNo: string; lineNo: number; orderLineId: string; customerId?: string; customerName?: string; ownerId?: string
  ownerName?: string; materialId: string; materialCode: string; materialName: string; materialSpec?: string; baseUom: string; qty: string; openQty: string
  customerDate: string; promisedDate?: string; availableQty: string; wipQty: string; suggestedDate: string; suggestBasis: string; rereply: boolean
}
export interface KitLine {
  componentId: string; code: string; name: string; uom: string; requiredQty: string; availableQty: string; inTransitQty: string; kitDate: string; basis: string
}
export interface KitResult { materialId: string; qty: string; kitDate: string; leadTimeDays: number; suggestedDate: string; lines: KitLine[] }

export const demandApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<DemandRow>>('/pmc/demands', q),
  create: (d: DemandSave) => http.post<string>('/pmc/demands', d),
  update: (id: string, d: DemandSave) => http.put<void>(`/pmc/demands/${id}`, d),
  close: (id: string) => http.post<void>(`/pmc/demands/${id}/close`),
  reconcile: () => http.post<string>('/pmc/demands/reconcile'),
  pending: (q: { customerId?: string; materialId?: string }) => http.get<ReplyRow[]>('/pmc/delivery-replies/pending', q),
  reply: (lines: { demandId: string; promisedDate: string; remark?: string }[]) => http.post<number>('/pmc/delivery-replies', { lines }),
  kit: (materialId: string, qty: string) => http.post<KitResult>('/pmc/delivery-replies/kit-analysis', { materialId, qty })
}

// ==================== MPS ====================

export interface MpsRow {
  id: string; docNo: string; title: string; startWeek: string; endWeek: string; mpsStatus: string; materialCount: number; publishedAt?: string
  ownerName?: string; createdAt: string
}
export interface MpsDetail {
  id: string; docNo: string; title: string; startWeek: string; endWeek: string; mpsStatus: string; remark?: string; publishedAt?: string
  copiedFromId?: string; copiedFromNo?: string; ownerName?: string; createdAt: string; version: number
}
export interface MpsCell { week: string; demandQty: string; wipQty: string; plannedQty: string; projectedQty: string; locked: boolean; remark?: string }
export interface MpsRowData {
  materialId: string; materialCode: string; materialName: string; materialSpec?: string; baseUom: string; safetyStock: string; openingQty: string
  mpq?: string; cells: MpsCell[]
}
export interface MpsMatrix { mpsId: string; mpsStatus: string; weeks: string[]; rows: MpsRowData[] }
export interface CapRow { workCenterId: string; workCenterCode: string; workCenterName: string; cells: { week: string; loadHours: string; capacityHours: string; overloaded: boolean }[] }

export const mpsApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<MpsRow>>('/pmc/mps', q),
  get: (id: string) => http.get<MpsDetail>(`/pmc/mps/${id}`),
  create: (d: { title: string; startWeek: string; endWeek: string; remark?: string }) => http.post<string>('/pmc/mps', d),
  update: (id: string, d: { title: string; startWeek: string; endWeek: string; remark?: string; version?: number }) => http.put<void>(`/pmc/mps/${id}`, d),
  remove: (id: string) => http.delete<void>(`/pmc/mps/${id}`),
  publish: (id: string) => http.post<void>(`/pmc/mps/${id}/publish`),
  close: (id: string) => http.post<void>(`/pmc/mps/${id}/close`),
  copy: (id: string) => http.post<string>(`/pmc/mps/${id}/copy`),
  matrix: (id: string) => http.get<MpsMatrix>(`/pmc/mps/${id}/matrix`),
  saveMatrix: (id: string, rows: { materialId: string; cells: { week: string; plannedQty?: string; remark?: string }[] }[]) =>
    http.put<MpsMatrix>(`/pmc/mps/${id}/matrix`, { rows }),
  generate: (id: string, materialIds?: string[]) => http.post<MpsMatrix>(`/pmc/mps/${id}/generate`, { materialIds }),
  capacityCheck: (id: string) => http.post<CapRow[]>(`/pmc/mps/${id}/capacity-check`)
}

// ==================== MRP ====================

export interface RunRow {
  id: string; runNo: string; runType: string; runStatus: string; progress: number; startedAt: string; finishedAt?: string; durationSeconds?: number
  materialCount: number; suggestionCount: number; exceptionCount: number; operatorName?: string; errorMsg?: string; params?: string; latest: boolean
}
export interface RunReq { runType: string; orderLineIds?: string[]; horizonDays?: number; includeForecast?: boolean; includeSafety?: boolean; useSubstitute?: boolean }
export interface SuggestionRow {
  id: string; runId: string; type: string; materialId: string; materialCode: string; materialName: string; materialSpec?: string; baseUom: string
  qty: string; originalQty: string; netRequirement: string; requiredDate: string; releaseDate: string; late: boolean; lateDays: number
  supplierId?: string; supplierName?: string; plannerId?: string; plannerName?: string; buyerId?: string; buyerName?: string; bomId?: string
  deptId?: string; availableQty: string; inTransitQty: string; sourceSummary?: string; status: string; convertedDocType?: string
  convertedDocId?: string; convertedDocNo?: string; ignoreReason?: string; moq?: string; mpq?: string; version: number
}
export interface PegRow {
  id: string; demandType: string; sourceId?: string; sourceNo?: string; parentMaterialId?: string; parentCode?: string; parentName?: string
  parentResultId?: string; qty: string; requiredDate: string
}
export interface ExceptionRow {
  id: string; runId: string; type: string; materialId: string; materialCode: string; materialName: string; docType?: string; docId?: string
  docNo?: string; supplyDate?: string; suggestedDate?: string; qty: string; message: string; ownerId?: string; ownerName?: string; handled: boolean
  pushedAt?: string
}
export interface BalanceRow {
  date: string; type: string; docNo?: string; parentMaterialId?: string; parentCode?: string; demandQty: string; supplyQty: string; projectedQty: string
  safetyStock: string
}
export interface Balance {
  runId?: string; runNo?: string; materialId: string; materialCode: string; materialName: string; baseUom: string; safetyStock: string; rows: BalanceRow[]
}

export const mrpApi = {
  run: (d: RunReq) => http.post<string>('/pmc/mrp/runs', d),
  runs: (q: PageParam & Record<string, unknown>) => http.get<PageResult<RunRow>>('/pmc/mrp/runs', q),
  getRun: (id: string) => http.get<RunRow>(`/pmc/mrp/runs/${id}`),
  balance: (materialId: string, runId?: string) => http.get<Balance>('/pmc/mrp/balance', { materialId, runId }),
  suggestions: (q: PageParam & Record<string, unknown>) => http.get<PageResult<SuggestionRow>>('/pmc/mrp/suggestions', q),
  update: (id: string, d: { qty?: string; requiredDate?: string; supplierId?: string; bomId?: string; deptId?: string }) =>
    http.put<string[]>(`/pmc/mrp/suggestions/${id}`, d),
  convert: (ids: string[], release: boolean) => http.post<BatchResult>('/pmc/mrp/suggestions/convert', { ids, release }),
  ignore: (ids: string[], reason: string) => http.post<number>('/pmc/mrp/suggestions/ignore', { ids, reason }),
  pegging: (id: string) => http.get<PegRow[]>(`/pmc/mrp/suggestions/${id}/pegging`),
  exceptions: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ExceptionRow>>('/pmc/mrp/exceptions', q),
  push: (ids: string[]) => http.post<BatchResult>('/pmc/mrp/exceptions/push', { ids }),
  handled: (id: string, handled: boolean) => http.post<void>(`/pmc/mrp/exceptions/${id}/handled?handled=${handled}`)
}

// ==================== 产能 / 排产 ====================

export interface CalendarDay {
  date: string; availableHours?: string; defaultHours?: string; exception: boolean; reason?: string; loadHours: string; loadRate?: string
}
export interface CalendarMonth { workCenterId: string; workCenterName?: string; month: string; days: CalendarDay[] }
export interface LoadRow {
  workCenterId: string; workCenterCode: string; workCenterName: string; deptId?: string; totalLoad: string; totalCapacity: string
  cells: { date: string; loadHours: string; capacityHours: string; loadRate?: string; overloaded: boolean }[]
}
export interface LoadDetail { prodOrderId: string; prodOrderNo: string; materialCode: string; materialName: string; operationSeq: number; operation: string; hours: string; scheduled: boolean }
export interface ScheduleRow {
  id: string; prodOrderId: string; prodOrderNo: string; materialId: string; materialCode: string; materialName: string; qty?: string; operationSeq: number
  operation?: string; workCenterId?: string; workCenterName?: string; deptId?: string; schedStart: string; schedEnd: string; loadHours: string
  dueDate?: string; late: boolean; priority: number; locked: boolean; manual: boolean; applied: boolean; prodStatus?: string
}
export interface SimulateResult {
  prodOrderId: string; prodOrderNo: string; newEnd?: string
  delayed: { prodOrderId: string; prodOrderNo: string; materialCode: string; oldEnd: string; newEnd: string; delayDays: number; dueDate?: string; lateAfter: boolean }[]
}
export interface ApplyRow { prodOrderId: string; prodOrderNo: string; materialCode: string; planStart: string; planEnd: string; newStart: string; newEnd: string; prodStatus: string }
export interface WorkCenterSimple { id: string; code: string; name: string }

export const capacityApi = {
  workCenters: () => http.get<WorkCenterSimple[]>('/engineering/work-centers/simple'),
  month: (workCenterId: string, month: string) => http.get<CalendarMonth>('/pmc/capacity-calendars', { workCenterId, month }),
  save: (d: { workCenterId: string; date: string; hours?: string | null; reason?: string }) => http.put<void>('/pmc/capacity-calendars', d),
  batch: (d: { workCenterId: string; from: string; to: string; hours?: string | null; reason?: string }) => http.post<number>('/pmc/capacity-calendars/batch', d),
  load: (q: { from?: string; to?: string; deptId?: string }) => http.get<LoadRow[]>('/pmc/capacity/load', q),
  loadDetail: (workCenterId: string, date: string) => http.get<LoadDetail[]>('/pmc/capacity/load-detail', { workCenterId, date })
}
export const scheduleApi = {
  run: (d: { deptId?: string; mode?: string }) => http.post<{ orderCount: number; operationCount: number; lateCount: number; mode: string }>('/pmc/schedules/run', d),
  list: (q: { from?: string; to?: string; deptId?: string }) => http.get<ScheduleRow[]>('/pmc/schedules', q),
  adjust: (id: string, d: { workCenterId?: string; startDate: string }) => http.put<void>(`/pmc/schedules/${id}`, d),
  lock: (id: string, locked: boolean) => http.post<void>(`/pmc/schedules/${id}/lock?locked=${locked}`),
  simulate: (prodOrderId: string, priority: number) => http.post<SimulateResult>('/pmc/schedules/simulate', { prodOrderId, priority }),
  confirm: (prodOrderId: string, priority: number) => http.post<unknown>('/pmc/schedules/simulate/confirm', { prodOrderId, priority }),
  applyPreview: () => http.get<ApplyRow[]>('/pmc/schedules/apply-preview'),
  apply: (prodOrderIds: string[]) => http.post<number>('/pmc/schedules/apply', { prodOrderIds })
}

// ==================== 缺料 ====================

export interface SupplyItem { docType?: string; docNo?: string; date: string; qty: string }
export interface ShortageOrder {
  prodOrderId: string; prodOrderNo: string; priority: number; productId: string; productCode: string; productName: string; qty: string
  planStart?: string; prodStatus?: string; lineKitRate: string; qtyKitRate: string; kitableQty: string; lineCount: number; shortLineCount: number
  etaDate?: string; etaLate: boolean; hasNoSupply: boolean; salesOrderNo?: string; customerDate?: string
}
export interface ShortageLine {
  prodOrderId: string; componentId: string; componentCode: string; componentName: string; uom: string; unissuedQty: string; allocatedQty: string
  shortageQty: string; supplies: SupplyItem[]; etaDate?: string; noSupplyQty: string; buyerId?: string; buyerName?: string
}
export interface ShortageMaterial {
  componentId: string; componentCode: string; componentName: string; uom: string; shortageQty: string; orderCount: number; firstNeedDate?: string
  supplies: SupplyItem[]; noSupplyQty: string; buyerId?: string; buyerName?: string; orderNos: string[]
}
export interface SnapshotRow { snapshotNo: string; createdAt: string; orderCount: number; shortOrderCount: number; createdByName?: string }

export const shortageApi = {
  analyze: (d: { deptId?: string; statuses?: string[]; planStartFrom?: string; planStartTo?: string; prodOrderIds?: string[] }) =>
    http.post<{ snapshotNo?: string; orderCount: number; shortOrderCount: number; shortMaterialCount: number }>('/pmc/shortages/analyze', d),
  orders: (snapshotNo?: string) => http.get<ShortageOrder[]>('/pmc/shortages/orders', { snapshotNo }),
  materials: (snapshotNo?: string) => http.get<ShortageMaterial[]>('/pmc/shortages/materials', { snapshotNo }),
  lines: (snapshotNo: string | undefined, prodOrderId: string) => http.get<ShortageLine[]>('/pmc/shortages/lines', { snapshotNo, prodOrderId }),
  snapshots: () => http.get<SnapshotRow[]>('/pmc/shortages/snapshots'),
  push: (snapshotNo: string | undefined, componentIds: string[]) => http.post<BatchResult>('/pmc/shortages/push', { snapshotNo, componentIds })
}

// ==================== 交期预警 ====================

export interface AlertRow {
  id: string; orderId: string; orderNo: string; lineNo: number; orderLineId: string; customerId?: string; customerName?: string; materialId: string
  materialCode: string; materialName: string; openQty: string; promisedDate: string; estimatedDate: string; delayDays: number; alertLevel: string
  cause: string; causeDetail?: string; ownerId?: string; ownerName?: string; handleStatus: string; handleRemark?: string; handledByName?: string
  handledAt?: string; calculatedAt: string
}
export const alertApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<AlertRow>>('/pmc/delivery-alerts', q),
  summary: (q: Record<string, unknown>) => http.get<{ critical: number; warning: number; info: number }>('/pmc/delivery-alerts/summary', q),
  recalculate: () => http.post<{ lineCount: number; alertCount: number; raised: number; resolved: number }>('/pmc/delivery-alerts/recalculate'),
  handle: (id: string, remark: string) => http.post<void>(`/pmc/delivery-alerts/${id}/handle`, { remark }),
  ignore: (id: string, remark: string) => http.post<void>(`/pmc/delivery-alerts/${id}/ignore`, { remark })
}

// ==================== 出货计划 ====================

export interface ShipPlanRow {
  id: string; docNo: string; planWeek: string; planStatus: string; lineCount: number; planQty: string; noticedLineCount: number; publishedAt?: string
  ownerName?: string; createdAt: string
}
export interface ShipPlanLine {
  id?: string; lineNo?: number; orderLineId: string; orderId?: string; orderNo?: string; orderLineNo?: number; customerId?: string; customerName?: string
  materialId?: string; materialCode?: string; materialName?: string; baseUom?: string; dueDate?: string; openQty?: string; availableQty?: string
  planQty?: string; planShipDate?: string; transportMode?: string; noticedQty?: string; lineStatus?: string; remark?: string; shortage?: boolean
}
export interface ShipPlanDetail {
  id: string; docNo: string; planWeek: string; planStatus: string; remark?: string; publishedAt?: string; ownerName?: string; createdAt: string
  version: number; lines: ShipPlanLine[]
}
export interface ShipPlanSave {
  planWeek: string; remark?: string; version?: number
  lines: { id?: string; orderLineId: string; planQty: string; planShipDate?: string; transportMode?: string; remark?: string }[]
}
export const shippingPlanApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ShipPlanRow>>('/pmc/shipping-plans', q),
  get: (id: string) => http.get<ShipPlanDetail>(`/pmc/shipping-plans/${id}`),
  create: (d: ShipPlanSave) => http.post<string>('/pmc/shipping-plans', d),
  update: (id: string, d: ShipPlanSave) => http.put<void>(`/pmc/shipping-plans/${id}`, d),
  remove: (id: string) => http.delete<void>(`/pmc/shipping-plans/${id}`),
  generate: (id: string) => http.post<ShipPlanDetail>(`/pmc/shipping-plans/${id}/generate`),
  publish: (id: string) => http.post<void>(`/pmc/shipping-plans/${id}/publish`),
  close: (id: string) => http.post<void>(`/pmc/shipping-plans/${id}/close`)
}
