import { useCallback, useEffect, useState } from 'react'
import { Activity, CircleCheck, CircleX, Clock, Database, MessageSquareText, Package, UserCheck, Users, UserX } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { fmtCompact, fmtDuration, fmtNum, fmtRelative } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { Legend, SeriesChart, usePalette } from '../../components/charts'
import { Card, CardHeader, ErrorState, LoadingSkeleton, Segmented, StatusBadge, cx } from '../../components/ui'
import { AdminMetricCard, SystemHealthPanel } from './shared'

const ICON = { USER_REGISTERED: Users, DATASET_UPLOADED: Database, JOB_COMPLETED: CircleCheck, JOB_FAILED: CircleX }

export default function AdminDashboard() {
  const c = usePalette()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [period, setPeriod] = useState('daily')
  const load = useCallback(() => api.get('/admin/dashboard').then((r) => { setData(r.data); setError('') }).catch((e) => setError(errorMessage(e))), [])
  useEffect(() => { load(); const t = setInterval(load, 30_000); return () => clearInterval(t) }, [load])
  if (error) return <ErrorState message={error} onRetry={load} />
  if (!data) return <LoadingSkeleton />
  const u = data.users
  const j = data.jobs
  const usage = data.usage[period]
  const p = data.processing

  return (
    <>
      <PageHeader eyebrow="Administration" title="Platform overview" description="Live figures from MongoDB — users, content, analysis jobs and service health." />
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <AdminMetricCard label="Registered users" value={fmtNum(u.total)} sub={`${fmtNum(u.admins)} administrator${u.admins === 1 ? '' : 's'}`} icon={Users} />
        <AdminMetricCard label="Active users" value={fmtNum(u.active)} sub={`${fmtNum(u.inactive)} inactive`} icon={UserCheck} delay={0.03} />
        <AdminMetricCard label="Datasets" value={fmtNum(data.datasets)} sub={`${fmtNum(data.products)} products`} icon={Database} delay={0.06} />
        <AdminMetricCard label="Reviews" value={fmtCompact(data.reviews)} sub={`${fmtCompact(data.reviewsAnalyzed)} analysed`} icon={MessageSquareText} delay={0.09} />
        <AdminMetricCard label="Pending jobs" value={fmtNum(j.queued + j.running)} sub={`${j.running} running · ${j.queued} queued`} icon={Clock} delay={0.12} />
        <AdminMetricCard label="Failed jobs" value={fmtNum(j.failed)} sub={`${fmtNum(j.completed)} completed`} icon={CircleX} tone={j.failed ? 'text-danger' : undefined} delay={0.15} />
        <AdminMetricCard label="Success rate (30 d)" value={p.successRate == null ? '–' : `${p.successRate}%`} sub={`${fmtNum(p.jobsLast30Days)} finished jobs`} icon={Activity} delay={0.18} />
        <AdminMetricCard label="Avg. analysis time" value={fmtDuration(p.avgDurationMs)} sub={p.reviewsPerSecond ? `${fmtNum(p.reviewsPerSecond)} reviews / s` : 'no completed jobs yet'} icon={Package} delay={0.21} />
      </div>

      <div className="mt-5 grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Platform usage" description="New accounts and dataset uploads per period (UTC)"
            action={<Segmented value={period} onChange={setPeriod} options={[{ value: 'daily', label: '30 days' }, { value: 'weekly', label: '12 weeks' }, { value: 'monthly', label: '12 months' }]} />} />
          <div className="px-5 pt-3"><Legend items={[{ label: 'New users', color: c.indigo }, { label: 'Dataset uploads', color: c.series[2] }]} /></div>
          <div className="px-3 pb-4 pt-2"><SeriesChart data={usage} type="bar" series={[{ key: 'newUsers', label: 'New users', color: c.indigo }, { key: 'datasets', label: 'Dataset uploads', color: c.series[2] }]} /></div>
          <div className="border-t border-line px-5 pt-4"><h4 className="text-[13px] font-semibold">Reviews processed</h4></div>
          <div className="px-3 pb-4 pt-1"><SeriesChart data={usage} height={180} series={[{ key: 'reviewsProcessed', label: 'Reviews processed', color: c.accent }]} /></div>
        </Card>
        <Card>
          <CardHeader title="Recent activity" description="Accounts, uploads, analysis jobs and admin actions" />
          <ul className="max-h-[560px] space-y-0.5 overflow-y-auto p-3">
            {data.recentActivity.map((a, i) => {
              const Icon = ICON[a.type] || Activity
              return (
                <li key={i} className="flex gap-3 rounded-lg px-2 py-2.5 hover:bg-subtle">
                  <span className={cx('mt-0.5 grid size-7 shrink-0 place-items-center rounded-lg', a.type === 'JOB_FAILED' ? 'bg-neg-soft text-danger' : 'bg-accent-soft text-accent-ink')}><Icon className="size-3.5" /></span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2"><span className="truncate text-[13px] font-medium">{a.title}</span><span className="shrink-0 text-[11px] text-ink-3">{fmtRelative(a.timestamp)}</span></div>
                    <p className="truncate text-xs text-ink-3">{a.detail}{a.actor && ` · by ${a.actor}`}</p>
                  </div>
                </li>
              )
            })}
            {data.recentActivity.length === 0 && <li className="px-2 py-6 text-center text-sm text-ink-3">No activity yet.</li>}
          </ul>
        </Card>
      </div>
      <div className="mt-5"><SystemHealthPanel health={data.health} onRefresh={load} /></div>
      {!u.inactive ? null : <p className="mt-4 flex items-center gap-1.5 text-xs text-ink-3"><UserX className="size-3.5" />{u.inactive} deactivated account(s) cannot sign in or use the API.</p>}
      <p className="mt-2 text-xs text-ink-3">Analysis jobs: <StatusBadge status="QUEUED" label={`${j.queued} queued`} /> <StatusBadge status="RUNNING" label={`${j.running} running`} /> <StatusBadge status="CANCELLED" label={`${j.cancelled} cancelled`} /></p>
    </>
  )
}
