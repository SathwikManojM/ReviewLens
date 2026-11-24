import { useEffect, useState } from 'react'
import { Search, Trash2 } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useToast } from '../../lib/providers'
import { useDebounced } from '../../lib/scope'
import { fmtNum } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { ReviewDrawer, ReviewTable } from '../../components/data'
import { Button, Card, ConfirmationDialog, ErrorState, Field, Input, LoadingSkeleton, Pagination, Select, cx } from '../../components/ui'
import { FilterCard, usePaged } from './shared'

const FLAGS = [['', 'All reviews', 'total'], ['DUPLICATE', 'Duplicates', 'duplicates'], ['TOO_SHORT', 'Too short', 'tooShort'], ['NO_RATING', 'No rating', 'noRating'], ['NO_ASPECTS', 'No aspects', 'noAspects']]

/** Review moderation: quality flags (duplicates / invalid records), search, single and bulk delete. */
export default function AdminReviews() {
  const toast = useToast()
  const [q, setQ] = useState('')
  const dq = useDebounced(q, 350)
  const [flag, setFlag] = useState('')
  const [sentiment, setSentiment] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState([])
  const [open, setOpen] = useState(null)
  const [del, setDel] = useState(null)
  const [busy, setBusy] = useState(false)
  const summary = usePaged('/admin/reviews/summary', {})
  const { data, error, reload } = usePaged('/admin/reviews', { q: dq, flag, sentiment, page, size: 20 })
  useEffect(() => setSelected([]), [data])

  const remove = async (reason) => {
    setBusy(true)
    try {
      if (del === 'bulk') {
        const r = await api.post('/admin/reviews/bulk-delete', { ids: selected, reason: reason || 'Bulk moderation' })
        toast.success(`Deleted ${fmtNum(r.data.reviews)} reviews`)
      } else {
        await api.delete(`/admin/reviews/${del.id}`, { params: { reason } })
        toast.success('Review deleted')
        setOpen(null)
      }
      setDel(null)
      reload()
      summary.reload()
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  const rows = (data?.content || []).map((r) => ({ ...r.review, datasetName: r.datasetName }))
  const toggle = (id) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : s.length < 100 ? [...s, id] : s))

  return (
    <>
      <PageHeader eyebrow="Administration" title="Reviews" description="Find duplicate or invalid records and moderate reviews. Deletions update product statistics and are audited." />
      <div className="mb-5 grid grid-cols-2 gap-3 md:grid-cols-5">
        {FLAGS.map(([value, label, k]) => (
          <button key={k} type="button" onClick={() => { setFlag(value); setPage(0) }}
            className={cx('card p-4 text-left transition hover:shadow-pop', flag === value && 'ring-2 ring-accent')}>
            <div className="text-[12.5px] font-medium text-ink-2">{label}</div>
            <div className="tnum mt-1.5 text-[22px] font-semibold tracking-tight">{summary.data ? fmtNum(summary.data[k]) : '–'}</div>
          </button>
        ))}
      </div>
      <FilterCard>
        <Field label="Search text" className="min-w-[220px] flex-1"><div className="relative"><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-3" />
          <Input className="pl-9" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Words in the review" aria-label="Search reviews" /></div></Field>
        <Field label="Sentiment" className="w-40"><Select value={sentiment} onChange={(e) => { setSentiment(e.target.value); setPage(0) }}><option value="">Any</option><option value="POSITIVE">Positive</option><option value="NEGATIVE">Negative</option><option value="NEUTRAL">Neutral</option></Select></Field>
        {selected.length > 0 && <Button variant="danger" icon={Trash2} onClick={() => setDel('bulk')}>Delete {selected.length} selected</Button>}
      </FilterCard>
      <Card>
        {error ? <div className="p-5"><ErrorState message={error} onRetry={reload} /></div> : !data ? <LoadingSkeleton variant="rows" /> : (
          <>
            <ReviewTable rows={rows} onOpen={setOpen} extraColumn={(r) => (
              <input type="checkbox" aria-label="Select review" checked={selected.includes(r.id)} onChange={() => toggle(r.id)} className="size-4 accent-[var(--accent)]" />
            )} />
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onChange={setPage} />
          </>
        )}
      </Card>
      <ReviewDrawer review={open} onClose={() => setOpen(null)}
        actions={open && <Button size="sm" variant="ghost" icon={Trash2} className="text-danger" onClick={() => setDel(open)}>Delete</Button>} />
      <ConfirmationDialog open={Boolean(del)} onClose={() => setDel(null)} onConfirm={remove} loading={busy} reasonField
        title={del === 'bulk' ? `Delete ${selected.length} reviews?` : 'Delete review?'} confirmLabel="Delete"
        description="The review(s) and their aspect results are removed; product statistics and keyword caches are refreshed." />
    </>
  )
}
