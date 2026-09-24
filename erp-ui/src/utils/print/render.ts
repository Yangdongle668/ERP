import Handlebars from 'handlebars'
import qrcode from 'qrcode-generator'
import { formatAmount, formatDate, formatDateTime, formatPrice, formatQty } from '@/utils/format'
import { code128Svg } from './code128'
import { amountInWordsCn, amountInWordsEn } from './amountWords'

/**
 * 打印模板渲染（需求 01-系统管理/09 第 1.1 节）：HTML + Handlebars，在浏览器端用打印数据渲染，新窗口预览后调用浏览器打印。
 *
 * 帮助函数：formatDate、formatDateTime、formatQty、formatAmount、formatPrice、amountInWordsCn、amountInWordsEn、
 * barcode（Code128 SVG）、qrcode（SVG）、dict（字典标签）、add、eq。
 * 字段权限不足的字段在数据中为 null（R04），格式化帮助函数统一显示 ***。
 */
export interface DictLookup {
  label: (type: string, value?: string | null) => string | undefined
}

const MASK = '***'
const masked = (v: unknown) => v === null

/** 当前渲染使用的字典查询（dict 帮助函数） */
let currentDict: DictLookup | undefined

function createEngine() {
  const hb = Handlebars.create()
  const num = (fn: (v: number | string, p?: number) => string) => (v: unknown, p?: unknown) => {
    if (masked(v)) return MASK
    if (v === undefined || v === '') return ''
    return fn(v as number | string, typeof p === 'number' ? p : undefined)
  }
  hb.registerHelper('formatDate', (v: unknown) => (masked(v) ? MASK : v ? formatDate(String(v)) : ''))
  hb.registerHelper('formatDateTime', (v: unknown) => (masked(v) ? MASK : v ? formatDateTime(String(v), true) : ''))
  hb.registerHelper('formatQty', num((v, p) => formatQty(v, p ?? 4)))
  hb.registerHelper('formatAmount', num((v, p) => formatAmount(v, p ?? 2)))
  hb.registerHelper('formatPrice', num((v) => formatPrice(v)))
  hb.registerHelper('amountInWordsCn', (v: unknown) => (masked(v) ? MASK : amountInWordsCn(v)))
  hb.registerHelper('amountInWordsEn', (v: unknown, currency: unknown) =>
    masked(v) ? MASK : amountInWordsEn(v, typeof currency === 'string' ? currency : 'USD'))
  hb.registerHelper('barcode', (v: unknown, height: unknown) =>
    new hb.SafeString(v ? code128Svg(String(v), typeof height === 'number' ? height : 40) : ''))
  hb.registerHelper('qrcode', (v: unknown, size: unknown) => {
    if (!v) return ''
    const q = qrcode(0, 'M')
    q.addData(String(v))
    q.make()
    const cell = typeof size === 'number' ? Math.max(1, Math.round(size / q.getModuleCount())) : 3
    return new hb.SafeString(q.createSvgTag({ cellSize: cell, margin: 0 }))
  })
  hb.registerHelper('dict', (type: unknown, v: unknown) => (masked(v) ? MASK : currentDict?.label(String(type), v as string) ?? (v ?? '')))
  hb.registerHelper('add', (a: unknown, b: unknown) => Number(a) + Number(b))
  hb.registerHelper('eq', (a: unknown, b: unknown) => a === b)
  // 未知变量显示为空；null（无字段权限）显示 ***
  return hb
}

const engine = createEngine()
const cache = new Map<string, HandlebarsTemplateDelegate>()

/** 渲染模板；语法错误抛出带行号的 Error */
export function renderTemplate(content: string, data: unknown, dict?: DictLookup): string {
  let tpl = cache.get(content)
  if (!tpl) {
    tpl = engine.compile(content)
    if (cache.size > 20) cache.clear()
    cache.set(content, tpl)
  }
  currentDict = dict
  try {
    return sanitize(tpl(data))
  } finally {
    currentDict = undefined
  }
}

/**
 * 语法检查：无错误返回 undefined；有错误返回“第 N 行：原因”，其后各行为出错位置的代码片段。
 * 只要摘要时取第一行。
 */
