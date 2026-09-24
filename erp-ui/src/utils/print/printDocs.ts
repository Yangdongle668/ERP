import { ElMessage } from 'element-plus'
import { BizError, http } from '@/api/http'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { buildPrintHtml, renderTemplate, writePrintWindow, type PaperSetting } from './render'

/** 打印按钮下拉用的模板（GET /system/print-templates/available） */
export interface TemplateBrief {
  id: string
  name: string
  language: string
  isDefault: boolean
  paper: string
}

export interface Available {
  bizType: string
  bizName: string
  dataApi: string
  templates: TemplateBrief[]
}

interface ForPrint extends PaperSetting {
  id: string
  name: string
  language: string
  content: string
}

export const MAX_BATCH = 50

export function loadAvailable(bizType: string) {
  return http.get<Available>('/system/print-templates/available', { bizType })
}

/**
 * 打印单据（需求 01-09 第 3.3 节）：取模板 → 按单据取打印数据 → 渲染 → 新窗口预览（顶部 [打印] [关闭]）→ 记录打印。
 * 草稿单据（数据 status = DRAFT）加“草稿”水印；已作废（status = CANCELED）不允许打印。
 *
 * @param templateId 不传时使用当前语言的默认模板
 */
export async function printDocuments(opts: { bizType: string; ids: string[]; templateId?: string; language?: string; available?: Available }) {
  const { bizType, ids } = opts
  if (!ids.length) {
    ElMessage.warning('请先勾选数据')
    return
  }
  if (ids.length > MAX_BATCH) {
    ElMessage.warning(`一次最多打印 ${MAX_BATCH} 张单据`)
    return
  }
  // 先同步打开窗口，避免浏览器拦截异步弹窗
  const win = window.open('', '_blank')
  if (!win) {
    ElMessage.error('打印窗口被浏览器拦截，请允许本站弹出窗口')
    return
  }
  win.document.write('<p style="font-family:sans-serif;color:#646a73;padding:24px">正在准备打印…</p>')
  try {
    const available = opts.available ?? (await loadAvailable(bizType))
    const lang = opts.language ?? 'zh-CN'
    const brief = opts.templateId
      ? available.templates.find((t) => t.id === opts.templateId)
      : available.templates.find((t) => t.isDefault && t.language === lang) ?? available.templates.find((t) => t.isDefault) ?? available.templates[0]
    if (!brief) throw new BizError(-1, `单据类型「${available.bizName}」没有可用的打印模板`)
    const tpl = await http.get<ForPrint>(`/system/print-templates/${brief.id}/for-print`)
    const datas: Record<string, unknown>[] = []
    for (const id of ids) {
      datas.push(await http.get<Record<string, unknown>>(available.dataApi.replace('{id}', encodeURIComponent(id))))
    }
    const canceled = datas.find((d) => d.status === 'CANCELED')
    if (canceled) throw new BizError(-1, `已作废的单据不能打印${canceled.docNo ? `：${canceled.docNo}` : ''}`)
    const dict = useDictStore()
    await dict.load()
    const docs = datas.map((d) => ({ html: renderTemplate(tpl.content, d, { label: (t, v) => dict.label(t, v) }), draft: d.status === 'DRAFT' }))
    writePrintWindow(win, buildPrintHtml({
      title: `${available.bizName} - ${tpl.name}`, setting: tpl, docs, printedBy: useUserStore().user?.realName
    }))
    http.post('/system/print-logs', { bizType, bizIds: ids, templateId: tpl.id }, { silent: true }).catch(() => undefined)
  } catch (e) {
    win.close()
    if (e instanceof BizError && e.code === -1) ElMessage.error(e.message)
    else if (!(e instanceof BizError)) ElMessage.error(`打印失败：${e instanceof Error ? e.message : String(e)}`)
  }
}
