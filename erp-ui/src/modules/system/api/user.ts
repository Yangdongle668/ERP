import { http, type PageParam, type PageResult } from '@/api/http'

export interface UserRow {
  id: string
  username: string
  realName: string
  employeeNo?: string
  deptId: string
  deptName?: string
  roleNames: string[]
  position?: string
  mobile?: string
  status: 'ENABLED' | 'DISABLED'
  locked: boolean
  lastLoginAt?: string
  lastLoginIp?: string
  createdAt: string
  admin: boolean
}

export interface UserQuery extends PageParam {
  deptId?: string
  keyword?: string
  status?: string
  roleId?: string
  position?: string
  lastLogin?: [string, string]
  sortField?: string
  sortOrder?: string
}

export interface UserDetail {
  id: string
  username: string
  realName: string
  employeeNo?: string
  gender: string
  mobile?: string
  email?: string
  position?: string
  language: string
  remark?: string
  deptId: string
  partDeptIds: string[]
  superiorUserId?: string
  superiorName?: string
  roleIds: string[]
  status: string
  admin: boolean
  locked: boolean
  lastLoginAt?: string
  version: number
}

export interface UserSave {
  username: string
  realName: string
  employeeNo?: string
  gender?: string
  mobile?: string
  email?: string
  position?: string
  language?: string
  remark?: string
  deptId?: string
  partDeptIds: string[]
  superiorUserId?: string
  roleIds: string[]
  password?: string
  mustChangePassword?: boolean
  version?: number
}

export interface BatchResult {
  id: string
  name: string
  success: boolean
  message?: string
}

const BASE = '/system/users'

/** 查询参数：日期范围拆为 lastLoginFrom / lastLoginTo */
export function userQueryParams(q: UserQuery) {
  const { lastLogin, ...rest } = q
  return { ...rest, lastLoginFrom: lastLogin?.[0], lastLoginTo: lastLogin?.[1] }
}

export const userApi = {
  page: (q: UserQuery) => http.get<PageResult<UserRow>>(BASE, userQueryParams(q)),
  get: (id: string) => http.get<UserDetail>(`${BASE}/${id}`),
  create: (data: UserSave) => http.post<{ id: string; initPassword: string }>(BASE, data),
  update: (id: string, data: UserSave) => http.put<void>(`${BASE}/${id}`, data),
  enable: (id: string) => http.post<void>(`${BASE}/${id}/enable`),
  disable: (id: string) => http.post<void>(`${BASE}/${id}/disable`),
  batchDisable: (ids: string[]) => http.post<BatchResult[]>(`${BASE}/batch-disable`, ids),
  unlock: (id: string) => http.post<void>(`${BASE}/${id}/unlock`),
  kick: (id: string) => http.post<void>(`${BASE}/${id}/kick`),
  resetPassword: (id: string, data: { mode: 'RANDOM' | 'MANUAL'; password?: string; mustChangePassword: boolean }) =>
    http.post<{ password?: string }>(`${BASE}/${id}/reset-password`, data)
}
