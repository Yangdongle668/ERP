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
  /**
   * 条码：{{barcode docNo 40}}；第三个参数 "stretch" 时不带文字、按 CSS 宽高拉伸（针式打印用宽条码，单条线约 2 个针点，扫码更稳）：
   * {{barcode docNo 24 "stretch"}} + CSS svg { width: 55mm; height: 4.5mm }
   */
  hb.registerHelper('barcode', (v: unknown, height: unknown, mode: unknown) => {
    if (!v) return ''
    const h = typeof height === 'number' ? height : 40
    if (mode === 'stretch') return new hb.SafeString(code128Svg(String(v), h, false).replace('<svg ', '<svg preserveAspectRatio="none" '))
    return new hb.SafeString(code128Svg(String(v), h))
  })
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
  hb.registerHelper('or', (...args: unknown[]) => args.slice(0, -1).some(Boolean))
  hb.registerHelper('gt', (a: unknown, b: unknown) => Number(a) > Number(b))
  /**
   * 小数点对齐：{{decAlign (formatQty qty) 3}}。整数部分右对齐，小数部分（含小数点）放进固定宽度（reserve 位小数 + 1）的格子，
   * 同一列的数字小数点上下对齐；*** / 空值原样输出。
   */
  hb.registerHelper('decAlign', (v: unknown, reserve: unknown) => {
    const s = v == null ? '' : String(v)
    if (!s || !/\d/.test(s)) return s
    const i = s.indexOf('.')
    const r = typeof reserve === 'number' ? reserve : 2
    const int = i < 0 ? s : s.slice(0, i)
    const frac = i < 0 ? '' : s.slice(i)
    return new hb.SafeString(`<span class="num-i">${hb.escapeExpression(int)}</span><span class="num-f" style="display:inline-block;min-width:${r + 1}ch;text-align:left">${hb.escapeExpression(frac)}</span>`)
  })
  /** 数组某字段求和：{{formatQty (sum page.lines "qty")}} */
  hb.registerHelper('sum', (rows: unknown, key: unknown) => (Array.isArray(rows) ? sumOf(rows, String(key)) : 0))
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
  /** 每页明细行数：设置后按固定行数分页（针式多联纸），空为浏览器自动分页 */
  rowsPerPage?: number | null
  /** 联次说明，| 分隔：①白 存根|②红 财务|③黄 仓库 */
  copiesNote?: string | null
  /** CARBON 多联纸一次打印 / REPEAT 普通纸逐联打印 */
  copyMode?: string | null
}

/**
 * 针式连续纸按英寸：宽 9.5in（241mm），二等分 5.5in（139.7mm）、三等分 11/3in（93.1mm）、整张 11in（279.4mm）。
 * 用精确英寸而不是取整毫米，连续打印多页时不会累积走纸误差。
 */
const PAPERS: Record<string, [string, string]> = {
  A4_P: ['210mm', '297mm'],
  A4_L: ['297mm', '210mm'],
  A5_L: ['210mm', '148mm'],
  DOT_241_140: ['9.5in', '5.5in'],
  DOT_241_93: ['9.5in', '3.6667in'],
  DOT_241_280: ['9.5in', '11in']
}

function paperDims(p: PaperSetting): [string, string] {
  if (p.paper === 'CUSTOM' && p.paperWidth && p.paperHeight) return [`${p.paperWidth}mm`, `${p.paperHeight}mm`]
  return PAPERS[p.paper] ?? PAPERS.A4_P
}

export function pageSize(p: PaperSetting): string {
  if (p.paper === 'A4_L') return 'A4 landscape'
  if (p.paper === 'A5_L') return 'A5 landscape'
  if (p.paper === 'CUSTOM' ? !(p.paperWidth && p.paperHeight) : p.paper === 'A4_P' || !PAPERS[p.paper]) return 'A4 portrait'
  const [w, h] = paperDims(p)
  return `${w} ${h}`
}

