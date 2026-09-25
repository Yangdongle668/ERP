import { http, type PageParam, type PageResult } from '@/api/http'
import type { StatusMap } from '@/components'
import { optionsOf } from './common'

/** 供应商（需求 07-01） */
export type SupplierStatus = 'POTENTIAL' | 'PENDING' | 'QUALIFIED' | 'SUSPENDED' | 'ELIMINATED'

export const SUPPLIER_STATUS: StatusMap = {
  POTENTIAL: { label: '潜在', type: 'info' },
  PENDING: { label: '准入审批中', type: 'warning' },
  QUALIFIED: { label: '合格', type: 'success' },
  SUSPENDED: { label: '暂停', type: 'warning' },
  ELIMINATED: { label: '淘汰', type: 'danger' }
}
export const SUPPLIER_STATUS_OPTIONS = optionsOf(SUPPLIER_STATUS)

export const SUPPLY_STATUS: StatusMap = {
  TRIAL: { label: '试供', type: 'warning' },
  QUALIFIED: { label: '合格', type: 'success' },
  DISABLED: { label: '停供', type: 'info', plain: true }
}
export const SUPPLY_STATUS_OPTIONS = optionsOf(SUPPLY_STATUS)

export const CERT_STATUS: StatusMap = {
  VALID: { label: '有效', type: 'success' },
  EXPIRING: { label: '即将到期', type: 'warning' },
  EXPIRED: { label: '已过期', type: 'danger' }
}

export const INVOICE_TYPE_OPTIONS = [
  { value: 'SPECIAL_VAT', label: '增值税专用发票' },
  { value: 'NORMAL_VAT', label: '增值税普通发票' },
  { value: 'NONE', label: '不开票' }
]

export const CERT_EXPIRY_OPTIONS = [
  { value: 'EXPIRING', label: '即将到期' },
  { value: 'EXPIRED', label: '已过期' }
]

export interface SupplierRow {
  id: string
  code: string
  shortName: string
  name: string
  supplierType: string
  level?: string
  country?: string
  buyerId?: string
  buyerName?: string
  currency: string
  paymentTermId?: string
  paymentTermName?: string
  primaryContact?: string
  primaryPhone?: string
  certExpired: boolean
  certExpiring: boolean
  status: SupplierStatus
  qualifiedAt?: string
  updatedAt: string
}

export interface SupplierQuery extends PageParam {
  keyword?: string
  statuses?: string
  level?: string
  supplierType?: string
  buyerId?: string
  certExpiry?: string
  materialId?: string
}

export interface Contact {
  id?: string
  name?: string
  title?: string
  role?: string
  phone?: string
  mobile?: string
  email?: string
  isPrimary?: boolean
}

export interface Bank {
  id?: string
  bankName?: string
  accountName?: string
  accountNo?: string
  swift?: string
  currency?: string
  isDefault?: boolean
}

export interface Cert {
  id?: string
  certType?: string
  certNo?: string
  issueDate?: string
  expireDate?: string
  fileId?: string
  fileName?: string
  remark?: string
  certStatus?: string
}

export interface SupplierMaterial {
  id?: string
  supplierId?: string
  supplierCode?: string
  supplierName?: string
  supplierStatus?: SupplierStatus
  materialId?: string
  materialCode?: string
  materialName?: string
  materialSpec?: string
  baseUom?: string
  supplierPartNo?: string
  supplyStatus?: string
  isDefault?: boolean
  leadTimeDays?: number
  moq?: string
  mpq?: string
  quotaPct?: string
  approvedAt?: string
  remark?: string
}

export interface SupplierDetail {
  id: string
  code: string
  name: string
  nameEn?: string
  shortName: string
  supplierType: string
  level?: string
  status: SupplierStatus
  country?: string
  province?: string
  city?: string
  address?: string
  taxNo?: string
  phone?: string
  email?: string
  website?: string
  buyerId?: string
  buyerName?: string
  deptId?: string
  deptName?: string
  currency: string
  paymentTermId?: string
  paymentTermName?: string
  tradeTerm?: string
  purchaseTaxRate?: string
  invoiceType?: string
  leadTimeDays?: number
  qualifiedAt?: string
  suspendReason?: string
  remark?: string
  contacts: Contact[]
  banks: Bank[]
  certs: Cert[]
  materials: SupplierMaterial[]
  openOrderCount: number
  createdByName?: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface SupplierSave {
  code?: string
  name: string
  nameEn?: string
  shortName: string
  supplierType: string
  level?: string
  country?: string
  province?: string
  city?: string
  address?: string
  taxNo?: string
  phone?: string
  email?: string
  website?: string
  buyerId?: string
  deptId?: string
  currency: string
  paymentTermId?: string
  tradeTerm?: string
  purchaseTaxRate?: string
  invoiceType?: string
  leadTimeDays?: number
  remark?: string
  contacts: Contact[]
  banks: Bank[]
  certs: Cert[]
  materials: SupplierMaterial[]
  fileIds?: string[]
  version?: number
}

export interface StatusResult {
  status: SupplierStatus
  openOrderCount: number
}

export interface QualityLot {
  receiptId: string
  receiptNo: string
  arrivalDate?: string
  materialCode: string
  materialName: string
  qty: string
  inspectStatus: string
  qualifiedQty: string
  rejectedQty: string
  inspectionNo?: string
}

export interface QualitySummary {
  lotCount: number
  passCount: number
  passRate?: string
  lots: QualityLot[]
}

const BASE = '/purchase/suppliers'

export const supplierApi = {
  page: (q: SupplierQuery) => http.get<PageResult<SupplierRow>>(BASE, q),
  get: (id: string) => http.get<SupplierDetail>(`${BASE}/${id}`),
  create: (data: SupplierSave) => http.post<string>(BASE, data),
  update: (id: string, data: SupplierSave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  qualify: (id: string) => http.post<SupplierStatus>(`${BASE}/${id}/qualify`),
  suspend: (id: string, reason: string) => http.post<StatusResult>(`${BASE}/${id}/suspend`, { reason }),
  resume: (id: string) => http.post<StatusResult>(`${BASE}/${id}/resume`),
  eliminate: (id: string, reason: string) => http.post<StatusResult>(`${BASE}/${id}/eliminate`, { reason }),
  quality: (id: string) => http.get<QualitySummary>(`${BASE}/${id}/quality`),
  materials: (id: string) => http.get<SupplierMaterial[]>(`${BASE}/${id}/materials`),
  addMaterial: (id: string, data: SupplierMaterial) => http.post<string>(`${BASE}/${id}/materials`, data),
  updateMaterial: (id: string, lineId: string, data: SupplierMaterial) => http.put<string>(`${BASE}/${id}/materials/${lineId}`, data),
  removeMaterial: (id: string, lineId: string) => http.delete<void>(`${BASE}/${id}/materials/${lineId}`),
  suppliersOfMaterial: (materialId: string) => http.get<SupplierMaterial[]>('/purchase/supplier-materials', { materialId })
}
