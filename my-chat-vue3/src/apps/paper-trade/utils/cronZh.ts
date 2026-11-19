/**
 * Spring 6 域 cron（秒 分 时 日 月 星期）→ 简短中文。
 * 覆盖本应用常用写法；解析不了时回退为原表达式。
 */

/** 各角色默认 cron（与后端 PaperScheduleService / Properties 一致）。 */
export const DEFAULT_CRONS = {
  research: '0 0 9 * * *',
  trader: '0 35 15 * * MON-FRI',
  analyst: '0 0 18 * * *',
  cleanup: '0 0 23 * * *',
  evaluator: '0 0 10 * * SAT',
} as const

const DOW_NAME: Record<string, string> = {
  SUN: '周日',
  MON: '周一',
  TUE: '周二',
  WED: '周三',
  THU: '周四',
  FRI: '周五',
  SAT: '周六',
  '0': '周日',
  '1': '周一',
  '2': '周二',
  '3': '周三',
  '4': '周四',
  '5': '周五',
  '6': '周六',
  '7': '周日',
}

/** 将 cron 译为自然语言；空或非法时给可读回退。 */
export function describeSpringCron(cron: string | null | undefined): string {
  const raw = (cron ?? '').trim()
  if (!raw) return '（空）'
  const parts = raw.split(/\s+/)
  if (parts.length !== 6) return raw

  const [, minute, hour, dom, month, dow] = parts
  const when = describeDay(dom, month, dow)
  const clock = describeClock(minute, hour)
  if (!clock) return raw
  return `${when}${clock}`
}

/** 「默认值…；当前为…」一行说明。 */
export function cronHintLine(defaultCron: string, currentCron: string): string {
  return `默认值${describeSpringCron(defaultCron)}；当前为${describeSpringCron(currentCron)}`
}

function describeDay(dom: string, month: string, dow: string): string {
  const m = month.toUpperCase()
  const d = dom.toUpperCase()
  const w = dow.toUpperCase()

  if (m !== '*' && m !== '?') {
    const monthPart = describeMonth(m)
    const dayPart = d === '*' || d === '?' ? '' : ` ${d} 日`
    const weekPart = w === '*' || w === '?' ? '' : describeDow(w)
    return [monthPart + dayPart, weekPart].filter(Boolean).join(' ') + ' '
  }

  if (d !== '*' && d !== '?') {
    return `每月 ${d} 日 `
  }

  return describeDow(w) + ' '
}

function describeDow(field: string): string {
  const f = field.toUpperCase()
  if (f === '*' || f === '?') return '每天'
  if (f === 'MON-FRI' || f === '1-5') return '工作日'
  if (f === 'SAT-SUN' || f === '6-7' || f === '6-0') return '周末'
  if (f.includes(',')) {
    return f
      .split(',')
      .map((x) => mapDowToken(x.trim()))
      .join('、')
  }
  if (/^[A-Z]{3}-[A-Z]{3}$/.test(f) || /^\d-\d$/.test(f)) {
    const [a, b] = f.split('-')
    return `${mapDowToken(a)}至${mapDowToken(b)}`
  }
  return mapDowToken(f)
}

function mapDowToken(token: string): string {
  return DOW_NAME[token.toUpperCase()] || token
}

function describeMonth(field: string): string {
  const months: Record<string, string> = {
    JAN: '1 月',
    FEB: '2 月',
    MAR: '3 月',
    APR: '4 月',
    MAY: '5 月',
    JUN: '6 月',
    JUL: '7 月',
    AUG: '8 月',
    SEP: '9 月',
    OCT: '10 月',
    NOV: '11 月',
    DEC: '12 月',
  }
  if (/^\d+$/.test(field)) return `${Number(field)} 月`
  return months[field] || field
}

/** 时分；含步长/列表时给简短描述。 */
function describeClock(minute: string, hour: string): string | null {
  if (/^\d+$/.test(minute) && /^\d+$/.test(hour)) {
    return `${pad(hour)}:${pad(minute)}`
  }
  if (minute === '0' && hour.includes('-') && !hour.includes('/')) {
    return `${hour.replace('-', '–')} 点整点`
  }
  if (minute.startsWith('*/') && hour === '*') {
    return `每 ${minute.slice(2)} 分钟`
  }
  if (minute.includes('/') && hour === '*') {
    const step = minute.split('/')[1]
    return step ? `每 ${step} 分钟` : null
  }
  if (minute === '0' && hour.startsWith('*/')) {
    return `每 ${hour.slice(2)} 小时整点`
  }
  if (minute === '0' && hour === '*') {
    return '每小时整点'
  }
  if (/^\d+$/.test(minute) && hour.includes(',')) {
    return `${hour
      .split(',')
      .map((h) => `${pad(h)}:${pad(minute)}`)
      .join('、')}`
  }
  if (/^\d+$/.test(minute) && hour.includes('-') && hour.includes('/')) {
    return `${hour} 分=${minute}`
  }
  // 回退：尽量拼可读片段
  if (/^\d+$/.test(minute)) {
    return `${hour} 时 ${pad(minute)} 分`
  }
  return `${hour}:${minute}`
}

function pad(n: string): string {
  return n.padStart(2, '0')
}
