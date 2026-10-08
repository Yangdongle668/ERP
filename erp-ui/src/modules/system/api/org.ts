import { http, upload } from '@/api/http'

export interface OrgNode {
  id: string
  parentId?: string
  code: string
  name: string
  shortName?: string
  orgType: 'COMPANY' | 'DEPT'
  leaderUserId?: string
  leaderName?: string
  phone?: string
  userCount: number
  sort: number
  status: 'ENABLED' | 'DISABLED'
  level: number
  children: OrgNode[]
}

export interface OrgDetail {
  id: string
  parentId?: string
  code: string
  name: string
  shortName?: string
  orgType: 'COMPANY' | 'DEPT'
  leaderUserId?: string
  leaderName?: string
  phone?: string
  address?: string
  nameEn?: string
  addressEn?: string
  taxNo?: string
  logoFileId?: string
  sort: number
  status: string
  remark?: string
  version: number
}

export type OrgSave = Omit<OrgDetail, 'id' | 'status' | 'leaderName' | 'version'> & { version?: number }

const BASE = '/system/orgs'

export const orgApi = {
  /** 上传公司 Logo（PNG / SVG / JPG，≤ 512KB），返回文件 ID；用于系统左上角、登录页、打印单据抬头 */
  uploadLogo: (id: string, file: File) => upload<string>(`/system/orgs/${id}/logo`, file),
  removeLogo: (id: string) => http.delete<void>(`/system/orgs/${id}/logo`),
  tree: (params: { keyword?: string; status?: string }) => http.get<OrgNode[]>(`${BASE}/tree`, params),
  get: (id: string) => http.get<OrgDetail>(`${BASE}/${id}`),
  create: (data: OrgSave) => http.post<string>(BASE, data),
  update: (id: string, data: OrgSave) => http.put<void>(`${BASE}/${id}`, data),
  enable: (id: string) => http.post<void>(`${BASE}/${id}/enable`),
  disable: (id: string) => http.post<void>(`${BASE}/${id}/disable`),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`)
}
