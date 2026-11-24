import { useEffect, useState } from 'react'
import { Info, RotateCcw, Search, Trash2 } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useToast } from '../../lib/providers'
import { useDebounced } from '../../lib/scope'
import { fmtBytes, fmtDateTime, fmtDuration, fmtNum, fmtRelative } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { Alert, Card, ConfirmationDialog, Drawer, EmptyState, ErrorState, Field, IconButton, Input, LoadingSkeleton, Pagination, Select, StatusBadge, TD, TH, cx } from '../../components/ui'
import { FilterCard, usePaged } from './shared'

const ACTIVE = ['QUEUED', 'PARSING', 'ANALYZING']

function DatasetDetail({ id, onClose }) {
  const { data, error } = usePaged(id ? `/admin/datasets/${id}` : null, {}, [id])
  const d = data?.row.dataset
  return (
    <Drawer open={Boolean(id)} onClose={onClose} title={d?.name || 'Dataset'} subtitle={data && `${data.row.ownerName} · ${data.row.ownerEmail}`}>
      {error ? <ErrorState message={error} /> : !data ? <LoadingSkeleton variant="rows" /> : (
        <div className="space-y-6">
          {d.errorMessage && <Alert>{d.errorMessage}</Alert>}
          <dl className="divide-y divide-line rounded-xl border border-line text-sm">
            {[['Status', <StatusBadge key="s" status={d.status} />], ['File', `${d.fileName} · ${fmtBytes(d.fileSize)}`], ['Records', fmtNum(d.totalReviews)],
              ['Products', fmtNum(d.productCount)], ['Skipped rows', fmtNum(d.skippedRows)], ['Duplicates', fmtNum(d.duplicateReviews)],
              ['Uploaded', fmtDateTime(d.createdAt)], ['NLP engine', d.nlpEngine || '–']].map(([k, v]) => (
              <div key={k} className="flex items-center justify-between gap-4 px-4 py-2.5"><dt className="text-ink-3">{k}</dt><dd className="text-right">{v}</dd></div>
            ))}
          </dl>
          <div>
            <h4 className="eyebrow mb-3">Analysis jobs</h4>
            <ol className="space-y-2">{data.jobs.map((j) => (
              <li key={j.id} className="rounded-xl border border-line px-4 py-3 text-[13px]">
                <div className="flex items-center justify-between gap-2"><span className="font-medium">{j.type} · {fmtDateTime(j.createdAt)}</span><StatusBadge status={j.status} /></div>
                <div className="mt-1 text-ink-3">{fmtNum(j.processedReviews)} / {fmtNum(j.totalReviews)} reviews · {fmtDuration(j.durationMs)}</div>
                {j.errorMessage && <p className="mt-1 text-danger">{j.errorMessage}</p>}
              </li>))}
            </ol>
          </div>
        </div>
      )}
    </Drawer>
  )
}

