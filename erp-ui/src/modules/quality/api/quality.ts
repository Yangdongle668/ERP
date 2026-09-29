import { http, type PageParam, type PageResult } from '@/api/http'
import type { Option, StatusMap } from '@/components'

/** 品质模块接口与公共类型（需求 10-品质）。数量均为基本单位 */

export const INSPECT_TYPE: Record<string, string> = {
  IQC: '来料检验', IPQC: '制程检验', FQC: '成品检验', OQC: '出货检验', RETURN: '退货检验', RECHECK: '复检'
}
export const INSP_STATUS: StatusMap = {
  PENDING: { label: '待检', type: 'warning' },
  INSPECTING: { label: '检验中', type: 'primary' },
  WAIT_MRB: { label: '待 MRB', type: 'warning', plain: true },
  JUDGED: { label: '已判定', type: 'success' },
  HANDLED: { label: '已处理', type: 'info', plain: true },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const INSP_RESULT: StatusMap = {
  QUALIFIED: { label: '合格', type: 'success' },
  REJECTED: { label: '拒收', type: 'danger' },
  CONCESSION: { label: '特采', type: 'warning' },
  SORTED: { label: '挑选', type: 'primary' }
}
export const SUGGEST_RESULT: StatusMap = {
  PASS: { label: '建议合格', type: 'success', plain: true },
  FAIL: { label: '建议不合格', type: 'danger', plain: true }
}
export const IPQC_KIND: Record<string, string> = { FIRST: '首件', PATROL: '巡检', LAST: '末件', REPORT: '报工触发' }
export const LEVELS: Option[] = [{ value: 'CR', label: 'CR 致命' }, { value: 'MA', label: 'MA 严重' }, { value: 'MI', label: 'MI 轻微' }]
export const STD_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  EFFECTIVE: { label: '生效', type: 'success' },
  OBSOLETE: { label: '作废', type: 'info', plain: true }
}
export const ENABLE: StatusMap = { ENABLED: { label: '启用', type: 'success' }, DISABLED: { label: '停用', type: 'info', plain: true } }
export const PLAN_TYPE: Option[] = [
  { value: 'GB2828', label: 'GB/T 2828.1 抽样' }, { value: 'FULL', label: '全检' }, { value: 'FIXED', label: '固定数量' }, { value: 'EXEMPT', label: '免检' }
]
export const INSPECTION_LEVELS: Option[] = ['S1', 'S2', 'S3', 'S4', 'I', 'II', 'III'].map((v) => ({ value: v, label: v }))
export const AQLS = ['0', '0.010', '0.015', '0.025', '0.040', '0.065', '0.10', '0.15', '0.25', '0.40', '0.65', '1.0', '1.5', '2.5', '4.0', '6.5', '10']
export const ITEM_TYPE: Option[] = [{ value: 'QUALITATIVE', label: '定性' }, { value: 'QUANTITATIVE', label: '定量' }]
export const DOC_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_APPROVAL: { label: '待审批', type: 'warning' },
  APPROVED: { label: '处置中', type: 'primary' },
  CLOSED: { label: '已关闭', type: 'success' },
  VOIDED: { label: '已作废', type: 'danger' }
}
export const NCR_SOURCE: Record<string, string> = {
  IQC: '来料检验', IPQC: '制程检验', FQC: '成品检验', OQC: '出货检验', RETURN: '退货检验', RECHECK: '复检',
  PRODUCTION: '生产不良', INVENTORY: '库存', COMPLAINT: '客诉'
}
export const SEVERITY: StatusMap = {
  CRITICAL: { label: '致命', type: 'danger' },
  MAJOR: { label: '严重', type: 'warning' },
  MINOR: { label: '轻微', type: 'info' }
}
export const DISPOSITION: Record<string, string> = { RETURN: '退供应商', CONCESSION: '特采', SORT: '挑选', REWORK: '返工', SCRAP: '报废' }
/** QC-NCR-R02：检验来源的可选处置 */
export function allowedDispositions(source?: string): string[] {
  if (source === 'IQC' || source === 'RECHECK') return ['RETURN', 'CONCESSION', 'SORT', 'SCRAP']
  if (source === 'FQC' || source === 'RETURN') return ['CONCESSION', 'REWORK', 'SCRAP']
  if (source === 'OQC') return ['REWORK', 'SORT', 'CONCESSION']
  return Object.keys(DISPOSITION)
}
export const CAPA_STATUS: StatusMap = {
  OPEN: { label: '进行中', type: 'primary' },
  VERIFYING: { label: '待验证', type: 'warning' },
  CLOSED: { label: '已结案', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const CAPA_SOURCE: Record<string, string> = { NCR: 'NCR', COMPLAINT: '客诉', AUDIT: '审核', OTHER: '其他' }
export const CAPA_STEPS = ['D1 成立小组', 'D2 问题描述', 'D3 临时围堵', 'D4 根本原因', 'D5 永久措施', 'D6 实施验证', 'D7 预防再发', 'D8 结案']
export const COMPLAINT_STATUS: StatusMap = {
  OPEN: { label: '新建', type: 'warning' },
  ANALYZING: { label: '分析中', type: 'primary' },
  REPLIED: { label: '已回复', type: 'primary', plain: true },
  CLOSING: { label: '结案审批中', type: 'warning', plain: true },
  CLOSED: { label: '已结案', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const HANDLING: Option[] = [
  { value: 'NONE', label: '无' }, { value: 'RETURN', label: '退货' }, { value: 'REPLACE', label: '补货' }, { value: 'CREDIT', label: '折让' },
  { value: 'REWORK_ONSITE', label: '现场返工' }
]
export const SCAR_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  SENT: { label: '已发出', type: 'warning' },
  REPLIED: { label: '已回复', type: 'primary' },
  VERIFYING: { label: '验证中', type: 'primary', plain: true },
  CLOSED: { label: '已结案', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}

export const optionsOf = (map: StatusMap, exclude: string[] = []): Option[] =>
  Object.entries(map).filter(([k]) => !exclude.includes(k)).map(([value, s]) => ({ value, label: s.label }))
export const recordOptions = (map: Record<string, string>): Option[] => Object.entries(map).map(([value, label]) => ({ value, label }))
export const labelOf = (options: Option[], v?: string | null) => (v ? options.find((o) => o.value === v)?.label ?? v : '-')
export const joinList = (v?: string[]) => (v?.length ? v.join(',') : undefined)
export const num = (v?: string | number | null) => (v === undefined || v === null || v === '' ? 0 : Number(v))
export const waitText = (minutes: number) => (minutes < 60 ? `${minutes} 分钟` : minutes < 1440 ? `${Math.floor(minutes / 60)} 小时` : `${Math.floor(minutes / 1440)} 天`)

// ==================== 基础数据 ====================

export interface ItemLibRow { id: string; code: string; name: string; itemType: string; method: string; unit?: string; defectLevel: string; tool?: string; description?: string; status: string }
export interface SamplingRow { id: string; code: string; name: string; planType: string; inspectionLevel?: string; aqlCr?: string; aqlMa?: string; aqlMi?: string; fixedQty?: number; status: string; remark?: string }
export interface LevelPlan { level: string; aql: string; n: number; ac: number; re: number }
export interface SamplingResult { planType: string; inspectionLevel?: string; letter?: string; lotQty: string; sampleQty: number; full: boolean; levels: LevelPlan[]; text: string; planName?: string }
export interface DefectCodeRow { id: string; code: string; name: string; category: string; defaultLevel: string; status: string }
export interface StandardRow {
  id: string; code: string; name: string; inspectType: string; scopeType: string; materialId?: string; materialCode?: string; materialName?: string
  categoryId?: string; categoryName?: string; operation?: string; samplingPlanId: string; samplingPlanName?: string; stdVersion: number; itemCount: number; status: string; updatedAt: string
}
export interface StandardItem {
  id?: string; seq?: number; libItemId?: string; name?: string; itemType?: string; method?: string; unit?: string; spec?: string; target?: string
  upperLimit?: string; lowerLimit?: string; defectLevel?: string; samplingPlanId?: string; samplingPlanName?: string; isKey?: boolean
}
export interface StandardDetail extends StandardRow { fileId?: string; remark?: string; effectiveAt?: string; version: number; items: StandardItem[]; versions: StandardRow[] }

export const basicApi = {
  items: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ItemLibRow>>('/quality/inspection-items', q),
  saveItem: (id: string | undefined, d: object) => (id ? http.put<void>(`/quality/inspection-items/${id}`, d) : http.post<string>('/quality/inspection-items', d)),
  deleteItem: (id: string) => http.delete<void>(`/quality/inspection-items/${id}`),
  plans: (q: PageParam & Record<string, unknown>) => http.get<PageResult<SamplingRow>>('/quality/sampling-plans', q),
  enabledPlans: () => http.get<SamplingRow[]>('/quality/sampling-plans/enabled'),
  savePlan: (id: string | undefined, d: object) => (id ? http.put<void>(`/quality/sampling-plans/${id}`, d) : http.post<string>('/quality/sampling-plans', d)),
  deletePlan: (id: string) => http.delete<void>(`/quality/sampling-plans/${id}`),
  preview: (d: object) => http.post<SamplingResult>('/quality/sampling-plans/preview', d),
  defects: (q: PageParam & Record<string, unknown>) => http.get<PageResult<DefectCodeRow>>('/quality/defect-codes', q),
  enabledDefects: () => http.get<DefectCodeRow[]>('/quality/defect-codes/enabled'),
  saveDefect: (id: string | undefined, d: object) => (id ? http.put<void>(`/quality/defect-codes/${id}`, d) : http.post<string>('/quality/defect-codes', d)),
  deleteDefect: (id: string) => http.delete<void>(`/quality/defect-codes/${id}`)
}

export const standardApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<StandardRow>>('/quality/standards', q),
  get: (id: string) => http.get<StandardDetail>(`/quality/standards/${id}`),
  save: (id: string | undefined, d: object) => (id ? http.put<void>(`/quality/standards/${id}`, d) : http.post<string>('/quality/standards', d)),
  remove: (id: string) => http.delete<void>(`/quality/standards/${id}`),
  newVersion: (id: string) => http.post<string>(`/quality/standards/${id}/new-version`),
  activate: (id: string) => http.post<void>(`/quality/standards/${id}/activate`),
  obsolete: (id: string) => http.post<void>(`/quality/standards/${id}/obsolete`)
}

// ==================== 检验单 ====================

export interface InspectionRow {
  id: string; docNo: string; inspectType: string; ipqcKind?: string; materialId: string; materialCode: string; materialName: string; materialSpec?: string
  batchNo?: string; supplierName?: string; customerName?: string; lotQty: string; sampleQty: number; upstreamType?: string; upstreamId?: string; upstreamNo?: string
  operationSeq?: number; waitMinutes: number; overdue: boolean; suggestedResult?: string; result?: string; qualifiedQty: string; concessionQty: string
  rejectedQty: string; inspectorName?: string; status: string; createdAt: string; judgeAt?: string
}
export interface ItemResult {
  id: string; seq: number; itemName: string; itemType: string; method?: string; unit?: string; spec?: string; target?: string; upperLimit?: string
  lowerLimit?: string; defectLevel: string; isKey: boolean; sampleQty: number; measuredValues: string[]; ngCount: number; itemResult?: string; remark?: string
}
export interface DefectRow { id?: string; defectCode: string; defectName?: string; defectLevel?: string; qty: number; description?: string; imageFileIds?: string[] }
export interface InspectionDetail {
  id: string; docNo: string; inspectType: string; ipqcKind?: string; materialId: string; materialCode: string; materialName: string; materialSpec?: string
  baseUom: string; batchNo?: string; lotQty: string; supplierName?: string; customerName?: string; sourceType?: string; sourceNo?: string; upstreamType?: string
  upstreamId?: string; upstreamNo?: string; prodOrderId?: string; operationSeq?: number; standardId?: string; standardCode?: string; standardVersion?: number
  standardName?: string; standardFileId?: string; sampling?: SamplingResult; sampleQty: number; inspectorName?: string; startedAt?: string; inspectedAt?: string
  suggestedResult?: string; result?: string; qualifiedQty: string; concessionQty: string; rejectedQty: string; crCount: number; maCount: number; miCount: number
  judgeName?: string; judgeAt?: string; judgeReason?: string; ncrId?: string; ncrNo?: string; status: string; mrbSort: boolean; presetConcessionQty: string
  presetRejectedQty: string; transferIds: string[]; rejudgeCount: number; rejudgePending: boolean; remark?: string; createdAt: string; version: number
  items: ItemResult[]; defects: DefectRow[]
}
export interface ProdOrderOption { id: string; docNo: string; materialCode?: string; materialName?: string; qty: string; operations: { seq: number; operation?: string }[] }

export const inspectionApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<InspectionRow>>('/quality/inspections', q),
  counts: (types: string) => http.get<{ pending: number; overdue: number; todayJudged: number }>('/quality/inspections/counts', { types }),
  get: (id: string) => http.get<InspectionDetail>(`/quality/inspections/${id}`),
  create: (d: object) => http.post<string>('/quality/inspections', d),
  prodOrders: (keyword?: string) => http.get<ProdOrderOption[]>('/quality/inspections/prod-orders', { keyword }),
  saveResults: (id: string, d: object) => http.put<void>(`/quality/inspections/${id}/results`, d),
  judge: (id: string, d: { result: string; qualifiedQty?: string; rejectedQty?: string; reason?: string }) => http.post<void>(`/quality/inspections/${id}/judge`, d),
  batchJudgePass: (ids: string[]) => http.post<{ success: number; errors: string[] }>('/quality/inspections/batch-judge-pass', { ids }),
  toMrb: (id: string) => http.post<string>(`/quality/inspections/${id}/to-mrb`),
  rejudge: (id: string, reason: string) => http.post<void>(`/quality/inspections/${id}/rejudge`, { reason })
}

// ==================== NCR ====================

export interface NcrRow {
  id: string; docNo: string; source: string; sourceNo?: string; materialCode?: string; materialName?: string; batchNo?: string; ncrQty: string; severity: string
  responsibility: string; dispositionSummary?: string; supplierName?: string; customerName?: string; capaRequired: boolean; scarRequired: boolean; status: string
  ownerName?: string; docDate: string
}
export interface DispositionRow { id?: string; seq?: number; disposition: string; qty: string; remark?: string; followDocNo?: string; done?: boolean; doneAt?: string }
export interface NcrDetail {
  id: string; docNo: string; docDate: string; source: string; sourceNo?: string; inspectionId?: string; inspectionNo?: string; inspectionResult?: string
  materialId: string; materialCode: string; materialName: string; materialSpec?: string; baseUom?: string; batchNo?: string; ncrQty: string; supplierId?: string
  supplierName?: string; customerId?: string; customerName?: string; defectDescription: string; defectCodes: string[]; severity: string; responsibility: string
  containment?: string; capaRequired: boolean; scarRequired: boolean; capaSuggested: boolean; amountBase?: string; capaId?: string; capaNo?: string; capaStatus?: string
  scarId?: string; scarNo?: string; scarStatus?: string; complaintId?: string; complaintNo?: string; batchFrozen: boolean; status: string; approvalRunning: boolean
  ownerName?: string; approvedAt?: string; closedAt?: string; version: number; dispositions: DispositionRow[]
}
export const ncrApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<NcrRow>>('/quality/ncrs', q),
  get: (id: string) => http.get<NcrDetail>(`/quality/ncrs/${id}`),
  save: (id: string | undefined, d: object) => (id ? http.put<void>(`/quality/ncrs/${id}`, d) : http.post<string>('/quality/ncrs', d)),
  suggestCapa: (d: object) => http.post<boolean>('/quality/ncrs/suggest-capa', d),
  submit: (id: string) => http.post<void>(`/quality/ncrs/${id}/submit`),
  done: (id: string, dispId: string, followDocNo?: string) => http.post<void>(`/quality/ncrs/${id}/dispositions/${dispId}/done`, { followDocNo }),
  purchaseReturn: (id: string) => http.post<void>(`/quality/ncrs/${id}/create-purchase-return`),
  reworkOrder: (id: string) => http.post<string>(`/quality/ncrs/${id}/create-rework-order`),
  scrapOut: (id: string) => http.post<string>(`/quality/ncrs/${id}/create-scrap-out`),
  createCapa: (id: string) => http.post<string>(`/quality/ncrs/${id}/create-capa`),
  createScar: (id: string) => http.post<string>(`/quality/ncrs/${id}/create-scar`),
  close: (id: string, unfreeze: boolean) => http.post<void>(`/quality/ncrs/${id}/close`, { unfreeze }),
  void: (id: string, reason: string) => http.post<void>(`/quality/ncrs/${id}/void`, { reason })
}

// ==================== CAPA ====================

export interface CapaRow {
  id: string; docNo: string; title: string; source: string; sourceId?: string; sourceNo?: string; materialCode?: string; leaderName?: string; currentStep: number
  dueDate: string; overdue: boolean; d3Due?: string; status: string; invalidCount: number; createdAt: string
}
export interface CapaDetail {
  id: string; docNo: string; title: string; source: string; sourceId?: string; sourceNo?: string; materialId?: string; materialCode?: string; materialName?: string
  customerName?: string; supplierName?: string; leaderId: string; leaderName?: string; teamMembers: { id: string; name?: string }[]; d1Team?: string
  d2Problem?: string; d3Containment?: string; d3Due?: string; d3DoneAt?: string; d4RootCause?: string; d4Method?: string; d5Actions?: string
  d6Implementation?: string; d7Prevention?: string; d8Summary?: string; currentStep: number; dueDate: string; overdue: boolean; verifyResult?: string
  verifyName?: string; verifyAt?: string; invalidCount: number; verifyHistory?: string; status: string; canEdit: boolean; closedAt?: string; cancelReason?: string; version: number
}
export const capaApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<CapaRow>>('/quality/capas', q),
  get: (id: string) => http.get<CapaDetail>(`/quality/capas/${id}`),
  save: (id: string | undefined, d: object) => (id ? http.put<void>(`/quality/capas/${id}`, d) : http.post<string>('/quality/capas', d)),
  saveStep: (id: string, step: number, d: object) => http.put<void>(`/quality/capas/${id}/steps/${step}`, d),
  completeStep: (id: string, step: number, d: object) => http.post<void>(`/quality/capas/${id}/steps/${step}/complete`, d),
  verify: (id: string, result: string, content: string) => http.post<void>(`/quality/capas/${id}/verify`, { result, content }),
  close: (id: string, text?: string) => http.post<void>(`/quality/capas/${id}/close`, { text }),
  cancel: (id: string, reason: string) => http.post<void>(`/quality/capas/${id}/cancel`, { reason })
}

