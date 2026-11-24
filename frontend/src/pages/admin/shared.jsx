import { useCallback, useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Cpu, Database, Server } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { fmtDuration, fmtRelative } from '../../lib/format'
import { Card, CardHeader, StatusBadge, cx } from '../../components/ui'

/** Fetches a (paged) admin resource; params with empty values are dropped. */
export function usePaged(url, params, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const key = JSON.stringify(params)
  const load = useCallback(() => {
    setError('')
    if (!url) return Promise.resolve()
    const clean = Object.fromEntries(Object.entries(params || {}).filter(([, v]) => v !== '' && v != null))
    return api.get(url, { params: clean }).then((r) => setData(r.data)).catch((e) => setError(errorMessage(e)))
  }, [url, key, ...deps]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { load() }, [load])
  return { data, error, reload: load, setData }
}

export function AdminMetricCard({ label, value, sub, icon: Icon, tone, delay = 0 }) {
  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay }} className="card p-4">
      <div className="flex items-center justify-between">
        <span className="text-[12.5px] font-medium text-ink-2">{label}</span>
        {Icon && <Icon className={cx('size-4', tone || 'text-ink-3')} />}
      </div>
      <div className={cx('tnum mt-2 text-[26px] font-semibold tracking-[-0.03em]', tone)}>{value}</div>
      {sub && <div className="mt-1 text-xs text-ink-3">{sub}</div>}
    </motion.div>
  )
}

const ICONS = { 'Spring Boot API': Server, MongoDB: Database, 'Python NLP engine': Cpu }
const DETAIL_LABELS = { uptimeSeconds: 'Uptime', heapUsedMb: 'Heap used', heapMaxMb: 'Heap max', runningJobs: 'Running jobs', queuedJobs: 'Queued jobs', javaVersion: 'Java', database: 'Database', collections: 'Collections', engine: 'Engine', type: 'Model type', aspectCategories: 'Aspect categories', error: 'Error' }

export function SystemHealthPanel({ health, onRefresh }) {
  if (!health) return null
  return (
    <Card>
      <CardHeader title="System health" description={`Checked ${fmtRelative(health.checkedAt)}`}
        action={<div className="flex items-center gap-2"><StatusBadge status={health.overall} />{onRefresh && <button onClick={onRefresh} className="text-xs font-medium text-accent-ink hover:underline">Refresh</button>}</div>} />
      <div className="grid gap-3 p-5 md:grid-cols-3">
        {health.services.map((s) => {
          const Icon = ICONS[s.name] || Server
          return (
            <div key={s.name} className="rounded-xl border border-line p-4">
              <div className="flex items-center gap-2.5">
                <span className={cx('grid size-8 place-items-center rounded-lg', s.status === 'UP' ? 'bg-success-soft text-success' : 'bg-neg-soft text-danger')}><Icon className="size-4" /></span>
                <div className="min-w-0 flex-1"><div className="truncate text-sm font-semibold">{s.name}</div><div className="text-xs text-ink-3">{s.version ? `v${s.version}` : '—'}{s.latencyMs != null && ` · ${s.latencyMs} ms`}</div></div>
                <span className={cx('size-2.5 rounded-full', s.status === 'UP' ? 'bg-success shadow-[0_0_0_4px] shadow-success/15' : 'bg-danger shadow-[0_0_0_4px] shadow-danger/15')} title={s.status} />
              </div>
              <dl className="mt-3 space-y-1 text-xs">
                {Object.entries(s.details || {}).filter(([k]) => k !== 'startedAt').map(([k, v]) => (
                  <div key={k} className="flex justify-between gap-3"><dt className="text-ink-3">{DETAIL_LABELS[k] || k}</dt>
                    <dd className={cx('truncate text-right', k === 'error' ? 'text-danger' : 'text-ink-2')}>{k === 'uptimeSeconds' ? fmtDuration(v * 1000) : k.endsWith('Mb') ? `${v} MB` : String(v)}</dd></div>
                ))}
              </dl>
            </div>
          )
        })}
      </div>
    </Card>
  )
}

export function FilterCard({ children }) {
  return <Card className="mb-5 flex flex-wrap items-end gap-3 p-4">{children}</Card>
}
