import { http, upload, type PageParam, type PageResult } from '@/api/http'
import type { Option, StatusMap } from '@/components'

/** 财务模块接口与公共类型（需求 12-财务）。金额为原币，*Base 为本位币；红字单据金额为负数 */

export const AR_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'success' },
  VOIDED: { label: '已作废', type: 'danger' }
}
export const CASH_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  CONFIRMED: { label: '已确认', type: 'success' },
  VOIDED: { label: '已作废', type: 'danger' }
}
export const REQUEST_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '待付款', type: 'primary', plain: true },
  PARTIAL: { label: '部分付款', type: 'primary' },
  PAID: { label: '已付款', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true },
  VOIDED: { label: '已作废', type: 'danger' }
}
export const INVOICE_STATUS: StatusMap = {
  REGISTERED: { label: '已登记', type: 'success' },
  VOIDED: { label: '已作废', type: 'danger' },
  RED: { label: '已红冲', type: 'warning' }
}
export const MATCH_STATUS: StatusMap = {
  UNMATCHED: { label: '未匹配', type: 'info' },
  MATCHED: { label: '已匹配', type: 'success' },
  DIFF: { label: '有差异', type: 'danger' }
}
export const DEDUCTION_STATUS: StatusMap = {
  NOT_CERTIFIED: { label: '未认证', type: 'warning', plain: true },
  CERTIFIED: { label: '已认证', type: 'success', plain: true }
}
export const PERIOD_STATUS: StatusMap = {
  NOT_OPEN: { label: '未开启', type: 'info', plain: true },
  OPEN: { label: '已开启', type: 'primary' },
  CLOSED: { label: '已结账', type: 'success' }
}
export const ENABLE: StatusMap = { ENABLED: { label: '启用', type: 'success' }, DISABLED: { label: '停用', type: 'info', plain: true } }

export const AR_TYPES: Option[] = [
  { value: 'SALES', label: '销售出货' }, { value: 'SALES_RETURN', label: '退货（红字）' }, { value: 'DISCOUNT', label: '折让（红字）' }, { value: 'OTHER', label: '其他' }
]
export const AP_TYPES: Option[] = [{ value: 'PURCHASE', label: '采购' }, { value: 'OUTSOURCE', label: '委外加工费' }, { value: 'OTHER', label: '其他' }]
export const RECEIPT_TYPES: Option[] = [
  { value: 'SALES', label: '销售收款' }, { value: 'ADVANCE', label: '预收款' }, { value: 'OTHER', label: '其他收款' }, { value: 'REFUND', label: '退款' }
]
export const REQUEST_TYPES: Option[] = [{ value: 'PAYABLE', label: '按应付付款' }, { value: 'PREPAYMENT', label: '预付款' }]
export const SALES_INVOICE_TYPES: Option[] = [
  { value: 'VAT_SPECIAL', label: '增值税专票' }, { value: 'VAT_NORMAL', label: '增值税普票' }, { value: 'EXPORT', label: '出口发票' }, { value: 'OTHER', label: '其他' }
]
export const PURCHASE_INVOICE_TYPES: Option[] = [
  { value: 'VAT_SPECIAL', label: '增值税专票' }, { value: 'VAT_NORMAL', label: '增值税普票' }, { value: 'OTHER', label: '其他' }
]
export const PROGRESS: Option[] = [
  { value: 'NONE', label: '未开始' }, { value: 'PARTIAL', label: '部分' }, { value: 'FULL', label: '全部' }, { value: 'OPEN', label: '未完成' }
]
export const VERIFY_TYPES: Record<string, string> = {
  RECEIPT_AR: '收款-应收', ADVANCE_AR: '预收冲应收', RED_BLUE_AR: '红蓝应收对冲', PAYMENT_AP: '付款-应付', PREPAY_AP: '预付冲应付', RED_BLUE_AP: '红蓝应付对冲'
}
export const ACCOUNT_TYPES: Option[] = [
  { value: 'ASSET', label: '资产' }, { value: 'LIABILITY', label: '负债' }, { value: 'EQUITY', label: '权益' }, { value: 'COST', label: '成本' },
  { value: 'PROFIT_LOSS', label: '损益' }
]
export const AUX_TYPES: Option[] = [
  { value: 'CUSTOMER', label: '客户' }, { value: 'SUPPLIER', label: '供应商' }, { value: 'DEPT', label: '部门' }, { value: 'MATERIAL', label: '物料' },
  { value: 'PROJECT', label: '项目' }
]
export const MAPPING_BIZ_TYPES: Option[] = [
  { value: 'SALES_AR', label: '出货应收' }, { value: 'SALES_RETURN_AR', label: '退货应收' }, { value: 'RECEIPT', label: '收款' },
  { value: 'PURCHASE_AP', label: '采购应付' }, { value: 'PAYMENT', label: '付款' }, { value: 'STOCK_IN_PURCHASE', label: '采购入库' },
  { value: 'STOCK_OUT_SALES_COST', label: '销售成本结转' }, { value: 'PRODUCTION_ISSUE', label: '生产领料' }, { value: 'PRODUCTION_IN', label: '完工入库' },
  { value: 'FX_GAIN_LOSS', label: '汇兑损益' }
]
export const AMOUNT_FIELDS: Option[] = [
  { value: 'totalAmount', label: '价税合计' }, { value: 'amount', label: '不含税金额' }, { value: 'tax', label: '税额' }, { value: 'cost', label: '成本' },
  { value: 'fee', label: '手续费' }, { value: 'fxDiff', label: '汇兑差异' }
]

