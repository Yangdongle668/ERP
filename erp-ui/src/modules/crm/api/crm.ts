import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'

/** CRM（需求 03）：客户、联系人、客户料号、信用、跟进、商机 */

export const labelOf = (options: { value: string; label: string }[], v?: string) => options.find((o) => o.value === v)?.label ?? v ?? ''

// ==================== 客户 ====================

export type CustomerStatus = 'PROSPECT' | 'PENDING' | 'ACTIVE' | 'DISABLED' | 'BLACKLIST'
/** 潜在灰、审批中橙、正式绿、停用灰 plain、黑名单红 */
export const CUSTOMER_STATUS: StatusMap = {
  PROSPECT: { label: '潜在', type: 'info' },
  PENDING: { label: '审批中', type: 'warning' },
  ACTIVE: { label: '正式', type: 'success' },
  DISABLED: { label: '停用', type: 'info', plain: true },
  BLACKLIST: { label: '黑名单', type: 'danger' }
}
export const CUSTOMER_STATUS_OPTIONS = Object.entries(CUSTOMER_STATUS).map(([value, s]) => ({ value, label: s.label }))
export const GENDER_OPTIONS = [
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
  { value: 'UNKNOWN', label: '未知' }
]
export const ADDRESS_TYPE_OPTIONS = [
  { value: 'SHIP_TO', label: '收货' },
  { value: 'BILL_TO', label: '开票' },
  { value: 'NOTIFY', label: '通知方' }
]
export const CREDIT_CONTROL_OPTIONS = [
  { value: 'DEFAULT', label: '按系统参数' },
  { value: 'NONE', label: '不控制' },
  { value: 'WARN', label: '警告' },
  { value: 'BLOCK', label: '阻止' }
]

export interface CustomerRow {
  id: string
  code: string
  shortName: string
  name: string
  nameEn?: string
  country: string
  customerType: string
  level: string
  ownerId: string
  ownerName?: string
  primaryContact?: string
  primaryContactEmail?: string
  currency: string
  creditLimit?: string
  creditVisible: boolean
  lastOrderDate?: string
  customerStatus: CustomerStatus
  createdAt: string
  version: number
}

export interface Contact {
  id?: string
  name: string
  gender?: string
  title?: string
  role?: string
  email?: string
  phone?: string
  mobile?: string
  im?: string
  birthday?: string
  isPrimary?: boolean
  status?: string
  remark?: string
}

export interface Address {
  id?: string
  addressType: string
  companyName: string
  contactName?: string
  phone?: string
  country: string
  province?: string
  city?: string
  zip?: string
  addressLine: string
  isDefault?: boolean
  remark?: string
}

export interface Bank {
  id?: string
  bankName: string
  accountName: string
  accountNo: string
  swift?: string
  currency?: string
  remark?: string
}

export interface CreditSummary {
  creditLimit?: string
  creditDays?: number
  creditControl: string
  receivableBalance: string
  overdueAmount: string
  openOrderAmount: string
  used: string
  available?: string
  usagePct?: string
}