/** 边距 "上 右 下 左"（CSS 简写，1～4 个值） */
function marginParts(margin: string): [string, string, string, string] {
  const v = margin.trim().split(/\s+/).filter(Boolean)
  const [t = '0', r = t, b = t, l = r] = v
  return [t, r, b, l]
}

export interface PrintDoc {
  html: string
  /** 草稿单据加“草稿”水印（固定分页的针式模板由模板自己印“草稿”字样） */
  draft?: boolean
}

/** 渲染时附加给模板的上下文（模板变量 company / print / page / copy / copies） */
export interface PrintExtras {
  /** 打印抬头：公司中英文名、地址、电话、税号、logo（data URI） */
  company?: Record<string, unknown>
  /** 该单据此前已打印的次数（不含本次） */
  printedCount?: number
  printedBy?: string
}

function sumOf(rows: unknown[], key: string): number {
  return rows.reduce<number>((s, r) => {
    const v = (r as Record<string, unknown>)?.[key]
    const n = typeof v === 'number' ? v : typeof v === 'string' && v.trim() !== '' ? Number(v) : NaN
    return Number.isFinite(n) ? s + n : s
  }, 0)
}

/**
 * 把一张单据渲染为若干打印页（需求 01-09 第 4 节）。
 * - 模板未设每页行数：整张单据渲染一次，由浏览器自动分页（原 A4 模板）。
 * - 设了每页行数：明细 lines 按行数切成多页，每页单独渲染模板（单头、表头、签名栏每页都有），
 *   模板变量 page = { lines, no, count, isFirst, isLast, blanks（补空行用的数组）}；合计请放在 {{#if page.isLast}} 中。
 * - 联次：copies = [{ no, text }]（来自联次说明）；逐联打印（REPEAT）时每页按联数重复，copy = 当前联。
 * - print = { count（本次是第几次打印）, reprint（是否补打）, draft, printedAt, printedBy }。
 */
export function layoutDocument(content: string, data: Record<string, unknown>, setting: PaperSetting, extras: PrintExtras, dict?: DictLookup): PrintDoc[] {
  const draft = data.status === 'DRAFT'
  const printedCount = extras.printedCount ?? 0
  const printedAt = formatDateTime(new Date().toISOString().slice(0, 19).replace('T', ' '), true)
  const copies = (setting.copiesNote ?? '').split('|').map((t) => t.trim()).filter(Boolean).map((text, i) => ({ no: i + 1, text }))
  const print = { count: printedCount + 1, reprint: printedCount > 0, draft, printedAt, printedBy: extras.printedBy ?? '' }
  const base = { ...data, company: extras.company ?? data.company ?? {}, print, copies }
  const rows = setting.rowsPerPage && setting.rowsPerPage > 0 ? setting.rowsPerPage : 0
  if (!rows) return [{ html: renderTemplate(content, base, dict), draft }]
  const lines = Array.isArray(data.lines) ? (data.lines as unknown[]) : []
  const count = Math.max(1, Math.ceil(lines.length / rows))
  const pages: PrintDoc[] = []
  const repeat = setting.copyMode === 'REPEAT' && copies.length ? copies : [undefined]
  for (let i = 0; i < count; i++) {
    const pageLines = lines.slice(i * rows, (i + 1) * rows)
    const page = {
      lines: pageLines, no: i + 1, count, isFirst: i === 0, isLast: i === count - 1,
      blanks: Array.from({ length: rows - pageLines.length }, (_, k) => i * rows + pageLines.length + k + 1),
      sumQty: sumOf(pageLines, 'qty')
    }
    for (const copy of repeat) pages.push({ html: renderTemplate(content, { ...base, page, copy }, dict), draft })
  }
  return pages
}

/**
 * 组装打印页面：每张单据（或固定分页的每一页）另起一页。
 * A4 等自动分页：页脚“第 X 页 / 共 Y 页”、打印时间、打印人。固定分页（针式）：每页高度固定为纸张高度减上下边距，
 * 超出部分裁掉、不足留白，保证每页正好落在一张多联纸上；页码、打印信息由模板自己印。
 */