export const labelOf = (options: Option[], v?: string) => options.find((o) => o.value === v)?.label ?? v ?? ''
export const optionsOf = (map: StatusMap, exclude: string[] = []): Option[] =>
  Object.entries(map).filter(([k]) => !exclude.includes(k)).map(([value, s]) => ({ value, label: s.label }))
export const joinList = (v?: string[]) => (v?.length ? v.join(',') : undefined)
export const num = (v?: string | number | null) => (v === undefined || v === null || v === '' ? 0 : Number(v))
export const round2 = (v: number) => Math.round(v * 100) / 100

export interface BatchResult { success: number; errors: string[] }
export interface SubmitResult { id: string; status: string }

// ==================== 基础设置 ====================

export interface AccountNode {
  id: string; code: string; name: string; parentCode?: string; accountType: string; direction: string; auxTypes: string[]
  currencyAccounting: boolean; leaf: boolean; level: number; status: string; children: AccountNode[]
}
export interface AccountSave { code: string; name: string; parentCode?: string; accountType?: string; direction?: string; auxTypes?: string[]; currencyAccounting?: boolean }
export interface AccountOption { code: string; name: string; fullName: string; auxTypes: string[] }
export interface PeriodVO { id: string; period: string; startDate: string; endDate: string; status: string; costLocked: boolean; closedByName?: string; closedAt?: string }
export interface BankAccount {
  id?: string; code: string; name: string; bankName: string; accountNo: string; currency: string; swift?: string; bankAddress?: string
  accountCode?: string; isDefault: boolean; status?: string; remark?: string
}
export interface BankOption { id: string; code: string; name: string; currency: string; accountNo: string; isDefault: boolean }
export interface MappingEntry { direction: string; accountCode: string; amountField: string; summaryTemplate?: string; auxFrom?: string }
export interface Mapping {
  id?: string; bizType: string; matchCondition?: string; conditionDesc?: string; priority: number; entries: MappingEntry[]; status?: string; remark?: string
}

