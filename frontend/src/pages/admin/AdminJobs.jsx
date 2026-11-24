import { useEffect, useState } from 'react'
import { RotateCcw, Search } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useToast } from '../../lib/providers'
import { useDebounced } from '../../lib/scope'
import { fmtDateTime, fmtDuration, fmtNum, fmtRelative } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { Badge, Button, Card, ConfirmationDialog, EmptyState, ErrorState, Field, Input, LoadingSkeleton, Pagination, StatusBadge, TD, TH, cx } from '../../components/ui'
import { FilterCard, SystemHealthPanel, usePaged } from './shared'

const STATUSES = [['', 'All'], ['QUEUED', 'Queued'], ['RUNNING', 'Running'], ['COMPLETED', 'Completed'], ['FAILED', 'Failed'], ['CANCELLED', 'Cancelled']]

/** AnalysisJobTable + NLP / API / MongoDB monitoring with safe retry. */
export default function AdminJobs() {
  const toast = useToast()
  const [status, setStatus] = useState('')
  const [q, setQ] = useState('')
  const dq = useDebounced(q, 350)
  const [page, setPage] = useState(0)
  const [retry, setRetry] = useState(null)
  const [busy, setBusy] = useState(false)
  const stats = usePaged('/admin/analysis-jobs/stats', {})
  const health = usePaged('/admin/system/health', {})
  const { data, error, reload } = usePaged('/admin/analysis-jobs', { status, q: dq, page, size: 15 })
  const live = data?.content.some((r) => ['QUEUED', 'RUNNING'].includes(r.job.status))
  useEffect(() => {
    if (!live) return undefined
    const t = setInterval(() => { reload(); stats.reload() }, 2500)
    return () => clearInterval(t)
  }, [live]) // eslint-disable-line react-hooks/exhaustive-deps

  const run = async () => {
    setBusy(true)
    try { await api.post(`/admin/analysis-jobs/${retry.job.id}/retry`); toast.success('Retry queued'); setRetry(null); reload(); stats.reload() } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  const s = stats.data

  return (
    <>
      <PageHeader eyebrow="Administration" title="Analysis jobs" description="Monitor the NLP processing queue, durations and failures, and retry jobs safely." />
      <div className="mb-5"><SystemHealthPanel health={health.data} onRefresh={health.reload} /></div>
      <div className="mb-5 flex flex-wrap gap-2">
        {STATUSES.map(([v, label]) => {
          const n = !s ? null : v === '' ? s.queued + s.running + s.completed + s.failed + s.cancelled : s[v.toLowerCase()]
          return (
            <button key={v} type="button" onClick={() => { setStatus(v); setPage(0) }}
              className={cx('inline-flex items-center gap-2 rounded-full border px-3.5 py-1.5 text-[13px] font-medium transition', status === v ? 'border-accent bg-accent-soft text-accent-ink' : 'border-line bg-surface text-ink-2 hover:border-line-strong')}>
              {label}{n != null && <span className="tnum rounded-full bg-subtle px-1.5 text-[11px] text-ink-3">{fmtNum(n)}</span>}
            </button>
          )
        })}
      </div>
      <FilterCard>
        <Field label="Dataset name" className="min-w-[220px] flex-1"><div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-3" />
          <Input className="pl-9" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Search by dataset" aria-label="Search jobs" /></div></Field>
      </FilterCard>
      <Card>
        {error ? <div className="p-5"><ErrorState message={error} onRetry={reload} /></div> : !data ? <LoadingSkeleton variant="rows" /> : data.content.length === 0 ? <EmptyState title="No analysis jobs" /> : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[980px] text-sm">
                <thead><tr><th className={TH}>Dataset</th><th className={TH}>Type</th><th className={TH}>Status</th><th className={TH}>Progress</th><th className={cx(TH, 'text-right')}>Duration</th><th className={TH}>Created</th><th className={TH} /></tr></thead>
                <tbody>{data.content.map(({ job: j, ownerEmail, datasetExists }) => (
                  <tr key={j.id} className="hover:bg-subtle/60">
                    <td className={TD}><div className="font-medium">{j.datasetName}{!datasetExists && <Badge className="ml-2">deleted</Badge>}</div><div className="text-xs text-ink-3">{ownerEmail}</div>
                      {j.errorMessage && <div className="max-w-sm truncate text-xs text-danger" title={j.errorMessage}>{j.errorMessage}</div>}</td>
                    <td className={cx(TD, 'text-[13px] text-ink-2')}>{j.type.toLowerCase()}</td>
                    <td className={TD}><StatusBadge status={j.status} /></td>
                    <td className={TD}>
                      <div className="w-36"><div className="h-1.5 overflow-hidden rounded-full bg-subtle"><div className="h-full rounded-full bg-accent transition-all" style={{ width: `${j.totalReviews ? (100 * j.processedReviews) / j.totalReviews : j.status === 'COMPLETED' ? 100 : 0}%` }} /></div>
                        <div className="tnum mt-1 text-[11px] text-ink-3">{fmtNum(j.processedReviews)} / {fmtNum(j.totalReviews)}{j.stage && j.status === 'RUNNING' ? ` · ${j.stage.toLowerCase()}` : ''}</div></div>
                    </td>
                    <td className={cx(TD, 'tnum text-right text-[13px]')}>{fmtDuration(j.durationMs)}</td>
                    <td className={cx(TD, 'whitespace-nowrap text-[13px] text-ink-3')} title={fmtDateTime(j.createdAt)}>{fmtRelative(j.createdAt)}</td>
                    <td className={TD}>{['FAILED', 'CANCELLED'].includes(j.status) && datasetExists && <Button size="xs" icon={RotateCcw} onClick={() => setRetry({ job: j })}>Retry</Button>}</td>
                  </tr>))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onChange={setPage} />
          </>
        )}
      </Card>
      <ConfirmationDialog open={Boolean(retry)} onClose={() => setRetry(null)} onConfirm={run} loading={busy} tone="primary" confirmLabel="Queue retry"
        title="Retry this analysis?" description={retry && `“${retry.job.datasetName}” will be re-processed from the original upload. Existing results for the dataset are replaced.`} />
    </>
  )
}
