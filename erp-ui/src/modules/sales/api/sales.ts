import { http, type PageParam, type PageResult } from '@/api/http'
import type { Option, RelatedDoc, StatusMap } from '@/components'

/** 销售模块接口与公共类型（需求 04-销售） */

export type DocStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED' | 'VOIDED'

export const DOC_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_APPROVAL: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已审核', type: 'primary' },
  IN_PROGRESS: { label: '执行中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true },
  VOIDED: { label: '已作废', type: 'danger' }
}
export const PRICE_LIST_STATUS: StatusMap = { ...DOC_STATUS, APPROVED: { label: '已生效', type: 'success' } }
export const FORECAST_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  APPROVED: { label: '已发布', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true }
}
export const RFQ_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  EVALUATING: { label: '评估中', type: 'warning' },
  COSTED: { label: '已核算', type: 'primary' },
  QUOTED: { label: '已报价', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true }
}
export const QUOTE_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已审核', type: 'primary' },
  SENT: { label: '已发送', type: 'primary' },
  WON: { label: '已成交', type: 'success' },
  LOST: { label: '未成交', type: 'danger', plain: true },
  EXPIRED: { label: '已过期', type: 'info', plain: true },
  REVISED: { label: '已修订', type: 'info', plain: true }
}
export const LINE_STATUS: StatusMap = {
  OPEN: { label: '未完成', type: 'primary' },
  SHIPPED: { label: '已出齐', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true }
}
export const PLAN_STATUS: StatusMap = {
  NOT_DUE: { label: '未到期', type: 'info' },
  DUE: { label: '已到期', type: 'warning' },
  OVERDUE: { label: '逾期', type: 'danger' },
  PARTIAL: { label: '部分收款', type: 'primary' },
  RECEIVED: { label: '已收齐', type: 'success' },
  CANCELED: { label: '已取消', type: 'info', plain: true }
}
export const PROGRESS_STATUS: StatusMap = {
  NONE: { label: '未开始', type: 'info', plain: true },
  PARTIAL: { label: '部分', type: 'warning' },
  DONE: { label: '完成', type: 'success' }
}
export const SCOPE_OPTIONS: Option[] = [
  { value: 'CUSTOMER', label: '指定客户' },
  { value: 'LEVEL', label: '客户等级' },
  { value: 'ALL', label: '所有客户' }
]
export const HANDLING_OPTIONS: Option[] = [
  { value: 'REFUND', label: '退货退款' },
  { value: 'REPLACE', label: '退货换货' }
]
export const FEASIBILITY_OPTIONS: Option[] = [
  { value: 'PENDING', label: '待评估' },
  { value: 'OK', label: '可行' },
  { value: 'NG', label: '不可行' }
]
export const CHANGE_TYPE_OPTIONS: Option[] = [
  { value: 'MODIFY', label: '修改' },
  { value: 'ADD', label: '新增' },
  { value: 'CANCEL', label: '取消' }
]
export const BASE_EVENT_OPTIONS: Option[] = [
  { value: 'ORDER_DATE', label: '下单日' }, { value: 'BEFORE_SHIPMENT', label: '出货前' }, { value: 'SHIPMENT', label: '出货日' },
  { value: 'BL_DATE', label: '提单日' }, { value: 'INVOICE_DATE', label: '开票日' }, { value: 'MONTH_END', label: '月结' },
  { value: 'RECEIPT_DATE', label: '到货日' }
]
export const EXEC_TYPE_OPTIONS: Option[] = [
  { value: 'NOTICE', label: '出货通知' }, { value: 'SHIP', label: '出库' }, { value: 'SHIP_REVERSE', label: '出库冲销' }, { value: 'BL', label: '提单' },
  { value: 'INVOICE', label: '开票' }, { value: 'RECEIPT', label: '回款' }, { value: 'RETURN', label: '退货' }
]

export const optionsOf = (map: StatusMap, exclude: string[] = []): Option[] =>
  Object.entries(map).filter(([k]) => !exclude.includes(k)).map(([value, s]) => ({ value, label: s.label }))
export const labelOf = (options: Option[], v?: string | null) => (v ? options.find((o) => o.value === v)?.label ?? v : '-')
export const joinList = (v?: string[]) => (v?.length ? v.join(',') : undefined)
export const num = (v?: string | number | null) => (v === undefined || v === null || v === '' ? 0 : Number(v))
export const submitText = (status?: string) =>
  status === 'PENDING_APPROVAL' || status === 'PENDING' ? '已提交，等待审批' : status === 'APPROVED' ? '提交成功，已审核' : '提交成功'
