import { ElMessage, ElMessageBox } from 'element-plus'
import { BizError, http, type PageParam, type PageResult } from '@/api/http'
import type { Option, RelatedDoc, StatusMap } from '@/components'

/** 生产模块接口与公共类型（需求 09-生产）。数量均为基本单位 */

export const PROD_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  PLANNED: { label: '已计划', type: 'primary', plain: true },
  RELEASED: { label: '已下达', type: 'primary' },
  IN_PROGRESS: { label: '生产中', type: 'warning' },
  SUSPENDED: { label: '暂停', type: 'danger', plain: true },
  COMPLETED: { label: '已完工', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true },
  VOIDED: { label: '已作废', type: 'danger' }
}
/** 领料单 / 退料单：已审核 = 已生成仓库单据待确认 */
export const MATERIAL_DOC_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_APPROVAL: { label: '待审批', type: 'warning' },
  APPROVED: { label: '待仓库确认', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  VOIDED: { label: '已作废', type: 'danger' }
}
export const REPORT_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  APPROVED: { label: '已审核', type: 'success' }
}
export const WO_STATUS: StatusMap = {
  DISPATCHED: { label: '已派工', type: 'primary', plain: true },
  RUNNING: { label: '进行中', type: 'warning' },
  DONE: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const FINISH_STATUS: StatusMap = {
  SUBMITTED: { label: '待入库', type: 'warning' },
  STOCKED: { label: '已入库待检', type: 'primary' },
  JUDGED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const DISPOSITION_STATUS: StatusMap = {
  DRAFT: { label: '未审核', type: 'info', plain: true },
  PENDING: { label: '待处理', type: 'warning' },
  REPAIRED: { label: '已返修', type: 'success' },
  SCRAPPED: { label: '已报废', type: 'danger', plain: true }
}
export const OP_STATUS: StatusMap = {
  WAITING: { label: '未开始', type: 'info', plain: true },
  RUNNING: { label: '进行中', type: 'warning' },
  DONE: { label: '完成', type: 'success' }
}
export const ORDER_TYPE_OPTIONS: Option[] = [
  { value: 'STANDARD', label: '标准' }, { value: 'REWORK', label: '返工' }, { value: 'SAMPLE', label: '样品' }
]
export const ISSUE_TYPE_OPTIONS: Option[] = [
  { value: 'NORMAL', label: '正常领料' }, { value: 'OVER', label: '超领' }, { value: 'BACKFLUSH', label: '倒冲' }
]
export const RETURN_TYPE_OPTIONS: Option[] = [{ value: 'GOOD', label: '良品退料' }, { value: 'DEFECT', label: '不良退料' }]
export const ISSUE_METHOD_OPTIONS: Option[] = [{ value: 'PICK', label: '领料' }, { value: 'BACKFLUSH', label: '倒冲' }]
export const REPORT_KIND_OPTIONS: Option[] = [{ value: 'NORMAL', label: '报工' }, { value: 'REPAIR', label: '返修' }, { value: 'SCRAP', label: '不良报废' }]

export const optionsOf = (map: StatusMap, exclude: string[] = []): Option[] =>
  Object.entries(map).filter(([k]) => !exclude.includes(k)).map(([value, s]) => ({ value, label: s.label }))
export const labelOf = (options: Option[], v?: string | null) => (v ? options.find((o) => o.value === v)?.label ?? v : '-')
export const joinList = (v?: string[]) => (v?.length ? v.join(',') : undefined)
export const num = (v?: string | number | null) => (v === undefined || v === null || v === '' ? 0 : Number(v))
export const pct = (v?: string | number | null, digits = 1) => (v === undefined || v === null || v === '' ? '-' : `${(Number(v) * 100).toFixed(digits)}%`)

export interface DocResult { status: string; warnings: string[] }
export interface SaveResult { id: string; warnings: string[] }
export interface CreateResult { ids: string[]; docNos: string[]; warnings: string[] }
export interface BatchResult { success: number; errors: string[] }
export type { RelatedDoc }

/**
 * 需要确认的操作（下达缺料、关闭余料/在制）：后端返回 needConfirm 时弹出确认框，确认后以 confirm=true 重试。
 * 返回 undefined 表示未执行成功。
 */
export async function withConfirm<T>(run: (confirm: boolean) => Promise<T>, title: string, okText: string): Promise<T | undefined> {
  try {
    return await run(false)
  } catch (e) {
    const data = e instanceof BizError ? (e.data as { needConfirm?: boolean; message?: string } | undefined) : undefined
    if (!data?.needConfirm) {
      ElMessage.error(e instanceof Error ? e.message : String(e))
      return undefined
    }
    const ok = await ElMessageBox.confirm(`${data.message}。确定继续吗？`, title, { type: 'warning', confirmButtonText: okText })
      .then(() => true).catch(() => false)
    if (!ok) return undefined
    return run(true)
  }
}

// ==================== 生产订单 ====================

export interface ProdOrderRow {
  id: string; docNo: string; orderType: string; materialId: string; materialCode: string; materialName: string; materialSpec?: string; baseUom: string
  qty: string; completedQty: string; stockedQty: string; qualifiedStockedQty: string; scrappedQty: string; progress: string; planStart: string
  planEnd: string; overdue: boolean; deptId?: string; deptName?: string; priority: number; salesOrderNo?: string; prodStatus: string; ownerName?: string
  docDate: string
}
export interface MaterialPreview {
  lineNo: number; componentId: string; code: string; name: string; spec?: string; uom: string; qtyPer: string; scrapRate?: string; requiredQty: string
  issueMethod: string; operationSeq?: number; availableQty: string; shortage: boolean
}
export interface OperationPreview {
  seq: number; operation: string; workCenterId?: string; workCenterName?: string; stdRunSeconds?: string; stdSetupMinutes?: string; reportPoint: boolean
  inspectionPoint: boolean; outsourced: boolean
}
export interface Preview {
  bomId?: string; bomNo?: string; bomVersion?: number; boms: { id: string; label: string; isDefault: boolean }[]; routingId?: string; routingNo?: string
  routings: { id: string; label: string; isDefault: boolean }[]; defaultDeptId?: string; leadTimeDays: number; materials: MaterialPreview[]
  operations: OperationPreview[]
}
export interface MaterialResp {
  id: string; lineNo: number; componentId: string; code: string; name: string; spec?: string; uom: string; qtyPer: string; scrapRate?: string
  requiredQty: string; issueMethod: string; operationSeq?: number; issuedQty: string; overIssuedQty: string; returnedQty: string; returnedGoodQty: string
  openQty: string; netQty: string; availableQty: string; substituteOfId?: string; added: boolean
  substitutes: { substituteId: string; code: string; name: string; ratio: string }[]; remark?: string
}
export interface OperationResp {
  id: string; seq: number; operation: string; workCenterId?: string; workCenterName?: string; reportPoint: boolean; inspectionPoint: boolean
  outsourced: boolean; stdRunSeconds?: string; stdSetupMinutes?: string; goodQty: string; defectQty: string; scrapQty: string; repairedQty: string
  dispatchedQty: string; actualHours: string; stdHours: string; opStatus: string; reportableQty: string
  /** 最近一次 IPQC 判定为拒收（警示） */
  ipqcRejectedId?: string; ipqcRejectedNo?: string
}
export interface ProdOrderDetail {
  id: string; docNo: string; orderType: string; prodStatus: string; materialId: string; materialCode: string; materialName: string; materialSpec?: string
  baseUom: string; tracking?: string; qty: string; bomId?: string; bomNo?: string; bomVersion?: number; routingId?: string; routingNo?: string
  planStart: string; planEnd: string; actualStart?: string; actualEnd?: string; releasedAt?: string; priority: number; batchNo?: string
  salesOrderLineId?: string; salesOrderId?: string; salesOrderNo?: string; sourceType?: string; sourceId?: string; sourceNo?: string; completedQty: string
  scrappedQty: string; finishedRequestQty: string; stockedQty: string; qualifiedStockedQty: string; fqcRejectedQty: string; finishableQty: string
  pendingDefectQty: string; fqcRequired: boolean; deptId?: string; deptName?: string; ownerId?: string; ownerName?: string; closeReason?: string
  remark?: string; createdAt: string; version: number; materials: MaterialResp[]; operations: OperationResp[]; related: RelatedDoc[]
}
export interface MaterialSave { componentId: string; qtyPer: string; scrapRate?: string; issueMethod?: string; operationSeq?: number; remark?: string }
export interface ProdOrderSave {
  orderType?: string; materialId: string; qty: string; bomId?: string; routingId?: string; planStart: string; planEnd: string; deptId?: string
  priority?: number; salesOrderLineId?: string; batchNo?: string; remark?: string; materials?: MaterialSave[]; version?: number
}
export interface Shortage { componentId: string; code: string; name: string; uom: string; requiredQty: string; availableQty: string; shortageQty: string }
export interface KitCheck { prodOrderId: string; prodOrderNo: string; complete: boolean; shortages: Shortage[] }
export interface AdjustLine {
  id?: string; componentId?: string; requiredQty?: string; qtyPer?: string; issueMethod?: string; operationSeq?: number; delete?: boolean; remark?: string
}
export interface AdjustReq {
  reason: string; qty?: string; lines?: AdjustLine[]; substitutions?: { lineId: string; substituteId: string; qty: string }[]; version?: number
}

export const prodOrderApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ProdOrderRow>>('/production/prod-orders', q),
  preview: (q: { materialId: string; bomId?: string; routingId?: string; qty?: string; orderType?: string }) =>
    http.get<Preview>('/production/prod-orders/preview-materials', q),
  get: (id: string) => http.get<ProdOrderDetail>(`/production/prod-orders/${id}`),
  create: (d: ProdOrderSave) => http.post<SaveResult>('/production/prod-orders', d),
  update: (id: string, d: ProdOrderSave) => http.put<SaveResult>(`/production/prod-orders/${id}`, d),
  remove: (id: string) => http.delete<void>(`/production/prod-orders/${id}`),
  submit: (id: string) => http.post<DocResult>(`/production/prod-orders/${id}/submit`),
  withdraw: (id: string) => http.post<void>(`/production/prod-orders/${id}/withdraw`),
  release: (id: string, confirmShortage = false) =>
    http.post<DocResult>(`/production/prod-orders/${id}/release`, { confirmShortage }, { silent: true }),
  batchRelease: (ids: string[], confirmShortage: boolean) => http.post<BatchResult>('/production/prod-orders/batch-release', { ids, confirmShortage }),
  unrelease: (id: string) => http.post<void>(`/production/prod-orders/${id}/unrelease`),
  suspend: (id: string, reason: string) => http.post<void>(`/production/prod-orders/${id}/suspend`, { reason }),
  resume: (id: string) => http.post<void>(`/production/prod-orders/${id}/resume`),
  close: (id: string, reason: string | undefined, confirmScrap = false) =>
    http.post<void>(`/production/prod-orders/${id}/close`, { reason, confirmScrap }, { silent: true }),
  void: (id: string, reason: string) => http.post<void>(`/production/prod-orders/${id}/void`, { reason }),
  adjust: (id: string, d: AdjustReq) => http.put<void>(`/production/prod-orders/${id}/materials`, d),
  kitCheck: (id: string) => http.post<KitCheck>(`/production/prod-orders/${id}/kit-check`),
  finish: (id: string, d: { qty: string; batchNo?: string; serialNos?: string[]; remark?: string }) =>
    http.post<string>(`/production/prod-orders/${id}/finish`, d)
}

// ==================== 工单派工 ====================

export interface WorkOrderRow {
  id: string; docNo: string; prodOrderId: string; prodOrderNo: string; materialId: string; materialCode: string; materialName: string; operationSeq: number
  operation: string; workCenterId: string; workCenterName?: string; planDate: string; shift: string; planQty: string; goodQty: string; defectQty: string
  scrapQty: string; completionRate: string; teamLeaderId?: string; teamLeaderName?: string; woStatus: string; remark?: string
}
export interface Dispatchable {
  prodOrderId: string; prodOrderNo: string; materialId: string; materialCode: string; materialName: string; priority: number; planStart: string
  planEnd: string; deptId?: string; operationSeq: number; operation: string; workCenterId?: string; workCenterName?: string; orderQty: string
  dispatchedQty: string; undispatchedQty: string; stdRunSeconds?: string; stdSetupMinutes?: string
}
export interface DispatchLine { planDate: string; shift: string; workCenterId?: string; planQty?: string; teamLeaderId?: string; remark?: string }
export interface Load { workCenterId: string; date: string; loadHours: string; capacityHours: string }

export interface WorkCenterSimple { id: string; code: string; name: string }

export const workOrderApi = {
  /** 工作中心下拉（研发工程维护，登录即可访问） */
  workCenters: () => http.get<WorkCenterSimple[]>('/engineering/work-centers/simple'),
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<WorkOrderRow>>('/production/work-orders', q),
  dispatchable: (q: { deptId?: string; prodOrderId?: string }) => http.get<Dispatchable[]>('/production/work-orders/dispatchable', q),
  load: (workCenterId: string, date: string) => http.get<Load>('/production/work-orders/load', { workCenterId, date }),
  dispatch: (d: { prodOrderId: string; operationSeq: number; lines: DispatchLine[] }) =>
    http.post<{ ids: string[]; warnings: string[] }>('/production/work-orders/batch', d),
  cancel: (id: string) => http.post<void>(`/production/work-orders/${id}/cancel`),
  complete: (id: string) => http.post<void>(`/production/work-orders/${id}/complete`)
}

// ==================== 领料 / 退料 ====================

export interface IssueRow {
  id: string; docNo: string; issueType: string; prodOrderId: string; prodOrderNo: string; productCode: string; productName: string; warehouseId: string
  warehouseName?: string; lineCount: number; status: string; stockOutNos?: string; overReason?: string; ownerName?: string; docDate: string
}
export interface IssueLine {
  id?: string; lineNo?: number; materialLineId: string; materialId: string; code: string; name: string; spec?: string; uom: string; requiredQty?: string
  lineIssuedQty?: string; openQty?: string; availableQty?: string; requestQty?: string; issuedQty?: string; remark?: string
}
export interface IssueDetail {
  id: string; docNo: string; issueType: string; status: string; docDate: string; prodOrderId: string; prodOrderNo: string; prodStatus: string
  productId: string; productCode: string; productName: string; warehouseId: string; warehouseName?: string; kitQty?: string; overReason?: string
  overRemark?: string; reportId?: string; reportNo?: string; stockOutIds?: string; stockOutNos?: string; remark?: string; ownerName?: string
  submittedAt?: string; createdAt: string; version: number; lines: IssueLine[]
}
export interface IssueCandidate {
  materialLineId: string; materialId: string; code: string; name: string; spec?: string; uom: string; issueMethod: string; requiredQty: string
  issuedQty: string; openQty: string; pendingQty: string; warehouseId: string; warehouseName?: string; availableQty: string; requestQty: string
}
export interface IssueSave { prodOrderId: string; kitQty?: string; remark?: string; lines: { materialLineId: string; requestQty: string; warehouseId?: string; remark?: string }[]; version?: number }

export const issueApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<IssueRow>>('/production/issues', q),
  candidates: (prodOrderId: string, kitQty?: string) => http.get<IssueCandidate[]>('/production/issues/candidates', { prodOrderId, kitQty }),
  get: (id: string) => http.get<IssueDetail>(`/production/issues/${id}`),
  create: (d: IssueSave) => http.post<CreateResult>('/production/issues', d),
  update: (id: string, d: IssueSave) => http.put<void>(`/production/issues/${id}`, d),
  remove: (id: string) => http.delete<void>(`/production/issues/${id}`),
  submit: (id: string) => http.post<DocResult>(`/production/issues/${id}/submit`),
  withdraw: (id: string) => http.post<void>(`/production/issues/${id}/withdraw`),
  byKit: (prodOrderIds: string[], kitQty: string | undefined, submit: boolean) =>
    http.post<CreateResult>('/production/issues/by-kit', { prodOrderIds, kitQty, submit }),
  over: (d: { prodOrderId: string; materialLineId: string; qty: string; overReason?: string; overRemark?: string; submit?: boolean }) =>
    http.post<CreateResult>('/production/issues/over', d)
}

