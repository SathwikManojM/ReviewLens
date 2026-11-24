import { useEffect, useRef } from 'react'
import { animate, motion, useInView } from 'framer-motion'
import { AppWindow, BadgeDollarSign, BatteryMedium, Camera, Gauge, Hammer, HardDrive, LifeBuoy, Lightbulb, MessageSquareText, Smartphone, Tag, Thermometer, Truck, Volume2, Wifi } from 'lucide-react'
import { fmtDate, fmtNum, fmtScore, segmentText } from '../lib/format'
import { Badge, Card, Drawer, EmptyState, Field, Input, SentimentBadge, Select, Segmented, Stars, TD, TH, cx } from './ui'

export const ASPECT_ICONS = {
  Camera, Battery: BatteryMedium, Display: Smartphone, Price: BadgeDollarSign, Performance: Gauge, 'Build Quality': Hammer,
  Sound: Volume2, Software: AppWindow, Storage: HardDrive, Connectivity: Wifi, Heating: Thermometer,
  'Delivery & Packaging': Truck, 'Customer Service': LifeBuoy,
}

export function AspectIcon({ aspect, className }) {
  const Icon = ASPECT_ICONS[aspect] || Tag
  return <Icon className={className} />
}

function CountUp({ value, format = fmtNum, decimals = 0 }) {
  const ref = useRef(null)
  const inView = useInView(ref, { once: true })
  useEffect(() => {
    if (!inView || typeof value !== 'number' || !ref.current) return undefined
    const controls = animate(0, value, {
      duration: 0.9, ease: [0.22, 1, 0.36, 1],
      onUpdate: (v) => { if (ref.current) ref.current.textContent = format(decimals ? Number(v.toFixed(decimals)) : Math.round(v)) },
    })
    return () => controls.stop()
  }, [inView, value, format, decimals])
  // The real value is always rendered; the count-up is only a progressive enhancement.
  return <span ref={ref}>{typeof value === 'number' ? format(value) : value}</span>
}

export function MetricCard({ label, value, suffix, sub, icon: Icon, decimals = 0, format, delay = 0, tone }) {
  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4, delay }}
      className="card relative overflow-hidden p-5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[13px] font-medium text-ink-2">{label}</span>
        {Icon && <span className="grid size-7 place-items-center rounded-lg bg-subtle ring-1 ring-line"><Icon className="size-3.5 text-ink-3" /></span>}
      </div>
      <div className={cx('tnum mt-3 text-[30px] font-semibold leading-none tracking-[-0.03em]', tone)}>
        {value == null ? '–' : <CountUp value={value} decimals={decimals} format={format} />}{value != null && suffix}
      </div>
      {sub && <div className="mt-2 text-xs text-ink-3">{sub}</div>}
    </motion.div>
  )
}

export function InsightCard({ title = 'Customer insight', text, note }) {
  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.45, delay: 0.1 }}
      className="relative overflow-hidden rounded-2xl border border-accent/20 bg-accent-soft/60 p-5 sm:p-6">
      <div className="flex gap-4">
        <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-accent text-white shadow-sm shadow-accent/30"><Lightbulb className="size-[18px]" /></span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-[13px] font-semibold text-accent-ink">{title}</h3>
            {note && <span className="text-[11.5px] text-ink-3">· {note}</span>}
          </div>
          <p className="mt-1.5 text-[15.5px] leading-relaxed text-ink">{text}</p>
        </div>
      </div>
    </motion.div>
  )
}

