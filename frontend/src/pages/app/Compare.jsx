import { useEffect, useState } from 'react'
import { Plus, TrendingDown, TrendingUp, X } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useScope } from '../../lib/scope'
import { fmtNum, fmtScore, pct } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { Legend, ProductComparisonChart, usePalette } from '../../components/charts'
import { InsightCard, NoDatasets, ScopeBar } from '../../components/data'
import { Badge, Button, Card, CardHeader, EmptyState, ErrorState, LoadingSkeleton, Select, TD, TH, cx } from '../../components/ui'

const short = (n, l = 34) => (n.length > l ? `${n.slice(0, l).trim()}…` : n)

export default function Compare() {
  const scope = useScope()
  const c = usePalette()
  const [selected, setSelected] = useState([])
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => { setData(null); setSelected(scope.products.slice(0, 2).map((p) => p.id)) }, [scope.products])
  const ids = selected.filter(Boolean)
  const key = ids.join(',')
  useEffect(() => {
    if (!scope.datasetId || ids.length < 2) { setData(null); return }
    setError('')
    api.get('/analytics/compare', { params: { datasetId: scope.datasetId, productIds: key, from: scope.from || undefined, to: scope.to || undefined } })
      .then((r) => setData(r.data)).catch((e) => setError(errorMessage(e)))
  }, [scope.datasetId, key, scope.from, scope.to]) // eslint-disable-line react-hooks/exhaustive-deps

  if (scope.error) return <ErrorState message={scope.error} onRetry={scope.reload} />
  if (scope.datasets === null) return <LoadingSkeleton />
  if (!scope.datasets.length) return <><PageHeader eyebrow="Analysis" title="Compare products" /><NoDatasets /></>
  const colorOf = (id) => c.series[Math.max(0, ids.indexOf(id)) % 4]
  const rows = (data?.aspects || []).map((aspect) => {
    const row = { aspect }
    data.products.forEach((p) => {
      const a = p.aspects.find((x) => x.aspect === aspect)
      row[`p_${p.productId}`] = a ? a.netSentiment : null
      row[`m_${p.productId}`] = a ? a.mentions : 0
    })
    return row
  })
  const winners = Object.fromEntries((data?.winners || []).map((w) => [w.aspect, w]))

  return (
    <>
      <PageHeader eyebrow="Analysis" title="Compare products" description="How customers perceive each aspect across two to four products — strengths, weaknesses and the evidence behind them." />
      <ScopeBar scope={scope} showProduct={false} />
      {scope.products.length < 2 ? <Card><EmptyState title="Only one product in this dataset">Upload a dataset with several products to compare them.</EmptyState></Card> : (
        <div className="space-y-5">
          <Card className="flex flex-wrap items-center gap-3 p-4">
            {selected.map((id, i) => (
              <div key={i} className="flex items-center gap-2">
                <span className="size-2.5 rounded-full" style={{ background: c.series[i] }} />
                <Select className="w-60" value={id || ''} aria-label={`Product ${i + 1}`}
                  onChange={(e) => setSelected((s) => s.map((v, k) => (k === i ? e.target.value : v)))}>
                  <option value="">Select a product</option>
                  {scope.products.filter((p) => p.id === id || !selected.includes(p.id)).map((p) => <option key={p.id} value={p.id}>{short(p.name, 48)} ({fmtNum(p.reviewCount)})</option>)}
                </Select>
                {selected.length > 2 && <button type="button" onClick={() => setSelected((s) => s.filter((_, k) => k !== i))} className="rounded-md p-1 text-ink-3 hover:bg-muted hover:text-ink" aria-label="Remove product"><X className="size-4" /></button>}
              </div>
            ))}
            {selected.length < Math.min(4, scope.products.length) && <Button size="sm" variant="ghost" icon={Plus} onClick={() => setSelected((s) => [...s, ''])}>Add product</Button>}
          </Card>

          {error ? <ErrorState message={error} /> : ids.length < 2 ? <Card><EmptyState title="Select at least two products" /></Card> : !data ? <LoadingSkeleton /> : (
            <>
              <InsightCard title="Comparison summary" text={data.summary} note="derived from aspect-level net sentiment" />
              <Card>
                <CardHeader title="Net sentiment by aspect" description="(positive − negative) ÷ mentions · +1 = all positive, −1 = all negative"
                  action={<Legend items={data.products.map((p) => ({ label: short(p.name, 26), color: colorOf(p.productId) }))} />} />
                <div className="px-3 pb-4 pt-3"><ProductComparisonChart rows={rows} products={data.products} colorOf={colorOf} /></div>
              </Card>

              <div className="grid gap-5 lg:grid-cols-2">
                <Card>
                  <CardHeader title="Overall sentiment distribution" description="Share of positive, neutral and negative reviews" />
                  <ul className="space-y-4 p-5">
                    {data.products.map((p) => (
                      <li key={p.productId}>
                        <div className="mb-1.5 flex justify-between gap-3 text-[13px]"><span className="truncate font-medium">{p.name}</span><span className="tnum shrink-0 text-ink-3">{fmtNum(p.reviewCount)} reviews · {p.avgRating?.toFixed(2) ?? '–'}★</span></div>
                        <div className="flex h-2.5 gap-[2px] overflow-hidden rounded-full bg-subtle">
                          <span style={{ width: `${pct(p.positive, p.reviewCount)}%`, background: c.pos }} /><span style={{ width: `${pct(p.neutral, p.reviewCount)}%`, background: c.neu }} /><span style={{ width: `${pct(p.negative, p.reviewCount)}%`, background: c.neg }} />
                        </div>
                        <div className="tnum mt-1 text-[11.5px] text-ink-3">{pct(p.positive, p.reviewCount)}% positive · {pct(p.negative, p.reviewCount)}% negative</div>
                      </li>
                    ))}
                  </ul>
                </Card>
                <Card>
                  <CardHeader title="Strengths & weaknesses" description="Aspects with enough mentions and a clear lean" />
                  <div className="grid gap-4 p-5 sm:grid-cols-2">
                    {data.products.map((p) => (
                      <div key={p.productId} className="rounded-xl border border-line p-4" style={{ borderTopColor: colorOf(p.productId), borderTopWidth: 3 }}>
                        <div className="truncate text-sm font-semibold">{p.name}</div>
                        <ul className="mt-3 space-y-1.5 text-[13px]">
                          {p.strengths.map((s) => <li key={s.aspect} className="flex items-center gap-2"><TrendingUp className="size-3.5 text-pos" /><span className="flex-1">{s.aspect}</span><span className="tnum text-ink-3">{s.positivePct}% pos.</span></li>)}
                          {p.weaknesses.map((s) => <li key={s.aspect} className="flex items-center gap-2"><TrendingDown className="size-3.5 text-neg" /><span className="flex-1">{s.aspect}</span><span className="tnum text-ink-3">{s.negativePct}% neg.</span></li>)}
                          {!p.strengths.length && !p.weaknesses.length && <li className="text-ink-3">No clear strengths or weaknesses yet.</li>}
                        </ul>
                      </div>
                    ))}
                  </div>
                </Card>
              </div>

              <Card>
                <CardHeader title="Aspect scorecard" description="Net sentiment (mentions) · leader highlighted when the margin is at least 0.10" />
                <div className="mt-3 overflow-x-auto">
                  <table className="w-full min-w-[640px] text-sm">
                    <thead><tr><th className={TH}>Aspect</th>{data.products.map((p) => <th key={p.productId} className={cx(TH, 'text-right')}><span className="inline-flex items-center gap-1.5"><i className="size-2 rounded-full" style={{ background: colorOf(p.productId) }} />{short(p.name, 22)}</span></th>)}<th className={TH}>Preferred</th></tr></thead>
                    <tbody>{rows.map((r) => {
                      const w = winners[r.aspect]
                      return (
                        <tr key={r.aspect}>
                          <td className={cx(TD, 'font-medium')}>{r.aspect}</td>
                          {data.products.map((p) => {
                            const v = r[`p_${p.productId}`]
                            const lead = w && w.winnerProductId === p.productId && w.margin >= 0.1
                            return <td key={p.productId} className={cx(TD, 'tnum text-right', lead && 'font-semibold text-ink')}>{v == null ? '–' : fmtScore(v)} <span className="text-xs font-normal text-ink-3">({r[`m_${p.productId}`]})</span></td>
                          })}
                          <td className={TD}>{w && w.margin >= 0.1 ? <Badge tone="accent">{short(w.winnerName, 22)}</Badge> : <span className="text-[13px] text-ink-3">Similar</span>}</td>
                        </tr>
                      )
                    })}</tbody>
                  </table>
                </div>
              </Card>
            </>
          )}
        </div>
      )}
    </>
  )
}
