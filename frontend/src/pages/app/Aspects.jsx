import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowUpRight, Info } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useScope } from '../../lib/scope'
import { fmtDate, fmtNum, fmtScore, pct } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { AspectTrendChart, usePalette } from '../../components/charts'
import { AspectIcon, NoDatasets, ScopeBar } from '../../components/data'
import { Card, CardHeader, ErrorState, LoadingSkeleton, Skeleton, Stars, TD, TH, cx } from '../../components/ui'

function AspectCard({ a, core, selected, onSelect, i }) {
  const c = usePalette()
  const empty = a.mentions === 0
  return (
    <motion.button type="button" onClick={() => !empty && onSelect(a.aspect)} disabled={empty}
      initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
      className={cx('card group flex flex-col p-4 text-left transition', empty ? 'opacity-60' : 'hover:-translate-y-0.5 hover:shadow-pop',
        selected && 'ring-2 ring-accent')}>
      <div className="flex items-center gap-2.5">
        <span className="grid size-8 place-items-center rounded-lg bg-subtle ring-1 ring-line"><AspectIcon aspect={a.aspect} className="size-4 text-ink-2" /></span>
        <span className="min-w-0 flex-1 truncate text-sm font-semibold">{a.aspect}</span>
        {core && <span className="text-[10px] font-semibold uppercase tracking-wider text-ink-3">Core</span>}
      </div>
      {empty ? <p className="mt-4 text-[13px] text-ink-3">No mentions in this scope</p> : (
        <>
          <div className="mt-4 flex items-baseline justify-between">
            <span className={cx('tnum text-[26px] font-semibold tracking-tight', a.netSentiment > 0.05 ? 'text-pos' : a.netSentiment < -0.05 ? 'text-neg' : 'text-ink')}>{fmtScore(a.netSentiment)}</span>
            <span className="tnum text-xs text-ink-3">{fmtNum(a.mentions)} mentions</span>
          </div>
          <div className="mt-2.5 flex h-1.5 gap-[2px] overflow-hidden rounded-full bg-subtle">
            <span style={{ width: `${pct(a.positive, a.mentions)}%`, background: c.pos }} />
            <span style={{ width: `${pct(a.neutral, a.mentions)}%`, background: c.neu }} />
            <span style={{ width: `${pct(a.negative, a.mentions)}%`, background: c.neg }} />
          </div>
          <div className="tnum mt-2 flex justify-between text-[11.5px] text-ink-3">
            <span>{pct(a.positive, a.mentions)}% positive</span><span>{pct(a.negative, a.mentions)}% negative</span>
          </div>
        </>
      )}
    </motion.button>
  )
}

