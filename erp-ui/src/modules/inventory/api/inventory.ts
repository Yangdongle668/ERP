import { http, type PageResult } from '@/api/http'
import type { Option, StatusMap } from '@/components'

// ==================== 枚举与显示 ====================

export type WarehouseType = 'RAW' | 'SEMI' | 'FG' | 'FPC' | 'ELEC' | 'PKG' | 'AUX' | 'NG' | 'QC' | 'RTN'
export const WAREHOUSE_TYPE_OPTIONS: { value: WarehouseType; label: string }[] = [
  { value: 'RAW', label: '原材料仓' }, { value: 'SEMI', label: '半成品仓' }, { value: 'FG', label: '成品仓' },
  { value: 'FPC', label: 'FPC 仓' }, { value: 'ELEC', label: '电子料仓' }, { value: 'PKG', label: '包材仓' },
  { value: 'AUX', label: '辅料仓' }, { value: 'NG', label: '不良品仓' }, { value: 'QC', label: '待检仓' }, { value: 'RTN', label: '退货仓' }
]
export const AVAILABLE_TYPES: WarehouseType[] = ['RAW', 'SEMI', 'FG', 'FPC', 'ELEC', 'PKG', 'AUX']

export const IN_TYPE_OPTIONS: Option[] = [
  { value: 'PURCHASE_IN', label: '采购入库' }, { value: 'OUTSOURCE_IN', label: '委外入库' }, { value: 'OUTSOURCE_RETURN', label: '委外退料入库' },
  { value: 'PRODUCTION_IN', label: '生产入库' }, { value: 'PRODUCTION_RETURN', label: '生产退料入库' }, { value: 'SALES_RETURN', label: '销售退货入库' },
  { value: 'OTHER_IN', label: '其他入库' }, { value: 'COUNT_GAIN', label: '盘盈入库' }, { value: 'OPENING', label: '期初入库' }
]
export const OUT_TYPE_OPTIONS: Option[] = [
  { value: 'PRODUCTION_ISSUE', label: '生产领料出库' }, { value: 'OUTSOURCE_ISSUE', label: '委外发料出库' }, { value: 'SALES_OUT', label: '销售出库' },
  { value: 'PURCHASE_RETURN', label: '采购退货出库' }, { value: 'OTHER_OUT', label: '其他出库' }, { value: 'COUNT_LOSS', label: '盘亏出库' }
]
export const TRANSFER_TYPE_OPTIONS: Option[] = [
  { value: 'NORMAL', label: '普通调拨' }, { value: 'INSPECTION', label: '检验调拨' }, { value: 'RECHECK', label: '复检送检' }
]
export const JUDGE_OPTIONS: Option[] = [
  { value: 'QUALIFIED', label: '合格' }, { value: 'CONCESSION', label: '特采' }, { value: 'REJECTED', label: '不合格' }
]
export const BIZ_TYPE_OPTIONS: Option[] = [...IN_TYPE_OPTIONS, ...OUT_TYPE_OPTIONS, ...TRANSFER_TYPE_OPTIONS]

export const labelOf = (opts: Option[], v?: string | null) => opts.find((o) => o.value === v)?.label ?? v ?? ''

/** 出入库单、调拨单状态 */
export const DOC_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_APPROVAL: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已审核', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  VOIDED: { label: '已作废', type: 'danger', plain: true }
}
export const DOC_STATUS_OPTIONS = Object.entries(DOC_STATUS).map(([value, s]) => ({ value, label: s.label }))

export const COUNT_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  COUNTING: { label: '盘点中', type: 'warning' },
  SUBMITTED: { label: '已提交', type: 'primary' },
  APPROVED: { label: '已审核', type: 'success' },
  VOIDED: { label: '已作废', type: 'danger', plain: true }
}
export const COUNT_STATUS_OPTIONS = Object.entries(COUNT_STATUS).map(([value, s]) => ({ value, label: s.label }))

export const PERIOD_STATUS: StatusMap = {
  OPEN: { label: '未结账', type: 'primary' },
  CLOSED: { label: '已结账', type: 'success' }
}

