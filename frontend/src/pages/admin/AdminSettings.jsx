import { useEffect, useState } from 'react'
import { Save, TriangleAlert } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useStatus, useToast } from '../../lib/providers'
import { fmtDateTime } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { Button, Card, CardHeader, ConfirmationDialog, ErrorState, Field, Input, LoadingSkeleton, Switch, Textarea, cx } from '../../components/ui'

const NUMBERS = [
  ['maxUploadSizeMb', 'Maximum upload size (MB)', 1, 8192, 'Use chunked uploads for multi-GB datasets'],
  ['maxReviewsPerDataset', 'Maximum reviews per dataset', 100, 200000, 'Rows beyond this limit are not analysed'],
  ['maxConcurrentJobs', 'Concurrent analysis jobs', 1, 8, 'Jobs above this number wait in the queue'],
  ['maxDatasetsPerUser', 'Datasets per user', 1, 1000, 'Usage limit per account'],
  ['maxAnalysesPerUserPerDay', 'Analyses per user per 24 h', 1, 1000, 'Uploads plus re-runs'],
]
const FLAGS = [
  ['registrationOpen', 'Public registration', 'Allow new users to create accounts'],
  ['liveAnalyzer', 'Live analyzer', 'Interactive single-review analysis in the workspace'],
  ['productComparison', 'Product comparison', 'Side-by-side comparison of 2–4 products'],
  ['reportGeneration', 'Report generation', 'PDF and CSV reports'],
  ['publicDemo', 'Landing-page demo', 'Unauthenticated, rate-limited demo on the landing page'],
]

export default function AdminSettings() {
  const toast = useToast()
  const { refresh } = useStatus()
  const [saved, setSaved] = useState(null)
  const [form, setForm] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const load = () => api.get('/admin/settings').then((r) => { setSaved(r.data); setForm(r.data); setError('') }).catch((e) => setError(errorMessage(e)))
  useEffect(() => { load() }, [])
  if (error) return <ErrorState message={error} onRetry={load} />
  if (!form) return <LoadingSkeleton />

  const invalid = NUMBERS.filter(([k, , min, max]) => !(Number(form[k]) >= min && Number(form[k]) <= max)).map(([k]) => k)
  const dirty = JSON.stringify(form) !== JSON.stringify(saved)
  const save = async (confirmed = false) => {
    setBusy(true)
    try {
      const body = { ...form, ...Object.fromEntries(NUMBERS.map(([k]) => [k, Number(form[k])])), confirm: confirmed }
      const r = await api.put('/admin/settings', body)
      setSaved(r.data)
      setForm(r.data)
      setConfirm(false)
      refresh()
      toast.success('Settings saved and recorded in the audit log')
    } catch (e) {
      if (e.response?.data?.code === 'CONFIRMATION_REQUIRED') setConfirm(true)
      else toast.error(errorMessage(e))
    } finally { setBusy(false) }
  }
  const toggleFormat = (f) => setForm((s) => ({ ...s, allowedFormats: s.allowedFormats.includes(f) ? s.allowedFormats.filter((x) => x !== f) : [...s.allowedFormats, f] }))

  return (
    <>
      <PageHeader eyebrow="Administration" title="System settings" description="Limits, feature flags and maintenance mode. Values are validated on the server and every change is audited."
        actions={<>
          {dirty && <Button onClick={() => setForm(saved)} disabled={busy}>Discard</Button>}
          <Button variant="primary" icon={Save} loading={busy} disabled={!dirty || invalid.length > 0 || form.allowedFormats.length === 0} onClick={() => save(false)}>Save changes</Button>
        </>} />
      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader title="Limits" description="Upload, processing and per-user usage limits" />
          <div className="grid gap-4 p-5 sm:grid-cols-2">
            {NUMBERS.map(([k, label, min, max, hint]) => (
              <Field key={k} label={label} hint={`${hint} (${min}–${max.toLocaleString()})`} error={invalid.includes(k) ? `Enter a value between ${min} and ${max.toLocaleString()}` : ''}>
                <Input type="number" min={min} max={max} value={form[k]} invalid={invalid.includes(k)} onChange={(e) => setForm({ ...form, [k]: e.target.value })} />
              </Field>
            ))}
            <Field label="Allowed dataset formats" error={form.allowedFormats.length === 0 ? 'Select at least one format' : ''}>
              <div className="flex gap-2">
                {['csv', 'tsv', 'txt', 'jsonl', 'jsonl.gz', 'ndjson'].map((f) => (
                  <button key={f} type="button" onClick={() => toggleFormat(f)} aria-pressed={form.allowedFormats.includes(f)}
                    className={cx('rounded-lg border px-3 py-1.5 text-[13px] font-medium transition', form.allowedFormats.includes(f) ? 'border-accent bg-accent-soft text-accent-ink' : 'border-line text-ink-3')}>.{f}</button>
                ))}
              </div>
            </Field>
          </div>
        </Card>
        <div className="space-y-5">
          <Card>
            <CardHeader title="Feature flags" description="Enforced by the backend for every request" />
            <div className="space-y-4 p-5">
              {FLAGS.map(([k, label, desc]) => (
                <Switch key={k} label={label} description={desc} checked={form.featureFlags[k] !== false}
                  onChange={(v) => setForm({ ...form, featureFlags: { ...form.featureFlags, [k]: v } })} />
              ))}
            </div>
          </Card>
          <Card className={cx(form.maintenanceMode && 'ring-2 ring-warning/50')}>
            <CardHeader title="Maintenance mode" description="Blocks the user workspace for everyone except administrators" />
            <div className="space-y-4 p-5">
              <Switch label="Enable maintenance mode" description="Users see the message below; sign-in and the admin portal stay available." checked={form.maintenanceMode}
                onChange={(v) => setForm({ ...form, maintenanceMode: v })} />
              <Field label="Message shown to users"><Textarea rows={2} maxLength={300} value={form.maintenanceMessage || ''} onChange={(e) => setForm({ ...form, maintenanceMessage: e.target.value })} /></Field>
            </div>
          </Card>
        </div>
      </div>
      {saved.updatedAt && <p className="mt-5 text-xs text-ink-3">Last changed {fmtDateTime(saved.updatedAt)} by {saved.updatedBy}.</p>}
      <ConfirmationDialog open={confirm} onClose={() => setConfirm(false)} onConfirm={() => save(true)} loading={busy} requireText="CONFIRM"
        title="Apply a platform-wide change?" confirmLabel="Apply change"
        description="This change affects every user (for example enabling maintenance mode or closing registration).">
        <p className="flex items-start gap-2 rounded-xl bg-warning-soft px-3.5 py-3 text-[13px] text-ink"><TriangleAlert className="mt-0.5 size-4 shrink-0 text-warning" />It takes effect immediately and is recorded in the audit log.</p>
      </ConfirmationDialog>
    </>
  )
}

