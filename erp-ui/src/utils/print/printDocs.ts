import { ElMessage } from 'element-plus'
import { BizError, http } from '@/api/http'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import { buildPrintHtml, layoutDocument, writePrintWindow, type PaperSetting, type PrintDoc } from './render'

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

/** 针式模板打印前的提示（打印窗口顶部） */
const DOT_PAPER_TEXT: Record<string, string> = {
  DOT_241_93: '241×93（三等分，9.5×3.67 英寸）', DOT_241_140: '241×140（二等分，9.5×5.5 英寸）', DOT_241_280: '241×280（整张，9.5×11 英寸）'
}
export const dotHint = (paper: string) =>
  DOT_PAPER_TEXT[paper] ? `针式多联纸：打印对话框选纸张 ${DOT_PAPER_TEXT[paper]}、边距「无」、缩放 100%，取消「页眉和页脚」` : undefined

/** 打印抬头（GET /system/print-header）：单据所属公司的中英文名、地址、电话、税号、logo（data URI） */
export type PrintHeader = Record<string, unknown>
const headerCache = new Map<string, Promise<PrintHeader>>()
export function loadHeader(orgId?: unknown) {
  const key = orgId == null || orgId === '' ? '' : String(orgId)
  let p = headerCache.get(key)
  if (!p) {
    p = http.get<PrintHeader>('/system/print-header', key ? { orgId: key } : undefined, { silent: true }).catch(() => ({}))
    headerCache.set(key, p)
    window.setTimeout(() => headerCache.delete(key), 5 * 60 * 1000)
  }
  return p
}

/** 已打印次数（补打判断） */
async function printedCounts(bizType: string, ids: string[]): Promise<Map<string, number>> {
  const list = await http.get<{ bizId: string; count: number }[]>('/system/print-logs/counts', { bizType, bizIds: ids.join(',') }, { silent: true })
    .catch(() => [])
  return new Map(list.map((c) => [String(c.bizId), Number(c.count) || 0]))
}

/** 按单据渲染打印页（打印、模板编辑器预览共用） */
export async function layoutDocs(tpl: { content: string } & PaperSetting, datas: Record<string, unknown>[], opts: { printedCounts?: number[] } = {}): Promise<PrintDoc[]> {
  const dict = useDictStore()
  await dict.load()
  const printedBy = useUserStore().user?.realName
  const pages: PrintDoc[] = []
  for (let i = 0; i < datas.length; i++) {
    const d = datas[i]
    const company = await loadHeader(d.orgId)
    pages.push(...layoutDocument(tpl.content, d, tpl, { company, printedCount: opts.printedCounts?.[i] ?? 0, printedBy }, { label: (t, v) => dict.label(t, v) }))
  }
  return pages
}

/**
 * 按单号找单据 ID（模板编辑器“真实单据预览”）：打印数据接口去掉 /{id}/print-data 即列表接口，
 * 按 docNo / no / keyword 查询后取单号完全一致的一条；输入纯数字长 ID 时直接使用。
 */
export async function findDocId(dataApi: string, input: string): Promise<string | undefined> {
  const v = input.trim()
  if (!v) return undefined
  if (/^\d{15,20}$/.test(v)) return v
  const listApi = dataApi.replace(/\/\{id\}.*$/, '')
  const res = await http.get<unknown>(listApi, { docNo: v, no: v, keyword: v, pageNo: 1, pageSize: 20 }, { silent: true }).catch(() => undefined)
  const rows = (Array.isArray(res) ? res : (res as { list?: unknown[] } | undefined)?.list ?? []) as Record<string, unknown>[]
  const up = v.toUpperCase()
  const hit = rows.find((r) => ['docNo', 'no', 'code', 'plNo', 'invoiceNo'].some((k) => String(r[k] ?? '').toUpperCase() === up))
  return hit?.id != null ? String(hit.id) : undefined
}

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
    const counts = await printedCounts(bizType, ids)
    const docs = await layoutDocs(tpl, datas, { printedCounts: ids.map((id) => counts.get(id) ?? 0) })
    writePrintWindow(win, buildPrintHtml({
      title: `${available.bizName} - ${tpl.name}`, setting: tpl, docs, printedBy: useUserStore().user?.realName,
      hint: dotHint(tpl.paper)
    }))
    http.post('/system/print-logs', { bizType, bizIds: ids, templateId: tpl.id }, { silent: true }).catch(() => undefined)
  } catch (e) {
    win.close()
    if (e instanceof BizError && e.code === -1) ElMessage.error(e.message)
    else if (!(e instanceof BizError)) ElMessage.error(`打印失败：${e instanceof Error ? e.message : String(e)}`)
  }
}
