import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ChartColumn, Database, FileSpreadsheet, Info, MessageSquareText, RefreshCw, Trash2 } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useToast } from '../../lib/providers'
import { fmtBytes, fmtDate, fmtDateTime, fmtDuration, fmtNum, fmtRelative } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { DatasetUploader } from '../../components/uploader'
import { Alert, Badge, Button, Card, CardHeader, ConfirmationDialog, Drawer, EmptyState, ErrorState, IconButton, LoadingSkeleton, StatusBadge, TD, TH, cx } from '../../components/ui'

const ACTIVE = ['QUEUED', 'PARSING', 'ANALYZING']

function Progress({ d }) {
  const value = d.status === 'ANALYZING' && d.totalReviews ? (100 * d.processedReviews) / d.totalReviews : d.status === 'PARSING' ? 8 : 2
  return (
    <div className="mt-2 w-40">
      <div className="h-1.5 overflow-hidden rounded-full bg-subtle"><motion.div className="h-full rounded-full bg-accent" animate={{ width: `${value}%` }} transition={{ duration: 0.5 }} /></div>
      <div className="tnum mt-1 text-[11px] text-ink-3">{d.status === 'ANALYZING' ? `${fmtNum(d.processedReviews)} / ${fmtNum(d.totalReviews)} reviews` : d.status === 'PARSING' ? 'Reading CSV…' : 'Waiting in queue…'}</div>
    </div>
  )
}

function DatasetDetails({ dataset, onClose }) {
  const [jobs, setJobs] = useState(null)
  useEffect(() => {
    if (!dataset) return
    setJobs(null)
    api.get(`/datasets/${dataset.id}/jobs`).then((r) => setJobs(r.data)).catch(() => setJobs([]))
  }, [dataset])
  const rows = dataset && [
    ['File', `${dataset.fileName} · ${fmtBytes(dataset.fileSize)}`], ['Uploaded', fmtDateTime(dataset.createdAt)],
    ['Completed', dataset.completedAt ? fmtDateTime(dataset.completedAt) : '–'], ['Reviews', fmtNum(dataset.totalReviews)],
    ['Products', fmtNum(dataset.productCount)], ['Skipped rows', fmtNum(dataset.skippedRows)], ['Duplicate reviews', fmtNum(dataset.duplicateReviews)],
    ['Review dates', dataset.minReviewDate ? `${fmtDate(dataset.minReviewDate)} – ${fmtDate(dataset.maxReviewDate)}` : 'No dates in file'],
    ['NLP engine', dataset.nlpEngine || '–'],
  ]
  return (
    <Drawer open={Boolean(dataset)} onClose={onClose} title={dataset?.name} subtitle="Dataset details and processing history">
      {dataset && (
        <div className="space-y-7">
          {dataset.errorMessage && <Alert>{dataset.errorMessage}</Alert>}
          <dl className="divide-y divide-line rounded-xl border border-line text-sm">
            {rows.map(([k, v]) => <div key={k} className="flex justify-between gap-4 px-4 py-2.5"><dt className="text-ink-3">{k}</dt><dd className="text-right text-ink">{v}</dd></div>)}
          </dl>
          <div>
            <h4 className="eyebrow mb-3">Column mapping {dataset.mappingOverridden && <Badge tone="accent" className="ml-2 normal-case tracking-normal">Customised</Badge>}</h4>
            <dl className="grid grid-cols-2 gap-2 text-[13px]">
              {Object.entries(dataset.columnMapping || {}).map(([k, v]) => <div key={k} className="rounded-lg bg-subtle px-3 py-2"><dt className="text-ink-3">{k}</dt><dd className="truncate font-medium">{v}</dd></div>)}
            </dl>
          </div>
          <div>
            <h4 className="eyebrow mb-3">Analysis jobs</h4>
            {!jobs ? <LoadingSkeleton variant="rows" /> : (
              <ol className="space-y-2">
                {jobs.map((j) => (
                  <li key={j.id} className="rounded-xl border border-line px-4 py-3 text-[13px]">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-medium">{j.type === 'INITIAL' ? 'Initial analysis' : j.type === 'RETRY' ? 'Retry' : 'Re-run'}</span>
                      <StatusBadge status={j.status} />
                    </div>
                    <div className="mt-1 text-ink-3">{fmtDateTime(j.createdAt)} · {fmtNum(j.processedReviews)} reviews · {fmtDuration(j.durationMs)}{j.nlpEngine && ` · ${j.nlpEngine}`}</div>
                    {j.errorMessage && <p className="mt-1.5 text-danger">{j.errorMessage}</p>}
                  </li>
                ))}
              </ol>
            )}
          </div>
        </div>
      )}
    </Drawer>
  )
}

