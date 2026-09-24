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
  children?: CategorySimple[]
}

export interface CategorySave {
  parentId?: string
  code: string
  name: string
  codePrefix: string
  defaultMaterialType: MaterialType
  defaultBaseUom?: string
  defaultTracking: Tracking
  defaultIqcRequired: boolean
  defaultShelfLifeDays?: number
  sort: number
  remark?: string
  version?: number
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
  disable: (id: string) => http.post<void>(`${BASE}/${id}/disable`)
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