function Examples({ title, items, tone }) {
  return (
    <div>
      <h4 className="eyebrow mb-3">{title}</h4>
      {items.length === 0 ? <p className="text-sm text-ink-3">No examples in this scope.</p> : (
        <ul className="space-y-2.5">
          {items.map((e) => (
            <li key={e.reviewId} className={cx('rounded-xl border-l-[3px] bg-subtle/70 px-4 py-3', tone === 'pos' ? 'border-pos' : 'border-neg')}>
              <p className="text-[14px] leading-relaxed text-ink">“{e.snippet}”</p>
              <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-ink-3">
                <Stars rating={e.rating} /><span>{e.productName}</span>{e.date && <span>· {fmtDate(e.date)}</span>}<span className="tnum">· score {fmtScore(e.score)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function AspectDetail({ scope, aspect }) {
  const c = usePalette()
  const [d, setD] = useState(null)
  const [error, setError] = useState('')
  const load = useCallback(() => {
    setD(null)
    setError('')
    api.get('/analytics/aspects/detail', { params: { datasetId: scope.datasetId, aspect, productId: scope.productId || undefined, from: scope.from || undefined, to: scope.to || undefined } })
      .then((r) => setD(r.data)).catch((e) => setError(errorMessage(e)))
  }, [scope.datasetId, scope.productId, scope.from, scope.to, aspect])
  useEffect(() => { load() }, [load])
  if (error) return <ErrorState message={error} onRetry={load} />
  if (!d) return <Skeleton className="h-96 rounded-2xl" />
  const st = d.stat
  const stats = [['Mentions', fmtNum(st.mentions)], ['Positive', `${pct(st.positive, st.mentions)}%`], ['Negative', `${pct(st.negative, st.mentions)}%`],
    ['Neutral', `${pct(st.neutral, st.mentions)}%`], ['Net sentiment', fmtScore(st.netSentiment)], ['Signal strength', st.signalStrength.toFixed(2)]]
  return (
    <Card>
      <CardHeader title={<span className="flex items-center gap-2.5"><AspectIcon aspect={aspect} className="size-[18px] text-accent" />{aspect}</span>}
        description="Aspect-level detail for the selected scope"
        action={<Link to={`/app/reviews?${new URLSearchParams({ dataset: scope.datasetId, aspect, ...(scope.productId && { product: scope.productId }) })}`}
          className="inline-flex items-center gap-1 text-[13px] font-medium text-accent-ink hover:underline">Read reviews<ArrowUpRight className="size-3.5" /></Link>} />
      <div className="grid grid-cols-3 gap-px overflow-hidden border-y border-line bg-line sm:grid-cols-6 mt-4">
        {stats.map(([k, v]) => <div key={k} className="bg-surface px-4 py-3"><div className="text-[11.5px] text-ink-3">{k}</div><div className="tnum mt-0.5 text-[17px] font-semibold">{v}</div></div>)}
      </div>
      <div className="grid gap-6 p-5 lg:grid-cols-5">
        <div className="lg:col-span-3">
          <h4 className="eyebrow mb-2">Sentiment over time</h4>
          <AspectTrendChart data={d.trend.map((t) => ({ month: t.month, [aspect]: t.avgScore }))} aspects={[aspect]} height={220} />
        </div>
        <div className="lg:col-span-2">
          <h4 className="eyebrow mb-3">How customers refer to it</h4>
          <ul className="space-y-2">
            {d.terms.map((t) => (
              <li key={t.term} className="grid grid-cols-[1fr_120px_36px] items-center gap-3 text-[13px]">
                <span className="truncate">“{t.term}”</span>
                <span className="flex h-1.5 overflow-hidden rounded-full bg-subtle">
                  <span style={{ width: `${pct(t.positive, t.mentions)}%`, background: c.pos }} /><span style={{ width: `${pct(t.negative, t.mentions)}%`, background: c.neg }} />
                </span>
                <span className="tnum text-right text-xs text-ink-3">{t.mentions}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
      {d.products.length > 1 && (
        <div className="border-t border-line">
          <div className="px-5 pt-4"><h4 className="eyebrow">By product</h4></div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] text-sm">
              <thead><tr><th className={TH}>Product</th><th className={cx(TH, 'text-right')}>Mentions</th><th className={cx(TH, 'text-right')}>Positive</th><th className={cx(TH, 'text-right')}>Negative</th><th className={cx(TH, 'text-right')}>Net</th></tr></thead>
              <tbody>{d.products.map((p) => (
                <tr key={p.productId}><td className={cx(TD, 'font-medium')}>{p.name}</td><td className={cx(TD, 'tnum text-right')}>{p.mentions}</td>
                  <td className={cx(TD, 'tnum text-right')}>{pct(p.positive, p.mentions)}%</td><td className={cx(TD, 'tnum text-right')}>{pct(p.negative, p.mentions)}%</td>
                  <td className={cx(TD, 'tnum text-right font-semibold', p.netSentiment > 0.05 ? 'text-pos' : p.netSentiment < -0.05 ? 'text-neg' : '')}>{fmtScore(p.netSentiment)}</td></tr>
              ))}</tbody>
            </table>
          </div>
        </div>
      )}
      <div className="grid gap-6 border-t border-line p-5 lg:grid-cols-2">
        <Examples title="What customers like" items={d.positiveExamples} tone="pos" />
        <Examples title="What customers dislike" items={d.negativeExamples} tone="neg" />
      </div>
    </Card>
  )
}

export default function Aspects() {
  const scope = useScope()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)
  const load = useCallback(() => {
    if (!scope.datasetId) return
    setError('')
    api.get('/analytics/aspects', { params: { datasetId: scope.datasetId, productId: scope.productId || undefined, from: scope.from || undefined, to: scope.to || undefined } })
      .then((r) => {
        setData(r.data)
        setSelected((cur) => (r.data.aspects.some((a) => a.aspect === cur && a.mentions) ? cur : r.data.aspects.find((a) => a.mentions)?.aspect || null))
      }).catch((e) => setError(errorMessage(e)))
  }, [scope.datasetId, scope.productId, scope.from, scope.to])
  useEffect(() => { load() }, [load])

  if (scope.error) return <ErrorState message={scope.error} onRetry={scope.reload} />
  if (scope.datasets === null) return <LoadingSkeleton />
  if (!scope.datasets.length) return <><PageHeader eyebrow="Analysis" title="Aspect analysis" /><NoDatasets /></>
  const core = data?.coreAspects || []
  const ordered = data ? [...data.aspects.filter((a) => core.includes(a.aspect)).sort((x, y) => core.indexOf(x.aspect) - core.indexOf(y.aspect)),
    ...data.aspects.filter((a) => !core.includes(a.aspect) && a.mentions > 0)] : []

  return (
    <>
      <PageHeader eyebrow="Analysis" title="Aspect analysis" description="How customers feel about each product feature — sentiment split, trends, wording and real example opinions." />
      <ScopeBar scope={scope} />
      {error ? <ErrorState message={error} onRetry={load} /> : !data ? <LoadingSkeleton /> : (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
            {ordered.map((a, i) => <AspectCard key={a.aspect} a={a} i={i} core={core.includes(a.aspect)} selected={selected === a.aspect} onSelect={setSelected} />)}
          </div>
          <AnimatePresence mode="wait">
            {selected && <motion.div key={selected} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}><AspectDetail scope={scope} aspect={selected} /></motion.div>}
          </AnimatePresence>
          <p className="flex items-start gap-2 text-xs leading-relaxed text-ink-3"><Info className="mt-0.5 size-3.5 shrink-0" />{data.engineNote}</p>
        </div>
      )}
    </>
  )
}
