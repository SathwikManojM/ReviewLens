import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Bar, BarChart, CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import api, { errorMessage } from '../api'
import {
  ChartTooltip, DatasetSelect, Loading, NoDatasets, ProductSelect, pct, useChartColors, useScope,
} from '../components/shared'

const monthLabel = (m) => {
  const [y, mo] = m.split('-')
  return new Date(Number(y), Number(mo) - 1).toLocaleString(undefined, { month: 'short', year: '2-digit' })
}

export default function DashboardPage() {
  const scope = useScope()
  const [productId, setProductId] = useState(null)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const c = useChartColors()
  const navigate = useNavigate()

  useEffect(() => { setProductId(null) }, [scope.datasetId])
  useEffect(() => {
    if (!scope.datasetId) return
    setLoading(true)
    setError('')
    api.get('/analytics/dashboard', { params: { datasetId: scope.datasetId, productId: productId || undefined } })
      .then((r) => setData(r.data)).catch((e) => setError(errorMessage(e))).finally(() => setLoading(false))
  }, [scope.datasetId, productId])

  if (scope.datasets === null) return <Loading />
  if (scope.datasets.length === 0) return <NoDatasets />

  const total = data?.totalReviews || 0
  const s = data?.sentimentDistribution || {}
  const aspectRows = (data?.aspects || []).map((a) => ({
    ...a,
    negPct: -pct(a.negative, a.mentions),
    posPct: pct(a.positive, a.mentions),
    neuPct: pct(a.neutral, a.mentions),
  }))
  const trendAspects = data?.aspects?.slice(0, 4).map((a) => a.aspect) || []
  const ratingRows = Object.entries(data?.ratingDistribution || {}).map(([k, v]) => ({ rating: `${k}★`, count: v }))
  const exploreAspect = (aspect, sentiment) => {
    const q = new URLSearchParams({ aspect, ...(sentiment ? { sentiment } : {}), ...(productId ? { productId } : {}) })
    navigate(`/reviews?${q}`)
  }

  return (
    <div className="stack">
      <div className="page-head">
        <div>
          <h1>Analytics dashboard</h1>
          <p>{data ? data.scopeName : 'Aspect-level customer sentiment'}</p>
        </div>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <DatasetSelect datasets={scope.datasets} value={scope.datasetId} onChange={scope.setDatasetId} />
          <ProductSelect products={scope.products} value={productId} onChange={setProductId} />
        </div>
      </div>

      {(error || scope.error) && <div className="alert">{error || scope.error}</div>}
      {!data ? <Loading /> : (
        <div className="stack" style={{ opacity: loading ? 0.6 : 1, transition: 'opacity .2s' }}>
          <div className="grid g4">
            <Kpi label="Reviews analysed" value={total.toLocaleString()} sub={productId ? 'for this product' : `${scope.products.length} products`} />
            <Kpi label="Average rating" value={data.avgRating ? data.avgRating.toFixed(2) : '–'} sub="out of 5 stars" />
            <Kpi label="Positive reviews" value={`${pct(s.POSITIVE, total)}%`} sub={`${(s.POSITIVE || 0).toLocaleString()} reviews`} />
            <Kpi label="Negative reviews" value={`${pct(s.NEGATIVE, total)}%`} sub={`${(s.NEGATIVE || 0).toLocaleString()} reviews`} />
          </div>

          <div className="insight">
            <div className="icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3Z" /></svg></div>
            <div><h3>Customer insight</h3><p>{data.insight}</p></div>
          </div>

          <div className="grid g3">
            <div className="card span2">
              <div className="card-head">
                <div><h2>Sentiment by product aspect</h2><p>Share of mentions · negative left, positive right · click a bar to read the reviews</p></div>
                <div className="legend"><span><i style={{ background: c.neg }} />Negative</span><span><i style={{ background: c.pos }} />Positive</span></div>
              </div>
              {aspectRows.length === 0 ? <div className="empty">No aspects detected.</div> : (
                <ResponsiveContainer width="100%" height={Math.max(220, aspectRows.length * 36 + 40)}>
                  <BarChart data={aspectRows} layout="vertical" stackOffset="sign" margin={{ left: 8, right: 24, top: 4, bottom: 4 }} barCategoryGap={8}>
                    <CartesianGrid horizontal={false} stroke={c.grid} />
                    <XAxis type="number" domain={[-100, 100]} ticks={[-100, -50, 0, 50, 100]} tickFormatter={(v) => `${Math.abs(v)}%`} tick={{ fill: c.text3, fontSize: 11 }} axisLine={false} tickLine={false} />
                    <YAxis type="category" dataKey="aspect" width={130} tick={{ fill: c.text2, fontSize: 12 }} axisLine={false} tickLine={false} />
                    <ReferenceLine x={0} stroke={c.text3} />
                    <Tooltip cursor={{ fill: c.grid, opacity: 0.5 }} content={<AspectTooltip c={c} />} />
                    <Bar isAnimationActive={false} dataKey="negPct" name="Negative" stackId="s" fill={c.neg} radius={[4, 0, 0, 4]} maxBarSize={18} cursor="pointer" onClick={(d) => exploreAspect(d.aspect, 'NEGATIVE')} />
                    <Bar isAnimationActive={false} dataKey="posPct" name="Positive" stackId="s" fill={c.pos} radius={[0, 4, 4, 0]} maxBarSize={18} cursor="pointer" onClick={(d) => exploreAspect(d.aspect, 'POSITIVE')} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>

            <div className="stack">
              <div className="card">
                <div className="card-head"><h2>Overall review sentiment</h2></div>
                <SentimentBar s={s} total={total} c={c} />
              </div>
              <div className="card">
                <div className="card-head"><h2>Star ratings</h2><span className="muted small">reviews per rating</span></div>
                <ResponsiveContainer width="100%" height={170}>
                  <BarChart data={ratingRows} margin={{ left: -18, right: 4, top: 4 }}>
                    <CartesianGrid vertical={false} stroke={c.grid} />
                    <XAxis dataKey="rating" tick={{ fill: c.text2, fontSize: 12 }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fill: c.text3, fontSize: 11 }} axisLine={false} tickLine={false} allowDecimals={false} />
                    <Tooltip cursor={{ fill: c.grid, opacity: 0.5 }} content={<ChartTooltip formatter={(v) => v.toLocaleString()} />} />
                    <Bar isAnimationActive={false} dataKey="count" name="Reviews" fill={c.accent} radius={[4, 4, 0, 0]} maxBarSize={28} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          <div className="grid g2">
            <div className="card">
              <div className="card-head">
                <div><h2>Review sentiment over time</h2><p>Reviews per month</p></div>
                <div className="legend"><span><i style={{ background: c.pos }} />Positive</span><span><i style={{ background: c.neg }} />Negative</span><span><i style={{ background: c.neu }} />Neutral</span></div>
              </div>
              {data.trend.length < 2 ? <div className="empty">Not enough dated reviews for a trend.</div> : (
                <ResponsiveContainer width="100%" height={240}>
                  <LineChart data={data.trend} margin={{ left: -14, right: 8, top: 6 }}>
                    <CartesianGrid vertical={false} stroke={c.grid} />
                    <XAxis dataKey="month" tickFormatter={monthLabel} tick={{ fill: c.text3, fontSize: 11 }} axisLine={false} tickLine={false} minTickGap={16} />
                    <YAxis tick={{ fill: c.text3, fontSize: 11 }} axisLine={false} tickLine={false} allowDecimals={false} />
                    <Tooltip content={<ChartTooltip title={monthLabel} />} />
                    <Line isAnimationActive={false} dataKey="positive" name="Positive" stroke={c.pos} strokeWidth={2} dot={false} activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
                    <Line isAnimationActive={false} dataKey="negative" name="Negative" stroke={c.neg} strokeWidth={2} dot={false} activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
                    <Line isAnimationActive={false} dataKey="neutral" name="Neutral" stroke={c.neu} strokeWidth={2} dot={false} activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </div>
            <div className="card">
              <div className="card-head">
                <div><h2>Aspect sentiment trend</h2><p>Average sentiment score (−1 to +1) of the most discussed aspects</p></div>
                <div className="legend">{trendAspects.map((a, i) => <span key={a}><i style={{ background: c.series[i] }} />{a}</span>)}</div>
              </div>
              {data.aspectTrend.length < 2 ? <div className="empty">Not enough dated reviews for a trend.</div> : (
                <ResponsiveContainer width="100%" height={240}>
                  <LineChart data={data.aspectTrend} margin={{ left: -14, right: 8, top: 6 }}>
                    <CartesianGrid vertical={false} stroke={c.grid} />
                    <XAxis dataKey="month" tickFormatter={monthLabel} tick={{ fill: c.text3, fontSize: 11 }} axisLine={false} tickLine={false} minTickGap={16} />
                    <YAxis domain={[-1, 1]} ticks={[-1, -0.5, 0, 0.5, 1]} tick={{ fill: c.text3, fontSize: 11 }} axisLine={false} tickLine={false} />
                    <ReferenceLine y={0} stroke={c.text3} strokeDasharray="3 3" />
                    <Tooltip content={<ChartTooltip title={monthLabel} formatter={(v) => (v > 0 ? '+' : '') + v.toFixed(2)} />} />
                    {trendAspects.map((a, i) => (
                      <Line isAnimationActive={false} key={a} dataKey={a} name={a} stroke={c.series[i]} strokeWidth={2} dot={false} connectNulls activeDot={{ r: 4, stroke: c.surface, strokeWidth: 2 }} />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          <div className="grid g3">
            <div className="card">
              <div className="card-head"><div><h2>Top keywords</h2><p>Most distinctive terms across reviews</p></div></div>
              <div className="chips">
                {data.keywords.slice(0, 28).map((k) => <span className="chip" key={k.term}>{k.term}<b>{k.frequency}</b></span>)}
                {data.keywords.length === 0 && <span className="muted">No keywords yet.</span>}
              </div>
            </div>
            <TermList title="What customers praise" sub="Terms concentrated in positive opinions" items={data.praise} color={c.pos} />
            <TermList title="Customer concerns" sub="Terms concentrated in negative opinions" items={data.concerns} color={c.neg} />
          </div>

          {!productId && data.topProducts.length > 1 && (
            <div className="card">
              <div className="card-head"><div><h2>Products</h2><p>Top 10 by review volume · click to drill down</p></div></div>
              <div className="table-wrap">
                <table className="table">
                  <thead><tr><th>Product</th><th className="num">Reviews</th><th className="num">Avg rating</th><th className="num">Positive</th><th style={{ width: '22%' }} /></tr></thead>
                  <tbody>
                    {data.topProducts.map((p) => (
                      <tr key={p.id} style={{ cursor: 'pointer' }} onClick={() => setProductId(p.id)}>
                        <td style={{ fontWeight: 500 }}>{p.name}</td>
                        <td className="num">{p.reviewCount.toLocaleString()}</td>
                        <td className="num">{p.avgRating?.toFixed(2) ?? '–'}</td>
                        <td className="num">{p.positivePct}%</td>
                        <td><div className="progress"><div style={{ width: `${p.positivePct}%`, background: c.pos }} /></div></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          <div className="card">
            <div className="card-head"><div><h2>Aspect table</h2><p>Mentions and sentiment split for every detected aspect</p></div></div>
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Aspect</th><th className="num">Mentions</th><th className="num">Positive</th><th className="num">Negative</th><th className="num">Neutral</th><th className="num">Net sentiment</th><th className="num">Avg score</th><th /></tr></thead>
                <tbody>
                  {aspectRows.map((a) => (
                    <tr key={a.aspect}>
                      <td style={{ fontWeight: 600 }}>{a.aspect}</td>
                      <td className="num">{a.mentions.toLocaleString()}</td>
                      <td className="num">{a.posPct}%</td>
                      <td className="num">{-a.negPct}%</td>
                      <td className="num">{a.neuPct}%</td>
                      <td className="num">{a.netSentiment > 0 ? '+' : ''}{a.netSentiment.toFixed(2)}</td>
                      <td className="num">{a.avgScore > 0 ? '+' : ''}{a.avgScore.toFixed(2)}</td>
                      <td className="num"><button className="btn sm" onClick={() => exploreAspect(a.aspect)}>Reviews</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Kpi({ label, value, sub }) {
  return <div className="card kpi"><div className="label">{label}</div><div className="value">{value}</div><div className="sub">{sub}</div></div>
}

function AspectTooltip({ active, payload, c }) {
  if (!active || !payload?.length) return null
  const a = payload[0].payload
  return (
    <div className="tooltip">
      <div className="t">{a.aspect} · {a.mentions} mentions</div>
      <div className="r"><span><i style={{ background: c.pos }} />Positive</span><b>{a.positive} ({a.posPct}%)</b></div>
      <div className="r"><span><i style={{ background: c.neg }} />Negative</span><b>{a.negative} ({-a.negPct}%)</b></div>
      <div className="r"><span><i style={{ background: c.neu }} />Neutral</span><b>{a.neutral} ({a.neuPct}%)</b></div>
    </div>
  )
}

function SentimentBar({ s, total, c }) {
  const parts = [['POSITIVE', 'Positive', c.pos], ['NEUTRAL', 'Neutral', c.neu], ['NEGATIVE', 'Negative', c.neg]]
  return (
    <div>
      <div style={{ display: 'flex', gap: 2, height: 14, borderRadius: 4, overflow: 'hidden', background: c.grid }}>
        {parts.map(([k, label, color]) => (s[k] ? <div key={k} title={`${label}: ${s[k]}`} style={{ width: `${(100 * s[k]) / total}%`, background: color }} /> : null))}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', marginTop: 12, gap: 8 }}>
        {parts.map(([k, label, color]) => (
          <div key={k}>
            <div className="legend"><span><i style={{ background: color }} />{label}</span></div>
            <div className="tnum" style={{ fontSize: 18, fontWeight: 600, marginTop: 2 }}>{pct(s[k], total)}%</div>
            <div className="muted small tnum">{(s[k] || 0).toLocaleString()}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

function TermList({ title, sub, items, color }) {
  const max = Math.max(1, ...items.map((i) => i.frequency))
  return (
    <div className="card">
      <div className="card-head"><div><h2>{title}</h2><p>{sub}</p></div></div>
      {items.length === 0 ? <span className="muted">Not enough opinions yet.</span> : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
          {items.slice(0, 9).map((k) => (
            <div key={k.term} style={{ display: 'grid', gridTemplateColumns: '1fr 90px 34px', gap: 10, alignItems: 'center', fontSize: 13 }}>
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{k.term}</span>
              <div className="progress"><div style={{ width: `${(100 * k.frequency) / max}%`, background: color }} /></div>
              <span className="muted small tnum" style={{ textAlign: 'right' }}>{k.frequency}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
