/**
 * 可视化打印模板（需求 01-09 第 9 节）：用“版式”（单头条目、明细列、签名栏等的勾选 / 顺序 / 宽度）生成针式多联模板的 HTML。
 *
 * 内置针式模板的资源文件 `print-templates/<bizType>-zh-CN-dot.html` 也由本文件生成（scripts/gen-dot-templates.mjs），
 * 编辑器里改版式时用同一份代码实时生成，保证两者完全一致。本文件不依赖其他模块，便于脚本直接打包运行。
 */

export interface LayoutField {
  key: string
  label: string
  /** Handlebars 表达式，如 {{prodOrderNo}}；自定义空白项为空串（手写） */
  expr: string
  /** 占几列（单头 4 列网格） */
  span: number
  visible: boolean
}

export interface LayoutTotal {
  /** qty 数量合计 / amount 金额合计 */
  kind: 'qty' | 'amount'
  /** 明细行字段，本页小计按它求和 */
  field: string
  /** 最后一页的单据合计取单头字段（如 totalAmount）；为空时对全部明细求和 */
  docExpr?: string | null
}

export interface LayoutColumn {
  key: string
  label: string
  /** 宽度（相对值，按勾选的列等比分配整行宽度） */
  width: number
  expr: string
  align: 'left' | 'center' | 'right'
  visible: boolean
  total?: LayoutTotal | null
}

export interface LayoutSign {
  key: string
  label: string
  /** 系统已知的姓名（如 {{createdByName}}），为空则留空手签 */
  expr: string
  /** 占两格（如客户签收） */
  wide: boolean
  visible: boolean
}

export interface PrintLayout {
  version: 1
  /** 标题，可含表达式（领料单按领料原因） */
  title: string
  subtitle: string
  /** 页眉日期表达式 */
  date: string
  header: { logo: boolean; nameEn: boolean; barcode: boolean }
  info: LayoutField[]
  columns: LayoutColumn[]
  memo: { visible: boolean; expr: string }
  signs: LayoutSign[]
  footer: { printInfo: boolean; address: boolean }
  /** 正文字号 pt（8～10） */
  fontSize: number
  /** 以下为模板设置（导入内置模板时写入首行注释） */
  paper?: string
  margin?: string
  rowsPerPage?: number
  copies?: string
}

const fmt = (n: number) => String(Math.round(n * 100) / 100)

