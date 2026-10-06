import { http } from '@/api/http'
import type { MaterialType, Tracking } from './material'

export interface CategoryNode {
  id: string
  parentId?: string
  code: string
  name: string
  codePrefix: string
  defaultMaterialType: MaterialType
  defaultBaseUom?: string
  defaultTracking: Tracking
  defaultIqcRequired: boolean
  defaultShelfLifeDays?: number
  level: number
  sort: number
  status: 'ENABLED' | 'DISABLED'
  remark?: string
  /** 本类别（含下级）启用物料数 */
  materialCount: number
  /** 本类别直接挂有物料（含停用） */
  hasMaterial: boolean
  version: number
  /** 流水号位数，空则按编码规则 */
  codeSeqLength?: number
  /** 编码段数（《物料编码原则》特征值） */
  segmentCount: number
  children?: CategoryNode[]
}

/** 选择器节点：leaf 为末级（物料只能挂在末级） */
export interface CategorySimple {
  id: string
  parentId?: string
  code: string
  name: string
  codePrefix: string
  defaultMaterialType: MaterialType
  defaultBaseUom?: string
  defaultTracking: Tracking
  defaultIqcRequired: boolean
  defaultShelfLifeDays?: number
  leaf: boolean
  codeSeqLength?: number
  segmentCount: number
  children?: CategorySimple[]
}

export interface CategorySave {
  parentId?: string
  code: string
  name: string
  codePrefix: string
  codeSeqLength?: number
  defaultMaterialType: MaterialType
  defaultBaseUom?: string
  defaultTracking: Tracking
  defaultIqcRequired: boolean
  defaultShelfLifeDays?: number
  sort: number
  remark?: string
  version?: number
}

/** 编码段特征值，如线材型号 02 = UL3302 */
export interface SegmentValue {
  id?: string
  code: string
  name: string
  status: 'ENABLED' | 'DISABLED'
  remark?: string
}
export interface CodeSegment {
  id?: string
  name: string
  length: number
  remark?: string
  values: SegmentValue[]
}
/** 类别编码方案：编码 = 前缀 + 各段特征值 + 流水号；locked 为已有物料（只能改名称、新增 / 停用特征值） */
export interface CodeScheme {
  categoryId: string
  categoryName: string
  codePrefix: string
  codeSeqLength?: number
  leaf: boolean
  locked: boolean
  segments: CodeSegment[]
}

/** 编码预览：前缀 + 特征值 + 流水号占位（N 位 #） */
export function codePreview(prefix: string, values: (string | undefined)[], seqLength?: number): string {
  return prefix + values.map((v) => v || '?').join('') + '#'.repeat(seqLength ?? 5)
}

const BASE = '/engineering/categories'

export const categoryApi = {
  tree: (q: { keyword?: string; status?: string }) => http.get<CategoryNode[]>(`${BASE}/tree`, q),
  simpleTree: () => http.get<CategorySimple[]>(`${BASE}/simple-tree`),
  nextSort: (parentId?: string) => http.get<number>(`${BASE}/next-sort`, { parentId }),
  create: (data: CategorySave) => http.post<string>(BASE, data),
  update: (id: string, data: CategorySave) => http.put<void>(`${BASE}/${id}`, data),
  remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
  enable: (id: string) => http.post<void>(`${BASE}/${id}/enable`),
  disable: (id: string) => http.post<void>(`${BASE}/${id}/disable`),
  codeScheme: (id: string) => http.get<CodeScheme>(`${BASE}/${id}/code-scheme`),
  saveCodeScheme: (id: string, segments: CodeSegment[]) => http.put<void>(`${BASE}/${id}/code-scheme`, { segments })
}

/** 在树中查找节点 */
export function findCategory<T extends { id: string; children?: T[] }>(nodes: T[], id?: string): T | undefined {
  if (!id) return undefined
  for (const n of nodes) {
    if (n.id === id) return n
    const c = findCategory(n.children ?? [], id)
    if (c) return c
  }
  return undefined
}