export function checkTemplate(content: string): string | undefined {
  try {
    Handlebars.parse(content)
    return undefined
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    const lines = msg.split('\n')
    const m = /line (\d+)/.exec(lines[0])
    if (!m) return msg
    const reason = lines.length > 1 ? lines[lines.length - 1] : lines[0]
    return [`第 ${m[1]} 行：${reason}`, ...lines.slice(1, -1)].join('\n')
  }
}

/** 纵深防御：去掉脚本与事件属性（后端保存时已拦截） */
function sanitize(html: string): string {
  return html
    .replace(/<\s*(script|iframe|object|embed)\b[\s\S]*?(<\s*\/\s*\1\s*>|$)/gi, '')
    .replace(/\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/javascript\s*:/gi, '')
}

// ==================== 页面与打印窗口 ====================

export interface PaperSetting {
  paper: string
  paperWidth?: number
  paperHeight?: number
  margin: string
}

export function pageSize(p: PaperSetting): string {
  switch (p.paper) {
    case 'A4_L': return 'A4 landscape'
    case 'A5_L': return 'A5 landscape'
    case 'CUSTOM': return p.paperWidth && p.paperHeight ? `${p.paperWidth}mm ${p.paperHeight}mm` : 'A4 portrait'
    default: return 'A4 portrait'
  }
}

export interface PrintDoc {
  html: string
  /** 草稿单据加“草稿”水印 */
  draft?: boolean
}

/** 组装打印页面：每张单据另起一页；页脚“第 X 页 / 共 Y 页”、打印时间、打印人 */
export function buildPrintHtml(opts: { title: string; setting: PaperSetting; docs: PrintDoc[]; printedBy?: string; toolbar?: boolean }): string {
  const printedAt = formatDateTime(new Date().toISOString().slice(0, 19).replace('T', ' '), true)
  const footer = `打印：${printedAt}${opts.printedBy ? ' ' + opts.printedBy : ''}`
  const docs = opts.docs.map((d) => `<section class="erp-print-doc">${d.draft ? '<div class="erp-print-watermark">草稿</div>' : ''}${d.html}</section>`).join('')
  const toolbar = opts.toolbar === false ? '' : `<div class="erp-print-toolbar"><span>${escapeHtml(opts.title)} · ${opts.docs.length} 张</span>
    <button onclick="window.print()" class="primary">打印</button><button onclick="window.close()">关闭</button></div>`
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${escapeHtml(opts.title)}</title>
<style>
@page { size: ${pageSize(opts.setting)}; margin: ${opts.setting.margin};
  @bottom-center { content: "第 " counter(page) " 页 / 共 " counter(pages) " 页"; font-size: 9pt; color: #666; }
  @bottom-right { content: "${footer}"; font-size: 8pt; color: #999; } }
html, body { margin: 0; padding: 0; }
body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; font-size: 10.5pt; color: #000; background: #f5f6f8; }
.erp-print-toolbar { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 8px; padding: 10px 24px;
  background: #fff; border-bottom: 1px solid #e5e6eb; font-size: 13px; color: #646a73; }
.erp-print-toolbar span { flex: 1; }
.erp-print-toolbar button { height: 30px; padding: 0 16px; border: 1px solid #d0d3d8; border-radius: 6px; background: #fff; cursor: pointer; font: inherit; color: #1f2329; }
.erp-print-toolbar button.primary { background: #2b6ce6; border-color: #2b6ce6; color: #fff; }
.erp-print-doc { position: relative; background: #fff; margin: 16px auto; padding: 12mm; max-width: 210mm; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
.erp-print-doc + .erp-print-doc { break-before: page; }
.erp-print-doc table { border-collapse: collapse; }
.erp-print-doc thead { display: table-header-group; }
.erp-print-doc tr { break-inside: avoid; }
.erp-print-watermark { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; pointer-events: none;
  font-size: 96pt; font-weight: 700; color: rgba(0,0,0,.08); transform: rotate(-30deg); -webkit-print-color-adjust: exact; print-color-adjust: exact; }
@media print {
  body { background: #fff; }
  .erp-print-toolbar { display: none; }
  .erp-print-doc { margin: 0; padding: 0; max-width: none; box-shadow: none; }
}
</style></head><body>${toolbar}${docs}</body></html>`
}

/** 在已打开的窗口中写入打印页面（先同步 window.open，避免浏览器拦截异步弹窗） */
export function writePrintWindow(win: Window, html: string) {
  win.document.open()
  win.document.write(html)
  win.document.close()
}

function escapeHtml(s: string) {
  return s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!)
}
