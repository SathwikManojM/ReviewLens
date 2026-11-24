import { useState } from 'react'
import { Download, FileSpreadsheet } from 'lucide-react'
import { download, errorMessage } from '../../lib/api'
import { useToast } from '../../lib/providers'
import { fmtNum } from '../../lib/format'
import { PageHeader } from '../../components/layout'
import { Heatmap, Legend, SeriesChart, usePalette } from '../../components/charts'
import { Button, Card, CardHeader, ErrorState, LoadingSkeleton, Segmented, TD, TH, cx } from '../../components/ui'
import { AdminMetricCard, usePaged } from './shared'

export default function AdminAnalytics() {
  const c = usePalette()
  const toast = useToast()
  const [range, setRange] = useState('30d')
  const [busy, setBusy] = useState('')
  const { data, error, reload } = usePaged('/admin/analytics', { range })
  const exportAs = async (format) => {
    setBusy(format)
    try { await download('/admin/reports/export', { range, format }, `platform-report.${format}`); toast.success('Report exported (recorded in the audit log)') } catch (e) { toast.error(errorMessage(e)) } finally { setBusy('') }
  }
  const t = data?.totals
  return (
    <>
      <PageHeader eyebrow="Administration" title="Platform analytics" description="Growth, uploads, processing volume and reliability across the whole platform (UTC)."
        actions={<>
          <Segmented value={range} onChange={setRange} options={[{ value: '30d', label: '30 days' }, { value: '12w', label: '12 weeks' }, { value: '12m', label: '12 months' }]} />
          <Button icon={FileSpreadsheet} loading={busy === 'csv'} onClick={() => exportAs('csv')}>CSV</Button>
          <Button variant="primary" icon={Download} loading={busy === 'pdf'} onClick={() => exportAs('pdf')}>PDF report</Button>
        </>} />
      {error ? <ErrorState message={error} onRetry={reload} /> : !data ? <LoadingSkeleton /> : (
        <div className="space-y-5">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
            <AdminMetricCard label="New users" value={fmtNum(t.newUsers)} sub={`${fmtNum(t.totalUsers)} registered`} />
            <AdminMetricCard label="Dataset uploads" value={fmtNum(t.uploads)} delay={0.03} />
            <AdminMetricCard label="Reviews processed" value={fmtNum(t.reviewsProcessed)} delay={0.06} />
            <AdminMetricCard label="Jobs completed" value={fmtNum(t.jobsCompleted)} sub={`${fmtNum(t.jobsFailed)} failed`} delay={0.09} />
            <AdminMetricCard label="Success rate" value={data.successRate == null ? '–' : `${data.successRate}%`} tone={data.successRate != null && data.successRate < 90 ? 'text-warning' : undefined} delay={0.12} />
          </div>
          <div className="grid gap-5 lg:grid-cols-2">
            <Card><CardHeader title="User growth" description="Registered accounts at the end of each period" />
              <div className="px-3 pb-4 pt-3"><SeriesChart data={data.userGrowth} series={[{ key: 'totalUsers', label: 'Registered users', color: c.indigo }]} /></div></Card>
            <Card><CardHeader title="Dataset uploads" description="Uploads per period" />
              <div className="px-3 pb-4 pt-3"><SeriesChart type="bar" data={data.uploads.map((u) => ({ period: u.period, uploads: u.count }))} series={[{ key: 'uploads', label: 'Uploads', color: c.series[2] }]} /></div></Card>
            <Card><CardHeader title="Review processing volume" description="Reviews analysed by completed jobs" />
              <div className="px-3 pb-4 pt-3"><SeriesChart data={data.reviewVolume.map((u) => ({ period: u.period, reviews: u.count }))} series={[{ key: 'reviews', label: 'Reviews processed', color: c.accent }]} /></div></Card>
            <Card><CardHeader title="Analysis outcomes" description="Completed vs failed jobs" action={<Legend items={[{ label: 'Completed', color: c.pos }, { label: 'Failed', color: c.neg }]} />} />
              <div className="px-3 pb-4 pt-3"><SeriesChart type="bar" stacked data={data.jobOutcomes} series={[{ key: 'completed', label: 'Completed', color: c.pos }, { key: 'failed', label: 'Failed', color: c.neg }]} /></div></Card>
          </div>
          <div className="grid gap-5 lg:grid-cols-5">
            <Card className="lg:col-span-2"><CardHeader title="Most analysed categories" description="By number of reviews" />
              {data.topCategories.length === 0 ? <p className="p-5 text-sm text-ink-3">No products yet.</p> : (
                <table className="mt-3 w-full text-sm"><thead><tr><th className={TH}>Category</th><th className={cx(TH, 'text-right')}>Products</th><th className={cx(TH, 'text-right')}>Reviews</th></tr></thead>
                  <tbody>{data.topCategories.map((k) => <tr key={k.category}><td className={cx(TD, 'max-w-[200px] truncate')}>{k.category}</td><td className={cx(TD, 'tnum text-right')}>{fmtNum(k.products)}</td><td className={cx(TD, 'tnum text-right')}>{fmtNum(k.reviews)}</td></tr>)}</tbody></table>
              )}
            </Card>
            <Card className="lg:col-span-3"><CardHeader title="Usage patterns" description="Uploads, analysis jobs and reports by weekday and hour (UTC)" />
              <div className="p-5"><Heatmap cells={data.usageHeatmap} /></div></Card>
          </div>
        </div>
      )}
    </>
  )
}
