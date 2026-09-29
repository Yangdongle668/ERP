import { ElMessage, ElMessageBox } from 'element-plus'
import { BizError } from '@/api/http'
import type { DocResult } from '../api/sales'

/**
 * 提交（带信用确认，SAL-SO-R06）：信用检查为“警告”时弹出确认框，确认后以 confirmCredit=true 再次提交；
 * 其他错误照常提示。返回 undefined 表示未提交成功。
 */
export async function submitWithCredit(submit: (confirm: boolean) => Promise<DocResult>): Promise<DocResult | undefined> {
  try {
    return await submit(false)
  } catch (e) {
    const data = e instanceof BizError ? (e.data as { needConfirm?: boolean; message?: string } | undefined) : undefined
    if (!data?.needConfirm) {
      ElMessage.error(e instanceof Error ? e.message : String(e))
      return undefined
    }
    const ok = await ElMessageBox.confirm(`${data.message}。确定继续提交吗？`, '信用警告', { type: 'warning', confirmButtonText: '继续提交' })
      .then(() => true).catch(() => false)
    if (!ok) return undefined
    try {
      return await submit(true)
    } catch (e2) {
      ElMessage.error(e2 instanceof Error ? e2.message : String(e2))
      return undefined
    }
  }
}
