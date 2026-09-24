import { http } from '@/api/http'

export type ResetCycle = 'NEVER' | 'YEAR' | 'MONTH' | 'DAY'

export const RESET_OPTIONS: { value: ResetCycle; label: string }[] = [
  { value: 'NEVER', label: '不重置' },
  { value: 'YEAR', label: '按年' },
  { value: 'MONTH', label: '按月' },
  { value: 'DAY', label: '按日' }
]

export const DATE_PATTERN_OPTIONS = ['', 'yyyy', 'yyMM', 'yyyyMM', 'yyMMdd', 'yyyyMMdd'].map((v) => ({ value: v, label: v || '无' }))

export interface CodeRuleRow {
  id: string
  moduleCode: string
  moduleName: string
  bizCode: string
  name: string
  prefix: string
  datePattern: string
  separator: string
  seqLength: number
  resetCycle: ResetCycle
  allowManual: boolean
  allowedVars: string[]
  example: string
  version: number
}

export interface CodeRuleSave {
  name: string
  prefix: string
  datePattern: string
  separator: string
  seqLength: number
  resetCycle: ResetCycle
  allowManual: boolean
  version?: number
}

export interface SeqRow {
  resetKey: string
  currentValue: number
  updatedAt?: string
}

export const codeRuleApi = {
  list: (params: { moduleCode?: string; keyword?: string }) => http.get<CodeRuleRow[]>('/system/code-rules', params),
  update: (id: string, data: CodeRuleSave) => http.put<void>(`/system/code-rules/${id}`, data),
  preview: (data: Omit<CodeRuleSave, 'name' | 'allowManual' | 'version'> & { bizCode: string }) =>
    http.post<string>('/system/code-rules/preview', data, { silent: true }),
  seqs: (id: string) => http.get<SeqRow[]>(`/system/code-rules/${id}/seqs`),
  adjust: (id: string, resetKey: string, newValue: number) => http.put<void>(`/system/code-rules/${id}/seqs`, { resetKey, newValue })
}