/** Dataset, product and date-range filters (all values come from the backend). */
export function ScopeBar({ scope, showProduct = true, showDates = true, children }) {
  const { datasets, datasetId, products, productId, from, to, dataset } = scope
  const max = dataset?.maxReviewDate
  const preset = !from && !to ? 'all' : (to === max && from) ? from : 'custom'
  const ago = (months) => {
    const d = new Date(`${max}T00:00:00Z`)
    d.setUTCMonth(d.getUTCMonth() - months)
    return d.toISOString().slice(0, 10)
  }
  const presets = max ? [{ value: 'all', label: 'All time' }, { value: ago(12), label: '12M' }, { value: ago(6), label: '6M' }, { value: ago(3), label: '3M' }] : []
  return (
    <Card className="mb-6 flex flex-wrap items-end gap-3 p-3 sm:p-4">
      <Field label="Dataset" className="w-full sm:w-60">
        <Select value={datasetId || ''} onChange={(e) => scope.setDataset(e.target.value)} aria-label="Dataset">
          {(datasets || []).map((d) => <option key={d.id} value={d.id}>{d.name} · {fmtNum(d.totalReviews)} reviews</option>)}
        </Select>
      </Field>
      {showProduct && (
        <Field label="Product" className="w-full sm:w-72">
          <Select value={productId} onChange={(e) => scope.setProduct(e.target.value)} aria-label="Product">
            <option value="">All products</option>
            {products.map((p) => <option key={p.id} value={p.id}>{p.name.length > 64 ? `${p.name.slice(0, 64)}…` : p.name} ({fmtNum(p.reviewCount)})</option>)}
          </Select>
        </Field>
      )}
      {showDates && max && (
        <div className="flex flex-wrap items-end gap-2">
          <Field label="Period (relative to the latest review)">
            <Segmented value={presets.some((p) => p.value === preset) ? preset : 'custom'} options={presets}
              onChange={(v) => (v === 'all' ? scope.setRange('', '') : scope.setRange(v, max))} />
          </Field>
          <div className="flex items-center gap-1.5">
            <Input type="date" aria-label="From date" className="w-[150px]" value={from} min={dataset?.minReviewDate} max={to || max}
              onChange={(e) => scope.setRange(e.target.value, to)} />
            <span className="text-ink-3">–</span>
            <Input type="date" aria-label="To date" className="w-[150px]" value={to} min={from || dataset?.minReviewDate} max={max}
              onChange={(e) => scope.setRange(from, e.target.value)} />
          </div>
        </div>
      )}
      {children}
    </Card>
  )
}

export function KeywordChips({ items = [], max = 24 }) {
  if (!items.length) return <p className="text-sm text-ink-3">No keywords for this scope.</p>
  const top = Math.max(...items.map((k) => k.frequency))
  return (
    <div className="flex flex-wrap gap-1.5">
      {items.slice(0, max).map((k) => (
        <span key={k.term} className="rounded-full border border-line bg-subtle px-2.5 py-1 text-ink"
          style={{ fontSize: `${12 + Math.round((4 * k.frequency) / top)}px` }}>
          {k.term}<span className="tnum ml-1.5 text-[11px] text-ink-3">{k.frequency}</span>
        </span>
      ))}
    </div>
  )
}

export function TermBars({ items = [], color, max = 8, empty = 'Not enough opinions yet.' }) {
  if (!items.length) return <p className="text-sm text-ink-3">{empty}</p>
  const top = Math.max(...items.map((k) => k.frequency))
  return (
    <ul className="space-y-2.5">
      {items.slice(0, max).map((k, i) => (
        <li key={k.term} className="grid grid-cols-[1fr_96px_34px] items-center gap-3 text-[13px]">
          <span className="truncate text-ink">{k.term}</span>
          <span className="h-1.5 overflow-hidden rounded-full bg-subtle">
            <motion.span className="block h-full rounded-full" style={{ background: color }} initial={{ width: 0 }}
              animate={{ width: `${(100 * k.frequency) / top}%` }} transition={{ duration: 0.6, delay: i * 0.04 }} />
          </span>
          <span className="tnum text-right text-xs text-ink-3">{k.frequency}</span>
        </li>
      ))}
    </ul>
  )
}

export function HighlightedText({ text, aspects, className }) {
  return (
    <p className={cx('leading-relaxed text-ink', className)}>
      {segmentText(text, aspects).map((s, i) => (s.sentiment ? (
        <mark key={i} title={`${s.aspect} · ${s.sentiment.toLowerCase()}`}
          className={cx('rounded-[4px] px-0.5 text-inherit', s.sentiment === 'POSITIVE' ? 'bg-pos/15 underline decoration-pos/50 decoration-2 underline-offset-4'
            : s.sentiment === 'NEGATIVE' ? 'bg-neg/15 underline decoration-neg/50 decoration-2 underline-offset-4' : 'bg-muted')}>
          {s.text}
        </mark>
      ) : <span key={i}>{s.text}</span>))}
    </p>
  )
}

