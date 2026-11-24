import { useState } from 'react'
import { Eye, Search, ShieldCheck, ShieldOff, UserCheck, UserX } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useAuth, useToast } from '../../lib/providers'
import { useDebounced } from '../../lib/scope'
import { fmtDate, fmtDateTime, fmtNum, fmtRelative } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { Badge, Card, ConfirmationDialog, Drawer, EmptyState, ErrorState, Field, IconButton, Input, LoadingSkeleton, Pagination, Select, StatusBadge, TD, TH, cx } from '../../components/ui'
import { FilterCard, usePaged } from './shared'

function UserProfile({ id, onClose }) {
  const { data, error } = usePaged(id ? `/admin/users/${id}` : null, {}, [id])
  if (!id) return null
  return (
    <Drawer open={Boolean(id)} onClose={onClose} title={data?.user.name || 'User'} subtitle={data?.user.email}>
      {error ? <ErrorState message={error} /> : !data ? <LoadingSkeleton variant="rows" /> : (
        <div className="space-y-7">
          <div className="flex flex-wrap gap-2"><StatusBadge status={data.user.role} /><StatusBadge status={data.user.active ? 'ACTIVE' : 'INACTIVE'} /></div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {[['Datasets', data.user.datasetCount], ['Reviews uploaded', data.user.reviewCount], ['Reports', data.reports], ['Analysis jobs', data.jobs], ['Failed jobs', data.failedJobs]].map(([k, v]) => (
              <div key={k} className="rounded-xl bg-subtle px-3.5 py-3"><div className="text-xs text-ink-3">{k}</div><div className="tnum mt-0.5 text-lg font-semibold">{fmtNum(v)}</div></div>
            ))}
          </div>
          <dl className="divide-y divide-line rounded-xl border border-line text-sm">
            <div className="flex justify-between px-4 py-2.5"><dt className="text-ink-3">Account created</dt><dd>{fmtDateTime(data.user.createdAt)}</dd></div>
            <div className="flex justify-between px-4 py-2.5"><dt className="text-ink-3">Last sign-in</dt><dd>{data.user.lastLoginAt ? fmtDateTime(data.user.lastLoginAt) : 'Never'}</dd></div>
          </dl>
          <div>
            <h4 className="eyebrow mb-3">Recent datasets</h4>
            {data.recentDatasets.length === 0 ? <p className="text-sm text-ink-3">No datasets.</p> : (
              <ul className="space-y-2">{data.recentDatasets.map((d) => (
                <li key={d.id} className="flex items-center justify-between gap-3 rounded-xl border border-line px-4 py-2.5 text-sm">
                  <div className="min-w-0"><div className="truncate font-medium">{d.name}</div><div className="text-xs text-ink-3">{fmtNum(d.totalReviews)} reviews · {fmtDate(d.createdAt)}</div></div>
                  <StatusBadge status={d.status} />
                </li>))}
              </ul>
            )}
          </div>
          <div>
            <h4 className="eyebrow mb-3">Audit trail</h4>
            {data.activity.length === 0 ? <p className="text-sm text-ink-3">No audited actions involving this user.</p> : (
              <ol className="space-y-2 text-[13px]">{data.activity.map((l) => (
                <li key={l.id} className="flex justify-between gap-3"><span><Badge tone="accent">{l.action}</Badge> <span className="text-ink-3">by {l.actorEmail || 'system'}</span></span><span className="shrink-0 text-xs text-ink-3">{fmtRelative(l.timestamp)}</span></li>))}
              </ol>
            )}
          </div>
        </div>
      )}
    </Drawer>
  )
}

