const nf = new Intl.NumberFormat()
const cf = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 })

export const fmtNum = (n) => (n == null ? '–' : nf.format(n))
export const fmtCompact = (n) => (n == null ? '–' : cf.format(n))
export const pct = (n, total) => (total ? Math.round((100 * (n || 0)) / total) : 0)
export const fmtPct = (v, digits = 0) => (v == null ? '–' : `${Number(v).toFixed(digits)}%`)
export const fmtScore = (v) => (v == null ? '–' : `${v > 0 ? '+' : v < 0 ? '−' : ''}${Math.abs(Number(v)).toFixed(2)}`)
export const fmtDate = (d) => (d ? new Date(d).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '–')
export const fmtDateTime = (d) =>
  d ? new Date(d).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '–'

export function fmtRelative(d) {
  if (!d) return '–'
  const s = Math.round((Date.now() - new Date(d).getTime()) / 1000)
  if (s < 45) return 'just now'
  const m = Math.round(s / 60)
  if (m < 60) return `${m} min ago`
  const h = Math.round(m / 60)
  if (h < 24) return `${h} h ago`
  const days = Math.round(h / 24)
  return days < 30 ? `${days} d ago` : fmtDate(d)
}

export function fmtBytes(b) {
  if (b == null) return '–'
  if (b < 1024) return `${b} B`
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`
  return `${(b / 1024 / 1024).toFixed(1)} MB`
}

export function fmtDuration(ms) {
  if (ms == null) return '–'
  if (ms < 1000) return `${ms} ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`
  return `${Math.floor(ms / 60_000)} m ${Math.round((ms % 60_000) / 1000)} s`
}

/** "2026-03" -> "Mar 26"; ISO weeks and days are returned in a short readable form. */
export function periodLabel(p) {
  if (!p) return ''
  if (/^\d{4}-W\d{2}$/.test(p)) return `W${p.slice(6)}`
  if (/^\d{4}-\d{2}-\d{2}$/.test(p)) return new Date(`${p}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
  if (/^\d{4}-\d{2}$/.test(p)) {
    const [y, m] = p.split('-')
    return new Date(Number(y), Number(m) - 1).toLocaleString(undefined, { month: 'short', year: '2-digit' })
  }
  return p
}

/**
 * Splits review text into plain and highlighted segments for the given aspect opinion snippets.
 * Matching is case-insensitive; overlapping snippets keep the first match.
 */
export function segmentText(text, aspects = []) {
  if (!text) return []
  const lower = text.toLowerCase()
  const spans = []
  for (const a of aspects) {
    if (!a?.snippet) continue
    const i = lower.indexOf(a.snippet.toLowerCase())
    if (i < 0) continue
    const end = i + a.snippet.length
    if (spans.some((s) => i < s.end && end > s.start)) continue
    spans.push({ start: i, end, sentiment: a.sentiment, aspect: a.aspect })
  }
  spans.sort((x, y) => x.start - y.start)
  const out = []
  let pos = 0
  for (const s of spans) {
    if (s.start > pos) out.push({ text: text.slice(pos, s.start) })
    out.push({ text: text.slice(s.start, s.end), sentiment: s.sentiment, aspect: s.aspect })
    pos = s.end
  }
  if (pos < text.length) out.push({ text: text.slice(pos) })
  return out
}

export const SENTIMENT = { POSITIVE: 'Positive', NEGATIVE: 'Negative', NEUTRAL: 'Neutral' }