function css(fontSize: number) {
  const fs = fmt(fontSize)
  const big = fmt(fontSize + 0.5)
  return `<style>
/* 针式多联（可视化版式生成）：单头为无线条的标签-内容网格，明细为细线网格，签名栏等分纯文本 */
.dm { position: relative; box-sizing: border-box; height: 100%; padding-right: 6mm; font-family: "SimSun", "宋体", "NSimSun", "Songti SC", serif; font-size: ${fs}pt; line-height: 1.2; color: #000; font-variant-numeric: tabular-nums; }
.dm-head { display: grid; grid-template-columns: 38% 1fr 30%; align-items: center; min-height: 10.5mm; padding-bottom: 0.6mm; border-bottom: 0.25mm solid #000; }
.dm-co { display: flex; align-items: center; gap: 1.5mm; overflow: hidden; }
.dm-co img { height: 8mm; max-width: 20mm; object-fit: contain; }
.dm-co b { display: block; font-size: ${big}pt; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dm-co small { display: block; font-size: 6.5pt; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dm-title { position: relative; text-align: center; font-size: 13pt; font-weight: bold; letter-spacing: 1mm; white-space: nowrap; }
.dm-title small { display: block; font-size: 7.5pt; font-weight: normal; letter-spacing: 0; }
.dm-marks { position: absolute; right: 0; top: 0; display: flex; flex-direction: column; gap: 0.6mm; }
.dm-stamp { display: inline-block; padding: 0 1mm; border: 0.3mm solid #000; font-size: 8pt; letter-spacing: 0; }
.dm-no { text-align: right; font-size: 7.5pt; line-height: 1.15; }
.dm-no b { font-size: ${big}pt; }
.dm-no svg { display: block; height: 4.2mm; width: 55mm; max-width: 100%; margin: 0.4mm 0 0.4mm auto; }
.dm-info { display: grid; grid-template-columns: repeat(4, 1fr); column-gap: 3mm; row-gap: 0.6mm; margin: 1.2mm 0; }
.dm-f { display: flex; min-width: 0; white-space: nowrap; }
.dm-f .l { flex: 0 0 14.5mm; white-space: nowrap; }
.dm-f .v { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; font-weight: bold; }
.dm-f.s2 { grid-column: span 2; } .dm-f.s3 { grid-column: span 3; } .dm-f.s4 { grid-column: span 4; }
.dm-lines { width: 100%; border-collapse: collapse; table-layout: fixed; }
.dm-lines th, .dm-lines td { height: 4.6mm; padding: 0 0.8mm; border: 0.2mm solid #000; font-size: ${fs}pt; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-align: left; }
.dm-lines th { font-weight: bold; text-align: center; }
.dm-lines .r { text-align: right; } .dm-lines .c { text-align: center; }
.dm-lines tfoot td { font-weight: bold; }
.dm-memo { margin-top: 0.8mm; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dm-sign { display: grid; grid-auto-columns: 1fr; grid-auto-flow: column; column-gap: 3mm; margin-top: 1.2mm; height: 6mm; }
.dm-sign div { white-space: nowrap; overflow: hidden; }
.dm-sign div.wide { grid-column: span 2; }
.dm-foot { position: absolute; left: 0; right: 6mm; bottom: 0; display: grid; grid-template-columns: 1fr auto; column-gap: 3mm; font-size: 6.5pt; white-space: nowrap; }
.dm-foot span:last-child { text-align: right; overflow: hidden; text-overflow: ellipsis; }
.dm-copies { position: absolute; top: 0; right: 0; bottom: 3mm; width: 4mm; display: flex; flex-direction: column; justify-content: space-around; align-items: center; font-size: 6.5pt; }
.dm-copies span { writing-mode: vertical-rl; letter-spacing: 0.4mm; white-space: nowrap; }
</style>`
}

function head(l: PrintLayout) {
  const co = `    <div class="dm-co">
      ${l.header.logo ? '{{#if company.logo}}<img src="{{company.logo}}" alt="">{{/if}}' : ''}
      <div><b>{{company.name}}</b>${l.header.nameEn ? '<small>{{company.nameEn}}</small>' : ''}</div>
    </div>`
  const sub = l.subtitle ? `<small>${l.subtitle}</small>` : ''
  return `<div class="dm">
  <div class="dm-head">
${co}
    <div class="dm-title">${l.title}${sub}<span class="dm-marks">{{#if print.draft}}<span class="dm-stamp">草稿</span>{{/if}}{{#if print.reprint}}<span class="dm-stamp">补打</span>{{/if}}</span></div>
    <div class="dm-no">
      No. <b>{{docNo}}</b>${l.header.barcode ? '{{barcode docNo 24 "stretch"}}' : '<br>'}
      日期 ${l.date}　第 {{page.no}}/{{page.count}} 页
    </div>
  </div>
`
}

function info(fields: LayoutField[]) {
  const visible = fields.filter((f) => f.visible)
  if (!visible.length) return ''
  const out = ['  <div class="dm-info">']
  for (const f of visible) {
    const span = Math.min(4, Math.max(1, Math.round(f.span || 1)))
    out.push(`    <div class="dm-f${span > 1 ? ` s${span}` : ''}"><span class="l">${f.label}</span><span class="v">${f.expr}</span></div>`)
  }
  out.push('  </div>')
  return out.join('\n')
}

function totalExprs(t: LayoutTotal): [string, string] {
  if (t.kind === 'amount') {
    return [`{{decAlign (formatAmount (sum page.lines "${t.field}")) 2}}`,
      t.docExpr ? `{{decAlign (formatAmount ${t.docExpr}) 2}}` : `{{decAlign (formatAmount (sum lines "${t.field}")) 2}}`]
  }
  return [`{{decAlign (formatQty (sum page.lines "${t.field}")) 3}}`, `{{decAlign (formatQty (sum lines "${t.field}")) 3}}`]
}

const ALIGN_CLASS: Record<string, string> = { right: ' class="r"', center: ' class="c"', left: '' }