// ==================== 仓库与库位 ====================

export interface WarehouseRow {
  id: string
  code: string
  name: string
  warehouseType: WarehouseType
  available: boolean
  isDefault: boolean
  locationEnabled: boolean
  allowNegative: boolean
  managerId?: string
  managerName?: string
  address?: string
  userIds: string[]
  userNames: string[]
  locationCount: number
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  version: number
}
export interface WarehouseSave {
  code: string
  name: string
  warehouseType?: WarehouseType
  managerId?: string
  address?: string
  locationEnabled?: boolean
  allowNegative?: boolean
  isDefault?: boolean
  remark?: string
  version?: number
}
export interface LocationRow {
  id: string
  warehouseId: string
  code: string
  name?: string
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  hasStock: boolean
  version: number
}
export interface LocationGenerate {
  zoneFrom: string
  zoneTo: string
  rowFrom: number
  rowTo: number
  levelFrom: number
  levelTo: number
  posFrom: number
  posTo: number
  preview: boolean
}
export interface CategoryWarehouseRow {
  id: string
  categoryId: string
  categoryPath: string
  warehouseId: string
  warehouseName: string
  warehouseType: WarehouseType
}

export const warehouseApi = {
  list: (params: { keyword?: string; type?: string; status?: string }) => http.get<WarehouseRow[]>('/inventory/warehouses', params),
  create: (d: WarehouseSave) => http.post<string>('/inventory/warehouses', d),
  update: (id: string, d: WarehouseSave) => http.put<void>(`/inventory/warehouses/${id}`, d),
  remove: (id: string) => http.delete<void>(`/inventory/warehouses/${id}`),
  enable: (id: string) => http.post<void>(`/inventory/warehouses/${id}/enable`),
  disable: (id: string) => http.post<void>(`/inventory/warehouses/${id}/disable`),
  setUsers: (id: string, userIds: string[]) => http.put<void>(`/inventory/warehouses/${id}/users`, { userIds }),
  locations: (id: string) => http.get<LocationRow[]>(`/inventory/warehouses/${id}/locations`),
  createLocation: (id: string, d: Partial<LocationRow>) => http.post<string>(`/inventory/warehouses/${id}/locations`, d),
  updateLocation: (id: string, locId: string, d: Partial<LocationRow>) => http.put<void>(`/inventory/warehouses/${id}/locations/${locId}`, d),
  enableLocation: (id: string, locId: string) => http.post<void>(`/inventory/warehouses/${id}/locations/${locId}/enable`),
  disableLocation: (id: string, locId: string) => http.post<void>(`/inventory/warehouses/${id}/locations/${locId}/disable`),
  removeLocation: (id: string, locId: string) => http.delete<void>(`/inventory/warehouses/${id}/locations/${locId}`),
  generate: (id: string, d: LocationGenerate) => http.post<{ codes: string[]; skipped: string[] }>(`/inventory/warehouses/${id}/locations/batch-generate`, d),
  categoryWarehouses: () => http.get<CategoryWarehouseRow[]>('/inventory/category-warehouses'),
  createCategoryWarehouse: (d: { categoryId: string; warehouseId: string }) => http.post<string>('/inventory/category-warehouses', d),
  updateCategoryWarehouse: (id: string, d: { categoryId: string; warehouseId: string }) => http.put<void>(`/inventory/category-warehouses/${id}`, d),
  removeCategoryWarehouse: (id: string) => http.delete<void>(`/inventory/category-warehouses/${id}`)
}

// ==================== 出入库单、调拨单 ====================

export type DocKind = 'in' | 'out' | 'transfer'

export interface DocQuery {
  pageNo: number
  pageSize: number
  docNo?: string
  types?: string
  warehouseId?: string
  toWarehouseId?: string
  statuses?: string
  sourceNo?: string
  materialId?: string
  supplierId?: string
  dateFrom?: string
  dateTo?: string
  quick?: string
  ids?: string
}