export const settingApi = {
  accounts: () => http.get<AccountNode[]>('/finance/accounts'),
  accountOptions: () => http.get<AccountOption[]>('/finance/accounts/options'),
  createAccount: (data: AccountSave) => http.post<string>('/finance/accounts', data),
  updateAccount: (id: string, data: AccountSave) => http.put<void>(`/finance/accounts/${id}`, data),
  accountStatus: (id: string, enabled: boolean) => http.put<void>(`/finance/accounts/${id}/status?enabled=${enabled}`),
  deleteAccount: (id: string) => http.delete<void>(`/finance/accounts/${id}`),
  periods: (year?: number) => http.get<PeriodVO[]>('/finance/periods', { year }),
  initYear: (year: number, openFrom?: string) => http.post<void>('/finance/periods/init-year', { year, openFrom }),
  banks: (params?: { keyword?: string; currency?: string; status?: string }) => http.get<BankAccount[]>('/finance/bank-accounts', params),
  bankOptions: (currency?: string) => http.get<BankOption[]>('/finance/bank-accounts/simple', { currency }),
  createBank: (data: BankAccount) => http.post<string>('/finance/bank-accounts', data),
  updateBank: (id: string, data: BankAccount) => http.put<void>(`/finance/bank-accounts/${id}`, data),
  deleteBank: (id: string) => http.delete<void>(`/finance/bank-accounts/${id}`),
  mappings: (bizType?: string) => http.get<Mapping[]>('/finance/account-mappings', { bizType }),
  createMapping: (data: Mapping) => http.post<string>('/finance/account-mappings', data),
  updateMapping: (id: string, data: Mapping) => http.put<void>(`/finance/account-mappings/${id}`, data),
  deleteMapping: (id: string) => http.delete<void>(`/finance/account-mappings/${id}`)
}

// ==================== 核销 ====================

export interface Verification {
  id: string; verifyType: string; batchNo: string; currency: string; docAType: string; docAId: string; docANo: string; docBType: string; docBId: string
  docBNo: string; amount: string; amountBaseA: string; amountBaseB: string; fxDiff: string; period: string; verifiedAt: string; operatorName?: string
  reversed: boolean; reversedAt?: string
}
export interface Candidate {
  docType: string; docId: string; docNo: string; kind: string; docDate?: string; dueDate?: string; orderId?: string; orderNo?: string; sourceNo?: string
  totalAmount: string; available: string; exchangeRate: string; remark?: string
}
export interface Candidates { partnerId: string; partnerName: string; currency: string; left: Candidate[]; right: Candidate[] }
export interface Pick { docType: string; docId: string; amount: string }
export interface VerifyReq { partnerId: string; currency: string; left: Pick[]; right: Pick[] }
export interface VerifyResult { batchNo: string; count: number; amount: string; fxDiff: string }

export const verifyApi = {
  candidates: (params: { customerId?: string; supplierId?: string; currency?: string }) => http.get<Candidates>('/finance/verifications/candidates', params),
  receipt: (data: VerifyReq) => http.post<VerifyResult>('/finance/verifications/receipt', data),
  payment: (data: VerifyReq) => http.post<VerifyResult>('/finance/verifications/payment', data),
  auto: (partnerType: 'CUSTOMER' | 'SUPPLIER', data: VerifyReq) => http.post<Pick[]>(`/finance/verifications/auto?partnerType=${partnerType}`, data),
  reverse: (id: string) => http.post<void>(`/finance/verifications/${id}/reverse`),
  list: (params: { docType?: string; docId?: string; partnerType?: string; partnerId?: string; dateFrom?: string; dateTo?: string; includeReversed?: boolean }) =>
    http.get<Verification[]>('/finance/verifications', params)
}

// ==================== 应收 ====================

