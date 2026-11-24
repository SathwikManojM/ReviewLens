import { motion } from 'framer-motion'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useTheme } from '../lib/providers'
import { fmtNum, fmtScore, pct, periodLabel } from '../lib/format'
import { cx } from './ui'

/** Validated palette (CVD-checked): sentiment is diverging blue <-> red with a grey midpoint. */
const PALETTES = {
  light: { pos: '#2a78d6', neg: '#e34948', neu: '#b4b3ad', grid: 'rgba(17,17,19,0.06)', axis: '#8c8c94', surface: '#ffffff', accent: '#0071e3', indigo: '#5856d6', series: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100'] },
  dark: { pos: '#3987e5', neg: '#e66767', neu: '#6f6e69', grid: 'rgba(255,255,255,0.07)', axis: '#6f6f78', surface: '#151517', accent: '#2997ff', indigo: '#7d7aff', series: ['#3987e5', '#d95926', '#199e70', '#c98500'] },
}
export const usePalette = () => PALETTES[useTheme().resolved]

const axis = (c) => ({ tick: { fill: c.axis, fontSize: 11 }, axisLine: false, tickLine: false })

export function ChartTooltip({ active, payload, label, formatter, labelFormatter }) {
  if (!active || !payload?.length) return null
  return (
    <div className="min-w-[150px] rounded-xl border border-line bg-surface/95 px-3 py-2.5 text-xs shadow-pop backdrop-blur">
      <div className="mb-1.5 font-medium text-ink">{labelFormatter ? labelFormatter(label) : label}</div>
      {payload.map((p) => (
        <div key={p.dataKey} className="flex items-center justify-between gap-4 py-0.5 text-ink-2">
          <span className="flex items-center gap-2"><i className="size-2 rounded-sm" style={{ background: p.color || p.fill }} />{p.name}</span>
          <b className="tnum font-medium text-ink">{formatter ? formatter(p.value, p) : fmtNum(p.value)}</b>
        </div>
      ))}
    </div>
  )
}

export function Legend({ items, className }) {
  return (
    <div className={cx('flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-ink-2', className)}>
      {items.map((i) => <span key={i.label} className="flex items-center gap-1.5"><i className="size-2.5 rounded-[3px]" style={{ background: i.color }} />{i.label}</span>)}
    </div>
  )
}

/** Diverging bars: share of negative mentions to the left, positive to the right, per aspect. */
export function AspectSentimentChart({ aspects, onSelect, selected }) {
  const c = usePalette()
  const rows = aspects.filter((a) => a.mentions > 0)
  if (!rows.length) return <p className="px-5 py-12 text-center text-sm text-ink-3">No aspects detected in this scope.</p>
  return (
    <div className="px-5 pb-5">
      <div className="mb-2 grid grid-cols-[minmax(92px,150px)_1fr_1fr_52px] items-center gap-3 text-[11px] font-medium uppercase tracking-wider text-ink-3">
        <span>Aspect</span><span className="text-right">Negative</span><span>Positive</span><span className="text-right">Net</span>
      </div>
      <ul className="space-y-1">
        {rows.map((a, i) => {
          const neg = pct(a.negative, a.mentions)
          const pos = pct(a.positive, a.mentions)
          return (
            <li key={a.aspect}>
              <button type="button" onClick={() => onSelect?.(a.aspect)}
                title={`${a.aspect}: ${a.positive} positive, ${a.negative} negative, ${a.neutral} neutral of ${a.mentions} mentions`}
                className={cx('grid w-full grid-cols-[minmax(92px,150px)_1fr_1fr_52px] items-center gap-3 rounded-lg px-1.5 py-1.5 text-left transition hover:bg-subtle',
                  selected === a.aspect && 'bg-subtle ring-1 ring-line')}>
                <span className="min-w-0">
                  <span className="block truncate text-[13px] font-medium text-ink">{a.aspect}</span>
                  <span className="tnum block text-[11px] text-ink-3">{fmtNum(a.mentions)} mentions</span>
                </span>
                <span className="flex h-[18px] justify-end rounded-l-md bg-subtle/60">
                  <motion.span className="flex h-full items-center justify-start rounded-l-md pl-1.5" style={{ background: c.neg }}
                    initial={{ width: 0 }} animate={{ width: `${neg}%` }} transition={{ duration: 0.7, delay: i * 0.03, ease: [0.22, 1, 0.36, 1] }}>
                    {neg >= 14 && <span className="tnum text-[10.5px] font-semibold text-white">{neg}%</span>}
                  </motion.span>
                </span>
                <span className="flex h-[18px] rounded-r-md border-l-2 border-ink/20 bg-subtle/60">
                  <motion.span className="flex h-full items-center justify-end rounded-r-md pr-1.5" style={{ background: c.pos }}
                    initial={{ width: 0 }} animate={{ width: `${pos}%` }} transition={{ duration: 0.7, delay: i * 0.03, ease: [0.22, 1, 0.36, 1] }}>
                    {pos >= 14 && <span className="tnum text-[10.5px] font-semibold text-white">{pos}%</span>}
                  </motion.span>
                </span>
                <span className={cx('tnum text-right text-[13px] font-semibold', a.netSentiment > 0.05 ? 'text-pos' : a.netSentiment < -0.05 ? 'text-neg' : 'text-ink-2')}>
                  {fmtScore(a.netSentiment)}
                </span>
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/** Overall sentiment distribution: one segmented bar plus large figures. */
export function SentimentChart({ distribution = {}, total = 0 }) {
  const c = usePalette()
  const parts = [['POSITIVE', 'Positive', c.pos], ['NEUTRAL', 'Neutral', c.neu], ['NEGATIVE', 'Negative', c.neg]]
  return (
    <div>
      <div className="flex h-3 gap-[3px] overflow-hidden rounded-full bg-subtle">
        {parts.map(([k, label, color], i) => (distribution[k] ? (
          <motion.div key={k} title={`${label}: ${fmtNum(distribution[k])}`} style={{ background: color }} className="h-full first:rounded-l-full last:rounded-r-full"
            initial={{ width: 0 }} animate={{ width: `${(100 * distribution[k]) / (total || 1)}%` }} transition={{ duration: 0.8, delay: i * 0.08, ease: [0.22, 1, 0.36, 1] }} />
        ) : null))}
      </div>
      <div className="mt-4 grid grid-cols-3 gap-3">
        {parts.map(([k, label, color]) => (
          <div key={k}>
            <div className="flex items-center gap-1.5 text-xs text-ink-2"><i className="size-2 rounded-full" style={{ background: color }} />{label}</div>
            <div className="tnum mt-1 text-[22px] font-semibold tracking-tight">{pct(distribution[k], total)}%</div>
            <div className="tnum text-xs text-ink-3">{fmtNum(distribution[k] || 0)} reviews</div>
          </div>
        ))}
      </div>
    </div>
  )
}

/** Star-rating distribution (5 -> 1). */
export function RatingChart({ ratings = {} }) {
  const total = Object.values(ratings).reduce((s, v) => s + v, 0)
  return (
    <ul className="space-y-2.5">
      {[5, 4, 3, 2, 1].map((r, i) => {
        const v = ratings[r] || 0
        return (
          <li key={r} className="grid grid-cols-[28px_1fr_44px] items-center gap-3 text-[13px]">
            <span className="tnum font-medium text-ink-2">{r}★</span>
            <span className="h-2 overflow-hidden rounded-full bg-subtle">
              <motion.span className="block h-full rounded-full bg-amber-400" initial={{ width: 0 }} animate={{ width: `${pct(v, total)}%` }}
                transition={{ duration: 0.7, delay: i * 0.05 }} />
            </span>
            <span className="tnum text-right text-ink-3">{pct(v, total)}%</span>
          </li>
        )
      })}
    </ul>
  )
}

export function TrendChart({ data, height = 260 }) {
  const c = usePalette()
  if (!data?.length || data.length < 2) return <p className="py-16 text-center text-sm text-ink-3">Not enough dated reviews to show a trend.</p>
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ left: -18, right: 8, top: 8, bottom: 0 }}>
        <defs>
          {[['pos', c.pos], ['neg', c.neg], ['neu', c.neu]].map(([k, col]) => (
            <linearGradient key={k} id={`g-${k}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={col} stopOpacity={0.28} /><stop offset="100%" stopColor={col} stopOpacity={0.02} />
            </linearGradient>
          ))}
        </defs>
        <CartesianGrid vertical={false} stroke={c.grid} />
        <XAxis dataKey="month" tickFormatter={periodLabel} minTickGap={20} {...axis(c)} />
        <YAxis allowDecimals={false} {...axis(c)} />
        <Tooltip content={<ChartTooltip labelFormatter={periodLabel} />} cursor={{ stroke: c.axis, strokeDasharray: '3 3' }} />
        <Area type="monotone" dataKey="positive" name="Positive" stroke={c.pos} strokeWidth={2} fill="url(#g-pos)" isAnimationActive={false} activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
        <Area type="monotone" dataKey="negative" name="Negative" stroke={c.neg} strokeWidth={2} fill="url(#g-neg)" isAnimationActive={false} activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
        <Area type="monotone" dataKey="neutral" name="Neutral" stroke={c.neu} strokeWidth={1.5} fill="url(#g-neu)" isAnimationActive={false} activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
      </AreaChart>
    </ResponsiveContainer>
  )
}

export function AspectTrendChart({ data, aspects, height = 260 }) {
  const c = usePalette()
  if (!data?.length || data.length < 2) return <p className="py-16 text-center text-sm text-ink-3">Not enough dated reviews to show a trend.</p>
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ left: -18, right: 8, top: 8 }}>
        <CartesianGrid vertical={false} stroke={c.grid} />
        <XAxis dataKey="month" tickFormatter={periodLabel} minTickGap={20} {...axis(c)} />
        <YAxis domain={[-1, 1]} ticks={[-1, -0.5, 0, 0.5, 1]} {...axis(c)} />
        <ReferenceLine y={0} stroke={c.axis} strokeDasharray="3 3" />
        <Tooltip content={<ChartTooltip labelFormatter={periodLabel} formatter={(v) => fmtScore(v)} />} />
        {aspects.map((a, i) => (
          <Line key={a} dataKey={a} name={a} stroke={c.series[i % 4]} strokeWidth={2} dot={false} connectNulls isAnimationActive={false}
            activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
        ))}
      </LineChart>
    </ResponsiveContainer>
  )
}

export function ProductComparisonChart({ rows, products, colorOf, height = 320 }) {
  const c = usePalette()
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={rows} margin={{ left: -14, right: 8, top: 8 }} barGap={3} barCategoryGap="24%">
        <CartesianGrid vertical={false} stroke={c.grid} />
        <XAxis dataKey="aspect" interval={0} tick={{ fill: c.axis, fontSize: 11 }} axisLine={false} tickLine={false}
          angle={rows.length > 6 ? -28 : 0} textAnchor={rows.length > 6 ? 'end' : 'middle'} height={rows.length > 6 ? 64 : 28} />
        <YAxis domain={[-1, 1]} ticks={[-1, -0.5, 0, 0.5, 1]} {...axis(c)} />
        <ReferenceLine y={0} stroke={c.axis} />
        <Tooltip cursor={{ fill: c.grid }} content={<ChartTooltip formatter={(v, p) => `${fmtScore(v)} · ${p.payload[`m_${p.dataKey.slice(2)}`] || 0} mentions`} />} />
        {products.map((p) => (
          <Bar key={p.productId} dataKey={`p_${p.productId}`} name={p.name.length > 30 ? `${p.name.slice(0, 30)}…` : p.name}
            fill={colorOf(p.productId)} radius={[4, 4, 4, 4]} maxBarSize={22} isAnimationActive={false} />
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}

/** Generic time series for the admin portal: area (default) or bars, optional stacking. */
export function SeriesChart({ data, series, type = 'area', stacked, height = 240, xKey = 'period' }) {
  const c = usePalette()
  const Chart = type === 'bar' ? BarChart : AreaChart
  return (
    <ResponsiveContainer width="100%" height={height}>
      <Chart data={data} margin={{ left: -18, right: 8, top: 8 }}>
        <CartesianGrid vertical={false} stroke={c.grid} />
        <XAxis dataKey={xKey} tickFormatter={periodLabel} minTickGap={16} {...axis(c)} />
        <YAxis allowDecimals={false} {...axis(c)} />
        <Tooltip cursor={type === 'bar' ? { fill: c.grid } : { stroke: c.axis, strokeDasharray: '3 3' }} content={<ChartTooltip labelFormatter={periodLabel} />} />
        {series.map((s) => (type === 'bar'
          ? <Bar key={s.key} dataKey={s.key} name={s.label} fill={s.color} stackId={stacked ? 'a' : undefined} radius={stacked ? 0 : [4, 4, 0, 0]} maxBarSize={26} isAnimationActive={false} />
          : <Area key={s.key} type="monotone" dataKey={s.key} name={s.label} stroke={s.color} strokeWidth={2} fill={s.color} fillOpacity={0.1} stackId={stacked ? 'a' : undefined} isAnimationActive={false} />))}
      </Chart>
    </ResponsiveContainer>
  )
}

/** Weekday x hour activity heatmap (UTC); sequential single-hue scale. */
export function Heatmap({ cells }) {
  const max = Math.max(1, ...cells.map((c) => c.count))
  const map = new Map(cells.map((c) => [`${c.dayOfWeek}:${c.hour}`, c.count]))
  const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
  return (
    <div className="overflow-x-auto">
      <div className="min-w-[560px]">
        <div className="ml-10 grid grid-cols-24 gap-[3px] text-[10px] text-ink-3" style={{ gridTemplateColumns: 'repeat(24, minmax(0, 1fr))' }}>
          {Array.from({ length: 24 }, (_, h) => <span key={h} className="text-center">{h % 3 === 0 ? h : ''}</span>)}
        </div>
        {days.map((d, i) => (
          <div key={d} className="mt-[3px] flex items-center gap-2">
            <span className="w-8 text-[11px] text-ink-3">{d}</span>
            <div className="grid flex-1 gap-[3px]" style={{ gridTemplateColumns: 'repeat(24, minmax(0, 1fr))' }}>
              {Array.from({ length: 24 }, (_, h) => {
                const v = map.get(`${i + 1}:${h}`) || 0
                return <span key={h} title={`${d} ${h}:00 UTC · ${v} events`} className="aspect-square rounded-[3px]"
                  style={{ background: v ? `color-mix(in srgb, var(--accent) ${18 + (82 * v) / max}%, transparent)` : 'var(--subtle)' }} />
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