// ==================== 客诉 ====================

export interface ComplaintRow {
  id: string; docNo: string; customerName?: string; materialCode?: string; materialName?: string; complaintType: string; severity: string; complaintQty?: string
  receivedAt: string; replyDueDate: string; replyOverdue: boolean; repliedAt?: string; qeName?: string; handling?: string; status: string; salesOwnerName?: string
}
export interface ComplaintDetail {
  id: string; docNo: string; customerId: string; customerName?: string; contactId?: string; contactName?: string; complaintType: string; severity: string
  materialId?: string; materialCode?: string; materialName?: string; customerPartNo?: string; orderNo?: string; shipmentNo?: string; batchNo?: string
  serialNos?: string; complaintQty?: string; description: string; receivedAt: string; replyDueDate: string; replyOverdue: boolean; qeId: string; qeName?: string
  salesOwnerName?: string; rootCause?: string; replyContent?: string; repliedAt?: string; handling?: string; handlingRemark?: string; claimAmount?: string
  agreedAmount?: string; currency?: string; status: string; capaId?: string; capaNo?: string; capaStatus?: string; ncrId?: string; ncrNo?: string; ncrStatus?: string
  closeMissing: string[]; approvalRunning: boolean; cancelReason?: string; closedAt?: string; createdAt: string; version: number
}
export const complaintApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ComplaintRow>>('/quality/complaints', q),
  get: (id: string) => http.get<ComplaintDetail>(`/quality/complaints/${id}`),
  contacts: (customerId: string) => http.get<{ id: string; name: string; phone?: string }[]>('/quality/complaints/customer-contacts', { customerId }),
  save: (id: string | undefined, d: object) => (id ? http.put<void>(`/quality/complaints/${id}`, d) : http.post<string>('/quality/complaints', d)),
  start: (id: string) => http.post<void>(`/quality/complaints/${id}/start`),
  reply: (id: string, d: object) => http.post<void>(`/quality/complaints/${id}/reply`, d),
  handling: (id: string, d: object) => http.post<void>(`/quality/complaints/${id}/handling`, d),
  createNcr: (id: string) => http.post<string>(`/quality/complaints/${id}/create-ncr`, {}),
  createCapa: (id: string) => http.post<string>(`/quality/complaints/${id}/create-capa`),
  close: (id: string) => http.post<void>(`/quality/complaints/${id}/close`),
  cancel: (id: string, reason: string) => http.post<void>(`/quality/complaints/${id}/cancel`, { reason })
}

