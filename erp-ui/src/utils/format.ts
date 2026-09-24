/**
 * 显示格式（UI 设计规范 7.1、7.2）。所有页面统一使用这些函数，不要各自格式化。
 * 数值入参接受 number 或 string（后端 BigDecimal 以字符串或数字返回）。
 */

export const EMPTY = '-'

type Num = number | string | null | undefined

function toNumber(v: Num): number | null {
  if (v === null || v === undefined || v === '') return null
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : null
}

function thousands(fixed: string): string {
  const [int, dec] = fixed.split('.')
  const sign = int.startsWith('-') ? '-' : ''
  const digits = sign ? int.slice(1) : int
  const grouped = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return sign + grouped + (dec !== undefined ? '.' + dec : '')
}

function trimZeros(fixed: string, minDecimals = 0): string {
  if (!fixed.includes('.')) return fixed
  let [int, dec] = fixed.split('.')
  dec = dec.replace(/0+$/, '')
  if (dec.length < minDecimals) dec = dec.padEnd(minDecimals, '0')
  return dec ? `${int}.${dec}` : int
}

/** 数量：千分位；小数位按单位精度，末尾 0 去掉。1,000 / 12.5 */
export function formatQty(v: Num, precision = 4): string {
  const n = toNumber(v)
  if (n === null) return EMPTY
  return thousands(trimZeros(n.toFixed(precision)))
}

/** 金额：千分位，固定小数位（默认 2，币别精度为 0 时传 0）。12,500.00 */
export function formatAmount(v: Num, precision = 2): string {
  const n = toNumber(v)
  if (n === null) return EMPTY
  return thousands(n.toFixed(precision))
}

/** 带币别金额：USD 12,500.00 */
export function formatMoney(v: Num, currency?: string, precision = 2): string {
  const s = formatAmount(v, precision)
  return s === EMPTY || !currency ? s : `${currency} ${s}`
}

/** 单价：最多 6 位小数，末尾 0 去掉，至少 2 位。0.50 / 0.1234 */
export function formatPrice(v: Num): string {
  const n = toNumber(v)
  if (n === null) return EMPTY
  return thousands(trimZeros(n.toFixed(6), 2))
}

/** 汇率：固定 4 位小数 */
export function formatRate(v: Num): string {
  const n = toNumber(v)
  return n === null ? EMPTY : n.toFixed(4)
}

/** 百分比：后端存小数（0.125），显示 12.50% */
export function formatPercent(v: Num, decimals = 2): string {
  const n = toNumber(v)
  return n === null ? EMPTY : `${(n * 100).toFixed(decimals)}%`
}

/** 日期：后端返回 'yyyy-MM-dd' 或 'yyyy-MM-dd HH:mm:ss'，取日期部分 */
export function formatDate(v?: string | null): string {
  return v ? v.slice(0, 10) : EMPTY
}

/** 日期时间：默认到秒；列表中可只到分钟 */
export function formatDateTime(v?: string | null, toMinute = false): string {
  if (!v) return EMPTY
  const s = v.replace('T', ' ')
  return toMinute ? s.slice(0, 16) : s.slice(0, 19)
}

/** 时长：N 天 / N 小时 M 分 */
export function formatDuration(minutes?: number | null): string {
  if (minutes === null || minutes === undefined) return EMPTY
  if (minutes >= 1440 && minutes % 1440 === 0) return `${minutes / 1440} 天`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h ? `${h} 小时 ${m} 分` : `${m} 分`
}

export function isNegative(v: Num): boolean {
  const n = toNumber(v)
  return n !== null && n < 0
}

/** 空值显示 “-” */
export function orEmpty(v: unknown): string {
  return v === null || v === undefined || v === '' ? EMPTY : String(v)
}

/** 今天 yyyy-MM-dd（本地时区） */
export function today(): string {
  return toDateString(new Date())
}

export function toDateString(d: Date): string {
  const p = (x: number) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 按精度四舍五入（HALF_UP），用于前端实时计算展示；最终以后端计算为准 */
export function round(v: number, precision: number): number {
  const f = 10 ** precision
  return Math.sign(v) * Math.round(Math.abs(v) * f + Number.EPSILON) / f
}

export function useFormatter() {
  return { formatQty, formatAmount, formatMoney, formatPrice, formatRate, formatPercent, formatDate, formatDateTime, formatDuration, orEmpty, isNegative }
}