/** UserTable with search, role/status filters, role changes and activation (all confirmed and audited). */
export default function AdminUsers() {
  const { user: me } = useAuth()
  const toast = useToast()
  const [q, setQ] = useState('')
  const dq = useDebounced(q, 350)
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [sort, setSort] = useState('newest')
  const [page, setPage] = useState(0)
  const [view, setView] = useState(null)
  const [action, setAction] = useState(null)
  const [busy, setBusy] = useState(false)
  const { data, error, reload } = usePaged('/admin/users', { q: dq, role, status, sort, page, size: 15 })

  const run = async (reason) => {
    setBusy(true)
    try {
      if (action.kind === 'status') await api.patch(`/admin/users/${action.user.id}/status`, { active: !action.user.active, reason })
      else await api.patch(`/admin/users/${action.user.id}/role`, { role: action.user.role === 'ADMIN' ? 'USER' : 'ADMIN', reason })
      toast.success('User updated')
      setAction(null)
      reload()
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  const dialog = action && (action.kind === 'status'
    ? action.user.active
      ? { title: `Deactivate ${action.user.name}?`, description: 'The user is signed out immediately and cannot sign in or use the API until reactivated.', confirmLabel: 'Deactivate', tone: 'danger' }
      : { title: `Reactivate ${action.user.name}?`, description: 'The user will be able to sign in again.', confirmLabel: 'Reactivate', tone: 'primary' }
    : action.user.role === 'ADMIN'
      ? { title: `Remove administrator access from ${action.user.name}?`, description: 'The account becomes a regular user and its current sessions end. The last active administrator cannot be demoted.', confirmLabel: 'Remove admin access', tone: 'danger', requireText: action.user.id === me?.id ? 'DEMOTE' : undefined }
      : { title: `Grant administrator access to ${action.user.name}?`, description: 'Administrators can manage every user, dataset and setting on the platform.', confirmLabel: 'Make administrator', tone: 'primary', requireText: 'ADMIN' })

  return (
    <>
      <PageHeader eyebrow="Administration" title="Users" description="Search accounts, review usage and manage roles and access. Changes take effect immediately and are audited." />
      <FilterCard>
        <Field label="Search" className="min-w-[220px] flex-1">
          <div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-3" />
            <Input className="pl-9" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Name or email" aria-label="Search users" /></div>
        </Field>
        <Field label="Role" className="w-36"><Select value={role} onChange={(e) => { setRole(e.target.value); setPage(0) }}><option value="">All roles</option><option value="USER">User</option><option value="ADMIN">Admin</option></Select></Field>
        <Field label="Status" className="w-36"><Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}><option value="">Any status</option><option value="active">Active</option><option value="inactive">Inactive</option></Select></Field>
        <Field label="Sort" className="w-40"><Select value={sort} onChange={(e) => setSort(e.target.value)}><option value="newest">Newest first</option><option value="oldest">Oldest first</option><option value="lastLogin">Last sign-in</option><option value="name">Name</option></Select></Field>
      </FilterCard>
      <Card>
        {error ? <div className="p-5"><ErrorState message={error} onRetry={reload} /></div> : !data ? <LoadingSkeleton variant="rows" /> : data.content.length === 0 ? <EmptyState title="No users match these filters" /> : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] text-sm">
                <thead><tr><th className={TH}>User</th><th className={TH}>Role</th><th className={TH}>Status</th><th className={cx(TH, 'text-right')}>Datasets</th><th className={cx(TH, 'text-right')}>Reviews</th><th className={TH}>Created</th><th className={TH}>Last sign-in</th><th className={TH} /></tr></thead>
                <tbody>{data.content.map((u) => (
                  <tr key={u.id} className="hover:bg-subtle/60">
                    <td className={TD}>
                      <button onClick={() => setView(u.id)} className="flex items-center gap-3 text-left">
                        <span className="grid size-8 shrink-0 place-items-center rounded-full bg-accent/15 text-[11px] font-semibold text-accent-ink">{u.name.split(/\s+/).map((s) => s[0]).slice(0, 2).join('').toUpperCase()}</span>
                        <span className="min-w-0"><span className="block truncate font-medium hover:underline">{u.name}{u.id === me?.id && <span className="ml-1.5 text-xs font-normal text-ink-3">(you)</span>}</span><span className="block truncate text-xs text-ink-3">{u.email}</span></span>
                      </button>
                    </td>
                    <td className={TD}><StatusBadge status={u.role} /></td>
                    <td className={TD}><StatusBadge status={u.active ? 'ACTIVE' : 'INACTIVE'} /></td>
                    <td className={cx(TD, 'tnum text-right')}>{fmtNum(u.datasetCount)}</td>
                    <td className={cx(TD, 'tnum text-right')}>{fmtNum(u.reviewCount)}</td>
                    <td className={cx(TD, 'whitespace-nowrap text-[13px] text-ink-3')}>{fmtDate(u.createdAt)}</td>
                    <td className={cx(TD, 'whitespace-nowrap text-[13px] text-ink-3')}>{u.lastLoginAt ? fmtRelative(u.lastLoginAt) : 'Never'}</td>
                    <td className={TD}>
                      <div className="flex justify-end gap-0.5">
                        <IconButton icon={Eye} label="View profile" onClick={() => setView(u.id)} />
                        <IconButton icon={u.role === 'ADMIN' ? ShieldOff : ShieldCheck} label={u.role === 'ADMIN' ? 'Remove admin access' : 'Make administrator'} onClick={() => setAction({ kind: 'role', user: u })} />
                        <IconButton icon={u.active ? UserX : UserCheck} label={u.active ? 'Deactivate' : 'Reactivate'} disabled={u.id === me?.id}
                          className={u.active ? 'hover:text-danger' : 'hover:text-success'} onClick={() => setAction({ kind: 'status', user: u })} />
                      </div>
                    </td>
                  </tr>))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onChange={setPage} />
          </>
        )}
      </Card>
      <UserProfile id={view} onClose={() => setView(null)} />
      <ConfirmationDialog open={Boolean(action)} onClose={() => setAction(null)} onConfirm={run} loading={busy} reasonField {...(dialog || {})} />
    </>
  )
}
