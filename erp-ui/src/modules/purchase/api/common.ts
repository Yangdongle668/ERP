import type { Option, RelatedDoc, StatusMap } from '@/components'

/** 资材模块公共类型与状态（需求 07-资材 README） */

export type DocStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED' | 'VOIDED'

/** 通用单据状态（UI 设计规范 7.3） */
export const DOC_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_APPROVAL: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已审核', type: 'primary' },
  IN_PROGRESS: { label: '执行中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true },
  VOIDED: { label: '已作废', type: 'danger' }
}

export const optionsOf = (map: StatusMap, exclude: string[] = []): Option[] =>
  Object.entries(map).filter(([k]) => !exclude.includes(k)).map(([value, s]) => ({ value, label: s.label }))

export const labelOf = (options: Option[], v?: string | null) => (v ? options.find((o) => o.value === v)?.label ?? v : '-')

export interface SaveResult {
  id: string
  warnings: string[]
}

export interface DocResult {
  status: DocStatus
  warnings: string[]
}

export type { RelatedDoc }

/** 数量字符串转数字（空为 0） */
export const num = (v?: string | number | null) => (v === undefined || v === null || v === '' ? 0 : Number(v))

/** 提交结果提示文案 */
export const submitText = (status?: string) =>
  status === 'PENDING_APPROVAL' ? '已提交，等待审批' : status === 'APPROVED' || status === 'COMPLETED' ? '提交成功，已审核' : '提交成功'

/** 列表查询中的多选状态转为逗号分隔 */
export const joinList = (v?: string[]) => (v?.length ? v.join(',') : undefined)

export const LINE_STATUS: StatusMap = {
  OPEN: { label: '未完成', type: 'primary' },
  ORDERED: { label: '已转订单', type: 'success' },
  RECEIVED: { label: '已收齐', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true }
}

export const INSPECT_STATUS: StatusMap = {
  NONE: { label: '免检', type: 'info', plain: true },
  PENDING: { label: '待检', type: 'warning' },
  QUALIFIED: { label: '合格', type: 'success' },
  CONCESSION: { label: '特采', type: 'primary' },
  PARTIAL: { label: '部分合格', type: 'warning' },
  REJECTED: { label: '不合格', type: 'danger' }
}
