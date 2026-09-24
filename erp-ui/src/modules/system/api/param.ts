import { http } from '@/api/http'

export interface ParamModule {
  moduleCode: string
  moduleName: string
  count: number
}

export interface ParamRow {
  key: string
  moduleCode: string
  groupName: string
  name: string
  valueType: 'STRING' | 'INT' | 'DECIMAL' | 'BOOL' | 'ENUM' | 'USER_LIST' | 'TIME'
  options: { value: string; label: string }[]
  minValue?: string
  maxValue?: string
  value?: string
  defaultValue?: string
  description: string
  modified: boolean
}

export const paramApi = {
  modules: () => http.get<ParamModule[]>('/system/params/modules'),
  list: (params: { module?: string; keyword?: string }) => http.get<ParamRow[]>('/system/params', params),
  save: (changes: { key: string; value?: string }[]) => http.put<string[]>('/system/params', changes),
  reset: (key: string) => http.post<void>(`/system/params/${key}/reset`)
}