export interface ArRow {
  id: string; docNo: string; arType: string; customerId: string; customerName?: string; sourceType?: string; sourceId?: string; sourceNo?: string
  bizDate: string; currency: string; exchangeRate: string; totalAmount: string; totalAmountBase: string; verifiedAmount: string; unverifiedAmount: string
  invoicedAmount: string; dueDate?: string; overdueDays: number; status: string; ownerName?: string; description?: string; createdAt: string
}
export interface ArLine {
  id: string; lineNo: number; orderId?: string; orderNo?: string; orderLineId?: string; materialId?: string; materialCode?: string; materialName?: string
  description?: string; qty?: string; priceInclTax?: string; taxRate: string; amount: string; taxAmount: string; totalAmount: string; invoicedQty: string
  invoicedAmount: string
}
export interface InvoiceRef { id: string; docNo: string; invoiceNo: string; invoiceType: string; invoiceDate: string; qty: string; totalAmount: string; status: string }
export interface ArDetail {
  header: ArRow; amount: string; taxAmount: string; paymentTermId?: string; orderId?: string; blDate?: string; confirmedAt?: string; voidReason?: string
  voucherId?: string; remark?: string; lines: ArLine[]; verifications: Verification[]; invoices: InvoiceRef[]
}
export interface OtherLine { description: string; totalAmount?: string; taxRate?: string }
export interface OtherArSave {
  customerId?: string; currency: string; exchangeRate?: string; bizDate: string; dueDate?: string; description: string; remark?: string; fileIds?: string[]
  lines: OtherLine[]
}
export type ArQuery = PageParam & {
  docNo?: string; customerId?: string; arTypes?: string; statuses?: string; bizDateFrom?: string; bizDateTo?: string; sourceNo?: string; orderNo?: string
  currency?: string; verifyState?: string; invoiceState?: string; dueFrom?: string; dueTo?: string; overdueOnly?: boolean
}

export const arApi = {
  page: (params: ArQuery) => http.get<PageResult<ArRow>>('/finance/receivables', params),
  summary: (params: ArQuery) => http.get<{ totalAmountBase: string; unverifiedBase: string }>('/finance/receivables/summary', params),
  get: (id: string) => http.get<ArDetail>(`/finance/receivables/${id}`),
  confirm: (id: string) => http.post<void>(`/finance/receivables/${id}/confirm`),
  batchConfirm: (ids: string[]) => http.post<BatchResult>('/finance/receivables/batch-confirm', { ids }),
  unconfirm: (id: string, reason: string) => http.post<void>(`/finance/receivables/${id}/unconfirm`, { reason }),
  void: (id: string, reason: string) => http.post<void>(`/finance/receivables/${id}/void`, { reason }),
  createOther: (data: OtherArSave) => http.post<string>('/finance/receivables/other', data),
  updateOther: (id: string, data: OtherArSave) => http.put<void>(`/finance/receivables/other/${id}`, data),
  submit: (id: string) => http.post<SubmitResult>(`/finance/receivables/${id}/submit`),
  withdraw: (id: string) => http.post<void>(`/finance/receivables/${id}/withdraw`)
}

export interface SalesInvoiceRow {
  id: string; docNo: string; customerId: string; customerName?: string; invoiceType: string; invoiceNo: string; invoiceDate: string; currency: string
  amount: string; taxAmount: string; totalAmount: string; status: string; voidReason?: string; remark?: string; createdByName?: string; createdAt: string
}
export interface SalesInvoiceLine {
  id: string; lineNo: number; receivableId: string; receivableNo?: string; receivableLineId: string; orderLineId?: string; orderNo?: string
  materialId?: string; materialCode?: string; materialName?: string; qty?: string; amount: string; taxAmount: string; totalAmount: string
}
export interface UninvoicedLine {
  receivableId: string; receivableNo: string; receivableLineId: string; sourceNo?: string; bizDate: string; currency: string; orderLineId?: string
  orderNo?: string; materialId?: string; materialCode?: string; materialName?: string; description?: string; qty?: string; priceInclTax?: string
  taxRate: string; totalAmount: string; invoicedQty: string; uninvoicedQty?: string; uninvoicedAmount: string
}
export interface SalesInvoiceSave {
  customerId: string; invoiceType: string; invoiceNo: string; invoiceDate: string; remark?: string; fileIds?: string[]
  lines: { receivableLineId: string; qty: string; totalAmount?: string }[]
}