export const pctOf = (v?: string | null) => (v === undefined || v === null || v === '' ? undefined : String(Number((Number(v) * 100).toFixed(4))))
export const rateOf = (v?: string | null) => (v === undefined || v === null || v === '' ? undefined : String(Number((Number(v) / 100).toFixed(6))))

export interface SaveResult { id: string; warnings: string[] }
export interface DocResult { status: string; warnings: string[] }
export type { RelatedDoc }

// ==================== 价格表 ====================

export interface PriceListRow {
  id: string; docNo: string; name: string; scope: string; customerId?: string; customerName?: string; customerLevel?: string; currency: string
  taxIncluded: boolean; effectiveFrom: string; effectiveTo?: string; itemCount: number; status: DocStatus; ownerName?: string; createdAt: string
}
export interface PriceItem {
  id?: string; lineNo?: number; materialId?: string; materialCode?: string; materialName?: string; materialSpec?: string; baseUom?: string; uom?: string
  minQty?: string; price?: string; costPrice?: string; marginRate?: string; belowFloor?: boolean; remark?: string
}
export interface PriceListDetail {
  id: string; docNo: string; name: string; scope: string; customerId?: string; customerName?: string; customerLevel?: string; currency: string
  taxIncluded: boolean; effectiveFrom: string; effectiveTo?: string; status: DocStatus; closeReason?: string; remark?: string; costVisible: boolean
  ownerName?: string; createdAt: string; version: number; items: PriceItem[]
}
export interface PriceListSave {
  name: string; scope: string; customerId?: string; customerLevel?: string; currency: string; taxIncluded: boolean; effectiveFrom: string
  effectiveTo?: string; remark?: string; items: { materialId: string; uom: string; minQty?: string; price: string; remark?: string }[]; version?: number
}
export interface PriceLookup { price: string; taxIncluded: boolean; uom: string; sourceType: string; sourceNo?: string; sourceLabel: string }

export const priceListApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<PriceListRow>>('/sales/price-lists', q),
  get: (id: string) => http.get<PriceListDetail>(`/sales/price-lists/${id}`),
  create: (d: PriceListSave) => http.post<string>('/sales/price-lists', d),
  update: (id: string, d: PriceListSave) => http.put<void>(`/sales/price-lists/${id}`, d),
  remove: (id: string) => http.delete<void>(`/sales/price-lists/${id}`),
  submit: (id: string) => http.post<DocResult>(`/sales/price-lists/${id}/submit`),
  close: (id: string, reason: string) => http.post<void>(`/sales/price-lists/${id}/close`, { reason }),
  copy: (id: string) => http.post<string>(`/sales/price-lists/${id}/copy`),
  lookup: (q: { customerId?: string; materialId: string; qty?: string; uom: string; date?: string; currency: string }) =>
    http.get<PriceLookup | null>('/sales/prices/lookup', q, { silent: true })
}

// ==================== RFQ / 报价 ====================

export interface RfqRow {
  id: string; docNo: string; customerId: string; customerName?: string; lineCount: number; replyDueDate: string; dueLevel?: string
  engineerName?: string; costEngineerName?: string; feasibility: string; rfqStatus: string; ownerName?: string; docDate: string
}
export interface CostSheetBrief { id: string; qty: string; totalCost?: string; suggestedPrice?: string; suggestedPriceCur?: string }
export interface RfqLine {
  id?: string; lineNo?: number; customerPartNo?: string; description?: string; materialId?: string; materialCode?: string; materialName?: string
  materialSpec?: string; baseUom?: string; annualQty?: string; qtyBreaks?: string; targetPrice?: string; requiredDate?: string; feasibility?: string
  feasibilityRemark?: string; remark?: string; costSheets?: CostSheetBrief[]
}
export interface RfqDetail {
  id: string; docNo: string; docDate: string; rfqStatus: string; customerId: string; customerName: string; customerStatus: string; contactId?: string
  contactName?: string; opportunityId?: string; currency: string; tradeTerm?: string; replyDueDate: string; engineerId?: string; engineerName?: string
  costEngineerId?: string; costEngineerName?: string; closeReason?: string; remark?: string; ownerId?: string; ownerName?: string; canEvaluate: boolean
  canCost: boolean; costVisible: boolean; createdAt: string; version: number; lines: RfqLine[]; related: RelatedDoc[]
}
export interface MaterialCostRow { componentId: string; code?: string; name?: string; spec?: string; uom?: string; qtyPer: string; unitPrice: string; source: string; amount: string }
export interface OperationCostRow {
  materialCode?: string; seq: number; operation: string; workCenterName?: string; runHours: string; setupHours: string; laborRate: string
  overheadRate: string; labor: string; overhead: string; setup: string
}
export interface CostSheet {
  id?: string; qty: string; bomNo?: string; materialCost: string; laborCost: string; overheadCost: string; setupCost: string; toolingTotal?: string
  toolingQty?: string; toolingCost: string; packingFreightCost: string; adminRate: string; profitRate: string; totalCost: string; suggestedPrice: string
  suggestedPriceCur?: string; baseCurrency: string; currency: string; targetPrice?: string; targetDiffPct?: string; materials: MaterialCostRow[]
  operations: OperationCostRow[]
}
export interface CostSheetReq {
  qty: string; materialPrices?: Record<string, string>; toolingTotal?: string; toolingQty?: string; packingFreightCost?: string; adminRate?: string
  profitRate?: string
}

