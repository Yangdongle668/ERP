import { http, type PageParam, type PageResult } from '@/api/http'

export type DataScope = 'ALL' | 'COMPANY' | 'DEPT_AND_CHILD' | 'DEPT' | 'SELF' | 'CUSTOM'

export const DATA_SCOPE_OPTIONS: { value: DataScope; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'COMPANY', label: '本公司' },
  { value: 'DEPT_AND_CHILD', label: '本部门及下级' },
  { value: 'DEPT', label: '本部门' },
  { value: 'SELF', label: '仅本人' },
  { value: 'CUSTOM', label: '自定义' }
]

export interface RoleRow {
  id: string
  code: string
  name: string
  dataScope: DataScope
  builtin: boolean
  userCount: number
  sort: number
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  version: number
}

export interface RoleDetail extends Omit<RoleRow, 'userCount'> {
  customDeptIds: string[]
}

export interface RoleSave {
  code: string
  name: string
  dataScope: DataScope
  customDeptIds: string[]
  sort: number
  remark?: string
  version?: number
}

export interface RoleSimple {
  id: string
  code: string
  name: string
}

export interface PermissionNode {
  code: string
  name: string
  type: 'MENU' | 'BUTTON' | 'FIELD'
  dependsOn: string[]
}

export interface PermissionGroup {
  code: string
  name: string
  permissions: PermissionNode[]
}

export interface PermissionModule {
  code: string
  name: string
  groups: PermissionGroup[]
}

export interface RoleMember {
  userId: string
  username: string
  realName: string
  deptName?: string
  status: string
}

export const roleApi = {
  page: (q: PageParam & { keyword?: string; status?: string }) => http.get<PageResult<RoleRow>>('/system/roles', q),
  simple: () => http.get<RoleSimple[]>('/system/roles/simple'),
  get: (id: string) => http.get<RoleDetail>(`/system/roles/${id}`),
  create: (data: RoleSave) => http.post<string>('/system/roles', data),
  update: (id: string, data: RoleSave) => http.put<void>(`/system/roles/${id}`, data),
  enable: (id: string) => http.post<void>(`/system/roles/${id}/enable`),
  disable: (id: string) => http.post<void>(`/system/roles/${id}/disable`),
  remove: (id: string) => http.delete<void>(`/system/roles/${id}`),
  copy: (id: string) => http.post<string>(`/system/roles/${id}/copy`),
  permissionTree: () => http.get<PermissionModule[]>('/system/permissions/tree'),
  permissions: (id: string) => http.get<string[]>(`/system/roles/${id}/permissions`),
  savePermissions: (id: string, permissions: string[]) => http.put<void>(`/system/roles/${id}/permissions`, { permissions }),
  members: (id: string) => http.get<RoleMember[]>(`/system/roles/${id}/users`),
  addMembers: (id: string, userIds: string[]) => http.post<void>(`/system/roles/${id}/users`, { userIds }),
  removeMember: (id: string, userId: string) => http.delete<void>(`/system/roles/${id}/users/${userId}`)
}
