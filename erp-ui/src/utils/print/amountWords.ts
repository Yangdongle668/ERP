/** 金额大写：人民币（中文模板）与英文（Invoice / PI） */

const CN_DIGITS = '零壹贰叁肆伍陆柒捌玖'
const CN_UNITS = ['', '拾', '佰', '仟']
const CN_GROUPS = ['', '万', '亿', '万亿']

/** 人民币大写：1234.56 → 壹仟贰佰叁拾肆元伍角陆分；100 → 壹佰元整 */
export function amountInWordsCn(value: unknown): string {
  const n = Number(value)
  if (value === null || value === undefined || value === '' || !Number.isFinite(n)) return ''
  const negative = n < 0
  const cents = Math.round(Math.abs(n) * 100)
  const yuan = Math.floor(cents / 100)
  const jiao = Math.floor((cents % 100) / 10)
  const fen = cents % 10
  let result = ''
  if (yuan > 0) {
    const groups: number[] = []
    let rest = yuan
    while (rest > 0) {
      groups.push(rest % 10000)
      rest = Math.floor(rest / 10000)
    }
    let zero = false
    for (let g = groups.length - 1; g >= 0; g--) {
      const group = groups[g]
      if (group === 0) {
        zero = true
        continue
      }
      if (zero || (g < groups.length - 1 && group < 1000)) result += '零'
      zero = false
      let part = ''
      let innerZero = false
      for (let i = 3; i >= 0; i--) {
        const d = Math.floor(group / 10 ** i) % 10
        if (d === 0) {
          innerZero = part.length > 0
          continue
        }
        if (innerZero) part += '零'
        innerZero = false
        part += CN_DIGITS[d] + CN_UNITS[i]
      }
      result += part + CN_GROUPS[g]
    }
    result = result.replace(/^零/, '') + '元'
  }
  if (jiao === 0 && fen === 0) result += yuan > 0 ? '整' : '零元整'
  else {
    if (jiao > 0) result += CN_DIGITS[jiao] + '角'
    else if (yuan > 0) result += '零'
    if (fen > 0) result += CN_DIGITS[fen] + '分'
  }
  return (negative ? '负' : '') + result
}

const ONES = ['', 'ONE', 'TWO', 'THREE', 'FOUR', 'FIVE', 'SIX', 'SEVEN', 'EIGHT', 'NINE', 'TEN', 'ELEVEN', 'TWELVE', 'THIRTEEN',
  'FOURTEEN', 'FIFTEEN', 'SIXTEEN', 'SEVENTEEN', 'EIGHTEEN', 'NINETEEN']
const TENS = ['', '', 'TWENTY', 'THIRTY', 'FORTY', 'FIFTY', 'SIXTY', 'SEVENTY', 'EIGHTY', 'NINETY']
const SCALES = ['', 'THOUSAND', 'MILLION', 'BILLION', 'TRILLION']
const CURRENCY_WORDS: Record<string, [string, string]> = {
  USD: ['US DOLLARS', 'CENTS'], EUR: ['EUROS', 'CENTS'], GBP: ['POUNDS STERLING', 'PENCE'], HKD: ['HONG KONG DOLLARS', 'CENTS'],
  CNY: ['CHINESE YUAN', 'FEN'], JPY: ['JAPANESE YEN', 'SEN'], AUD: ['AUSTRALIAN DOLLARS', 'CENTS'], CAD: ['CANADIAN DOLLARS', 'CENTS'],
  SGD: ['SINGAPORE DOLLARS', 'CENTS']
}

function below1000(n: number): string {
  const parts: string[] = []
  if (n >= 100) {
    parts.push(ONES[Math.floor(n / 100)], 'HUNDRED')
    n %= 100
    if (n) parts.push('AND')
  }
  if (n >= 20) parts.push(TENS[Math.floor(n / 10)] + (n % 10 ? '-' + ONES[n % 10] : ''))
  else if (n > 0) parts.push(ONES[n])
  return parts.join(' ')
}

function integerWords(n: number): string {
  if (n === 0) return 'ZERO'
  const parts: string[] = []
  let scale = 0
  while (n > 0) {
    const chunk = n % 1000
    if (chunk) parts.unshift(below1000(chunk) + (SCALES[scale] ? ' ' + SCALES[scale] : ''))
    n = Math.floor(n / 1000)
    scale++
  }
  return parts.join(' ')
}

/** 英文大写：SAY US DOLLARS ONE THOUSAND TWO HUNDRED AND CENTS FIFTY ONLY */
export function amountInWordsEn(value: unknown, currency = 'USD'): string {
  const n = Number(value)
  if (value === null || value === undefined || value === '' || !Number.isFinite(n)) return ''
  const cents = Math.round(Math.abs(n) * 100)
  const [major, minor] = CURRENCY_WORDS[currency] ?? [currency, 'CENTS']
  const whole = integerWords(Math.floor(cents / 100))
  const frac = cents % 100
  return `SAY ${n < 0 ? 'MINUS ' : ''}${major} ${whole}${frac ? ` AND ${minor} ${integerWords(frac)}` : ''} ONLY`
}