export const rfqApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<RfqRow>>('/sales/rfqs', q),
  get: (id: string) => http.get<RfqDetail>(`/sales/rfqs/${id}`),
  create: (d: object) => http.post<SaveResult>('/sales/rfqs', d),
  update: (id: string, d: object) => http.put<SaveResult>(`/sales/rfqs/${id}`, d),
  remove: (id: string) => http.delete<void>(`/sales/rfqs/${id}`),
  assign: (id: string, d: { engineerId?: string; costEngineerId?: string }) => http.post<void>(`/sales/rfqs/${id}/assign`, d),
  feasibility: (id: string, rows: { lineId: string; feasibility: string; remark?: string; materialId?: string }[]) =>
    http.post<void>(`/sales/rfqs/${id}/feasibility`, rows),
  costSheets: (id: string, lineId: string) => http.get<CostSheet[]>(`/sales/rfqs/${id}/lines/${lineId}/cost-sheets`),
  calc: (id: string, lineId: string, d: CostSheetReq) => http.post<CostSheet>(`/sales/rfqs/${id}/lines/${lineId}/cost-sheets/calc`, d),
  saveCost: (id: string, lineId: string, d: CostSheetReq) => http.put<CostSheet>(`/sales/rfqs/${id}/lines/${lineId}/cost-sheets`, d),
  toQuotation: (id: string) => http.post<string>(`/sales/rfqs/${id}/to-quotation`),
  close: (id: string, reason: string) => http.post<void>(`/sales/rfqs/${id}/close`, { reason })
}

export interface QuotationRow {
  id: string; docNo: string; revision: number; customerId: string; customerName?: string; currency: string; totalAmount: string; minMarginRate?: string
  lowMargin: boolean; validUntil: string; expired: boolean; quoteStatus: string; ownerName?: string; docDate: string
}
export interface QuotationLine {
  id?: string; lineNo?: number; materialId?: string; materialCode?: string; materialName?: string; materialSpec?: string; baseUom?: string
  customerPartNo?: string; description?: string; uom?: string; minQty?: string; price?: string; taxRate?: string; costPrice?: string; marginRate?: string
  belowFloor?: boolean; moq?: string; leadTimeDays?: number; toolingFee?: string; rfqLineId?: string; remark?: string
}
export interface QuotationDetail {
  id: string; docNo: string; revision: number; docDate: string; quoteStatus: string; customerId: string; customerName: string; customerStatus: string
  customerLevel?: string; contactId?: string; contactName?: string; rfqId?: string; rfqNo?: string; opportunityId?: string; currency: string
  exchangeRate: string; tradeTerm?: string; paymentTermId?: string; paymentTermName?: string; taxIncluded: boolean; validUntil: string; expired: boolean
  terms?: string; remark?: string; totalAmount: string; totalAmountBase: string; minMarginRate?: string; belowFloor: boolean; lostReason?: string
  lostRemark?: string; sentAt?: string; parentQuotationId?: string; ownerId?: string; ownerName?: string; costVisible: boolean; createdAt: string
  version: number; lines: QuotationLine[]; revisions: { id: string; docNo: string; revision: number; quoteStatus: string; docDate: string; totalAmount: string;
    lines: QuotationLine[] }[]; related: RelatedDoc[]
}
export interface QuoteOpenLine {
  quotationId: string; docNo: string; revision: number; customerId: string; customerName?: string; currency: string; lineId: string; materialId: string
  materialCode?: string; materialName?: string; uom: string; minQty: string; price: string; taxIncluded: boolean; leadTimeDays?: number; validUntil: string
}

