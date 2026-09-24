import { http, type PageParam, type PageResult } from '@/api/http'

export interface TemplateRow {
  id: string
  bizType: string
  bizTypeName: string
  name: string
  language: 'zh-CN' | 'en'
  paper: string
  paperWidth?: number
  paperHeight?: number
  margin: string
  isDefault: boolean
  isBuiltin: boolean
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  updatedAt: string
  version: number
}

export interface TemplateDetail extends TemplateRow {
  content: string
}

export interface TemplateSave {
  bizType: string
  name: string
  language: string
  paper: string
  paperWidth?: number
  paperHeight?: number
  margin: string
  content: string
  remark?: string
  version?: number
}

export interface PrintVariable {
  path: string
  name: string
  type: string
}

export interface PrintBiz {
  bizType: string
  name: string
  moduleCode: string
  moduleName: string
  dataApi: string
  variables: PrintVariable[]
  sampleData?: Record<string, unknown>
}

export interface TemplateQuery extends PageParam {
  bizType?: string
  language?: string
  status?: string
}

export const LANGUAGE_OPTIONS = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en', label: 'English' }
]

export const printApi = {
  page: (q: TemplateQuery) => http.get<PageResult<TemplateRow>>('/system/print-templates', q),
  get: (id: string) => http.get<TemplateDetail>(`/system/print-templates/${id}`),
  create: (data: TemplateSave) => http.post<string>('/system/print-templates', data),
  update: (id: string, data: TemplateSave) => http.put<void>(`/system/print-templates/${id}`, data),
  remove: (id: string) => http.delete<void>(`/system/print-templates/${id}`),
  copy: (id: string) => http.post<string>(`/system/print-templates/${id}/copy`),
  setDefault: (id: string) => http.post<void>(`/system/print-templates/${id}/set-default`),
  enable: (id: string) => http.post<void>(`/system/print-templates/${id}/enable`),
  disable: (id: string) => http.post<void>(`/system/print-templates/${id}/disable`),
  bizList: () => http.get<PrintBiz[]>('/system/print-biz'),
  biz: (bizType: string) => http.get<PrintBiz>(`/system/print-biz/${bizType}`)
}
