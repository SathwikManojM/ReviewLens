import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { Badge } from '../components/shared'

const BUSY = ['UPLOADED', 'PARSING', 'ANALYZING']
const STATUS_TEXT = { UPLOADED: 'Queued', PARSING: 'Reading CSV', ANALYZING: 'Analysing', COMPLETED: 'Completed', FAILED: 'Failed' }

export default function DatasetsPage() {
  const [datasets, setDatasets] = useState([])
  const [file, setFile] = useState(null)
  const [name, setName] = useState('')
  const [over, setOver] = useState(false)
  const [uploading, setUploading] = useState(0)
  const [error, setError] = useState('')
  const [confirmId, setConfirmId] = useState(null)
  const input = useRef()
  const navigate = useNavigate()

  const load = () => api.get('/datasets').then((r) => setDatasets(r.data)).catch((e) => setError(errorMessage(e)))
  useEffect(() => { load() }, [])

  // poll while any dataset is still processing
  const busy = datasets.some((d) => BUSY.includes(d.status))
  useEffect(() => {
    if (!busy) return
    const t = setInterval(load, 1500)
    return () => clearInterval(t)
  }, [busy])

  const pick = (f) => {
    if (!f) return
    setFile(f)
    if (!name) setName(f.name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' '))
  }

  const upload = async () => {
    setError('')
    const fd = new FormData()
    fd.append('file', file)
    if (name) fd.append('name', name)
    try {
      setUploading(1)
      await api.post('/datasets', fd, { onUploadProgress: (e) => setUploading(Math.max(1, Math.round((100 * e.loaded) / (e.total || 1)))) })
      setFile(null)
      setName('')
      load()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setUploading(0)
    }
  }

  const act = async (fn) => {
    setError('')
    try { await fn(); load() } catch (e) { setError(errorMessage(e)) }
  }

  const open = (d, path) => {
    localStorage.setItem('rl_dataset', String(d.id))
    navigate(path)
  }

  return (
    <div className="stack">
      <div className="page-head">
        <div>
          <h1>Datasets</h1>
          <p>Upload public Amazon review datasets (CSV). Columns are detected automatically.</p>
        </div>
      </div>

      {error && <div className="alert">{error}</div>}

      <div className="card">
        <div className="card-head"><div><h2>Upload a review dataset</h2><p>CSV or TSV · up to 300 MB · e.g. Datafiniti "Consumer Reviews of Amazon Products", Amazon Reviews 2023 (as CSV), Amazon Fine Food</p></div></div>
        <div
          className={`dropzone ${over ? 'over' : ''}`}
          onClick={() => input.current.click()}
          onDragOver={(e) => { e.preventDefault(); setOver(true) }}
          onDragLeave={() => setOver(false)}
          onDrop={(e) => { e.preventDefault(); setOver(false); pick(e.dataTransfer.files[0]) }}
        >
          <input ref={input} type="file" accept=".csv,.tsv,.txt" hidden onChange={(e) => pick(e.target.files[0])} />
          {file ? (
            <div><b>{file.name}</b> <span className="muted">· {(file.size / 1024 / 1024).toFixed(2)} MB</span></div>
          ) : (
            <div><b>Drop a CSV file here</b> or click to browse<div className="muted small" style={{ marginTop: 6 }}>Needs at least a review text column (e.g. reviews.text, reviewText, text, review_content)</div></div>
          )}
        </div>
        {file && (
          <div className="row" style={{ marginTop: 14, alignItems: 'flex-end' }}>
            <label className="field" style={{ flex: 1, minWidth: 220 }}>Dataset name<input value={name} onChange={(e) => setName(e.target.value)} /></label>
            <button className="btn" onClick={() => setFile(null)} disabled={!!uploading}>Cancel</button>
            <button className="btn primary" onClick={upload} disabled={!!uploading}>{uploading ? `Uploading ${uploading}%` : 'Upload & analyse'}</button>
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-head"><h2>Your datasets</h2><span className="muted small">{datasets.length} total</span></div>
        {datasets.length === 0 ? (
          <div className="empty">No datasets yet - upload your first CSV above. A synthetic test file is included at <code>sample-data/sample_electronics_reviews.csv</code>.</div>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Name</th><th>Status</th><th className="num">Reviews</th><th className="num">Products</th><th>Uploaded</th><th /></tr></thead>
              <tbody>
                {datasets.map((d) => (
                  <tr key={d.id}>
                    <td>
                      <div style={{ fontWeight: 600 }}>{d.name}</div>
                      <div className="muted small">{d.fileName}</div>
                      {d.errorMessage && <div className="small" style={{ color: 'var(--danger)', maxWidth: 480 }}>{d.errorMessage}</div>}
                    </td>
                    <td>
                      <Badge value={d.status}>{STATUS_TEXT[d.status]}</Badge>
                      {d.status === 'ANALYZING' && d.totalReviews > 0 && (
                        <div className="progress" style={{ marginTop: 6 }} title={`${d.processedReviews} / ${d.totalReviews}`}>
                          <div style={{ width: `${(100 * d.processedReviews) / d.totalReviews}%` }} />
                        </div>
                      )}
                    </td>
                    <td className="num">{d.totalReviews.toLocaleString()}{d.skippedRows > 0 && <div className="muted small">{d.skippedRows} skipped</div>}</td>
                    <td className="num">{d.productCount.toLocaleString()}</td>
                    <td className="muted small">{new Date(d.createdAt).toLocaleString()}</td>
                    <td>
                      <div className="row" style={{ justifyContent: 'flex-end', flexWrap: 'nowrap' }}>
                        {d.status === 'COMPLETED' && <button className="btn sm primary" onClick={() => open(d, '/dashboard')}>Dashboard</button>}
                        {d.status === 'COMPLETED' && <button className="btn sm" onClick={() => open(d, '/compare')}>Compare</button>}
                        {!BUSY.includes(d.status) && <button className="btn sm" onClick={() => act(() => api.post(`/datasets/${d.id}/reprocess`))}>Re-run</button>}
                        {confirmId === d.id ? (
                          <>
                            <button className="btn sm danger" onClick={() => { setConfirmId(null); act(() => api.delete(`/datasets/${d.id}`)) }}>Confirm delete</button>
                            <button className="btn sm" onClick={() => setConfirmId(null)}>Keep</button>
                          </>
                        ) : (
                          <button className="btn sm danger" disabled={BUSY.includes(d.status)} onClick={() => setConfirmId(d.id)}>Delete</button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
