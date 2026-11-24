import { useState } from 'react'
import { Search, Trash2 } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useToast } from '../../lib/providers'
import { useDebounced } from '../../lib/scope'
import { fmtNum, fmtScore, pct } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { usePalette } from '../../components/charts'
import { AspectChips } from '../../components/data'
import { Badge, Button, Card, ConfirmationDialog, Drawer, EmptyState, ErrorState, Field, Input, LoadingSkeleton, Pagination, Stars, TD, TH, cx } from '../../components/ui'
import { FilterCard, usePaged } from './shared'

function ProductDetail({ id, onClose, onDelete }) {
  const c = usePalette()
  const { data, error } = usePaged(id ? `/admin/products/${id}` : null, {}, [id])
  const p = data?.product
  return (
    <Drawer open={Boolean(id)} onClose={onClose} title={p?.name || 'Product'} subtitle={p && `${p.datasetName} · ${p.ownerEmail}`}
      actions={p && <Button size="sm" variant="ghost" icon={Trash2} className="text-danger" onClick={() => onDelete(p)}>Delete</Button>}>
      {error ? <ErrorState message={error} /> : !data ? <LoadingSkeleton variant="rows" /> : (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[['Reviews', fmtNum(p.reviewCount)], ['Avg rating', p.avgRating?.toFixed(2) ?? '–'], ['Duplicates', fmtNum(data.duplicateReviews)], ['Flagged', fmtNum(data.flaggedReviews)]].map(([k, v]) => (
              <div key={k} className="rounded-xl bg-subtle px-3.5 py-3"><div className="text-xs text-ink-3">{k}</div><div className="tnum mt-0.5 text-lg font-semibold">{v}</div></div>
            ))}
          </div>
          <div>
            <h4 className="eyebrow mb-3">Aspect sentiment</h4>
            <ul className="space-y-2">{data.aspects.map((a) => (
              <li key={a.aspect} className="grid grid-cols-[120px_1fr_48px] items-center gap-3 text-[13px]">
                <span className="truncate">{a.aspect}</span>
                <span className="flex h-2 overflow-hidden rounded-full bg-subtle"><span style={{ width: `${pct(a.positive, a.mentions)}%`, background: c.pos }} /><span style={{ width: `${pct(a.neutral, a.mentions)}%`, background: c.neu }} /><span style={{ width: `${pct(a.negative, a.mentions)}%`, background: c.neg }} /></span>
                <span className="tnum text-right text-xs text-ink-2">{fmtScore(a.netSentiment)}</span>
              </li>))}
              {data.aspects.length === 0 && <li className="text-sm text-ink-3">No aspects extracted.</li>}
            </ul>
          </div>
          <div>
            <h4 className="eyebrow mb-3">Latest reviews</h4>
            <ul className="space-y-2">{data.sampleReviews.map((r) => (
              <li key={r.id} className="rounded-xl border border-line px-4 py-3 text-[13px]">
                <div className="flex items-center gap-2"><Stars rating={r.rating} />{r.duplicate && <Badge tone="warning">Duplicate</Badge>}</div>
                <p className="mt-1.5 line-clamp-3 text-ink-2">{r.text}</p>
                <div className="mt-2"><AspectChips aspects={r.aspects} /></div>
              </li>))}
            </ul>
          </div>
        </div>
      )}
    </Drawer>
  )
}

export default function AdminProducts() {
  const toast = useToast()
  const c = usePalette()
  const [q, setQ] = useState('')
  const dq = useDebounced(q, 350)
  const [category, setCategory] = useState('')
  const dc = useDebounced(category, 350)
  const [page, setPage] = useState(0)
  const [view, setView] = useState(null)
  const [del, setDel] = useState(null)
  const [busy, setBusy] = useState(false)
  const { data, error, reload } = usePaged('/admin/products', { q: dq, category: dc, page, size: 15 })
  const remove = async (reason) => {
    setBusy(true)
    try {
      const r = await api.delete(`/admin/products/${del.id}`, { params: { reason } })
      toast.success(`Removed product and ${fmtNum(r.data.reviews)} reviews`)
      setDel(null)
      setView(null)
      reload()
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  return (
    <>
      <PageHeader eyebrow="Administration" title="Products" description="Every product across all datasets, with review counts and sentiment. Moderation is audited." />
      <FilterCard>
        <Field label="Search" className="min-w-[220px] flex-1"><div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-3" />
          <Input className="pl-9" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Name, brand or ASIN" aria-label="Search products" /></div></Field>
        <Field label="Category" className="w-56"><Input value={category} onChange={(e) => { setCategory(e.target.value); setPage(0) }} placeholder="e.g. Electronics" /></Field>
      </FilterCard>
      <Card>
        {error ? <div className="p-5"><ErrorState message={error} onRetry={reload} /></div> : !data ? <LoadingSkeleton variant="rows" /> : data.content.length === 0 ? <EmptyState title="No products found" /> : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] text-sm">
                <thead><tr><th className={TH}>Product</th><th className={TH}>Dataset</th><th className={cx(TH, 'text-right')}>Reviews</th><th className={cx(TH, 'text-right')}>Rating</th><th className={TH}>Sentiment</th><th className={TH} /></tr></thead>
                <tbody>{data.content.map((p) => (
                  <tr key={p.id} tabIndex={0} onClick={() => setView(p.id)} onKeyDown={(e) => e.key === 'Enter' && setView(p.id)} className="cursor-pointer hover:bg-subtle/60 focus:bg-subtle focus:outline-none">
                    <td className={TD}><div className="max-w-sm truncate font-medium">{p.name}</div><div className="text-xs text-ink-3">{[p.brand, p.category?.split(',')[0]].filter(Boolean).join(' · ') || '—'}</div></td>
                    <td className={TD}><div className="text-[13px]">{p.datasetName}</div><div className="text-xs text-ink-3">{p.ownerEmail}</div></td>
                    <td className={cx(TD, 'tnum text-right')}>{fmtNum(p.reviewCount)}</td>
                    <td className={cx(TD, 'tnum text-right')}>{p.avgRating?.toFixed(2) ?? '–'}</td>
                    <td className={TD}><div className="flex items-center gap-2"><span className="flex h-1.5 w-28 overflow-hidden rounded-full bg-subtle"><span style={{ width: `${p.positivePct}%`, background: c.pos }} /><span className="flex-1" /><span style={{ width: `${p.negativePct}%`, background: c.neg }} /></span><span className="tnum text-xs text-ink-3">{p.positivePct}%</span></div></td>
                    <td className={cx(TD, 'text-right text-xs font-medium text-accent-ink')}>View</td>
                  </tr>))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onChange={setPage} />
          </>
        )}
      </Card>
      <ProductDetail id={view} onClose={() => setView(null)} onDelete={setDel} />
      <ConfirmationDialog open={Boolean(del)} onClose={() => setDel(null)} onConfirm={remove} loading={busy} reasonField requireText="DELETE"
        title="Delete product?" confirmLabel="Delete product" description={del && `“${del.name}” and all ${fmtNum(del.reviewCount)} of its reviews and aspect results will be removed.`} />
    </>
  )
}
