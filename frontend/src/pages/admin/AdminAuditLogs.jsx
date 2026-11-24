import { useState } from 'react'
import { Download, Lock, Search } from 'lucide-react'
import { download, errorMessage } from '../../lib/api'
import { useToast } from '../../lib/providers'
import { useDebounced } from '../../lib/scope'
import { fmtDateTime, fmtRelative } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { Badge, Button, Card, Drawer, EmptyState, ErrorState, Field, Input, LoadingSkeleton, Pagination, Select, TD, TH, cx } from '../../components/ui'
import { FilterCard, usePaged } from './shared'

const TONE = (a) => (/DELETED|DEACTIVATED|FAILED/.test(a) ? 'danger' : /SETTINGS|ROLE|RETRIED/.test(a) ? 'warning' : /LOGIN|ACTIVATED|BOOTSTRAP/.test(a) ? 'success' : 'accent')
const summary = (m) => Object.entries(m || {}).filter(([k]) => k !== 'changes' && k !== 'reviewIds').map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : v}`).join(' · ')

/** AuditLogTable - read-only view of the append-only audit trail. */
export default function AdminAuditLogs() {
  const toast = useToast()
  const [action, setAction] = useState('')
  const [actor, setActor] = useState('')
  const da = useDebounced(actor, 350)
  const [resourceType, setResourceType] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState(null)
  const actions = usePaged('/admin/audit-logs/actions', {})
  const params = { action, actor: da, resourceType, from, to }
  const { data, error, reload } = usePaged('/admin/audit-logs', { ...params, page, size: 25 })
  const set = (fn) => (e) => { fn(e.target.value); setPage(0) }

  return (
    <>
      <PageHeader eyebrow="Administration" title="Audit logs" description="Every administrative action, sign-in and configuration change. Entries are append-only and cannot be edited or deleted through the application."
        actions={<Button icon={Download} onClick={() => download('/admin/audit-logs/export', params, 'audit-log.csv').catch((e) => toast.error(errorMessage(e)))}>Export CSV</Button>} />
      <FilterCard>
        <Field label="Action" className="w-52"><Select value={action} onChange={set(setAction)}><option value="">All actions</option>{(actions.data || []).map((a) => <option key={a} value={a}>{a}</option>)}</Select></Field>
        <Field label="Actor" className="min-w-[200px] flex-1"><div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-3" /><Input className="pl-9" value={actor} onChange={set(setActor)} placeholder="Administrator email" /></div></Field>
        <Field label="Resource" className="w-44"><Select value={resourceType} onChange={set(setResourceType)}><option value="">Any</option>{['USER', 'DATASET', 'PRODUCT', 'REVIEW', 'ANALYSIS_JOB', 'SYSTEM_SETTINGS', 'PLATFORM_REPORT'].map((r) => <option key={r} value={r}>{r.toLowerCase().replace('_', ' ')}</option>)}</Select></Field>
        <Field label="From"><Input type="date" value={from} onChange={set(setFrom)} /></Field>
        <Field label="To"><Input type="date" value={to} onChange={set(setTo)} /></Field>
      </FilterCard>
      <Card>
        {error ? <div className="p-5"><ErrorState message={error} onRetry={reload} /></div> : !data ? <LoadingSkeleton variant="rows" /> : data.content.length === 0 ? <EmptyState title="No audit entries match these filters" /> : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[920px] text-sm">
                <thead><tr><th className={TH}>Time</th><th className={TH}>Actor</th><th className={TH}>Action</th><th className={TH}>Resource</th><th className={TH}>Details</th><th className={TH}>IP</th></tr></thead>
                <tbody>{data.content.map((l) => (
                  <tr key={l.id} tabIndex={0} onClick={() => setOpen(l)} onKeyDown={(e) => e.key === 'Enter' && setOpen(l)} className="cursor-pointer hover:bg-subtle/60 focus:bg-subtle focus:outline-none">
                    <td className={cx(TD, 'whitespace-nowrap text-[13px] text-ink-2')} title={fmtDateTime(l.timestamp)}>{fmtRelative(l.timestamp)}</td>
                    <td className={cx(TD, 'text-[13px]')}>{l.actorEmail || 'system'}</td>
                    <td className={TD}><Badge tone={TONE(l.action)}>{l.action}</Badge></td>
                    <td className={cx(TD, 'max-w-[220px] truncate text-[13px]')}>{l.resourceLabel || '—'}<div className="text-xs text-ink-3">{l.resourceType?.toLowerCase().replace('_', ' ')}</div></td>
                    <td className={cx(TD, 'max-w-[280px] truncate text-xs text-ink-3')}>{l.metadata?.changes ? `${Object.keys(l.metadata.changes).length} setting(s) changed` : summary(l.metadata) || '—'}</td>
                    <td className={cx(TD, 'text-xs text-ink-3')}>{l.ipAddress || '—'}</td>
                  </tr>))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onChange={setPage} />
          </>
        )}
      </Card>
      <Drawer open={Boolean(open)} onClose={() => setOpen(null)} title={open?.action} subtitle={open && `${fmtDateTime(open.timestamp)} · ${open.actorEmail || 'system'}`}>
        {open && (
          <div className="space-y-5 text-sm">
            <dl className="divide-y divide-line rounded-xl border border-line">
              {[['Resource type', open.resourceType], ['Resource', open.resourceLabel], ['Resource id', open.resourceId], ['Actor id', open.actorId], ['IP address', open.ipAddress]].map(([k, v]) => (
                <div key={k} className="flex justify-between gap-4 px-4 py-2.5"><dt className="text-ink-3">{k}</dt><dd className="truncate text-right font-mono text-[12.5px]">{v || '—'}</dd></div>
              ))}
            </dl>
            <div><h4 className="eyebrow mb-2">Metadata</h4><pre className="overflow-x-auto rounded-xl bg-subtle p-4 text-xs leading-relaxed text-ink-2">{JSON.stringify(open.metadata, null, 2)}</pre></div>
            <p className="flex items-center gap-1.5 text-xs text-ink-3"><Lock className="size-3.5" />Audit entries are immutable — the API offers no way to modify or delete them.</p>
          </div>
        )}
      </Drawer>
    </>
  )
}
