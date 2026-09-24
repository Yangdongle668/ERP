import type { StatusMap } from './types'

/**
 * 通用状态颜色（UI 设计规范 7.3）。模块特有状态在模块内定义 StatusMap，未定义的按语义套用这里。
 */
export const COMMON_STATUS: StatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  SUBMITTED: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已审核', type: 'primary' },
  EXECUTING: { label: '执行中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info', plain: true },
  CANCELED: { label: '已作废', type: 'danger' },
  REJECTED: { label: '已驳回', type: 'danger' },
  ENABLED: { label: '启用', type: 'success' },
  DISABLED: { label: '停用', type: 'info', plain: true }
}

/** 启用/停用 */
export const ENABLE_STATUS: StatusMap = {
  ENABLED: COMMON_STATUS.ENABLED,
  DISABLED: COMMON_STATUS.DISABLED
}