function lines(columns: LayoutColumn[]) {
  const cols = columns.filter((c) => c.visible)
  if (!cols.length) return ''
  const sum = cols.reduce((s, c) => s + Math.max(1, c.width || 1), 0)
  const out = ['  <table class="dm-lines"><colgroup>' + cols.map((c) => `<col style="width:${fmt((Math.max(1, c.width || 1) / sum) * 100)}%">`).join('') + '</colgroup>']
  out.push('    <thead><tr>' + cols.map((c) => `<th>${c.label}</th>`).join('') + '</tr></thead>')
  out.push('    <tbody>')
  out.push('      {{#each page.lines}}<tr>' + cols.map((c) => `<td${ALIGN_CLASS[c.align] ?? ''}>${c.expr}</td>`).join('') + '</tr>{{/each}}')
  out.push('      {{#each page.blanks}}<tr>' + '<td></td>'.repeat(cols.length) + '</tr>{{/each}}')
  out.push('    </tbody>')
  const first = cols.findIndex((c) => c.total)
  if (first >= 0) {
    const cells: string[] = []
    if (first > 0) cells.push(`<td colspan="${first}" class="c">{{#if page.isLast}}合　计{{else}}本页小计{{/if}}</td>`)
    for (let i = first; i < cols.length; i++) {
      const t = cols[i].total
      if (t) {
        const [pe, de] = totalExprs(t)
        cells.push(`<td class="r">{{#if page.isLast}}${de}{{else}}${pe}{{/if}}</td>`)
      } else {
        cells.push('<td></td>')
      }
    }
    out.push('    <tfoot><tr>' + cells.join('') + '</tr></tfoot>')
  }
  out.push('  </table>')
  return out.join('\n')
}

function signs(items: LayoutSign[]) {
  const visible = items.filter((s) => s.visible)
  if (!visible.length) return ''
  return '  <div class="dm-sign">' + visible.map((s) => `<div${s.wide ? ' class=wide' : ''}>${s.label}：${s.expr}</div>`).join('') + '</div>'
}

function foot(l: PrintLayout) {
  const left = l.footer.printInfo ? '打印 {{print.printedAt}} {{print.printedBy}}{{#if print.reprint}}　补打（第 {{print.count}} 次打印）{{/if}}' : ''
  const right = l.footer.address ? '{{company.address}}{{#if company.phone}}　电话 {{company.phone}}{{/if}}' : ''
  return `  <div class="dm-foot">
    <span>${left}</span>
    <span>${right}</span>
  </div>
  <div class="dm-copies">{{#if copy}}<span>{{copy.text}}</span>{{else}}{{#each copies}}<span>{{text}}</span>{{/each}}{{/if}}</div>
</div>
`
}

/** 版式 → 模板 HTML；withMeta 时首行写入 erp-print 设置注释（内置模板资源文件用） */
export function buildLayoutHtml(l: PrintLayout, withMeta = false): string {
  const parts: string[] = []
  if (withMeta) {
    parts.push(`<!-- erp-print: paper=${l.paper ?? 'DOT_241_140'}; margin=${l.margin ?? '5mm 6mm 4mm 6mm'}; rowsPerPage=${l.rowsPerPage ?? 16}; copies=${l.copies ?? ''} -->`)
    parts.push('<!-- 由可视化版式生成（同目录 .layout.json），请勿直接修改；在「系统管理 / 打印模板」复制后可视化调整 -->')
  }
  parts.push(css(Math.min(10, Math.max(8, l.fontSize || 8.5))), head(l), info(l.info), lines(l.columns))
  if (l.memo.visible && l.memo.expr) parts.push(`  <div class="dm-memo">${l.memo.expr}</div>`)
  parts.push(signs(l.signs), foot(l))
  return parts.filter(Boolean).join('\n')
}

/** 变量类型 → 表达式（添加条目时用） */
export function exprOf(path: string, type: string, inLines = false): string {
  const p = inLines ? path.replace(/^lines\./, '') : path
  switch (type) {
    case 'date': return `{{formatDate ${p}}}`
    case 'datetime': return `{{formatDateTime ${p}}}`
    case 'qty': return `{{decAlign (formatQty ${p}) 3}}`
    case 'amount': return `{{decAlign (formatAmount ${p}) 2}}`
    case 'price': return `{{decAlign (formatPrice ${p}) 4}}`
    default: return `{{${p}}}`
  }
}