export const salesInvoiceApi = {
  page: (params: PageParam & { keyword?: string; customerId?: string; invoiceType?: string; status?: string; dateFrom?: string; dateTo?: string }) =>
    http.get<PageResult<SalesInvoiceRow>>('/finance/sales-invoices', params),
  get: (id: string) => http.get<{ header: SalesInvoiceRow; lines: SalesInvoiceLine[] }>(`/finance/sales-invoices/${id}`),
  uninvoiced: (customerId: string, receivableIds?: string[]) =>
    http.get<UninvoicedLine[]>('/finance/sales-invoices/uninvoiced-lines', { customerId, receivableIds: receivableIds?.join(',') }),
  register: (data: SalesInvoiceSave) => http.post<string>('/finance/sales-invoices', data),
  void: (id: string, reason: string) => http.post<void>(`/finance/sales-invoices/${id}/void`, { reason }),
  red: (id: string, reason: string) => http.post<void>(`/finance/sales-invoices/${id}/red`, { reason })
}

// ==================== 收款 ====================

export interface ReceiptRow {
  id: string; docNo: string; customerId: string; customerName?: string; receiptType: string; bankAccountId: string; bankAccountName?: string
  settlementMethod: string; receiptDate: string; currency: string; exchangeRate: string; amount: string; bankFee: string; amountBase: string
  allocatedAmount: string; unallocatedAmount: string; orderId?: string; orderNo?: string; bankRefNo?: string; payerName?: string; status: string
  ownerName?: string; remark?: string; createdAt: string
}
export interface ReceiptDetail { header: ReceiptRow; confirmedAt?: string; voucherId?: string; verifications: Verification[] }
export interface ReceiptSave {
  customerId?: string; receiptType: string; bankAccountId?: string; settlementMethod: string; receiptDate: string; exchangeRate?: string; amount?: string
  bankFee?: string; bankRefNo?: string; payerName?: string; orderId?: string; remark?: string; fileIds?: string[]
}
export interface OrderOption { orderId: string; orderNo: string; currency: string }
export interface BankImportResult {
  created: number; receiptIds: string[]
  unmatched: { rowNo: number; date?: string; amount?: string; payerName?: string; bankRefNo?: string; message: string }[]
}

export const receiptApi = {
  page: (params: PageParam & Record<string, unknown>) => http.get<PageResult<ReceiptRow>>('/finance/receipts', params),
  get: (id: string) => http.get<ReceiptDetail>(`/finance/receipts/${id}`),
  orderOptions: (customerId: string) => http.get<OrderOption[]>('/finance/receipts/order-options', { customerId }),
  create: (data: ReceiptSave) => http.post<string>('/finance/receipts', data),
  update: (id: string, data: ReceiptSave) => http.put<void>(`/finance/receipts/${id}`, data),
  remove: (id: string) => http.delete<void>(`/finance/receipts/${id}`),
  void: (id: string, reason: string) => http.post<void>(`/finance/receipts/${id}/void`, { reason }),
  confirm: (id: string) => http.post<void>(`/finance/receipts/${id}/confirm`),
  unconfirm: (id: string, reason: string) => http.post<void>(`/finance/receipts/${id}/unconfirm`, { reason }),
  importBank: (file: File, bankAccountId: string) => upload<BankImportResult>('/finance/receipts/import-bank', file, { bankAccountId })
}

// ==================== 应付 ====================

