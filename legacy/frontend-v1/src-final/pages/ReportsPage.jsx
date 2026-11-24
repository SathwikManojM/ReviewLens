import { useEffect, useState } from 'react'
import { download, errorMessage } from '../api'
import { DatasetSelect, Loading, NoDatasets, ProductSelect, useScope } from '../components/shared'

export default function ReportsPage() {
  const scope = useScope()
  const [productId, setProductId] = useState(null)
  const [compare, setCompare] = useState([])
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')

  useEffect(() => { setProductId(null); setCompare([]) }, [scope.datasetId])

  if (scope.datasets === null) return <Loading />
  if (scope.datasets.length === 0) return <NoDatasets />

  const run = async (kind) => {
    setBusy(kind)
    setError('')
    try {
      if (kind === 'pdf') {
        await download(`/reports/${scope.datasetId}/pdf`, { productId: productId || undefined, compare: compare.length >= 2 ? compare : undefined }, 'reviewlens-report.pdf')
      } else {
        await download(`/reports/${scope.datasetId}/csv`, { productId: productId || undefined }, 'reviewlens-aspects.csv')
      }
    } catch (e) {
      setError(e.response?.data instanceof Blob ? 'Report generation failed - check that the NLP engine and backend are running.' : errorMessage(e))
    } finally {
      setBusy('')
    }
  }

  const toggle = (id) => setCompare((c) => (c.includes(id) ? c.filter((x) => x !== id) : c.length < 4 ? [...c, id] : c))

  return (
    <div className="stack">
      <div className="page-head">
        <div><h1>Reports</h1><p>Export the analysis for your stakeholders.</p></div>
      </div>
      {(error || scope.error) && <div className="alert">{error || scope.error}</div>}

      <div className="card">
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <DatasetSelect datasets={scope.datasets} value={scope.datasetId} onChange={scope.setDatasetId} />
          <ProductSelect products={scope.products} value={productId} onChange={setProductId} label="Report scope" />
        </div>
      </div>

      <div className="grid g2">
        <div className="card">
          <div className="card-head"><div><h2>PDF analysis report</h2><p>Summary KPIs, customer insight, aspect sentiment table, keywords & concerns, representative opinions</p></div></div>
          {scope.products.length > 1 && (
            <>
              <h3 style={{ marginBottom: 8 }}>Include a product comparison (optional, pick 2–4)</h3>
              <div style={{ maxHeight: 220, overflowY: 'auto', border: '1px solid var(--border)', borderRadius: 8, padding: '6px 10px', marginBottom: 14 }}>
                {scope.products.slice(0, 200).map((p) => (
                  <label key={p.id} className="row" style={{ gap: 8, padding: '4px 0', fontSize: 13, flexWrap: 'nowrap' }}>
                    <input type="checkbox" checked={compare.includes(p.id)} onChange={() => toggle(p.id)} disabled={!compare.includes(p.id) && compare.length >= 4} />
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.name}</span>
                    <span className="muted small" style={{ marginLeft: 'auto' }}>{p.reviewCount}</span>
                  </label>
                ))}
              </div>
            </>
          )}
          <button className="btn primary" onClick={() => run('pdf')} disabled={!!busy}>{busy === 'pdf' ? 'Generating…' : 'Download PDF report'}</button>
        </div>
        <div className="card">
          <div className="card-head"><div><h2>Aspect-level data (CSV)</h2><p>One row per extracted aspect: review, product, rating, date, aspect, sentiment, score and opinion snippet. Opens in Excel.</p></div></div>
          <button className="btn primary" onClick={() => run('csv')} disabled={!!busy}>{busy === 'csv' ? 'Exporting…' : 'Download CSV export'}</button>
        </div>
      </div>
    </div>
  )
}