export interface DocRow {
  id: string
  docNo: string
  docType: string
  type: string
  warehouseId: string
  warehouseName?: string
  warehouseType?: WarehouseType
  toWarehouseId?: string
  toWarehouseName?: string
  toWarehouseType?: WarehouseType
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  partnerName?: string
  materialSummary: string
  lineCount: number
  totalQty?: string
  amount?: string
  docDate: string
  status: string
  manual: boolean
  confirmedByName?: string
  confirmedAt?: string
  createdByName?: string
}

export interface BatchResult {
  success: number
  failures: { id: string; docNo: string; message: string }[]
}

export interface StockInLine {
  id?: string
  lineNo?: number
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  baseUom?: string
  tracking?: string
  shelfLifeDays?: number
  uom?: string
  qty?: string
  baseQty?: string
  locationId?: string
  locationCode?: string
  batchNo?: string
  supplierBatchNo?: string
  productionDate?: string
  expireDate?: string
  serialNos?: string[]
  unitCost?: string
  amount?: string
  sourceLineId?: string
  remark?: string
}

export interface StockInDetail {
  id: string
  docNo: string
  inType: string
  warehouseId: string
  warehouseName: string
  warehouseType: WarehouseType
  locationEnabled: boolean
  docDate: string
  status: string
  manual: boolean
  reason?: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  sourceDate?: string
  supplierId?: string
  customerId?: string
  remark?: string
  confirmedByName?: string
  confirmedAt?: string
  rejectReason?: string
  createdByName?: string
  createdAt: string
  version: number
  lines: StockInLine[]
}

export interface StockOutLine {
  id?: string
  lineNo?: number
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  baseUom?: string
  tracking?: string
  issueRule?: string
  uom?: string
  requestQty?: string
  qty?: string
  baseQty?: string
  availableQty?: string
  locationId?: string
  locationCode?: string
  batchNo?: string
  serialNos?: string[]
  sourceLineId?: string
  remark?: string
  /** 自动分配：库存不足数量 */
  shortage?: string
}

export interface StockOutDetail {
  id: string
  docNo: string
  outType: string
  warehouseId: string
  warehouseName: string
  warehouseType: WarehouseType
  locationEnabled: boolean
  docDate: string
  status: string
  manual: boolean
  reason?: string
  receiverDeptId?: string
  receiverId?: string
  receiverName?: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  sourceDate?: string
  supplierId?: string
  customerId?: string
  remark?: string
  confirmedByName?: string
  confirmedAt?: string
  rejectReason?: string
  createdByName?: string
  createdAt: string
  version: number
  lines: StockOutLine[]
}

export interface AllocatedLine {
  id?: string
  materialId: string
  materialCode: string
  uom: string
  requestQty?: string
  qty: string
  locationId?: string
  locationCode?: string
  batchNo?: string
  sourceLineId?: string
  shortage?: string
}

export interface TransferLine {
  id?: string
  lineNo?: number
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  baseUom?: string
  tracking?: string
  qty?: string
  availableQty?: string
  batchNo?: string
  fromLocationId?: string
  fromLocationCode?: string
  toLocationId?: string
  toLocationCode?: string
  serialNos?: string[]
  judgeResult?: string
  remark?: string
}

export interface TransferDetail {
  id: string
  docNo: string
  transferType: string
  fromWarehouseId: string
  fromWarehouseName: string
  fromWarehouseType: WarehouseType
  fromLocationEnabled: boolean
  toWarehouseId: string
  toWarehouseName: string
  toWarehouseType: WarehouseType
  toLocationEnabled: boolean
  docDate: string
  status: string
  reason?: string
  inspectionId?: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  remark?: string
  confirmedByName?: string
  confirmedAt?: string
  createdByName?: string
  createdAt: string
  version: number
  lines: TransferLine[]
}

const base = (k: DocKind) => (k === 'in' ? '/inventory/stock-ins' : k === 'out' ? '/inventory/stock-outs' : '/inventory/transfers')