export const quotationApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<QuotationRow>>('/sales/quotations', q),
  get: (id: string) => http.get<QuotationDetail>(`/sales/quotations/${id}`),
  create: (d: object) => http.post<SaveResult>('/sales/quotations', d),
  update: (id: string, d: object) => http.put<SaveResult>(`/sales/quotations/${id}`, d),
  remove: (id: string) => http.delete<void>(`/sales/quotations/${id}`),
  submit: (id: string) => http.post<DocResult>(`/sales/quotations/${id}/submit`),
  send: (id: string) => http.post<void>(`/sales/quotations/${id}/send`),
  revise: (id: string) => http.post<string>(`/sales/quotations/${id}/revise`),
  lose: (id: string, lostReason: string, remark?: string) => http.post<void>(`/sales/quotations/${id}/lose`, { lostReason, remark }),
  toOrder: (id: string, lines: { quotationLineId: string; qty: string; requiredDate?: string }[]) => http.post<string>(`/sales/quotations/${id}/to-order`, lines)
}

// ==================== 订单 ====================

export interface OrderRow {
  id: string; docNo: string; orderVersion: number; orderType: string; customerId: string; customerName?: string; customerPoNo?: string; currency: string
  totalAmount: string; minMarginRate?: string; belowFloor: boolean; earliestRequiredDate?: string; shipProgress: string; receiveProgress: string
  deliveryRisk: boolean; status: DocStatus; ownerName?: string; docDate: string
}
export interface OrderLine {
  id?: string; lineNo?: number; materialId?: string; materialCode?: string; materialName?: string; materialSpec?: string; baseUom?: string
  customerPartNo?: string; description?: string; uom?: string; qty?: string; baseQty?: string; price?: string; priceInclTax?: string; taxRate?: string
  amount?: string; taxAmount?: string; totalAmount?: string; priceSource?: string; costPrice?: string; marginRate?: string; belowFloor?: boolean
  requiredDate?: string; promisedDate?: string; promiseRemark?: string; delayed?: boolean; noticedQty?: string; shippedQty?: string; returnedQty?: string
  invoicedQty?: string; openQty?: string; lineStatus?: string; quotationLineId?: string; remark?: string
}
export interface OrderDetail {
  id: string; docNo: string; docDate: string; status: DocStatus; orderType: string; customerId: string; customerCode: string; customerName: string
  customerLevel?: string; contactId?: string; contactName?: string; customerPoNo?: string; customerPoDate?: string; quotationId?: string; quotationNo?: string
  currency: string; exchangeRate: string; taxIncluded: boolean; paymentTermId?: string; paymentTermName?: string; tradeTerm?: string; portOfLoading?: string
  portOfDestination?: string; shipToAddressId?: string; shipToText?: string; billToAddressId?: string; billToText?: string; amount: string; taxAmount: string
  totalAmount: string; totalAmountBase: string; shippedAmount: string; receivedAmount: string; minMarginRate?: string; belowFloor: boolean
  creditWarning: boolean; orderVersion: number; deliveryRisk: boolean; riskLineCount: number; closeReason?: string; terms?: string; remark?: string
  ownerId?: string; ownerName?: string; approvedAt?: string; costVisible: boolean; runningChangeId?: string; runningChangeNo?: string
  createdByName?: string; createdAt: string; version: number; lines: OrderLine[]; related: RelatedDoc[]
}
export interface CustomerDefaults {
  customerId: string; customerName: string; customerStatus: string; foreign: boolean; currency: string; exchangeRate?: string; taxIncluded: boolean
  salesTaxRate?: string; paymentTermId?: string; tradeTerm?: string; ownerId?: string; ownerName?: string; shipToAddressId?: string
  billToAddressId?: string; contactId?: string; addresses: { id: string; type: string; text: string; isDefault: boolean }[]
  contacts: { id: string; name: string; title?: string; email?: string; phone?: string; primary: boolean }[]
}
export interface ExecRow { id: string; orderLineId?: string; lineNo?: number; execType: string; docType?: string; docNo?: string; qty?: string; amount?: string; execDate?: string; createdAt: string }
export interface SnapshotRow { id: string; orderVersion: number; changeId?: string; changeNo?: string; content: string; createdAt: string }
export interface PaymentPlanRow {
  id: string; orderId: string; orderNo: string; customerId: string; customerName?: string; ownerName?: string; seq: number; batchNo: number
  nodeName: string; percent: string; baseEvent: string; days: number; currency: string; planAmount: string; eventDate?: string; dueDate?: string
  overdueDays: number; receivedAmount: string; unreceivedAmount: string; planStatus: string; remark?: string; promisedPayDate?: string; followedAt?: string
}
export interface OrderPaymentSummary { totalAmount: string; receivedAmount: string; unreceivedAmount: string; advanceAmount: string; plans: PaymentPlanRow[] }

