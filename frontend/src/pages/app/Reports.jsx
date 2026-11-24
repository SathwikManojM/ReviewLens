import { useCallback, useEffect, useState } from 'react'
import { Download, FileSpreadsheet, FileText, Trash2 } from 'lucide-react'
import api, { download, errorMessage } from '../../lib/api'
import { useStatus, useToast } from '../../lib/providers'
import { useScope } from '../../lib/scope'
import { fmtBytes, fmtDateTime, fmtNum } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { NoDatasets } from '../../components/data'
import { Badge, Button, Card, CardHeader, ConfirmationDialog, EmptyState, ErrorState, Field, IconButton, LoadingSkeleton, Pagination, Segmented, Select, StatusBadge, TD, TH, cx } from '../../components/ui'

export default function Reports() {
  const scope = useScope()
  const toast = useToast()
  const { feature } = useStatus()
  const [format, setFormat] = useState('PDF')
  const [productId, setProductId] = useState('')
  const [compare, setCompare] = useState([])
  const [creating, setCreating] = useState(false)
  const [lib, setLib] = useState(null)
  const [page, setPage] = useState(0)
  const [error, setError] = useState('')
  const [confirm, setConfirm] = useState(null)

  const load = useCallback(() => api.get('/reports', { params: { page, size: 10 } }).then((r) => { setLib(r.data); setError('') }).catch((e) => setError(errorMessage(e))), [page])
  useEffect(() => { load() }, [load])
  const generating = lib?.content.some((r) => r.status === 'GENERATING')
  useEffect(() => {
    if (!generating) return undefined
    const t = setInterval(load, 1500)
    return () => clearInterval(t)
  }, [generating, load])
  useEffect(() => { setProductId(''); setCompare([]) }, [scope.datasetId])

  if (!feature('reportGeneration')) return <><PageHeader title="Reports" /><Card><EmptyState title="Report generation is disabled">An administrator has turned this feature off.</EmptyState></Card></>
  if (scope.datasets === null) return <LoadingSkeleton />

  const create = async () => {
    setCreating(true)
    try {
      await api.post('/reports', { datasetId: scope.datasetId, productId: productId || null, format, compareProductIds: format === 'PDF' && compare.length >= 2 ? compare : null })
      toast.success('Report queued — it will appear in the library when ready.')
      setPage(0)
      load()
    } catch (e) { toast.error(errorMessage(e)) } finally { setCreating(false) }
  }
  const toggle = (id) => setCompare((c) => (c.includes(id) ? c.filter((x) => x !== id) : c.length < 4 ? [...c, id] : c))
  const remove = async () => {
    try { await api.delete(`/reports/${confirm.id}`); toast.success('Report deleted'); setConfirm(null); load() } catch (e) { toast.error(errorMessage(e)) }
  }

  return (
    <>
      <PageHeader eyebrow="Workspace" title="Reports" description="Generate a polished PDF analysis report or export the aspect-level data as CSV. Files are kept in your report library." />
      {!scope.datasets.length ? <NoDatasets /> : (
        <Card className="mb-6">
          <CardHeader title="New report" description="Reports are generated in the background from the stored analysis results" />
          <div className="grid gap-4 p-5 md:grid-cols-[1fr_1fr_auto]">
            <Field label="Dataset"><Select value={scope.datasetId || ''} onChange={(e) => scope.setDataset(e.target.value)}>{scope.datasets.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</Select></Field>
            <Field label="Scope"><Select value={productId} onChange={(e) => setProductId(e.target.value)}><option value="">All products</option>{scope.products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</Select></Field>
            <Field label="Format"><Segmented value={format} onChange={setFormat} options={[{ value: 'PDF', label: 'PDF report', icon: FileText }, { value: 'CSV', label: 'CSV data', icon: FileSpreadsheet }]} /></Field>
          </div>
          {format === 'PDF' && scope.products.length > 1 && (
            <div className="border-t border-line px-5 py-4">
              <div className="mb-2 text-[13px] font-medium text-ink-2">Include a product comparison <span className="font-normal text-ink-3">(optional · choose 2–4)</span></div>
              <div className="flex max-h-40 flex-wrap gap-2 overflow-y-auto">
                {scope.products.slice(0, 60).map((p) => (
                  <button key={p.id} type="button" onClick={() => toggle(p.id)} aria-pressed={compare.includes(p.id)}
                    className={cx('rounded-full border px-3 py-1 text-[13px] transition', compare.includes(p.id) ? 'border-accent bg-accent-soft text-accent-ink' : 'border-line text-ink-2 hover:border-line-strong')}>
                    {p.name.length > 36 ? `${p.name.slice(0, 36)}…` : p.name}
                  </button>
                ))}
              </div>
            </div>
          )}
          <div className="flex justify-end border-t border-line px-5 py-4">
            <Button variant="primary" icon={format === 'PDF' ? FileText : FileSpreadsheet} loading={creating} disabled={!scope.datasetId || (compare.length === 1 && format === 'PDF')} onClick={create}>Generate {format}</Button>
          </div>
        </Card>
      )}

      <Card>
        <CardHeader title="Report library" description={lib ? `${fmtNum(lib.totalElements)} report${lib.totalElements === 1 ? '' : 's'}` : ''} />
        {error ? <div className="p-5"><ErrorState message={error} onRetry={load} /></div> : !lib ? <LoadingSkeleton variant="rows" /> : lib.content.length === 0 ? (
          <EmptyState icon={FileText} title="No reports yet">Generate your first report above.</EmptyState>
        ) : (
          <>
            <div className="mt-3 overflow-x-auto">
              <table className="w-full min-w-[760px] text-sm">
                <thead><tr><th className={TH}>Report</th><th className={TH}>Format</th><th className={TH}>Status</th><th className={cx(TH, 'text-right')}>Size</th><th className={TH}>Created</th><th className={TH} /></tr></thead>
                <tbody>
                  {lib.content.map((r) => (
                    <tr key={r.id} className="hover:bg-subtle/60">
                      <td className={TD}>
                        <div className="font-medium">{r.title}</div>
                        <div className="text-xs text-ink-3">Dataset: {r.datasetName}{r.compareProductIds?.length ? ` · compares ${r.compareProductIds.length} products` : ''}</div>
                        {r.errorMessage && <div className="text-xs text-danger">{r.errorMessage}</div>}
                      </td>
                      <td className={TD}><Badge tone={r.format === 'PDF' ? 'accent' : 'neutral'}>{r.format}</Badge></td>
                      <td className={TD}><StatusBadge status={r.status} /></td>
                      <td className={cx(TD, 'tnum text-right text-[13px] text-ink-2')}>{r.status === 'READY' ? fmtBytes(r.sizeBytes) : '–'}</td>
                      <td className={cx(TD, 'whitespace-nowrap text-[13px] text-ink-3')}>{fmtDateTime(r.createdAt)}</td>
                      <td className={TD}>
                        <div className="flex justify-end gap-0.5">
                          <IconButton icon={Download} label="Download" disabled={r.status !== 'READY'}
                            onClick={() => download(`/reports/${r.id}/download`, {}, `report.${r.format.toLowerCase()}`).catch((e) => toast.error(errorMessage(e)))} />
                          <IconButton icon={Trash2} label="Delete report" className="hover:text-danger" onClick={() => setConfirm(r)} />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={lib.page} totalPages={lib.totalPages} totalElements={lib.totalElements} size={lib.size} onChange={setPage} />
          </>
        )}
      </Card>
      <ConfirmationDialog open={Boolean(confirm)} onClose={() => setConfirm(null)} onConfirm={remove} title="Delete report?" confirmLabel="Delete"
        description={confirm && `“${confirm.title}” will be removed from your library.`} />
    </>
  )
}