export interface ReturnRow {
  id: string; docNo: string; returnType: string; prodOrderId: string; prodOrderNo: string; productCode: string; productName: string; warehouseId: string
  warehouseName?: string; lineCount: number; totalQty: string; status: string; stockInNos?: string; ownerName?: string; docDate: string
}
export interface ReturnLine {
  id?: string; lineNo?: number; materialLineId: string; materialId: string; code: string; name: string; spec?: string; uom: string
  returnableQty?: string; qty?: string; batchNo?: string; defectDesc?: string; receivedQty?: string; batchNos?: string[]
}
export interface ReturnDetail {
  id: string; docNo: string; returnType: string; status: string; docDate: string; prodOrderId: string; prodOrderNo: string; prodStatus: string
  productId: string; productCode: string; productName: string; warehouseId: string; warehouseName?: string; stockInIds?: string; stockInNos?: string
  remark?: string; ownerName?: string; createdAt: string; version: number; lines: ReturnLine[]
}
export interface ReturnCandidate {
  materialLineId: string; materialId: string; code: string; name: string; spec?: string; uom: string; issuedQty: string; returnedQty: string
  theoreticalQty: string; returnableQty: string; warehouseId?: string; warehouseName?: string; batchNos: string[]
}
export interface ReturnSave {
  prodOrderId: string; returnType: string; warehouseId?: string; remark?: string
  lines: { materialLineId: string; qty: string; batchNo?: string; defectDesc?: string }[]; version?: number
}