export interface ApRow {
  id: string; docNo: string; apType: string; supplierId: string; supplierName?: string; statementId?: string; statementNo?: string; bizDate: string
  currency: string; exchangeRate: string; totalAmount: string; totalAmountBase: string; invoicedAmount: string; requestedAmount: string; verifiedAmount: string
  unpaidAmount: string; requestableAmount: string; dueDate?: string; overdueDays: number; status: string; ownerName?: string; description?: string; createdAt: string
}
export interface ApLine {
  id: string; lineNo: number; lineType: string; sourceNo?: string; orderNo?: string; materialId?: string; materialCode?: string; materialName?: string
  description?: string; qty?: string; priceInclTax?: string; taxRate: string; amount: string; taxAmount: string; totalAmount: string; invoicedQty: string
  invoicedAmount: string
}
export interface ApDetail {
  header: ApRow; amount: string; taxAmount: string; confirmedAt?: string; voidReason?: string; voucherId?: string; remark?: string; lines: ApLine[]
  invoices: { invoiceId: string; docNo: string; invoiceNo: string; invoiceDate: string; matchStatus: string; qty: string; totalAmount: string; status: string }[]
  requests: { requestId: string; docNo: string; status: string; amount: string; paidAmount: string; planPayDate: string }[]
  verifications: Verification[]
}
export interface OtherApSave {
  supplierId?: string; apType: string; currency: string; exchangeRate?: string; bizDate: string; dueDate?: string; description: string; remark?: string
  fileIds?: string[]; lines: OtherLine[]
}
export interface PayableCandidate {
  payableId: string; docNo: string; statementNo?: string; bizDate: string; dueDate?: string; overdueDays: number; currency: string; totalAmount: string
  invoicedAmount: string; requestedAmount: string; verifiedAmount: string; requestableAmount: string; uninvoiced: boolean
}

export const apApi = {
  page: (params: PageParam & Record<string, unknown>) => http.get<PageResult<ApRow>>('/finance/payables', params),
  summary: (params: Record<string, unknown>) => http.get<{ totalAmountBase: string; unpaidBase: string }>('/finance/payables/summary', params),
  get: (id: string) => http.get<ApDetail>(`/finance/payables/${id}`),
  candidates: (supplierId: string, currency?: string) => http.get<PayableCandidate[]>('/finance/payables/payable-candidates', { supplierId, currency }),
  confirm: (id: string) => http.post<void>(`/finance/payables/${id}/confirm`),
  unconfirm: (id: string, reason: string) => http.post<void>(`/finance/payables/${id}/unconfirm`, { reason }),
  void: (id: string, reason: string) => http.post<void>(`/finance/payables/${id}/void`, { reason }),
  createOther: (data: OtherApSave) => http.post<string>('/finance/payables/other', data),
  updateOther: (id: string, data: OtherApSave) => http.put<void>(`/finance/payables/other/${id}`, data),
  submit: (id: string) => http.post<SubmitResult>(`/finance/payables/${id}/submit`),
  withdraw: (id: string) => http.post<void>(`/finance/payables/${id}/withdraw`)
}

export interface PurchaseInvoiceRow {
  id: string; docNo: string; supplierId: string; supplierName?: string; invoiceType: string; invoiceNo: string; invoiceCode?: string; invoiceDate: string
  currency: string; amount: string; taxAmount: string; totalAmount: string; matchStatus: string; deductionStatus?: string; certifiedPeriod?: string
  status: string; remark?: string; createdByName?: string; createdAt: string
}
export interface PurchaseInvoiceLine {
  id: string; lineNo: number; payableId: string; payableNo?: string; payableLineId: string; sourceNo?: string; orderNo?: string; materialId?: string
  materialCode?: string; materialName?: string; qty?: string; invoicePrice?: string; apPrice?: string; amount: string; taxAmount: string; totalAmount: string
  apAmount: string; priceDiffPct: string; overTolerance: boolean; diffReason?: string
}
export interface PurchaseInvoiceDetail {
  header: PurchaseInvoiceRow; diffConfirmedByName?: string; diffConfirmedAt?: string; voidReason?: string; lines: PurchaseInvoiceLine[]
}
export interface UninvoicedApLine {
  payableId: string; payableNo: string; payableLineId: string; statementNo?: string; sourceNo?: string; orderNo?: string; bizDate: string; currency: string
  lineType: string; materialId?: string; materialCode?: string; materialName?: string; description?: string; qty?: string; priceInclTax?: string
  taxRate: string; apPrice?: string; totalAmount: string; invoicedQty: string; uninvoicedQty?: string; uninvoicedAmount: string
}
export interface PurchaseInvoiceSave {
  supplierId: string; invoiceType: string; invoiceNo: string; invoiceCode?: string; invoiceDate: string; totalAmount: string; taxAmount: string
  remark?: string; fileIds?: string[]; lines: { payableLineId: string; qty?: string; invoicePrice?: string; diffReason?: string }[]
}