export const orderApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<OrderRow>>('/sales/orders', q),
  get: (id: string) => http.get<OrderDetail>(`/sales/orders/${id}`),
  customerDefaults: (customerId: string) => http.get<CustomerDefaults>('/sales/orders/customer-defaults', { customerId }),
  create: (d: object) => http.post<SaveResult>('/sales/orders', d),
  update: (id: string, d: object) => http.put<SaveResult>(`/sales/orders/${id}`, d),
  remove: (id: string) => http.delete<void>(`/sales/orders/${id}`),
  submit: (id: string, confirmCredit = false) => http.post<DocResult>(`/sales/orders/${id}/submit`, { confirmCredit }, { silent: true }),
  unapprove: (id: string, reason: string) => http.post<void>(`/sales/orders/${id}/unapprove`, { reason }),
  close: (id: string, reason: string) => http.post<void>(`/sales/orders/${id}/close`, { reason }),
  void: (id: string, reason: string) => http.post<void>(`/sales/orders/${id}/void`, { reason }),
  copy: (id: string) => http.post<SaveResult>(`/sales/orders/${id}/copy`),
  execution: (id: string) => http.get<ExecRow[]>(`/sales/orders/${id}/execution`),
  snapshots: (id: string) => http.get<SnapshotRow[]>(`/sales/orders/${id}/snapshots`),
  paymentPlans: (id: string) => http.get<OrderPaymentSummary>(`/sales/orders/${id}/payment-plans`),
  quotationLines: (customerId?: string) => http.get<QuoteOpenLine[]>('/sales/orders/quotation-lines', { customerId }),
  fromQuotations: (lines: { quotationLineId: string; qty: string; requiredDate?: string }[]) =>
    http.post<{ orderIds: string[]; messages: string[] }>('/sales/orders/from-quotations', lines)
}

export const paymentPlanApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<PaymentPlanRow>>('/sales/payment-plans', q),
  summary: () => http.get<{ dueThisMonth: string; overdue: string; receivedThisMonth: string; baseCurrency: string }>('/sales/payment-plans/summary'),
  followUp: (id: string, remark?: string, promisedPayDate?: string) => http.post<void>(`/sales/payment-plans/${id}/follow-up`, { remark, promisedPayDate })
}

// ==================== 订单变更 ====================

export interface ChangeRow {
  id: string; docNo: string; orderId: string; orderNo?: string; customerName?: string; versionFrom: number; versionTo: number; changeReason: string
  currency?: string; amountDiff: string; amountChangeBase: string; status: DocStatus; ownerName?: string; docDate: string
}
export interface ChangeHeader {
  customerPoNo?: string; paymentTermId?: string; tradeTerm?: string; portOfLoading?: string; portOfDestination?: string; shipToAddressId?: string
  contactId?: string; terms?: string; remark?: string
}
export interface ChangeLine {
  id?: string; lineNo?: number; changeType: string; orderLineId?: string; orderLineNo?: number; materialId?: string; materialCode?: string
  materialName?: string; uom?: string; oldQty?: string; newQty?: string; oldPrice?: string; newPrice?: string; oldRequiredDate?: string
  newRequiredDate?: string; newCustomerPartNo?: string; newDescription?: string; remark?: string
}
export interface ChangeDetail {
  id: string; docNo: string; docDate: string; status: DocStatus; orderId: string; orderNo: string; orderVersion: number; customerId: string
  customerName: string; currency: string; taxIncluded: boolean; orderVersionFrom: number; changeReason: string; reasonRemark: string; amountBefore: string
  amountAfter: string; amountChangeBase: string; headerChanges: { field: string; label: string; oldValue?: string; newValue?: string }[]
  header: ChangeHeader; ownerName?: string; createdByName?: string; createdAt: string; version: number; lines: ChangeLine[]; orderLines: OrderLine[]
}