export function AspectChips({ aspects = [], max = 4 }) {
  return (
    <div className="flex flex-wrap gap-1">
      {aspects.slice(0, max).map((a) => (
        <span key={a.aspect} className="inline-flex items-center gap-1 rounded-md bg-subtle px-1.5 py-0.5 text-[11.5px] text-ink-2 ring-1 ring-inset ring-line">
          <i className={cx('size-1.5 rounded-full', a.sentiment === 'POSITIVE' ? 'bg-pos' : a.sentiment === 'NEGATIVE' ? 'bg-neg' : 'bg-neu')} />{a.aspect}
        </span>
      ))}
      {aspects.length > max && <span className="text-[11.5px] text-ink-3">+{aspects.length - max}</span>}
    </div>
  )
}

/** Searchable review table row list; click or Enter opens the detail drawer. */
export function ReviewTable({ rows, onOpen, extraColumn }) {
  if (!rows.length) return <EmptyState icon={MessageSquareText} title="No reviews match these filters">Try removing a filter or searching for a different word.</EmptyState>
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[760px] text-sm">
        <thead><tr><th className={TH}>Rating</th><th className={TH}>Review</th><th className={TH}>Product</th><th className={TH}>Aspects</th><th className={TH}>Sentiment</th><th className={TH}>Date</th>{extraColumn && <th className={TH} />}</tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} tabIndex={0} onClick={() => onOpen(r)} onKeyDown={(e) => e.key === 'Enter' && onOpen(r)}
              className="cursor-pointer transition-colors hover:bg-subtle focus:bg-subtle focus:outline-none">
              <td className={TD}><Stars rating={r.rating} /></td>
              <td className={cx(TD, 'max-w-[380px]')}>
                {r.title && <div className="truncate font-medium text-ink">{r.title}</div>}
                <div className="line-clamp-2 text-[13px] leading-snug text-ink-2">{r.text}</div>
              </td>
              <td className={cx(TD, 'max-w-[180px] truncate text-[13px] text-ink-2')}>{r.productName}</td>
              <td className={TD}><AspectChips aspects={r.aspects} max={3} /></td>
              <td className={TD}><SentimentBadge value={r.overallSentiment} /></td>
              <td className={cx(TD, 'whitespace-nowrap text-[13px] text-ink-3')}>{r.date ? fmtDate(r.date) : '–'}</td>
              {extraColumn && <td className={TD} onClick={(e) => e.stopPropagation()}>{extraColumn(r)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function NoDatasets() {
  return (
    <Card>
      <EmptyState icon={MessageSquareText} title="No analysed datasets yet"
        action={<a href="/app/datasets" className="inline-flex h-9 items-center rounded-lg bg-accent px-3.5 text-sm font-medium text-white">Upload a dataset</a>}>
        Upload a public Amazon review CSV. Results appear here as soon as processing completes.
      </EmptyState>
    </Card>
  )
}

export function ReviewDrawer({ review, onClose, actions }) {
  return (
    <Drawer open={Boolean(review)} onClose={onClose} title={review?.title || 'Review'} actions={actions}
      subtitle={review && `${review.productName}${review.date ? ` · ${fmtDate(review.date)}` : ''}${review.reviewer ? ` · ${review.reviewer}` : ''}`}>
      {review && (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-2">
            <Stars rating={review.rating} />
            <SentimentBadge value={review.overallSentiment}>Overall {review.overallSentiment?.toLowerCase()} · {fmtScore(review.overallScore)}</SentimentBadge>
            {review.duplicate && <Badge tone="warning">Duplicate</Badge>}
            {(review.qualityFlags || []).filter((f) => f !== 'DUPLICATE').map((f) => <Badge key={f}>{f.replace('_', ' ').toLowerCase()}</Badge>)}
          </div>
          <HighlightedText text={review.text} aspects={review.aspects} className="text-[15px]" />
          <div>
            <h4 className="eyebrow mb-3">Extracted aspects</h4>
            {review.aspects.length === 0 ? <p className="text-sm text-ink-3">No product aspects detected.</p> : (
              <ul className="divide-y divide-line rounded-xl border border-line">
                {review.aspects.map((a) => (
                  <li key={a.aspect} className="flex items-start gap-3 px-4 py-3">
                    <AspectIcon aspect={a.aspect} className="mt-0.5 size-4 text-ink-3" />
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-medium">{a.aspect}</span>
                        <SentimentBadge value={a.sentiment} />
                        <span className="tnum text-xs text-ink-3">score {fmtScore(a.score)} · term “{a.term}”</span>
                      </div>
                      <p className="mt-1 text-[13px] italic text-ink-2">“{a.snippet}”</p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </Drawer>
  )
}
