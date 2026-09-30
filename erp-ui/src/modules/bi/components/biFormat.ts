import { formatAmount, formatQty, toDateString } from '@/utils/format'
import type { CompareCode, MetricUnit, PeriodCode } from '../api/bi'

/** 金额单位：元 / 万元（专题页可切换） */
export type AmountScale = 1 | 10000

const num = (v: unknown): number | null => (v === null || v === undefined || v === '' || Number.isNaN(Number(v)) ? null : Number(v))

/** 按指标单位格式化；比率类后端已是百分数 */
export function fmtMetric(v: unknown, unit?: MetricUnit, scale: AmountScale = 1): string {
  const n = num(v)
  if (n === null) return '-'
  switch (unit) {
    case 'PERCENT': return `${n.toFixed(1)}%`
    case 'COUNT': return formatAmount(n, 0)
    case 'QTY': return formatQty(n)
    case 'DAYS': return `${n.toFixed(1)} 天`
    case 'PRICE': return formatAmount(n, 4)
    default: return scale === 10000 ? `${formatAmount(n / 10000, 2)} 万` : formatAmount(n, 2)
  }
}

/** 图表坐标轴 / 标签的紧凑数字：1.2万、3.4亿 */
export function fmtCompact(v: unknown, unit?: MetricUnit): string {
  const n = num(v)
  if (n === null) return '-'
  if (unit === 'PERCENT') return `${n.toFixed(0)}%`
  const a = Math.abs(n)
  if (a >= 1e8) return `${(n / 1e8).toFixed(1)}亿`
  if (a >= 1e4) return `${(n / 1e4).toFixed(1)}万`
  return a >= 100 ? n.toFixed(0) : String(Number(n.toFixed(2)))
}

/** 坐标轴上限取整：1 / 2 / 2.5 / 5 × 10^n */
export function niceMax(max: number): number {
  if (max <= 0) return 1
  const p = Math.pow(10, Math.floor(Math.log10(max)))
  for (const m of [1, 2, 2.5, 5, 10]) if (m * p >= max) return m * p
  return 10 * p
}

/** 数值越小越好的指标（变化方向着色相反） */
export const LOWER_IS_BETTER = new Set([
  'sales_return_amount', 'ship_cost', 'ar_balance', 'ar_overdue', 'ar_overdue_ratio', 'dso', 'slow_moving_amount', 'slow_moving_ratio',
  'aged_amount', 'inventory_turnover_days', 'delayed_order_count', 'ncr_count', 'complaint_count', 'purchase_avg_price'
])

export interface Range { from: string; to: string; compareFrom: string; compareTo: string }

const pad = (n: number) => String(n).padStart(2, '0')
const ymd = (y: number, m: number, d: number) => `${y}-${pad(m)}-${pad(d)}`
const lastDay = (y: number, m: number) => new Date(y, m, 0).getDate()
function addMonths(date: string, months: number): { y: number; m: number } {
  const [y, m] = date.split('-').map(Number)
  const t = y * 12 + (m - 1) + months
  return { y: Math.floor(t / 12), m: (t % 12) + 1 }
}

/** 期间与对比期间（同驾驶舱 BI-DSH-R01：环比为上一个同长度期间，同比为去年同期） */
export function periodRange(period: PeriodCode, compare: CompareCode, custom?: [string, string]): Range {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth() + 1
  let from: string
  let to: string
  switch (period) {
    case 'LAST_MONTH': {
      const p = addMonths(ymd(y, m, 1), -1)
      from = ymd(p.y, p.m, 1)
      to = ymd(p.y, p.m, lastDay(p.y, p.m))
      break
    }
    case 'THIS_QUARTER': {
      const qm = Math.floor((m - 1) / 3) * 3 + 1
      from = ymd(y, qm, 1)
      to = ymd(y, qm + 2, lastDay(y, qm + 2))
      break
    }
    case 'THIS_YEAR':
      from = ymd(y, 1, 1)
      to = ymd(y, 12, 31)
      break
    case 'CUSTOM':
      from = custom?.[0] ?? ymd(y, m, 1)
      to = custom?.[1] ?? toDateString(now)
      break
    default:
      from = ymd(y, m, 1)
      to = ymd(y, m, lastDay(y, m))
  }
  if (compare === 'YOY') {
    const [fy, fm, fd] = from.split('-').map(Number)
    const [ty, tm, td] = to.split('-').map(Number)
    return { from, to, compareFrom: ymd(fy - 1, fm, Math.min(fd, lastDay(fy - 1, fm))), compareTo: ymd(ty - 1, tm, Math.min(td, lastDay(ty - 1, tm))) }
  }
  const wholeMonths = from.endsWith('-01') && Number(to.slice(8)) === lastDay(Number(to.slice(0, 4)), Number(to.slice(5, 7)))
  if (period !== 'CUSTOM' || wholeMonths) {
    const months = (Number(to.slice(0, 4)) * 12 + Number(to.slice(5, 7))) - (Number(from.slice(0, 4)) * 12 + Number(from.slice(5, 7))) + 1
    const cf = addMonths(from, -months)
    const ct = addMonths(to, -months)
    return { from, to, compareFrom: ymd(cf.y, cf.m, 1), compareTo: ymd(ct.y, ct.m, lastDay(ct.y, ct.m)) }
  }
  const days = Math.round((Date.parse(to) - Date.parse(from)) / 86400000) + 1
  const shift = (d: string) => {
    const [a, b, c] = d.split('-').map(Number)
    return toDateString(new Date(a, b - 1, c - days))
  }
  return { from, to, compareFrom: shift(from), compareTo: shift(to) }
}

/** 截至 to 所在月的近 n 个月区间 */
export function lastMonths(to: string, n: number): { from: string; to: string } {
  const s = addMonths(to, -(n - 1))
  const e = addMonths(to, 0)
  return { from: ymd(s.y, s.m, 1), to: ymd(e.y, e.m, lastDay(e.y, e.m)) }
}

/** 变化率（%） */
export function changePct(cur?: number | null, prev?: number | null): number | null {
  if (cur === null || cur === undefined || prev === null || prev === undefined || Number(prev) === 0) return null
  return ((Number(cur) - Number(prev)) * 100) / Math.abs(Number(prev))
}

/** 导出 CSV（Excel 可直接打开） */
export function downloadCsv(filename: string, header: string[], rows: (string | number | null | undefined)[][]) {
  const esc = (v: unknown) => {
    const s = v === null || v === undefined ? '' : String(v)
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const text = '﻿' + [header, ...rows].map((r) => r.map(esc).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename.endsWith('.csv') ? filename : `${filename}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