/** 三类单据共用的列表与动作接口 */
export const docApi = (k: DocKind) => ({
  base: base(k),
  page: (q: DocQuery) => http.get<PageResult<DocRow>>(base(k), q),
  quickCounts: () => http.get<{ todo: number; today: number }>(`${base(k)}/quick-counts`),
  remove: (id: string) => http.delete<void>(`${base(k)}/${id}`),
  submit: (id: string) => http.post<string>(`${base(k)}/${id}/submit`),
  confirm: (id: string, body?: object) => http.post<void>(`${base(k)}/${id}/confirm`, body ?? {}),
  batchConfirm: (ids: string[]) => http.post<BatchResult>(`${base(k)}/batch-confirm`, { ids }),
  reject: (id: string, reason: string) => http.post<void>(`${base(k)}/${id}/reject`, { reason }),
  unconfirm: (id: string, reason: string) => http.post<void>(`${base(k)}/${id}/unconfirm`, { reason }),
  void: (id: string, reason?: string) => http.post<void>(`${base(k)}/${id}/void`, { reason })
})

export const stockInApi = {
  get: (id: string) => http.get<StockInDetail>(`/inventory/stock-ins/${id}`),
  create: (d: object) => http.post<string>('/inventory/stock-ins', d),
  update: (id: string, d: object) => http.put<void>(`/inventory/stock-ins/${id}`, d)
}
export const stockOutApi = {
  get: (id: string) => http.get<StockOutDetail>(`/inventory/stock-outs/${id}`),
  create: (d: object) => http.post<string>('/inventory/stock-outs', d),
  update: (id: string, d: object) => http.put<void>(`/inventory/stock-outs/${id}`, d),
  autoAllocate: (id: string, lines?: object[]) => http.post<AllocatedLine[]>(`/inventory/stock-outs/${id}/auto-allocate`, lines as unknown as object)
}
export const transferApi = {
  get: (id: string) => http.get<TransferDetail>(`/inventory/transfers/${id}`),
  create: (d: object) => http.post<string>('/inventory/transfers', d),
  update: (id: string, d: object) => http.put<void>(`/inventory/transfers/${id}`, d)
}

// ==================== 盘点 ====================

export interface CountRow {
  id: string
  docNo: string
  countType: 'FULL' | 'PARTIAL'
  warehouseNames: string
  scopeSummary: string
  lineCount: number
  inputCount: number
  diffCount: number
  diffAmount?: string
  countStatus: string
  createdByName?: string
  docDate: string
}
export interface CountSave {
  countType: 'FULL' | 'PARTIAL'
  warehouseIds: string[]
  categoryIds?: string[]
  locationIds?: string[]
  materialIds?: string[]
  includeZero?: boolean
  blindCount?: boolean
  docDate?: string
  remark?: string
  version?: number
}
export interface CountDetail extends Omit<CountSave, 'version'> {
  id: string
  docNo: string
  warehouseNames: string
  scopeSummary: string
  snapshotAt?: string
  countStatus: string
  status: string
  gainInId?: string
  lossOutId?: string
  adjustDocs: { docType: string; id: string; docNo: string; status: string }[]
  createdByName?: string
  createdAt: string
  version: number
  lineCount: number
  inputCount: number
  diffCount: number
  recountCount: number
  diffAmount?: string
  bookVisible: boolean
}
export interface CountLineRow {
  id: string
  lineNo: number
  warehouseId: string
  warehouseName: string
  locationId?: string
  locationCode?: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  batchNo?: string
  bookQty?: string
  countQty?: string
  recountQty?: string
  finalQty?: string
  diffQty?: string
  diffAmount?: string
  needRecount: boolean
  added: boolean
  reason?: string
  counterName?: string
  countedAt?: string
  remark?: string
}
export interface LineInput {
  id: string
  countQty?: string
  recountQty?: string
  reason?: string
  remark?: string
}