export interface CustomerDetail {
  id: string
  code: string
  name: string
  nameEn?: string
  shortName: string
  customerType: string
  level: string
  customerStatus: CustomerStatus
  isForeign: boolean
  country: string
  province?: string
  city?: string
  address?: string
  industry?: string
  source?: string
  website?: string
  phone?: string
  email?: string
  taxNo?: string
  ownerId: string
  ownerName?: string
  deptId?: string
  deptName?: string
  currency: string
  paymentTermId?: string
  paymentTermName?: string
  tradeTerm?: string
  salesTaxRate: string
  blacklistReason?: string
  firstOrderDate?: string
  lastOrderDate?: string
  remark?: string
  contacts: Contact[]
  addresses: Address[]
  banks: Bank[]
  credit?: CreditSummary
  createdByName?: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface CustomerSave {
  code?: string
  name: string
  nameEn?: string
  shortName?: string
  customerType?: string
  level?: string
  country?: string
  isForeign?: boolean
  province?: string
  city?: string
  address?: string
  industry?: string
  source?: string
  website?: string
  phone?: string
  email?: string
  taxNo?: string
  ownerId?: string
  currency?: string
  paymentTermId?: string
  tradeTerm?: string
  salesTaxRate?: string
  creditDays?: number
  creditControl?: string
  remark?: string
  contacts: Contact[]
  addresses: Address[]
  banks: Bank[]
  fileIds?: string[]
  version?: number
}

export interface CustomerQuery extends PageParam {
  keyword?: string
  statuses?: string
  levels?: string
  countries?: string
  ownerId?: string
  customerType?: string
  source?: string
  lastOrderFrom?: string
  lastOrderTo?: string
  noOrderDays?: number
}

export interface DuplicateRow { id: string; code: string; name: string; country: string; ownerName?: string; matchedBy: 'NAME' | 'TAX_NO' | 'WEBSITE' }
export interface TransferLogRow {
  id: string
  fromOwnerName?: string
  toOwnerName?: string
  transferDocs: boolean
  reason: string
  operatorName?: string
  createdAt: string
}
export interface ContactRow {
  id: string
  customerId: string
  customerCode?: string
  customerShortName?: string
  name: string
  title?: string
  role?: string
  email?: string
  mobile?: string
  phone?: string
  isPrimary: boolean
  status: string
}

export const customerApi = {
  page: (q: CustomerQuery) => http.get<PageResult<CustomerRow>>('/crm/customers', q),
  get: (id: string) => http.get<CustomerDetail>(`/crm/customers/${id}`),
  create: (data: CustomerSave) => http.post<{ id: string; warnings: string[] }>('/crm/customers', data),
  update: (id: string, data: CustomerSave) => http.put<{ id: string; warnings: string[] }>(`/crm/customers/${id}`, data),
  remove: (id: string) => http.delete<void>(`/crm/customers/${id}`),
  duplicateCheck: (data: { id?: string; name?: string; country?: string; taxNo?: string; website?: string }) =>
    http.post<DuplicateRow[]>('/crm/customers/duplicate-check', data),
  activate: (id: string) => http.post<{ customerStatus: CustomerStatus }>(`/crm/customers/${id}/activate`),
  disable: (id: string, reason?: string) => http.post<void>(`/crm/customers/${id}/disable`, { reason }),
  enable: (id: string) => http.post<void>(`/crm/customers/${id}/enable`),
  blacklist: (id: string, reason: string) => http.post<void>(`/crm/customers/${id}/blacklist`, { reason }),
  unblacklist: (id: string, reason?: string) => http.post<void>(`/crm/customers/${id}/unblacklist`, { reason }),
  transfer: (data: { customerIds: string[]; newOwnerId: string; transferDocs: boolean; reason: string }) => http.post<number>('/crm/customers/transfer', data),
  transferLogs: (id: string) => http.get<TransferLogRow[]>(`/crm/customers/${id}/transfer-logs`),
  contacts: (q: PageParam & { name?: string; email?: string; phone?: string; customerId?: string; role?: string; status?: string }) =>
    http.get<PageResult<ContactRow>>('/crm/contacts', q)
}

// ==================== 客户料号 ====================

export interface PartRow {
  id: string
  customerId: string
  customerCode?: string
  customerShortName?: string
  customerPartNo: string
  customerPartName?: string
  customerPartSpec?: string
  customerRevision?: string
  materialId: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  updatedAt: string
  version: number
}
export interface PartSave {
  customerId?: string
  customerPartNo: string
  customerPartName?: string
  customerPartSpec?: string
  customerRevision?: string
  materialId?: string
  remark?: string
  version?: number
}
export const partApi = {
  page: (q: PageParam & { customerId?: string; customerPartNo?: string; materialId?: string; status?: string }) =>
    http.get<PageResult<PartRow>>('/crm/customer-parts', q),
  create: (data: PartSave) => http.post<string>('/crm/customer-parts', data),
  update: (id: string, data: PartSave) => http.put<void>(`/crm/customer-parts/${id}`, data),
  enable: (id: string) => http.post<void>(`/crm/customer-parts/${id}/enable`),
  disable: (id: string) => http.post<void>(`/crm/customer-parts/${id}/disable`),
  remove: (id: string) => http.delete<void>(`/crm/customer-parts/${id}`)
}

// ==================== 信用 ====================

export interface CreditRow {
  customerId: string
  customerCode: string
  customerShortName: string
  ownerId: string
  ownerName?: string
  control: string
  effectiveControl: string
  creditLimit?: string
  receivableBalance: string
  overdueAmount: string
  openOrderAmount: string
  used: string
  available?: string
  usagePct?: string
  creditDays?: number
  refreshedAt?: string
  pendingChangeId?: string
  pendingChangeNo?: string
}
export interface ChangeRow {
  id: string
  docNo: string
  docDate: string
  oldLimit?: string
  newLimit: string
  oldDays?: number
  newDays?: number
  oldControl?: string
  newControl?: string
  expireDate?: string
  restored: boolean
  reason: string
  status: string
  createdByName?: string
  createdAt: string
  approvedAt?: string
}
export const CHANGE_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_APPROVAL: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已生效', type: 'success' },
  VOIDED: { label: '已作废', type: 'danger', plain: true }
}
export const creditApi = {
  page: (q: PageParam & { customerId?: string; ownerId?: string; usageAtLeast?: number; overdueOnly?: boolean }) =>
    http.get<PageResult<CreditRow>>('/crm/credits', q),
  refresh: (customerIds: string[]) => http.post<void>('/crm/credits/refresh', customerIds),
  changes: (customerId: string) => http.get<ChangeRow[]>('/crm/credit-changes', { customerId }),
  create: (data: { customerId: string; newLimit: string; newDays?: number; newControl?: string; expireDate?: string; reason: string }) =>
    http.post<ChangeRow>('/crm/credit-changes', data),
  submit: (id: string) => http.post<void>(`/crm/credit-changes/${id}/submit`),
  void: (id: string) => http.post<void>(`/crm/credit-changes/${id}/void`)
}

