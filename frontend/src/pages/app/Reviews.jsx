import { useCallback, useEffect, useState } from 'react'
import { Search, X } from 'lucide-react'
import api, { errorMessage } from '../../lib/api'
import { useDebounced, useScope } from '../../lib/scope'
import { fmtNum } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { NoDatasets, ReviewDrawer, ReviewTable, ScopeBar } from '../../components/data'
import { Button, Card, ErrorState, Field, Input, LoadingSkeleton, Pagination, Select } from '../../components/ui'

const ASPECTS = ['Camera', 'Battery', 'Display', 'Price', 'Performance', 'Build Quality', 'Sound', 'Software', 'Storage', 'Connectivity', 'Heating', 'Delivery & Packaging', 'Customer Service']

export default function Reviews() {
  const scope = useScope()
  const p = scope.params
  const [q, setQ] = useState(p.get('q') || '')
  const debounced = useDebounced(q, 400)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [open, setOpen] = useState(null)
  const filters = { aspect: p.get('aspect') || '', sentiment: p.get('sentiment') || '', rating: p.get('rating') || '', sort: p.get('sort') || 'newest', page: Number(p.get('page') || 0) }

  useEffect(() => { if (debounced !== (p.get('q') || '')) scope.update({ q: debounced, page: '' }) }, [debounced]) // eslint-disable-line react-hooks/exhaustive-deps
  const key = `${scope.datasetId}|${scope.productId}|${p.get('q') || ''}|${filters.aspect}|${filters.sentiment}|${filters.rating}|${filters.sort}|${filters.page}`
  const load = useCallback(() => {
    if (!scope.datasetId) return
    setError('')
    api.get(`/datasets/${scope.datasetId}/reviews`, { params: {
      productId: scope.productId || undefined, aspect: filters.aspect || undefined, sentiment: filters.sentiment || undefined,
      rating: filters.rating || undefined, q: p.get('q') || undefined, sort: filters.sort, page: filters.page, size: 20,
    } }).then((r) => setData(r.data)).catch((e) => setError(errorMessage(e)))
  }, [key]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { load() }, [load])

  if (scope.error) return <ErrorState message={scope.error} onRetry={scope.reload} />
  if (scope.datasets === null) return <LoadingSkeleton />
  if (!scope.datasets.length) return <><PageHeader eyebrow="Analysis" title="Review explorer" /><NoDatasets /></>
  const set = (patch) => scope.update({ ...patch, page: '' })
  const anyFilter = filters.aspect || filters.sentiment || filters.rating || p.get('q') || scope.productId

  return (
    <>
      <PageHeader eyebrow="Analysis" title="Review explorer" description="Search every review and see exactly which aspects were extracted and how each one was scored." />
      <ScopeBar scope={scope} showDates={false} />
      <Card className="mb-5 flex flex-wrap items-end gap-3 p-4">
        <Field label="Search" className="min-w-[220px] flex-1">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-3" />
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Words in the review, e.g. charger" className="pl-9" aria-label="Search reviews" />
          </div>
        </Field>
        <Field label="Aspect" className="w-44">
          <Select value={filters.aspect} onChange={(e) => set({ aspect: e.target.value })}><option value="">Any aspect</option>{ASPECTS.map((a) => <option key={a}>{a}</option>)}</Select>
        </Field>
        <Field label={filters.aspect ? 'Aspect sentiment' : 'Review sentiment'} className="w-40">
          <Select value={filters.sentiment} onChange={(e) => set({ sentiment: e.target.value })}>
            <option value="">Any</option><option value="POSITIVE">Positive</option><option value="NEGATIVE">Negative</option><option value="NEUTRAL">Neutral</option>
          </Select>
        </Field>
        <Field label="Rating" className="w-32">
          <Select value={filters.rating} onChange={(e) => set({ rating: e.target.value })}><option value="">Any</option>{[5, 4, 3, 2, 1].map((r) => <option key={r} value={r}>{r} stars</option>)}</Select>
        </Field>
        <Field label="Sort" className="w-40">
          <Select value={filters.sort} onChange={(e) => set({ sort: e.target.value })}>
            <option value="newest">Newest first</option><option value="oldest">Oldest first</option><option value="rating_high">Highest rated</option><option value="rating_low">Lowest rated</option>
          </Select>
        </Field>
        {anyFilter && <Button variant="ghost" icon={X} onClick={() => { setQ(''); scope.update({ aspect: '', sentiment: '', rating: '', q: '', product: '', page: '' }) }}>Clear</Button>}
      </Card>
      <Card>
        <div className="flex items-center justify-between px-5 py-4">
          <h3 className="text-[15px] font-semibold tracking-tight">{data ? `${fmtNum(data.totalElements)} ${filters.aspect ? `${filters.aspect.toLowerCase()} opinions` : 'reviews'}` : 'Reviews'}</h3>
        </div>
        {error ? <div className="px-5 pb-5"><ErrorState message={error} onRetry={load} /></div> : !data ? <LoadingSkeleton variant="rows" /> : (
          <>
            <ReviewTable rows={data.content} onOpen={setOpen} />
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
              onChange={(page) => { scope.update({ page: String(page) }); window.scrollTo({ top: 0, behavior: 'smooth' }) }} />
          </>
        )}
      </Card>
      <ReviewDrawer review={open} onClose={() => setOpen(null)} />
    </>
  )
}