export function buildPrintHtml(opts: { title: string; setting: PaperSetting; docs: PrintDoc[]; printedBy?: string; toolbar?: boolean; hint?: string }): string {
  const printedAt = formatDateTime(new Date().toISOString().slice(0, 19).replace('T', ' '), true)
  const footer = `打印：${printedAt}${opts.printedBy ? ' ' + opts.printedBy : ''}`
  const fixed = !!(opts.setting.rowsPerPage && opts.setting.rowsPerPage > 0)
  const [w, h] = paperDims(opts.setting)
  const [mt, mr, mb, ml] = marginParts(opts.setting.margin)
  const docs = opts.docs.map((d) => fixed
    ? `<section class="erp-print-page">${d.html}</section>`
    : `<section class="erp-print-doc">${d.draft ? '<div class="erp-print-watermark">草稿</div>' : ''}${d.html}</section>`).join('')
  const hint = opts.hint ? `<span class="hint">${escapeHtml(opts.hint)}</span>` : ''
  const toolbar = opts.toolbar === false ? '' : `<div class="erp-print-toolbar"><span>${escapeHtml(opts.title)} · ${opts.docs.length} 页</span>${hint}
    <button onclick="window.print()" class="primary">打印</button><button onclick="window.close()">关闭</button></div>`
  const pageRule = fixed
    ? `@page { size: ${w} ${h}; margin: ${mt} ${mr} ${mb} ${ml}; }`
    : `@page { size: ${pageSize(opts.setting)}; margin: ${opts.setting.margin};
  @bottom-center { content: "第 " counter(page) " 页 / 共 " counter(pages) " 页"; font-size: 9pt; color: #666; }
  @bottom-right { content: "${footer}"; font-size: 8pt; color: #999; } }`
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${escapeHtml(opts.title)}</title>
<style>
${pageRule}
html, body { margin: 0; padding: 0; }
body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; font-size: 10.5pt; color: #000; background: #f5f6f8; }
.erp-print-toolbar { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 8px; padding: 10px 24px;
  background: #fff; border-bottom: 1px solid #e5e6eb; font-size: 13px; color: #646a73; }
.erp-print-toolbar span { flex: 1; }
.erp-print-toolbar .hint { flex: 2; font-size: 12px; color: #8f959e; }
.erp-print-toolbar button { height: 30px; padding: 0 16px; border: 1px solid #d0d3d8; border-radius: 6px; background: #fff; cursor: pointer; font: inherit; color: #1f2329; }
.erp-print-toolbar button.primary { background: #2b6ce6; border-color: #2b6ce6; color: #fff; }
.erp-print-doc { position: relative; background: #fff; margin: 16px auto; padding: 12mm; max-width: 210mm; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
.erp-print-doc + .erp-print-doc { break-before: page; }
.erp-print-doc table { border-collapse: collapse; }
.erp-print-doc thead { display: table-header-group; }
.erp-print-doc tr { break-inside: avoid; }
.erp-print-watermark { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; pointer-events: none;
  font-size: 96pt; font-weight: 700; color: rgba(0,0,0,.08); transform: rotate(-30deg); -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.erp-print-page { position: relative; box-sizing: border-box; overflow: hidden; background: #fff; margin: 16px auto;
  width: ${w}; height: ${h}; padding: ${mt} ${mr} ${mb} ${ml}; box-shadow: 0 1px 3px rgba(0,0,0,.12); }
@media print {
  body { background: #fff; }
  .erp-print-toolbar { display: none; }
  .erp-print-doc { margin: 0; padding: 0; max-width: none; box-shadow: none; }
  .erp-print-page { margin: 0; padding: 0; box-shadow: none; width: auto; height: calc(${h} - ${mt} - ${mb}); break-after: page; }
  .erp-print-page:last-child { break-after: auto; }
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