// ==================== 跟进 ====================

export interface FollowupRow {
  id: string
  customerId: string
  customerCode?: string
  customerShortName?: string
  contactId?: string
  contactName?: string
  opportunityId?: string
  opportunityName?: string
  followupType: string
  followupAt: string
  subject: string
  content: string
  nextFollowupAt?: string
  nextPlan?: string
  nextDue: boolean
  ownerId: string
  ownerName?: string
  fileCount: number
  editable: boolean
  version: number
}
export interface FollowupSave {
  customerId?: string
  contactId?: string
  opportunityId?: string
  followupType: string
  followupAt: string
  subject: string
  content: string
  nextFollowupAt?: string
  nextPlan?: string
  fileIds?: string[]
  version?: number
}
export interface FollowupQuery extends PageParam {
  customerId?: string
  ownerId?: string
  followupType?: string
  dateFrom?: string
  dateTo?: string
  opportunityId?: string
  pendingOnly?: boolean
}
export const followupApi = {
  page: (q: FollowupQuery) => http.get<PageResult<FollowupRow>>('/crm/followups', q),
  create: (data: FollowupSave) => http.post<string>('/crm/followups', data),
  update: (id: string, data: FollowupSave) => http.put<void>(`/crm/followups/${id}`, data),
  remove: (id: string) => http.delete<void>(`/crm/followups/${id}`)
}

// ==================== 商机 ====================

export const STAGE_OPTIONS = [
  { value: 'CONTACT', label: '初步接触', rate: '0.1' },
  { value: 'REQUIREMENT', label: '需求确认', rate: '0.3' },
  { value: 'QUOTATION', label: '报价', rate: '0.5' },
  { value: 'NEGOTIATION', label: '谈判', rate: '0.7' }
]
export const STAGE_LABELS: Record<string, string> = { ...Object.fromEntries(STAGE_OPTIONS.map((s) => [s.value, s.label])), WON: '赢单', LOST: '输单' }
export const OPP_STATUS: StatusMap = {
  OPEN: { label: '进行中', type: 'primary' },
  WON: { label: '赢单', type: 'success' },
  LOST: { label: '输单', type: 'danger' },
  SHELVED: { label: '搁置', type: 'info', plain: true }
}
export const OPP_STATUS_OPTIONS = Object.entries(OPP_STATUS).map(([value, s]) => ({ value, label: s.label }))

export interface OppRow {
  id: string
  code: string
  name: string
  customerId: string
  customerCode?: string
  customerShortName?: string
  contactId?: string
  contactName?: string
  stage: string
  status: string
  amount: string
  currency: string
  winRate: string
  expectedDate: string
  overdue: boolean
  products?: string
  competitor?: string
  ownerId: string
  ownerName?: string
  lastFollowupAt?: string
  lostReason?: string
  lostRemark?: string
  wonOrderNo?: string
  remark?: string
  closedAt?: string
  version: number
}
export interface OppSave {
  name: string
  customerId?: string
  contactId?: string
  stage?: string
  amount?: string
  currency?: string
  winRate?: string
  expectedDate?: string
  products?: string
  competitor?: string
  ownerId?: string
  remark?: string
  version?: number
}
export interface OppQuery extends PageParam {
  keyword?: string
  customerId?: string
  ownerId?: string
  stage?: string
  statuses?: string
  expectedFrom?: string
  expectedTo?: string
}
export interface Funnel {
  baseCurrency: string
  stages: { stage: string; count: number; amountBase: string; weightedBase: string }[]
  totalCount: number
  totalAmountBase: string
  totalWeightedBase: string
}
export const oppApi = {
  page: (q: OppQuery) => http.get<PageResult<OppRow>>('/crm/opportunities', q),
  funnel: (q: Omit<OppQuery, 'pageNo' | 'pageSize'>) => http.get<Funnel>('/crm/opportunities/funnel', q),
  get: (id: string) => http.get<OppRow>(`/crm/opportunities/${id}`),
  create: (data: OppSave) => http.post<string>('/crm/opportunities', data),
  update: (id: string, data: OppSave) => http.put<void>(`/crm/opportunities/${id}`, data),
  remove: (id: string) => http.delete<void>(`/crm/opportunities/${id}`),
  stage: (id: string, stage: string) => http.post<void>(`/crm/opportunities/${id}/stage`, { stage }),
  win: (id: string, orderNo?: string) => http.post<void>(`/crm/opportunities/${id}/win`, { orderNo }),
  lose: (id: string, lostReason: string, remark?: string) => http.post<void>(`/crm/opportunities/${id}/lose`, { lostReason, remark }),
  shelve: (id: string, remark?: string) => http.post<void>(`/crm/opportunities/${id}/shelve`, { remark }),
  resume: (id: string) => http.post<void>(`/crm/opportunities/${id}/resume`)
}
