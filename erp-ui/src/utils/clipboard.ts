import { ElMessage } from 'element-plus'

/**
 * 复制文本到剪贴板。navigator.clipboard 只在 HTTPS / localhost 下可用（内网 http://IP:1493 访问时不存在），
 * 不可用或失败时退回到隐藏文本框 + document.execCommand('copy')。返回是否复制成功。
 */
export async function copyText(text: string): Promise<boolean> {
  if (!text) return false
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 继续尝试兼容方式
    }
  }
  const ta = document.createElement('textarea')
  ta.value = text
  ta.setAttribute('readonly', '')
  ta.style.position = 'fixed'
  ta.style.top = '0'
  ta.style.left = '0'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  const active = document.activeElement as HTMLElement | null
  ta.focus()
  ta.select()
  ta.setSelectionRange(0, text.length)
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }
  document.body.removeChild(ta)
  active?.focus?.()
  return ok
}

/** 复制并提示：成功“已复制”，失败提示手动复制 */
export async function copyWithMessage(text: string, label = '') {
  const ok = await copyText(text)
  if (ok) ElMessage.success(label ? `已复制${label}` : '已复制')
  else ElMessage.warning('复制失败，请手动选中后按 Ctrl+C 复制')
  return ok
}
