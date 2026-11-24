import { Database, FileText, GitCompareArrows, LayoutDashboard, MessageSquareText, ScanSearch, Sparkles } from 'lucide-react'
import { Shell } from '../../components/layout'
import { useStatus } from '../../lib/providers'

const NAV = [
  { to: '/app/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/app/datasets', label: 'Datasets', icon: Database },
  { to: '/app/aspects', label: 'Aspect analysis', icon: ScanSearch },
  { to: '/app/compare', label: 'Compare products', icon: GitCompareArrows, feature: 'productComparison' },
  { to: '/app/reviews', label: 'Review explorer', icon: MessageSquareText },
  { to: '/app/analyzer', label: 'Live analyzer', icon: Sparkles, feature: 'liveAnalyzer' },
  { to: '/app/reports', label: 'Reports', icon: FileText, feature: 'reportGeneration' },
]

export default function AppShell() {
  const { feature } = useStatus()
  return (
    <Shell nav={NAV.filter((n) => !n.feature || feature(n.feature))} brandTo="/app/dashboard" sectionLabel="Workspace"
      sidebarFooter={
        <div className="rounded-xl border border-line bg-subtle/70 p-3.5">
          <div className="text-[12px] font-semibold text-ink">NLP engine</div>
          <p className="mt-1 text-[12px] leading-snug text-ink-3">Rule-based baseline (VADER + electronics domain rules). Results are labelled with the engine version.</p>
        </div>
      } />
  )
}