// ==================== SCAR ====================

export interface ScarRow {
  id: string; docNo: string; supplierName?: string; materialCode?: string; materialName?: string; problemSummary?: string; sentAt?: string; replyDueDate?: string
  replyOverdue: boolean; repliedAt?: string; invalidCount: number; status: string; ownerName?: string; ncrId?: string; ncrNo?: string
}
export interface ScarDetail {
  id: string; docNo: string; supplierId: string; supplierName?: string; ncrId?: string; ncrNo?: string; materialId: string; materialCode?: string; materialName?: string
  batchNo?: string; problemDescription: string; requirement: string; replyDueDate?: string; replyOverdue: boolean; sentAt?: string; replyContent?: string
  repliedAt?: string; verifyPlan?: string; verifyResult?: string; invalidCount: number; status: string; ownerName?: string; cancelReason?: string; closedAt?: string; version: number
}
export const scarApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ScarRow>>('/quality/scars', q),
  get: (id: string) => http.get<ScarDetail>(`/quality/scars/${id}`),
  save: (id: string | undefined, d: object) => (id ? http.put<void>(`/quality/scars/${id}`, d) : http.post<string>('/quality/scars', d)),
  send: (id: string) => http.post<void>(`/quality/scars/${id}/send`),
  reply: (id: string, d: object) => http.post<void>(`/quality/scars/${id}/reply`, d),
  startVerify: (id: string, verifyPlan: string) => http.post<void>(`/quality/scars/${id}/start-verify`, { verifyPlan }),
  verify: (id: string, result: string, remark?: string) => http.post<void>(`/quality/scars/${id}/verify`, { result, remark }),
  cancel: (id: string, reason: string) => http.post<void>(`/quality/scars/${id}/cancel`, { reason })
}