export const purchaseInvoiceApi = {
  page: (params: PageParam & Record<string, unknown>) => http.get<PageResult<PurchaseInvoiceRow>>('/finance/purchase-invoices', params),
  get: (id: string) => http.get<PurchaseInvoiceDetail>(`/finance/purchase-invoices/${id}`),
  uninvoiced: (supplierId: string, payableIds?: string[]) =>
    http.get<UninvoicedApLine[]>('/finance/payable-lines/uninvoiced', { supplierId, payableIds: payableIds?.join(',') }),
  register: (data: PurchaseInvoiceSave) => http.post<string>('/finance/purchase-invoices', data),
  confirmDiff: (id: string) => http.post<void>(`/finance/purchase-invoices/${id}/confirm-diff`),
  certify: (id: string, period: string) => http.post<void>(`/finance/purchase-invoices/${id}/certify`, { period }),
  void: (id: string, reason: string) => http.post<void>(`/finance/purchase-invoices/${id}/void`, { reason })
}

// ==================== 付款 ====================

export interface RequestRow {
  id: string; docNo: string; requestType: string; supplierId: string; supplierName?: string; currency: string; amount: string; amountBase: string
  paidAmount: string; unpaidAmount: string; planPayDate: string; orderId?: string; orderNo?: string; status: string; uninvoicedWarning: boolean
  ownerName?: string; reason?: string; createdAt: string
}
export interface RequestDetail {
  header: RequestRow; supplierBankId?: string; supplierBankText?: string; approvedAt?: string; remark?: string
  lines: { id: string; lineNo: number; payableId: string; payableNo: string; statementNo?: string; dueDate?: string; payableTotal?: string; amount: string; paidAmount: string }[]
  payments: { id: string; docNo: string; payDate: string; amount: string; status: string }[]
}
export interface RequestSave {
  supplierId?: string; requestType: string; currency: string; planPayDate: string; supplierBankId?: string; orderId?: string; amount?: string
  reason?: string; remark?: string; fileIds?: string[]; lines: { payableId: string; amount: string }[]
}
export interface RequestResult { id: string; status: string; warnings: string[] }
export interface SupplierBankOption { id: string; bankName: string; accountName?: string; accountNo: string; swift?: string; currency?: string; isDefault: boolean }
export interface PurchaseOrderOption { orderId: string; orderNo: string; orderDate: string; currency: string; totalAmount: string; prepaid: string; available: string }

export const requestApi = {
  page: (params: PageParam & Record<string, unknown>) => http.get<PageResult<RequestRow>>('/finance/payment-requests', params),
  get: (id: string) => http.get<RequestDetail>(`/finance/payment-requests/${id}`),
  supplierBanks: (supplierId: string) => http.get<SupplierBankOption[]>('/finance/payment-requests/supplier-banks', { supplierId }),
  orderOptions: (supplierId: string) => http.get<PurchaseOrderOption[]>('/finance/payment-requests/order-options', { supplierId }),
  create: (data: RequestSave) => http.post<RequestResult>('/finance/payment-requests', data),
  update: (id: string, data: RequestSave) => http.put<RequestResult>(`/finance/payment-requests/${id}`, data),
  submit: (id: string) => http.post<RequestResult>(`/finance/payment-requests/${id}/submit`),
  withdraw: (id: string) => http.post<void>(`/finance/payment-requests/${id}/withdraw`),
  close: (id: string, reason: string) => http.post<void>(`/finance/payment-requests/${id}/close`, { reason }),
  void: (id: string, reason: string) => http.post<void>(`/finance/payment-requests/${id}/void`, { reason })
}