export const countApi = {
  page: (q: object) => http.get<PageResult<CountRow>>('/inventory/counts', q),
  get: (id: string) => http.get<CountDetail>(`/inventory/counts/${id}`),
  create: (d: CountSave) => http.post<string>('/inventory/counts', d),
  update: (id: string, d: CountSave) => http.put<void>(`/inventory/counts/${id}`, d),
  remove: (id: string) => http.delete<void>(`/inventory/counts/${id}`),
  pendingDocs: (id: string) => http.get<{ docNos: string[] }>(`/inventory/counts/${id}/pending-docs`),
  generate: (id: string) => http.post<number>(`/inventory/counts/${id}/generate`),
  lines: (id: string, q: object) => http.get<PageResult<CountLineRow>>(`/inventory/counts/${id}/lines`, q),
  input: (id: string, inputs: LineInput[]) => http.put<void>(`/inventory/counts/${id}/lines`, inputs as unknown as object),
  addLine: (id: string, d: object) => http.post<string>(`/inventory/counts/${id}/lines/add`, d),
  submit: (id: string) => http.post<string>(`/inventory/counts/${id}/submit`),
  approve: (id: string) => http.post<void>(`/inventory/counts/${id}/approve`),
  reject: (id: string, reason?: string) => http.post<void>(`/inventory/counts/${id}/reject`, { reason }),
  void: (id: string, reason?: string) => http.post<void>(`/inventory/counts/${id}/void`, { reason })
}

// ==================== 批次与序列号 ====================

export interface BatchRow {
  id: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  uom: string
  batchNo: string
  supplierBatchNo?: string
  supplierId?: string
  productionDate?: string
  expireDate?: string
  remainingDays?: number
  firstInDate: string
  onHandQty: string
  availableQty: string
  concession: boolean
  frozen: boolean
  frozenReason?: string
  frozenByModule?: string
  sourceType?: string
  sourceNo?: string
  remark?: string
  version: number
}
export interface BatchDetail {
  batch: BatchRow
  distribution: { warehouseId: string; warehouseName: string; locationId?: string; locationCode?: string; qty: string }[]
  txns: { id: string; bizDate: string; direction: string; bizType: string; docType: string; docId: string; docNo: string; sourceNo?: string;
    warehouseName: string; qty: string; balanceQty: string; reversal: boolean; createdAt: string }[]
}
export interface SerialRow {
  id: string
  materialId: string
  materialCode: string
  materialName: string
  serialNo: string
  batchNo?: string
  status: string
  warehouseId?: string
  warehouseName?: string
  locationId?: string
  locationCode?: string
  customerId?: string
  lastDocNo?: string
}

export const batchApi = {
  page: (q: object) => http.get<PageResult<BatchRow>>('/inventory/batches', q),
  get: (id: string) => http.get<BatchDetail>(`/inventory/batches/${id}`),
  freeze: (id: string, reason: string) => http.post<void>(`/inventory/batches/${id}/freeze`, { reason }),
  unfreeze: (id: string, reason: string) => http.post<void>(`/inventory/batches/${id}/unfreeze`, { reason }),
  update: (id: string, d: object) => http.put<void>(`/inventory/batches/${id}`, d),
  serials: (q: object) => http.get<PageResult<SerialRow>>('/inventory/serials', q),
  serialHistory: (id: string) => http.get<{ txnId: string; direction: string; docNo: string; createdAt: string }[]>(`/inventory/serials/${id}/history`)
}

// ==================== 库存查询与报表 ====================