export const changeApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ChangeRow>>('/sales/order-changes', q),
  get: (id: string) => http.get<ChangeDetail>(`/sales/order-changes/${id}`),
  create: (orderId: string) => http.post<string>(`/sales/order-changes?orderId=${orderId}`),
  update: (id: string, d: object) => http.put<void>(`/sales/order-changes/${id}`, d),
  remove: (id: string) => http.delete<void>(`/sales/order-changes/${id}`),
  submit: (id: string, confirmCredit = false) => http.post<DocResult>(`/sales/order-changes/${id}/submit`, { confirmCredit }, { silent: true }),
  void: (id: string, reason: string) => http.post<void>(`/sales/order-changes/${id}/void`, { reason })
}

// ==================== 预测 ====================

export interface ForecastRow {
  id: string; docNo: string; title: string; startPeriod: string; endPeriod: string; lineCount: number; totalQty: string; consumedQty: string
  consumeRate: string; status: string; ownerName?: string; publishedAt?: string; createdAt: string
}
export interface ForecastCell { lineId?: string; period: string; qty?: string; consumedQty?: string }
export interface ForecastRowResp {
  customerId?: string; customerName?: string; materialId: string; materialCode?: string; materialName?: string; materialSpec?: string; baseUom?: string
  remark?: string; cells: ForecastCell[]; totalQty?: string; totalConsumed?: string
}
export interface ForecastDetail {
  id: string; docNo: string; title: string; startPeriod: string; endPeriod: string; periods: string[]; status: string; publishedAt?: string
  closeReason?: string; revisedFromId?: string; revisedFromNo?: string; remark?: string; ownerName?: string; createdAt: string; version: number
  rows: ForecastRowResp[]
}

export const forecastApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ForecastRow>>('/sales/forecasts', q),
  get: (id: string) => http.get<ForecastDetail>(`/sales/forecasts/${id}`),
  create: (d: object) => http.post<string>('/sales/forecasts', d),
  update: (id: string, d: object) => http.put<void>(`/sales/forecasts/${id}`, d),
  remove: (id: string) => http.delete<void>(`/sales/forecasts/${id}`),
  publish: (id: string) => http.post<void>(`/sales/forecasts/${id}/publish`),
  close: (id: string, reason: string) => http.post<void>(`/sales/forecasts/${id}/close`, { reason }),
  copy: (id: string) => http.post<string>(`/sales/forecasts/${id}/copy`),
  revise: (id: string) => http.post<string>(`/sales/forecasts/${id}/revise`),
  previousRows: (id: string) => http.get<ForecastRowResp[]>(`/sales/forecasts/${id}/previous-rows`),
  consumptions: (id: string, lineId: string) =>
    http.get<{ id: string; orderId?: string; orderNo?: string; lineNo?: number; customerName?: string; qty: string; createdAt: string }[]>(
      `/sales/forecasts/${id}/lines/${lineId}/consumptions`)
}

// ==================== 退货 ====================

export interface ReturnRow {
  id: string; docNo: string; customerId: string; customerName?: string; rmaNo?: string; returnReason: string; handling: string; materialSummary?: string
  totalQty: string; currency: string; totalAmount: string; receiveStatus: string; judgeStatus: string; status: DocStatus; ownerName?: string; docDate: string
}
export interface ReturnLine {
  id?: string; lineNo?: number; orderId?: string; orderNo?: string; orderLineId: string; orderLineNo?: number; materialId?: string; materialCode?: string
  materialName?: string; materialSpec?: string; baseUom?: string; batchNo?: string; serialNos?: string; qty?: string; returnableQty?: string
  priceInclTax?: string; taxRate?: string; totalAmount?: string; receivedQty?: string; goodQty?: string; reworkQty?: string; scrapQty?: string; remark?: string
}
export interface ReturnDetail {
  id: string; docNo: string; docDate: string; status: DocStatus; customerId: string; customerName: string; rmaNo?: string; returnReason: string
  handling: string; currency: string; exchangeRate: string; totalAmount: string; totalAmountBase: string; complaintNo?: string; stockInId?: string
  expectedArrivalDate?: string; voidReason?: string; remark?: string; ownerName?: string; createdAt: string; version: number; lines: ReturnLine[]
  related: RelatedDoc[]
}
export interface ShippedLine {
  orderLineId: string; orderId: string; orderNo: string; lineNo: number; materialId: string; materialCode?: string; materialName?: string
  materialSpec?: string; baseUom?: string; shippedQty: string; returnedQty: string; returnableQty: string; currency: string; priceInclTax: string
  lastShipDate?: string
}

