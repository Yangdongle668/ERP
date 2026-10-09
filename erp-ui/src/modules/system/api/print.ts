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
  rowsPerPage?: number
  copiesNote?: string
  copyMode?: 'CARBON' | 'REPEAT'
  layout?: string | null
}

/** 打印方式：多联纸一次打印（针式复写纸）/ 普通纸逐联打印（激光，每联单独一页） */
export const COPY_MODE_OPTIONS = [
  { value: 'CARBON', label: '多联纸一次打印' },
  { value: 'REPEAT', label: '普通纸逐联打印' }
]

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
  /** 每页明细行数：设置后按固定行数分页（针式多联纸），空为自动分页 */
  rowsPerPage?: number
  /** 联次说明，| 分隔：①白 存根|②红 财务|③黄 仓库 */
  copiesNote?: string
  copyMode?: 'CARBON' | 'REPEAT'
  /** 可视化版式 JSON（为空表示代码模板） */
  layout?: string | null
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
