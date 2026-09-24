import { http } from './http'

/**
 * 公共组件和布局使用的系统管理接口（登录即可访问的精简接口）。
 * 系统管理页面自身的维护接口在 src/modules/system/api 中。
 */

export type TagType = 'DEFAULT' | 'PRIMARY' | 'SUCCESS' | 'WARNING' | 'DANGER' | 'INFO'

export interface DictItem {
  value: string
  label: string
  labelEn?: string
  tagType: TagType
  sort: number
  isDefault: boolean
  enabled: boolean
}

export interface DictBundle {
  version: number
  types: { code: string; name: string; items: DictItem[] }[]
}

export interface OrgNode {
  id: string
  parentId?: string
  name: string
  code: string
  orgType: 'COMPANY' | 'DEPT'
  children?: OrgNode[]
}

export interface UserSimple {
  id: string
  username: string
  realName: string
  employeeNo?: string
  deptName?: string
}

export interface UomSimple {
  code: string
  name: string
  nameEn?: string
  category: string
  precision: number
}

export interface CurrencySimple {
  code: string
  name: string
  symbol?: string
  amountPrecision: number
  base: boolean
}

export interface Country {
  code: string
  nameCn: string
  nameEn: string
}

export interface RateLookup {
  rate: string
  effectiveDate: string
}

export interface PaymentTermSimple {
  id: string
  code: string
  name: string
  nameEn?: string
  usage: 'SALES' | 'PURCHASE' | 'BOTH'
}

export interface FileInfo {
  id: string
  fileName: string
  fileSize: number
  contentType?: string
  category?: string
  createdByName?: string
  createdAt: string
}

export interface DocLog {
  id: string
  action: string
  actionName: string
  fromStatus?: string
  toStatus?: string
  reason?: string
  operatorName: string
  createdAt: string
}

export interface PublicParams {
  systemName: string
  captchaAfterFails: number
  idleTimeoutMinutes: number
}

export const systemCommonApi = {
  dictAll: () => http.get<DictBundle>('/system/dicts/all', undefined, { silent: true }),
  dictVersion: () => http.get<number>('/system/dicts/version', undefined, { silent: true }),
  orgSimpleTree: () => http.get<OrgNode[]>('/system/orgs/simple-tree'),
  userSearch: (keyword: string, ids?: string[]) => http.get<UserSimple[]>('/system/users/simple', { keyword, ids: ids?.join(',') }),
  uomSimple: () => http.get<UomSimple[]>('/system/uoms/simple'),
  currencySimple: () => http.get<CurrencySimple[]>('/system/currencies/simple'),
  rateLookup: (currency: string, date: string, type = 'DAILY') =>
    http.get<RateLookup>('/system/exchange-rates/lookup', { currency, date, type }, { silent: true }),
  countries: () => http.get<Country[]>('/system/countries'),
  paymentTermSimple: (usage?: 'SALES' | 'PURCHASE') => http.get<PaymentTermSimple[]>('/system/payment-terms/simple', { usage }),
  files: (bizType: string, bizId: string) => http.get<FileInfo[]>('/system/files', { bizType, bizId }),
  deleteFile: (id: string) => http.delete<void>(`/system/files/${id}`),
  docLogs: (bizType: string, bizId: string) => http.get<DocLog[]>('/system/doc-logs', { bizType, bizId }),
  publicParams: () => http.get<PublicParams>('/system/params/public', undefined, { silent: true })
}
