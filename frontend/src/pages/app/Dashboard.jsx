import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { FileText, MessageSquareText, Package, Smile, Star } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useScope } from '../../lib/scope'
import { fmtDate, fmtNum, pct } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { AspectSentimentChart, AspectTrendChart, Legend, RatingChart, SentimentChart, TrendChart, usePalette } from '../../components/charts'
import { InsightCard, KeywordChips, MetricCard, NoDatasets, ScopeBar, TermBars } from '../../components/data'
import { Button, Card, CardHeader, ErrorState, LoadingSkeleton, TD, TH, cx } from '../../components/ui'

export default function Dashboard() {
  const scope = useScope()
  const c = usePalette()
  const navigate = useNavigate()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const load = useCallback(() => {
    if (!scope.datasetId) return
    setLoading(true)
    setError('')
    api.get('/analytics/dashboard', { params: { datasetId: scope.datasetId, productId: scope.productId || undefined, from: scope.from || undefined, to: scope.to || undefined } })
      .then((r) => setData(r.data)).catch((e) => setError(errorMessage(e))).finally(() => setLoading(false))
  }, [scope.datasetId, scope.productId, scope.from, scope.to])
  useEffect(() => { load() }, [load])

  if (scope.error) return <ErrorState message={scope.error} onRetry={scope.reload} />
  if (scope.datasets === null) return <LoadingSkeleton />
  if (!scope.datasets.length) return <><PageHeader eyebrow="Overview" title="Dashboard" /><NoDatasets /></>

  const s = data?.sentimentDistribution || {}
  const total = data?.totalReviews || 0
  const topAspects = (data?.aspects || []).slice(0, 4).map((a) => a.aspect)
  const explore = (aspect, sentiment) => navigate(`/app/reviews?${new URLSearchParams({
    dataset: scope.datasetId, ...(aspect && { aspect }), ...(sentiment && { sentiment }), ...(scope.productId && { product: scope.productId }),
  })}`)

  return (
    <>
      <PageHeader eyebrow="Overview" title="Dashboard"
        description={data ? `${data.scopeName}${data.minDate ? ` · reviews from ${fmtDate(scope.from || data.minDate)} to ${fmtDate(scope.to || data.maxDate)}` : ''}` : 'Aspect-level customer sentiment'}
        actions={<Link to={`/app/reports?dataset=${scope.datasetId}`}><Button icon={FileText}>Export report</Button></Link>} />
      <ScopeBar scope={scope} />
      {error ? <ErrorState message={error} onRetry={load} /> : !data ? <LoadingSkeleton /> : (
        <div className={cx('space-y-5 transition-opacity duration-200', loading && 'opacity-60')}>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <MetricCard label="Reviews analysed" value={total} icon={MessageSquareText} sub={scope.from || scope.to ? 'in the selected period' : 'in this scope'} />
            <MetricCard label="Products analysed" value={data.productsAnalyzed} icon={Package} delay={0.05} sub="with at least one review" />
            <MetricCard label="Average rating" value={data.avgRating} decimals={2} format={(v) => Number(v).toFixed(2)} icon={Star} delay={0.1} sub="out of 5 stars" />
            <MetricCard label="Positive reviews" value={pct(s.POSITIVE, total)} suffix="%" icon={Smile} delay={0.15} sub={`${pct(s.NEGATIVE, total)}% negative · ${pct(s.NEUTRAL, total)}% neutral`} />
          </div>

          <InsightCard text={data.insight} note="generated from the aspect statistics below" />

          <div className="grid gap-5 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader title="Sentiment by product aspect" description="Share of mentions · select an aspect to read the reviews"
                action={<Legend items={[{ label: 'Negative', color: c.neg }, { label: 'Positive', color: c.pos }]} />} />
              <div className="pt-4"><AspectSentimentChart aspects={data.aspects} onSelect={(a) => explore(a)} /></div>
            </Card>
            <div className="space-y-5">
              <Card className="p-5"><h3 className="mb-4 text-[15px] font-semibold tracking-tight">Overall sentiment</h3><SentimentChart distribution={s} total={total} /></Card>
              <Card className="p-5"><h3 className="mb-4 text-[15px] font-semibold tracking-tight">Star ratings</h3><RatingChart ratings={data.ratingDistribution} /></Card>
            </div>
          </div>

          <div className="grid gap-5 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader title="Review trends" description="Reviews per month by overall sentiment"
                action={<Legend items={[{ label: 'Positive', color: c.pos }, { label: 'Negative', color: c.neg }, { label: 'Neutral', color: c.neu }]} />} />
              <div className="px-3 pb-4 pt-3"><TrendChart data={data.trend} /></div>
            </Card>
            <Card>
              <CardHeader title="Customer concerns" description="Terms concentrated in negative opinions" />
              <div className="p-5"><TermBars items={data.concerns} color={c.neg} /></div>
            </Card>
          </div>

          <div className="grid gap-5 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader title="Aspect sentiment over time" description="Average sentiment score (−1 to +1) of the most discussed aspects"
                action={<Legend items={topAspects.map((a, i) => ({ label: a, color: c.series[i] }))} />} />
              <div className="px-3 pb-4 pt-3"><AspectTrendChart data={data.aspectTrend} aspects={topAspects} /></div>
            </Card>
            <Card>
              <CardHeader title="Positive feedback" description="Terms concentrated in positive opinions" />
              <div className="p-5"><TermBars items={data.praise} color={c.pos} /></div>
            </Card>
          </div>

          <Card>
            <CardHeader title="Top keywords" description={scope.from || scope.to ? 'Computed live for the selected period' : 'Most distinctive terms across the reviews'} />
            <div className="p-5"><KeywordChips items={data.keywords} /></div>
          </Card>

          {!scope.productId && data.topProducts.length > 1 && (
            <Card>
              <CardHeader title="Products" description="By review volume · select a product to focus the dashboard" />
              <div className="mt-3 overflow-x-auto">
                <table className="w-full min-w-[620px] text-sm">
                  <thead><tr><th className={TH}>Product</th><th className={cx(TH, 'text-right')}>Reviews</th><th className={cx(TH, 'text-right')}>Avg rating</th><th className={TH}>Positive share</th></tr></thead>
                  <tbody>
                    {data.topProducts.map((p) => (
                      <tr key={p.id} tabIndex={0} onClick={() => scope.setProduct(p.id)} onKeyDown={(e) => e.key === 'Enter' && scope.setProduct(p.id)}
                        className="cursor-pointer hover:bg-subtle focus:bg-subtle focus:outline-none">
                        <td className={cx(TD, 'font-medium')}>{p.name}</td>
                        <td className={cx(TD, 'tnum text-right')}>{fmtNum(p.reviewCount)}</td>
                        <td className={cx(TD, 'tnum text-right')}>{p.avgRating?.toFixed(2) ?? '–'}</td>
                        <td className={TD}>
                          <div className="flex items-center gap-3">
                            <span className="h-1.5 w-32 overflow-hidden rounded-full bg-subtle"><span className="block h-full rounded-full" style={{ width: `${p.positivePct}%`, background: c.pos }} /></span>
                            <span className="tnum text-[13px] text-ink-2">{p.positivePct}%</span>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          )}
          <p className="text-xs text-ink-3">Analysed with {data.nlpEngine || 'the rule-based baseline engine'} (VADER + electronics domain rules).</p>
        </div>
      )}
    </>
  )
}