export const returnApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ReturnRow>>('/production/returns', q),
  candidates: (prodOrderId: string, returnType: string, excludeId?: string) =>
    http.get<ReturnCandidate[]>('/production/returns/candidates', { prodOrderId, returnType, excludeId }),
  get: (id: string) => http.get<ReturnDetail>(`/production/returns/${id}`),
  create: (d: ReturnSave) => http.post<CreateResult>('/production/returns', d),
  update: (id: string, d: ReturnSave) => http.put<void>(`/production/returns/${id}`, d),
  remove: (id: string) => http.delete<void>(`/production/returns/${id}`),
  submit: (id: string) => http.post<DocResult>(`/production/returns/${id}/submit`),
  withdraw: (id: string) => http.post<void>(`/production/returns/${id}/withdraw`)
}

// ==================== 报工 / 不良 ====================

export interface ReportRow {
  id: string; docNo: string; reportDate: string; shift?: string; prodOrderId: string; prodOrderNo: string; materialId: string; materialCode: string
  materialName: string; operationSeq: number; operation: string; workCenterId?: string; workCenterName?: string; workOrderId?: string; workOrderNo?: string
  reportKind: string; goodQty: string; defectQty: string; scrapQty: string; yieldRate?: string; workHours: string; stdHours: string; efficiency?: string
  operators?: string; toolingCode?: string; status: string; approvedAt?: string
}
export interface OperatorSave { userId?: string; operatorName?: string; hours?: string }
export interface DefectSave { defectCode?: string; qty?: string; position?: string; description?: string; imageFileIds?: string[] }
export interface ReportSave {
  prodOrderId: string; operationSeq: number; workOrderId?: string; workCenterId?: string; reportDate?: string; shift?: string; goodQty?: string
  defectQty?: string; scrapQty?: string; scrapReason?: string; workHours?: string; machineHours?: string; toolingId?: string; startTime?: string
  endTime?: string; operators?: OperatorSave[]; defects?: DefectSave[]; remark?: string; version?: number
}
export interface ReportDetail {
  id: string; docNo: string; status: string; reportKind: string; prodOrderId: string; prodOrderNo: string; prodStatus: string; materialId: string
  materialCode: string; materialName: string; batchNo?: string; operationSeq: number; operation: string; workOrderId?: string; workOrderNo?: string
  workCenterId?: string; workCenterName?: string; reportDate: string; shift?: string; goodQty: string; defectQty: string; scrapQty: string
  scrapReason?: string; workHours: string; machineHours?: string; toolingId?: string; toolingCode?: string; startTime?: string; endTime?: string
  defectId?: string; remark?: string; ownerName?: string; approvedAt?: string; approvedByName?: string; createdAt: string; version: number
  operators: { userId?: string; userName?: string; operatorName?: string; hours?: string }[]
  defects: { id: string; defectCode: string; qty: string; position?: string; description?: string; disposition: string; repairedQty: string; scrappedQty: string; ncrNo?: string }[]
  backflushNos: string[]
}
export interface ReportContext {
  prodOrderId: string; prodOrderNo: string; prodStatus: string; materialId: string; materialCode: string; materialName: string; baseUom: string
  batchNo?: string; orderQty: string; operationSeq?: number; operation?: string; reportPoint: boolean; inspectionPoint: boolean; workCenterId?: string
  workCenterName?: string; workOrderId?: string; workOrderNo?: string; woPlanQty?: string; woReportedQty?: string; inputLimit?: string
  reportedQty?: string; reportableQty?: string; operations: { seq: number; operation: string; reportPoint: boolean; reportableQty: string }[]
  toolings: { id: string; code: string; name: string }[]; requireWorkOrder: boolean; autoApprove: boolean; releasedDate?: string
}
export interface DefectRow {
  id: string; reportId: string; reportNo: string; reportDate: string; prodOrderId: string; prodOrderNo: string; materialId: string; materialCode: string
  materialName: string; operationSeq: number; operation: string; defectCode: string; qty: string; position?: string; description?: string
  disposition: string; repairedQty: string; scrappedQty: string; pendingQty: string; ncrNo?: string; handledByName?: string; handledAt?: string
}
export interface YieldRow {
  key: string; label: string; inputQty: string; goodQty: string; defectQty: string; scrapQty: string; repairedQty: string; firstYield?: string
  finalYield?: string; defectRate?: string; scrapRate?: string
}
export interface YieldReport {
  inputQty: string; firstYield?: string; fpy?: string; rows: YieldRow[]; operations: YieldRow[]; trend: { date: string; firstYield?: string; inputQty: string }[]
  pareto: { defectCode: string; label: string; qty: string; share: string; cumulative: string }[]
}