/** DatasetTable: every user's datasets, with error inspection, retry and audited cascade delete. */
export default function AdminDatasets() {
  const toast = useToast()
  const [q, setQ] = useState('')
  const dq = useDebounced(q, 350)
  const [status, setStatus] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [view, setView] = useState(null)
  const [action, setAction] = useState(null)
  const [busy, setBusy] = useState(false)
  const { data, error, reload } = usePaged('/admin/datasets', { q: dq, status, from, to, page, size: 15 })
  const active = data?.content.some((r) => ACTIVE.includes(r.dataset.status))
  useEffect(() => { if (!active) return undefined; const t = setInterval(reload, 2500); return () => clearInterval(t) }, [active, reload])

  const run = async (reason) => {
    setBusy(true)
    try {
      if (action.kind === 'delete') {
        const r = await api.delete(`/admin/datasets/${action.row.dataset.id}`, { params: { reason } })
        toast.success(`Deleted dataset and ${fmtNum(r.data.reviews)} reviews`)
      } else {
        await api.post(`/admin/datasets/${action.row.dataset.id}/retry`)
        toast.success('Analysis queued')
      }
      setAction(null)
      reload()
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <>
      <PageHeader eyebrow="Administration" title="Datasets" description="All datasets uploaded by every user. Inspect processing errors, retry analyses and remove data." />
      <FilterCard>
        <Field label="Search" className="min-w-[220px] flex-1"><div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-3" />
          <Input className="pl-9" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Dataset, file or owner" aria-label="Search datasets" /></div></Field>
        <Field label="Status" className="w-40"><Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}><option value="">Any status</option>{['QUEUED', 'PARSING', 'ANALYZING', 'COMPLETED', 'FAILED'].map((s) => <option key={s} value={s}>{s.toLowerCase()}</option>)}</Select></Field>
        <Field label="Uploaded from"><Input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0) }} /></Field>
        <Field label="to"><Input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0) }} /></Field>
      </FilterCard>
      <Card>
        {error ? <div className="p-5"><ErrorState message={error} onRetry={reload} /></div> : !data ? <LoadingSkeleton variant="rows" /> : data.content.length === 0 ? <EmptyState title="No datasets match these filters" /> : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[920px] text-sm">
                <thead><tr><th className={TH}>Dataset</th><th className={TH}>Owner</th><th className={cx(TH, 'text-right')}>Records</th><th className={cx(TH, 'text-right')}>Size</th><th className={TH}>Status</th><th className={TH}>Uploaded</th><th className={TH} /></tr></thead>
                <tbody>{data.content.map((r) => {
                  const d = r.dataset
                  return (
                    <tr key={d.id} className="hover:bg-subtle/60">
                      <td className={TD}><button onClick={() => setView(d.id)} className="text-left"><div className="font-medium hover:underline">{d.name}</div><div className="text-xs text-ink-3">{d.fileName}</div></button>
                        {d.status === 'FAILED' && <div className="max-w-xs truncate text-xs text-danger" title={d.errorMessage}>{d.errorMessage}</div>}</td>
                      <td className={TD}><div className="text-[13px]">{r.ownerName}</div><div className="text-xs text-ink-3">{r.ownerEmail}</div></td>
                      <td className={cx(TD, 'tnum text-right')}>{fmtNum(d.totalReviews)}</td>
                      <td className={cx(TD, 'tnum text-right text-[13px] text-ink-2')}>{fmtBytes(d.fileSize)}</td>
                      <td className={TD}><StatusBadge status={d.status} /></td>
                      <td className={cx(TD, 'whitespace-nowrap text-[13px] text-ink-3')} title={fmtDateTime(d.createdAt)}>{fmtRelative(d.createdAt)}</td>
                      <td className={TD}><div className="flex justify-end gap-0.5">
                        <IconButton icon={Info} label="Details" onClick={() => setView(d.id)} />
                        {!ACTIVE.includes(d.status) && <IconButton icon={RotateCcw} label="Retry analysis" onClick={() => setAction({ kind: 'retry', row: r })} />}
                        <IconButton icon={Trash2} label="Delete dataset" className="hover:text-danger" onClick={() => setAction({ kind: 'delete', row: r })} />
                      </div></td>
                    </tr>
                  )
                })}</tbody>
              </table>
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onChange={setPage} />
          </>
        )}
      </Card>
      <DatasetDetail id={view} onClose={() => setView(null)} />
      <ConfirmationDialog open={Boolean(action)} onClose={() => setAction(null)} onConfirm={run} loading={busy}
        {...(action?.kind === 'delete'
          ? { title: 'Delete dataset?', confirmLabel: 'Delete permanently', requireText: 'DELETE', reasonField: true,
            description: `“${action.row.dataset.name}” (owner ${action.row.ownerEmail}) and its ${fmtNum(action.row.dataset.totalReviews)} reviews, aspect results, keywords and reports will be removed.` }
          : { title: 'Retry analysis?', tone: 'primary', confirmLabel: 'Queue analysis', description: action && `A new analysis job will process “${action.row.dataset.name}” again.` })} />
    </>
  )
}