export const returnApi = {
  page: (q: PageParam & Record<string, unknown>) => http.get<PageResult<ReturnRow>>('/sales/returns', q),
  get: (id: string) => http.get<ReturnDetail>(`/sales/returns/${id}`),
  shippedLines: (customerId: string) => http.get<ShippedLine[]>('/sales/shipped-lines', { customerId }),
  create: (d: object) => http.post<SaveResult>('/sales/returns', d),
  update: (id: string, d: object) => http.put<SaveResult>(`/sales/returns/${id}`, d),
  remove: (id: string) => http.delete<void>(`/sales/returns/${id}`),
  submit: (id: string) => http.post<DocResult>(`/sales/returns/${id}/submit`),
  void: (id: string, reason: string) => http.post<void>(`/sales/returns/${id}/void`, { reason }),
  judge: (id: string, rows: { lineId: string; goodQty?: string; reworkQty?: string; scrapQty?: string }[]) => http.post<void>(`/sales/returns/${id}/judge`, rows)
}

// ==================== 报表 ====================

export interface OpenOrderRow {
  orderId: string; orderNo: string; orderLineId: string; lineNo: number; customerName?: string; materialCode?: string; materialName?: string
  materialSpec?: string; baseUom?: string; orderQty: string; shippedQty: string; openQty: string; availableQty: string; wipQty?: string
  requiredDate: string; promisedDate?: string; daysToDue: number; ownerName?: string
}
export interface OrderLineReportRow {
  orderId: string; docNo: string; docDate: string; status: string; customerName?: string; customerPoNo?: string; ownerName?: string; currency: string
  lineNo: number; lineId: string; materialCode?: string; materialName?: string; customerPartNo?: string; uom: string; qty: string; price: string
  totalAmount: string; totalAmountBase: string; requiredDate: string; promisedDate?: string; shippedQty: string; openQty: string; invoicedQty: string
  lineStatus: string; marginRate?: string
}
export interface TraceStep { step: string; label: string; date?: string; time?: string; docNo?: string; qty?: string; amount?: string; remark?: string }
export interface Performance {
  baseCurrency: string
  rows: { ownerId: string; ownerName?: string; deptName?: string; orderAmount: string; shipAmount: string; receiptAmount: string; newCustomers: number; orderCount: number }[]
  trend: { month: string; orderAmount: string; shipAmount: string; receiptAmount: string }[]
}
export interface QuoteSuccess {
  quoteCount: number; wonCount: number; successRate: string
  rows: { groupKey: string; groupName?: string; quoteCount: number; wonCount: number; lostCount: number; successRate: string; avgCycleDays?: string }[]
  lostReasons: { name: string; label: string; value: string }[]
}
export interface CustomerRankRow {
  rank: number; customerId: string; customerCode?: string; customerName?: string; orderAmount: string; shipAmount: string; share: string
  lastYearAmount?: string; growth?: string; abcClass: string
}

export const reportApi = {
  orderLines: (q: PageParam & Record<string, unknown>) => http.get<PageResult<OrderLineReportRow>>('/sales/reports/order-lines', q),
  openOrders: (q: PageParam & Record<string, unknown>) => http.get<PageResult<OpenOrderRow>>('/sales/reports/open-orders', q),
  trace: (lineId: string) => http.get<{ orderNo: string; lineNo: number; materialCode: string; materialName: string; qty: string; steps: TraceStep[] }>(
    `/sales/reports/order-trace/${lineId}`),
  performance: (q: Record<string, unknown>) => http.get<Performance>('/sales/reports/performance', q),
  quoteSuccess: (q: Record<string, unknown>) => http.get<QuoteSuccess>('/sales/reports/quotation-success', q),
  customerRanking: (q: Record<string, unknown>) => http.get<CustomerRankRow[]>('/sales/reports/customer-ranking', q)
}