export const reportApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ReportRow>>('/production/reports', q),
  context: (q: { barcode?: string; prodOrderId?: string; seq?: number; workOrderId?: string }) => http.get<ReportContext>('/production/reports/context', q),
  get: (id: string) => http.get<ReportDetail>(`/production/reports/${id}`),
  create: (d: ReportSave) => http.post<{ id: string; docNo: string; status: string; warnings: string[] }>('/production/reports', d),
  update: (id: string, d: ReportSave) => http.put<{ id: string; docNo: string; status: string; warnings: string[] }>(`/production/reports/${id}`, d),
  remove: (id: string) => http.delete<void>(`/production/reports/${id}`),
  approve: (id: string) => http.post<string>(`/production/reports/${id}/approve`),
  batchApprove: (ids: string[]) => http.post<BatchResult>('/production/reports/batch-approve', { ids }),
  unapprove: (id: string) => http.post<void>(`/production/reports/${id}/unapprove`),
  yield: (q: Record<string, unknown>) => http.get<YieldReport>('/production/reports/yield', q)
}

export const defectApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<DefectRow>>('/production/defects', q),
  ncrAvailable: () => http.get<boolean>('/production/defects/ncr-available'),
  repair: (id: string, qty: string) => http.post<void>(`/production/defects/${id}/repair`, { qty }),
  scrap: (id: string, qty: string, scrapReason?: string) => http.post<void>(`/production/defects/${id}/scrap`, { qty, scrapReason }),
  toNcr: (id: string) => http.post<string>(`/production/defects/${id}/to-ncr`)
}

