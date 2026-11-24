import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { Badge, DatasetSelect, Loading, NoDatasets, ProductSelect, Stars, useScope } from '../components/shared'

const ASPECTS = ['Camera', 'Battery', 'Display', 'Price', 'Performance', 'Build Quality', 'Sound', 'Software',
  'Storage', 'Connectivity', 'Heating', 'Delivery & Packaging', 'Customer Service']

/** Highlights each aspect's opinion snippet inside the review text. */
function Highlighted({ text, aspects }) {
  const spans = []
  const lower = text.toLowerCase()
  aspects.forEach((a) => {
    if (!a.snippet) return
    const i = lower.indexOf(a.snippet.toLowerCase())
    if (i >= 0 && !spans.some((s) => i < s.end && i + a.snippet.length > s.start)) spans.push({ start: i, end: i + a.snippet.length, s: a.sentiment })
  })
  spans.sort((x, y) => x.start - y.start)
  const out = []
  let pos = 0
  spans.forEach((sp, k) => {
    out.push(text.slice(pos, sp.start))
    out.push(<mark key={k} className={sp.s}>{text.slice(sp.start, sp.end)}</mark>)
    pos = sp.end
  })
  out.push(text.slice(pos))
  return <div className="text">{out}</div>
}

export default function ReviewsPage() {
  const scope = useScope()
  const [params, setParams] = useSearchParams()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [q, setQ] = useState(params.get('q') || '')

  const filters = {
    productId: params.get('productId') ? Number(params.get('productId')) : null,
    aspect: params.get('aspect') || '',
    sentiment: params.get('sentiment') || '',
    q: params.get('q') || '',
    page: Number(params.get('page') || 0),
  }
  const update = (patch) => {
    const next = { ...filters, page: 0, ...patch }
    const p = new URLSearchParams()
    Object.entries(next).forEach(([k, v]) => { if (v !== null && v !== '' && !(k === 'page' && v === 0)) p.set(k, v) })
    setParams(p)
  }

  const key = JSON.stringify(filters)
  useEffect(() => {
    if (!scope.datasetId) return
    setError('')
    api.get(`/datasets/${scope.datasetId}/reviews`, {
      params: { productId: filters.productId || undefined, aspect: filters.aspect || undefined, sentiment: filters.sentiment || undefined, q: filters.q || undefined, page: filters.page, size: 15 },
    }).then((r) => setData(r.data)).catch((e) => setError(errorMessage(e)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope.datasetId, key])

  if (scope.datasets === null) return <Loading />
  if (scope.datasets.length === 0) return <NoDatasets />

  return (
    <div className="stack">
      <div className="page-head">
        <div><h1>Review explorer</h1><p>Read the reviews behind every number. Opinions are highlighted per aspect.</p></div>
        <DatasetSelect datasets={scope.datasets} value={scope.datasetId} onChange={(id) => { scope.setDatasetId(id); update({ productId: null }) }} />
      </div>
      {(error || scope.error) && <div className="alert">{error || scope.error}</div>}

      <div className="card">
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <ProductSelect products={scope.products} value={filters.productId} onChange={(id) => update({ productId: id })} />
          <label className="field">Aspect
            <select value={filters.aspect} onChange={(e) => update({ aspect: e.target.value })}>
              <option value="">Any aspect</option>
              {ASPECTS.map((a) => <option key={a}>{a}</option>)}
            </select>
          </label>
          <label className="field">{filters.aspect ? 'Aspect sentiment' : 'Review sentiment'}
            <select value={filters.sentiment} onChange={(e) => update({ sentiment: e.target.value })}>
              <option value="">Any</option><option value="POSITIVE">Positive</option><option value="NEGATIVE">Negative</option><option value="NEUTRAL">Neutral</option>
            </select>
          </label>
          <form className="row" style={{ alignItems: 'flex-end', gap: 6 }} onSubmit={(e) => { e.preventDefault(); update({ q }) }}>
            <label className="field">Contains text<input value={q} onChange={(e) => setQ(e.target.value)} placeholder="e.g. charger" /></label>
            <button className="btn">Search</button>
          </form>
          {(filters.aspect || filters.sentiment || filters.q || filters.productId) && <button className="btn" onClick={() => { setQ(''); setParams({}) }}>Clear</button>}
        </div>
      </div>

      <div className="card">
        {!data ? <Loading /> : (
          <>
            <div className="card-head"><h2>{data.totalElements.toLocaleString()} {filters.aspect ? `${filters.aspect.toLowerCase()} opinions` : 'reviews'}</h2>
              <span className="muted small">page {data.totalPages ? data.page + 1 : 0} of {data.totalPages}</span></div>
            {data.content.length === 0 && <div className="empty">No reviews match these filters.</div>}
            {data.content.map((r) => (
              <div className="review" key={`${r.id}`}>
                <div className="meta">
                  <Stars rating={r.rating} />
                  {r.overallSentiment && <Badge value={r.overallSentiment} />}
                  <span>{r.productName.length > 60 ? r.productName.slice(0, 60) + '…' : r.productName}</span>
                  {r.date && <span>· {r.date}</span>}
                  {r.reviewer && <span>· {r.reviewer}</span>}
                </div>
                {r.title && <div style={{ fontWeight: 600, marginTop: 6 }}>{r.title}</div>}
                <Highlighted text={r.text} aspects={r.aspects} />
                <div className="chips">
                  {r.aspects.map((a) => (
                    <Badge key={a.aspect} value={a.sentiment}>{a.aspect} · {a.sentiment.toLowerCase()} <span className="tnum" style={{ fontWeight: 500, opacity: 0.7 }}>{a.score > 0 ? '+' : ''}{a.score.toFixed(2)}</span></Badge>
                  ))}
                  {r.aspects.length === 0 && <span className="muted small">No aspects detected</span>}
                </div>
              </div>
            ))}
            {data.totalPages > 1 && (
              <div className="row" style={{ justifyContent: 'center', marginTop: 14 }}>
                <button className="btn sm" disabled={data.page === 0} onClick={() => update({ page: data.page - 1 })}>← Previous</button>
                <span className="muted small">{data.page + 1} / {data.totalPages}</span>
                <button className="btn sm" disabled={data.page + 1 >= data.totalPages} onClick={() => update({ page: data.page + 1 })}>Next →</button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
