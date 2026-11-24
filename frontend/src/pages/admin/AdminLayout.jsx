import { Activity, ChartColumn, Database, LayoutDashboard, MessageSquareText, Package, ScrollText, SlidersHorizontal, Users } from 'lucide-react'
import { Shell } from '../../components/layout'

export const ADMIN_NAV = [
  { to: '/admin/dashboard', label: 'Overview', icon: LayoutDashboard },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/datasets', label: 'Datasets', icon: Database },
  { to: '/admin/products', label: 'Products', icon: Package },
  { to: '/admin/reviews', label: 'Reviews', icon: MessageSquareText },
  { to: '/admin/analysis-jobs', label: 'Analysis jobs', icon: Activity },
  { to: '/admin/analytics', label: 'Platform analytics', icon: ChartColumn },
  { to: '/admin/audit-logs', label: 'Audit logs', icon: ScrollText },
  { to: '/admin/settings', label: 'System settings', icon: SlidersHorizontal },
]

/** AdminLayout = graphite AdminSidebar + AdminTopbar + content, with the indigo admin accent. */
export default function AdminLayout() {
  return (
    <Shell nav={ADMIN_NAV} variant="admin" brandTo="/admin/dashboard" sectionLabel="Administration"
      sidebarFooter={
        <div className="rounded-xl border border-white/10 bg-white/[0.04] p-3.5">
          <div className="text-[12px] font-semibold text-white/80">Audited workspace</div>
          <p className="mt-1 text-[12px] leading-snug text-white/45">Every administrative action is recorded in the audit log with your account and IP address.</p>
        </div>
      } />
  )
}