// ==================== 完工入库 / 追溯 ====================

export interface FinishRow {
  id: string; docNo: string; prodOrderId: string; prodOrderNo: string; materialId: string; materialCode: string; materialName: string; qty: string
  batchNo?: string; fqcRequired: boolean; warehouseId?: string; warehouseName?: string; stockInNos?: string; stockedQty: string; qualifiedQty: string
  rejectedQty: string; finishStatus: string; ownerName?: string; docDate: string; remark?: string
}
export interface TraceTreeNode {
  key: string; level: number; materialId: string; materialCode: string; materialName: string; batchNo?: string; batchTracked: boolean; qty?: string
  prodOrderId?: string; prodOrderNo?: string; completedQty?: string; supplierId?: string; supplierBatchNo?: string; batchSourceNo?: string
  concession: boolean; children: TraceTreeNode[]
}
export interface TraceResult { direction: string; root: TraceTreeNode; orderCount: number; batchCount: number; notes: string[] }

export const finishApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<FinishRow>>('/production/finishes', q),
  cancel: (id: string) => http.post<void>(`/production/finishes/${id}/cancel`)
}
export const traceApi = {
  backward: (materialId: string, batchNo: string) => http.get<TraceResult>('/production/trace/backward', { materialId, batchNo }),
  forward: (materialId: string, batchNo: string) => http.get<TraceResult>('/production/trace/forward', { materialId, batchNo })
}