export interface StockRow {
  key: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  categoryName?: string
  warehouseId?: string
  warehouseName?: string
  warehouseType?: WarehouseType
  locationId?: string
  locationCode?: string
  batchNo?: string
  productionDate?: string
  expireDate?: string
  concession: boolean
  frozen: boolean
  onHandQty: string
  availableQty: string
  reservedQty?: string
  qcQty?: string
  ngQty?: string
  safetyStock?: string
  belowSafety: boolean
  refCost?: string
  amount?: string
  lastInDate?: string
  lastOutDate?: string
}
export interface TxnRow {
  id: string
  bizDate: string
  docType: string
  docId: string
  docNo: string
  bizType: string
  sourceType?: string
  sourceNo?: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  warehouseName: string
  locationCode?: string
  batchNo?: string
  inQty?: string
  outQty?: string
  balanceQty: string
  unitCost?: string
  amount?: string
  reversal: boolean
  operatorName?: string
  createdAt: string
}
export interface SummaryRow {
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  warehouseId?: string
  warehouseName?: string
  openingQty: string
  openingAmount?: string
  inQty: string
  inDetail: Record<string, string>
  inAmount?: string
  outQty: string
  outDetail: Record<string, string>
  outAmount?: string
  closingQty: string
  closingAmount?: string
  mismatch: boolean
}
export interface SummaryResult {
  periodFrom: string
  periodTo: string
  costCalculated: boolean
  inKeys: { key: string; label: string }[]
  outKeys: { key: string; label: string }[]
  rows: SummaryRow[]
}
export interface AgingResult {
  bucketLabels: string[]
  rows: { materialId: string; materialCode: string; materialName: string; materialSpec?: string; baseUom: string; qty: string;
    bucketQty: string[]; bucketAmount?: string[]; maxDays: number }[]
  bucketTotals?: string[]
}
export interface SlowRow {
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  qty: string
  amount?: string
  lastInDate?: string
  lastOutDate?: string
  idleDays: number
  bomUsed: boolean
}
export interface AlertRow {
  type: string
  materialId: string
  materialCode: string
  materialName: string
  materialSpec?: string
  baseUom: string
  warehouseId?: string
  warehouseName?: string
  batchNo?: string
  qty?: string
  safetyStock?: string
  maxStock?: string
  availableQty?: string
  inTransitQty?: string
  gap?: string
  expireDate?: string
  daysLeft?: number
  inAt?: string
  waitHours?: number
  sourceNo?: string
  buyerName?: string
}

export const stockApi = {
  stocks: (q: object) => http.get<PageResult<StockRow> & { totalQty?: string; totalAmount?: string }>('/inventory/stocks', q),
  txns: (q: object) => http.get<PageResult<TxnRow>>('/inventory/stock-txns', q),
  summary: (q: object) => http.get<SummaryResult>('/inventory/reports/in-out-summary', q),
  aging: (q: object) => http.get<AgingResult>('/inventory/reports/aging', q),
  slow: (q: object) => http.get<SlowRow[]>('/inventory/reports/slow-moving', q),
  alerts: (type: string) => http.get<AlertRow[]>('/inventory/alerts', { type }),
  alertCounts: () => http.get<{ low: number; high: number; expiry: number; qcOverdue: number }>('/inventory/alerts/counts')
}

// ==================== 期初与月结 ====================

export interface PeriodRow {
  id: string
  period: string
  startDate: string
  endDate: string
  periodStatus: 'OPEN' | 'CLOSED'
  opening: boolean
  closedByName?: string
  closedAt?: string
  financeClosed: boolean
  canClose: boolean
  canReopen: boolean
}
export interface OpeningInfo {
  period?: string
  startDate?: string
  openingDate?: string
  completed: boolean
  docCount: number
  lineCount: number
  totalAmount?: string
  canClear: boolean
}
export interface CheckResult {
  period: string
  passed: boolean
  items: { level: 'BLOCK' | 'WARN'; title: string; details: string[] }[]
}

export const periodApi = {
  list: () => http.get<PeriodRow[]>('/inventory/periods'),
  init: (period: string) => http.post<void>('/inventory/periods/init', { period }),
  check: (period: string) => http.post<CheckResult>(`/inventory/periods/${period}/check`),
  close: (period: string) => http.post<void>(`/inventory/periods/${period}/close`),
  reopen: (period: string) => http.post<void>(`/inventory/periods/${period}/reopen`),
  opening: () => http.get<OpeningInfo>('/inventory/opening'),
  clearOpening: () => http.post<void>('/inventory/opening/clear'),
  completeOpening: () => http.post<void>('/inventory/opening/complete')
}

// ==================== 工具 ====================

/** 单据详情路由 */
export function docRoute(docType: string, id: string) {
  if (docType === 'STOCK_IN') return `/inventory/in/${id}`
  if (docType === 'STOCK_OUT') return `/inventory/out/${id}`
  if (docType === 'TRANSFER') return `/inventory/transfer/${id}`
  return undefined
}

/** 序列号文本（逗号、空格、换行分隔）→ 数组 */
export function parseSerials(text?: string) {
  return (text ?? '').split(/[,，\s]+/).map((s) => s.trim()).filter(Boolean)
}
