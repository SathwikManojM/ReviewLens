import { useCallback, useEffect, useState } from 'react'
import api, { errorMessage } from '../api'

/** Reads chart colors from CSS tokens so SVG charts follow light/dark mode. */
export function useChartColors() {
  const read = () => {
    const s = getComputedStyle(document.documentElement)
    const v = (n) => s.getPropertyValue(n).trim()
    return {
      pos: v('--pos'), neg: v('--neg'), neu: v('--neu'), grid: v('--grid'), surface: v('--surface'),
      text2: v('--text-2'), text3: v('--text-3'), accent: v('--accent'),
      series: [v('--s1'), v('--s2'), v('--s3'), v('--s4')],
    }
  }
  const [colors, setColors] = useState(read)
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const on = () => setColors(read())
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [])
  return colors
}

export const SENTIMENT_LABEL = { POSITIVE: 'Positive', NEGATIVE: 'Negative', NEUTRAL: 'Neutral' }

export function Badge({ value, children }) {
  return <span className={`badge ${value}`}><span className="dot" />{children ?? SENTIMENT_LABEL[value] ?? value}</span>
}

export function Stars({ rating }) {
  if (!rating) return null
  return <span className="stars" aria-label={`${rating} out of 5 stars`}>{'★'.repeat(rating)}<span style={{ opacity: 0.25 }}>{'★'.repeat(5 - rating)}</span></span>
}

/** Generic Recharts tooltip in the app's style. */
export function ChartTooltip({ active, payload, label, formatter, title }) {
  if (!active || !payload?.length) return null
  return (
    <div className="tooltip">
      <div className="t">{title ? title(label, payload) : label}</div>
      {payload.map((p) => (
        <div className="r" key={p.dataKey}>
          <span><i style={{ background: p.color || p.fill }} />{p.name}</span>
          <b>{formatter ? formatter(p.value, p) : p.value}</b>
        </div>
      ))}
    </div>
  )
}

export function Loading({ text = 'Loading…' }) {
  return <div className="empty"><span className="muted">{text}</span></div>
}

export function Empty({ title, children }) {
  return <div className="card empty"><h2>{title}</h2><div>{children}</div></div>
}

export const pct = (n, total) => (total ? Math.round((100 * n) / total) : 0)

/**
 * Shared dataset/product scope (persisted), used by Dashboard, Compare, Reviews and Reports.
 */
export function useScope() {
  const [datasets, setDatasets] = useState(null)
  const [datasetId, setDatasetIdState] = useState(() => Number(localStorage.getItem('rl_dataset')) || null)
  const [products, setProducts] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/datasets').then((r) => {
      const done = r.data.filter((d) => d.status === 'COMPLETED')
      setDatasets(done)
      if (done.length && !done.some((d) => d.id === datasetId)) setDatasetIdState(done[0].id)
    }).catch((e) => setError(errorMessage(e)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!datasetId) return
    localStorage.setItem('rl_dataset', String(datasetId))
    api.get(`/datasets/${datasetId}/products`).then((r) => setProducts(r.data)).catch((e) => setError(errorMessage(e)))
  }, [datasetId])

  const setDatasetId = useCallback((id) => { setProducts([]); setDatasetIdState(Number(id)) }, [])
  return { datasets, datasetId, setDatasetId, products, error }
}

export function DatasetSelect({ datasets, value, onChange }) {
  return (
    <label className="field">Dataset
      <select value={value || ''} onChange={(e) => onChange(e.target.value)}>
        {datasets.map((d) => <option key={d.id} value={d.id}>{d.name} ({d.totalReviews.toLocaleString()} reviews)</option>)}
      </select>
    </label>
  )
}

export function ProductSelect({ products, value, onChange, label = 'Product', allowAll = true, allLabel = 'All products' }) {
  return (
    <label className="field" style={{ minWidth: 240, maxWidth: 380 }}>{label}
      <select value={value || ''} onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}>
        {allowAll && <option value="">{allLabel}</option>}
        {products.map((p) => <option key={p.id} value={p.id}>{p.name.length > 70 ? p.name.slice(0, 70) + '…' : p.name} ({p.reviewCount})</option>)}
      </select>
    </label>
  )
}

export function NoDatasets() {
  return (
    <Empty title="No analysed datasets yet">
      Upload an Amazon review CSV on the <a href="/datasets">Datasets</a> page. Results appear here as soon as processing completes.
    </Empty>
  )
}