export interface PaymentRow {
  id: string; docNo: string; requestId: string; requestNo: string; requestType: string; supplierId: string; supplierName?: string; bankAccountId: string
  bankAccountName?: string; settlementMethod: string; payDate: string; currency: string; exchangeRate: string; amount: string; bankFee: string
  amountBase: string; allocatedAmount: string; bankRefNo?: string; status: string; ownerName?: string; remark?: string; createdAt: string
}
export interface PaymentDetail { header: PaymentRow; confirmedAt?: string; voucherId?: string; verifications: Verification[] }
export interface PaymentSave {
  requestId?: string; bankAccountId?: string; settlementMethod: string; payDate: string; exchangeRate?: string; amount?: string; bankFee?: string
  bankRefNo?: string; remark?: string; fileIds?: string[]
}

export const paymentApi = {
  page: (params: PageParam & Record<string, unknown>) => http.get<PageResult<PaymentRow>>('/finance/payments', params),
  get: (id: string) => http.get<PaymentDetail>(`/finance/payments/${id}`),
  create: (data: PaymentSave) => http.post<string>('/finance/payments', data),
  update: (id: string, data: PaymentSave) => http.put<void>(`/finance/payments/${id}`, data),
  remove: (id: string) => http.delete<void>(`/finance/payments/${id}`),
  void: (id: string, reason: string) => http.post<void>(`/finance/payments/${id}/void`, { reason }),
  confirm: (id: string) => http.post<void>(`/finance/payments/${id}/confirm`),
  unconfirm: (id: string, reason: string) => http.post<void>(`/finance/payments/${id}/unconfirm`, { reason })
}

// ==================== 报表 ====================

export interface AgingRow {
  partnerId: string; partnerCode?: string; partnerName?: string; currency: string; rate: string; total: string; notDue: string; d1to30: string
  d31to60: string; d61to90: string; d91to180: string; over180: string; totalBase: string; overdueBase: string
}
export interface AgingReport { asOf: string; baseCurrency: string; rows: AgingRow[]; totalBase: string; overdueBase: string }
export interface AgingDoc {
  id: string; docNo: string; docType: string; sourceNo?: string; bizDate: string; dueDate: string; overdueDays: number; bucket: string; currency: string
  totalAmount: string; openAmount: string
}
export interface StatementLine { date: string; docType: string; docId: string; docNo: string; summary?: string; currency: string; debit: string; credit: string; balance: string }
export interface Statement {
  partnerId: string; partnerCode?: string; partnerName?: string; partnerNameEn?: string; currency: string; dateFrom: string; dateTo: string; opening: string
  debit: string; credit: string; verified: string; closing: string; ledgerBalance: string; mismatch: boolean; lines: StatementLine[]
}

export const reportApi = {
  arAging: (params: { asOf?: string; customerId?: string; currency?: string }) => http.get<AgingReport>('/finance/reports/ar-aging', params),
  arAgingDocs: (params: { asOf?: string; customerId: string; currency?: string }) => http.get<AgingDoc[]>('/finance/reports/ar-aging/docs', params),
  apAging: (params: { asOf?: string; supplierId?: string; currency?: string }) => http.get<AgingReport>('/finance/reports/ap-aging', params),
  apAgingDocs: (params: { asOf?: string; supplierId: string; currency?: string }) => http.get<AgingDoc[]>('/finance/reports/ap-aging/docs', params),
  customerStatement: (params: { customerId: string; currency?: string; dateFrom?: string; dateTo?: string }) =>
    http.get<Statement>('/finance/reports/customer-statement', params),
  supplierStatement: (params: { supplierId: string; currency?: string; dateFrom?: string; dateTo?: string }) =>
    http.get<Statement>('/finance/reports/supplier-statement', params)
}