// ==================== 生产报表 ====================

export interface ProgressRow {
  prodOrderId: string; prodOrderNo: string; materialId: string; materialCode: string; materialName: string; qty: string
  operations: { seq: number; operation: string; goodQty: string; done: boolean }[]; completedQty: string; stockedQty: string; planEnd: string
  delayed: boolean; prodStatus: string; salesOrderNo?: string; customerDueDate?: string; deptName?: string
}
export interface VarianceRow {
  prodOrderId: string; prodOrderNo: string; productCode: string; componentId: string; componentCode: string; componentName: string; uom: string
  theoreticalQty: string; netQty: string; varianceQty: string; varianceRate?: string; overIssuedQty: string; overReasons?: string
}
export interface OutputRow {
  key: string; label: string; goodQty: string; scrapQty: string; workHours: string; stdHours: string; efficiency?: string; headcount: number; perCapita?: string
}
export interface Achievement {
  dueCount: number; onTimeCount: number; rate?: string
  delayed: { prodOrderId: string; prodOrderNo: string; materialCode: string; materialName: string; planEnd: string; actualEnd?: string; delayDays: number; prodStatus: string }[]
}

export const centerApi = {
  progress: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ProgressRow>>('/production/reports/progress', q),
  variance: (q: Record<string, unknown>) => http.get<VarianceRow[]>('/production/reports/material-variance', q),
  output: (q: Record<string, unknown>) => http.get<OutputRow[]>('/production/reports/output-hours', q),
  achievement: (q: Record<string, unknown>) => http.get<Achievement>('/production/reports/plan-achievement', q)
}

/** 生产订单远程搜索（领料、退料、报工选择订单）：默认已下达、生产中 */
export async function searchOrders(keyword: string, statuses = 'RELEASED,IN_PROGRESS'): Promise<ProdOrderRow[]> {
  return (await prodOrderApi.page({ pageNo: 1, pageSize: 20, docNo: keyword || undefined, statuses })).list
}
