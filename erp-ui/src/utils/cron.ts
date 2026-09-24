/**
 * Cron（6 段：秒 分 时 日 月 周）转中文说明，覆盖定时任务常见写法；无法识别时返回空串（界面显示原表达式）。
 * 例：0 0 2 * * ? → 每天 02:00；0 0 10 ? * MON-FRI → 每个工作日 10:00；0 0/5 * * * ? → 每 5 分钟
 */
const WEEK: Record<string, string> = { MON: '一', TUE: '二', WED: '三', THU: '四', FRI: '五', SAT: '六', SUN: '日', '1': '日', '2': '一', '3': '二', '4': '三', '5': '四', '6': '五', '7': '六' }

const pad = (v: string) => v.padStart(2, '0')
const isNum = (v: string) => /^\d+$/.test(v)

export function describeCron(cron?: string): string {
  if (!cron) return ''
  const p = cron.trim().split(/\s+/)
  if (p.length !== 6) return ''
  const [sec, min, hour, day, month, week] = p
  const anyDay = day === '*' || day === '?'
  const anyWeek = week === '*' || week === '?'
  if (month !== '*') return ''
  // 每 N 分钟 / 每 N 小时
  const everyMin = /^(0|\*)\/(\d+)$/.exec(min)
  if (everyMin && hour === '*' && anyDay && anyWeek) return `每 ${everyMin[2]} 分钟`
  const everyHour = /^(0|\*)\/(\d+)$/.exec(hour)
  if (everyHour && isNum(min) && anyDay && anyWeek) return `每 ${everyHour[2]} 小时（第 ${min} 分）`
  if (hour === '*' && isNum(min) && anyDay && anyWeek) return `每小时第 ${min} 分`
  if (!isNum(hour) || !isNum(min)) return ''
  const time = `${pad(hour)}:${pad(min)}${sec !== '0' && isNum(sec) ? ':' + pad(sec) : ''}`
  if (anyDay && anyWeek) return `每天 ${time}`
  if (anyDay && !anyWeek) {
    if (/^MON-FRI$/i.test(week) || week === '2-6') return `每个工作日 ${time}`
    const days = week.split(',').map((w) => WEEK[w.toUpperCase()]).filter(Boolean)
    return days.length ? `每周${days.join('、')} ${time}` : ''
  }
  if (isNum(day) && anyWeek) return `每月 ${day} 日 ${time}`
  if (day === 'L' && anyWeek) return `每月最后一天 ${time}`
  return ''
}