// ==================== 追溯、报表 ====================

export interface BatchInspection { inspectionId: string; docNo: string; inspectType: string; result?: string; ncrId?: string; ncrNo?: string; judgeAt?: string }
export interface TraceRow {
  level: number; prodOrderId: string; prodOrderNo: string; productCode?: string; productName?: string; productBatchNo?: string; componentCode?: string
  componentName?: string; componentBatchNo?: string; qty: string; productConcession: boolean; productFrozen: boolean; componentConcession: boolean
  componentFrozen: boolean; productInspections: BatchInspection[]; componentInspections: BatchInspection[]
}
export interface BatchStock {
  materialId: string; materialCode?: string; materialName?: string; batchNo: string; frozen: boolean; warehouses: { warehouseId: string; warehouseName: string; qty: string }[]
  onHandQty: string; shippedQty: string; shipments: { bizDate: string; docNo: string; sourceNo?: string; qty: string }[]
}
export interface TraceResult {
  materialId: string; materialCode?: string; materialName?: string; batchNo: string; frozen: boolean; inspections: BatchInspection[]; nodes: TraceRow[]; affected: BatchStock[]
}
export interface LotRow {
  key: string; name: string; lots: number; qualifiedLots: number; concessionLots: number; rejectedLots: number; sortedLots: number; passRate?: string
  inspectedQty: string; sampleQty: number; defectQty: number; defectRate?: string
}
export interface ParetoRow { code: string; name: string; qty: number; pct?: string; cumulativePct?: string }
export interface CountRow { key: string; name?: string; count: number; qty: string }
export const reportApi = {
  backward: (materialId: string, batchNo: string) => http.get<TraceResult>('/quality/trace/backward', { materialId, batchNo }),
  forward: (materialId: string, batchNo: string) => http.get<TraceResult>('/quality/trace/forward', { materialId, batchNo }),
  freeze: (ncrId: string, items: { materialId: string; batchNo: string }[]) => http.post<{ frozen: number; skipped: string[] }>('/quality/trace/freeze', { ncrId, items }),
  iqc: (q: object) => http.get<{ total: LotRow; bySupplier: LotRow[]; byMaterial: LotRow[]; byMonth: LotRow[] }>('/quality/reports/iqc', q),
  process: (q: object) => http.get<{ total: LotRow; byMonth: LotRow[]; byMaterial: LotRow[]; pareto: ParetoRow[] }>('/quality/reports/process', q),
  outgoing: (q: object) => http.get<{ fqc: LotRow; fqcFirstPassRate?: string; oqc: LotRow; shipmentLots: number; complaints: number; complaintRate?: string; fqcByMonth: LotRow[]; oqcByMonth: LotRow[] }>('/quality/reports/outgoing', q),
  ncrCapaComplaint: (q: object) => http.get<{
    ncrCount: number; ncrAvgCloseDays?: string; ncrBySource: CountRow[]; ncrByResponsibility: CountRow[]; ncrByDisposition: CountRow[]; ncrByMaterial: CountRow[]
    capaCount: number; capaClosed: number; capaOnTimeRate?: string; capaAvgDays?: string
    capaOverdue: { id: string; docNo: string; title: string; leaderName?: string; dueDate: string; overdueDays: number; currentStep: number }[]
    complaintCount: number; replyOnTimeRate?: string; complaintByCustomer: CountRow[]; complaintByType: CountRow[]; complaintBySeverity: CountRow[]; complaintByMonth: CountRow[]
  }>('/quality/reports/ncr-capa-complaint', q),
  pareto: (q: object) => http.get<ParetoRow[]>('/quality/reports/defect-pareto', q)
}
