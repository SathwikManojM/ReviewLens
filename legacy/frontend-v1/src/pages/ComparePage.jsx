import { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import api, { errorMessage } from '../api'
import { ChartTooltip, DatasetSelect, Loading, NoDatasets, ProductSelect, useChartColors, useScope } from '../components/shared'

const short = (n, len = 32) => (n.length > len ? n.slice(0, len).trim() + '…' : n)

export default function ComparePage() {
  const scope = useScope()
  const [selected, setSelected] = useState([])
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const c = useChartColors()

  // default: the two products with the most reviews
  useEffect(() => {
    setData(null)
    setSelected(scope.products.slice(0, 2).map((p) => p.id))
  }, [scope.products])

  const valid = selected.filter(Boolean)
  useEffect(() => {
    if (!scope.datasetId || valid.length < 2) { setData(null); return }
    setError('')
    api.get('/analytics/compare', { params: { datasetId: scope.datasetId, productIds: valid.join(',') } })
      .then((r) => setData(r.data)).catch((e) => setError(errorMessage(e)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope.datasetId, valid.join(',')])

  if (scope.datasets === null) return <Loading />
  if (scope.datasets.length === 0) return <NoDatasets />

  const setSlot = (i, id) => setSelected((s) => { const n = [...s]; n[i] = id; return n.filter((x, k) => x || k < 2) })
  const rows = (data?.aspects || []).map((aspect) => {
    const row = { aspect }
    data.products.forEach((p) => {
      const a = p.aspects.find((x) => x.aspect === aspect)
      row[`p${p.productId}`] = a ? a.netSentiment : null
      row[`m${p.productId}`] = a ? a.mentions : 0
    })
    return row
  })
  const winnerOf = Object.fromEntries((data?.winners || []).map((w) => [w.aspect, w]))
  const color = (pid) => c.series[valid.indexOf(pid)] || c.series[0]

  return (
    <div className="stack">
      <div className="page-head">
        <div><h1>Product comparison</h1><p>How customers perceive each aspect across 2–4 products</p></div>
        <DatasetSelect datasets={scope.datasets} value={scope.datasetId} onChange={scope.setDatasetId} />
      </div>
      {(error || scope.error) && <div className="alert">{error || scope.error}</div>}

      {scope.products.length < 2 ? (
        <div className="card empty">This dataset contains only one product - upload a dataset with several products to compare them.</div>
      ) : (
        <>
          <div className="card">
            <div className="row" style={{ alignItems: 'flex-end' }}>
              {[0, 1, 2, 3].slice(0, Math.min(4, Math.max(2, valid.length + 1), scope.products.length)).map((i) => (
                <div key={i} className="row" style={{ gap: 6, alignItems: 'flex-end' }}>
                  <span style={{ width: 10, height: 10, borderRadius: 3, background: c.series[i], marginBottom: 12 }} />
                  <ProductSelect label={`Product ${i + 1}`} products={scope.products.filter((p) => p.id === selected[i] || !selected.includes(p.id))}
                    value={selected[i]} onChange={(id) => setSlot(i, id)} allowAll={i >= 2} allLabel="None" />
                </div>
              ))}
            </div>
          </div>

          {data && (
            <>
              <div className="insight">
                <div className="icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M6 20V10M12 20V4M18 20v-7" /></svg></div>
                <div><h3>Comparison summary</h3><p>{data.summary}</p></div>
              </div>

              <div className="card">
                <div className="card-head">
                  <div><h2>Net sentiment by aspect</h2><p>(positive − negative) ÷ mentions · +1 = all positive, −1 = all negative</p></div>
                  <div className="legend">{data.products.map((p) => <span key={p.productId}><i style={{ background: color(p.productId) }} />{short(p.name, 28)}</span>)}</div>
                </div>
                <ResponsiveContainer width="100%" height={320}>
                  <BarChart data={rows} margin={{ left: -10, right: 8, top: 8 }} barGap={2} barCategoryGap="22%">
                    <CartesianGrid vertical={false} stroke={c.grid} />
                    <XAxis dataKey="aspect" tick={{ fill: c.text2, fontSize: 12 }} axisLine={false} tickLine={false} interval={0} angle={rows.length > 7 ? -25 : 0} textAnchor={rows.length > 7 ? 'end' : 'middle'} height={rows.length > 7 ? 60 : 30} />
                    <YAxis domain={[-1, 1]} ticks={[-1, -0.5, 0, 0.5, 1]} tick={{ fill: c.text3, fontSize: 11 }} axisLine={false} tickLine={false} />
                    <ReferenceLine y={0} stroke={c.text3} />
                    <Tooltip cursor={{ fill: c.grid, opacity: 0.5 }} content={<ChartTooltip formatter={(v, p) => `${v > 0 ? '+' : ''}${v.toFixed(2)} · ${p.payload[`m${p.dataKey.slice(1)}`]} mentions`} />} />
                    {data.products.map((p) => (
                      <Bar isAnimationActive={false} key={p.productId} dataKey={`p${p.productId}`} name={short(p.name, 28)} fill={color(p.productId)} radius={4} maxBarSize={22} />
                    ))}
                  </BarChart>
                </ResponsiveContainer>
              </div>

              <div className="card">
                <div className="card-head"><div><h2>Aspect scorecard</h2><p>Net sentiment (mentions) · winner per aspect highlighted</p></div></div>
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr><th>Aspect</th>{data.products.map((p) => <th key={p.productId} className="num"><span className="legend" style={{ justifyContent: 'flex-end' }}><span><i style={{ background: color(p.productId) }} />{short(p.name, 24)}</span></span></th>)}<th>Preferred</th></tr>
                    </thead>
                    <tbody>
                      {rows.map((r) => {
                        const w = winnerOf[r.aspect]
                        return (
                          <tr key={r.aspect}>
                            <td style={{ fontWeight: 600 }}>{r.aspect}</td>
                            {data.products.map((p) => {
                              const v = r[`p${p.productId}`]
                              const win = w && w.winnerProductId === p.productId && w.margin >= 0.1
                              return (
                                <td key={p.productId} className="num" style={{ fontWeight: win ? 700 : 400 }}>
                                  {v === null ? '–' : `${v > 0 ? '+' : ''}${v.toFixed(2)}`} <span className="muted small">({r[`m${p.productId}`]})</span>
                                </td>
                              )
                            })}
                            <td>{w && w.margin >= 0.1 ? short(w.winnerName, 26) : <span className="muted">Similar</span>}</td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="grid" style={{ gridTemplateColumns: `repeat(auto-fit, minmax(260px, 1fr))` }}>
                {data.products.map((p) => (
                  <div className="card" key={p.productId} style={{ borderTop: `3px solid ${color(p.productId)}` }}>
                    <h2 style={{ marginBottom: 10 }}>{p.name}</h2>
                    <div className="row" style={{ gap: 22, marginBottom: 10 }}>
                      <div><div className="muted small">Reviews</div><div className="tnum" style={{ fontSize: 20, fontWeight: 650 }}>{p.reviewCount}</div></div>
                      <div><div className="muted small">Avg rating</div><div className="tnum" style={{ fontSize: 20, fontWeight: 650 }}>{p.avgRating?.toFixed(2) ?? '–'}</div></div>
                      <div><div className="muted small">Positive</div><div className="tnum" style={{ fontSize: 20, fontWeight: 650 }}>{p.positivePct}%</div></div>
                    </div>
                    <p className="muted" style={{ margin: 0, lineHeight: 1.55 }}>{p.insight}</p>
                  </div>
                ))}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