export default function Datasets() {
  const toast = useToast()
  const navigate = useNavigate()
  const [datasets, setDatasets] = useState(null)
  const [error, setError] = useState('')
  const [details, setDetails] = useState(null)
  const [confirm, setConfirm] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => api.get('/datasets').then((r) => { setDatasets(r.data); setError('') }).catch((e) => setError(errorMessage(e))), [])
  useEffect(() => { load() }, [load])
  const active = datasets?.some((d) => ACTIVE.includes(d.status))
  useEffect(() => {
    if (!active) return undefined
    const t = setInterval(load, 1500)
    return () => clearInterval(t)
  }, [active, load])

  const open = (d, path) => { try { localStorage.setItem('rl_dataset', d.id) } catch { /* ignore */ } navigate(`${path}?dataset=${d.id}`) }
  const rerun = async (d) => {
    try { await api.post(`/datasets/${d.id}/reprocess`); toast.success(`Re-running analysis for “${d.name}”`); load() } catch (e) { toast.error(errorMessage(e)) }
  }
  const remove = async () => {
    setBusy(true)
    try { await api.delete(`/datasets/${confirm.id}`); toast.success(`Deleted “${confirm.name}”`); setConfirm(null); load() } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <>
      <PageHeader eyebrow="Data" title="Datasets" description="Upload public Amazon review datasets. Columns are detected automatically, analysed by the NLP engine and stored in MongoDB." />
      <Card className="mb-6">
        <CardHeader title="Upload a review dataset" description="CSV or TSV from public sources such as Datafiniti “Consumer Reviews of Amazon Products” or Amazon Reviews 2023 (converted to CSV)." />
        <div className="p-5"><DatasetUploader onUploaded={load} /></div>
      </Card>

      <Card>
        <CardHeader title="Your datasets" description={datasets ? `${datasets.length} dataset${datasets.length === 1 ? '' : 's'}` : 'Loading…'}
          action={<IconButton icon={RefreshCw} label="Refresh" onClick={load} />} />
        {error ? <div className="p-5"><ErrorState message={error} onRetry={load} /></div> : !datasets ? <LoadingSkeleton variant="rows" /> : datasets.length === 0 ? (
          <EmptyState icon={Database} title="No datasets yet">Upload your first review file above. A synthetic test file is included at <code className="rounded bg-subtle px-1">sample-data/sample_electronics_reviews.csv</code>.</EmptyState>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[820px] text-sm">
              <thead><tr><th className={TH}>Dataset</th><th className={TH}>Status</th><th className={cx(TH, 'text-right')}>Reviews</th><th className={cx(TH, 'text-right')}>Products</th><th className={TH}>Uploaded</th><th className={TH} /></tr></thead>
              <tbody>
                {datasets.map((d) => (
                  <tr key={d.id} className="hover:bg-subtle/60">
                    <td className={TD}>
                      <div className="flex items-center gap-3">
                        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-subtle ring-1 ring-line"><FileSpreadsheet className="size-4 text-ink-3" /></span>
                        <div className="min-w-0">
                          <div className="truncate font-medium">{d.name}</div>
                          <div className="truncate text-xs text-ink-3">{d.fileName} · {fmtBytes(d.fileSize)}</div>
                          {d.status === 'FAILED' && d.errorMessage && <div className="mt-0.5 max-w-sm truncate text-xs text-danger" title={d.errorMessage}>{d.errorMessage}</div>}
                        </div>
                      </div>
                    </td>
                    <td className={TD}><StatusBadge status={d.status} />{ACTIVE.includes(d.status) && <Progress d={d} />}</td>
                    <td className={cx(TD, 'tnum text-right')}>{fmtNum(d.totalReviews)}</td>
                    <td className={cx(TD, 'tnum text-right')}>{fmtNum(d.productCount)}</td>
                    <td className={cx(TD, 'whitespace-nowrap text-[13px] text-ink-3')} title={fmtDateTime(d.createdAt)}>{fmtRelative(d.createdAt)}</td>
                    <td className={TD}>
                      <div className="flex items-center justify-end gap-0.5">
                        {d.status === 'COMPLETED' && <Button size="sm" variant="ghost" icon={ChartColumn} onClick={() => open(d, '/app/dashboard')}>Dashboard</Button>}
                        {d.status === 'COMPLETED' && <IconButton icon={MessageSquareText} label="Explore reviews" onClick={() => open(d, '/app/reviews')} />}
                        <IconButton icon={Info} label="Details" onClick={() => setDetails(d)} />
                        {!ACTIVE.includes(d.status) && <IconButton icon={RefreshCw} label="Re-run analysis" onClick={() => rerun(d)} />}
                        <IconButton icon={Trash2} label="Delete dataset" className="hover:text-danger" onClick={() => setConfirm(d)} />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      <DatasetDetails dataset={details} onClose={() => setDetails(null)} />
      <ConfirmationDialog open={Boolean(confirm)} onClose={() => setConfirm(null)} onConfirm={remove} loading={busy}
        title="Delete dataset?" confirmLabel="Delete dataset"
        description={confirm && `“${confirm.name}” and all of its ${fmtNum(confirm.totalReviews)} reviews, aspect results, keywords and reports will be permanently removed.`} />
    </>
  )
}

