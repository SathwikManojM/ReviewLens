import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './lib/providers'
import { Logo } from './components/layout'
import { LoadingSkeleton } from './components/ui'

const Landing = lazy(() => import('./pages/Landing'))
const Login = lazy(() => import('./pages/auth/AuthPages').then((m) => ({ default: m.Login })))
const Register = lazy(() => import('./pages/auth/AuthPages').then((m) => ({ default: m.Register })))
const AdminLogin = lazy(() => import('./pages/auth/AuthPages').then((m) => ({ default: m.AdminLogin })))
const AppShell = lazy(() => import('./pages/app/AppShell'))
const Dashboard = lazy(() => import('./pages/app/Dashboard'))
const Datasets = lazy(() => import('./pages/app/Datasets'))
const Aspects = lazy(() => import('./pages/app/Aspects'))
const Compare = lazy(() => import('./pages/app/Compare'))
const Reviews = lazy(() => import('./pages/app/Reviews'))
const Analyzer = lazy(() => import('./pages/app/Analyzer'))
const Reports = lazy(() => import('./pages/app/Reports'))
const AdminLayout = lazy(() => import('./pages/admin/AdminLayout'))
const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'))
const AdminUsers = lazy(() => import('./pages/admin/AdminUsers'))
const AdminDatasets = lazy(() => import('./pages/admin/AdminDatasets'))
const AdminProducts = lazy(() => import('./pages/admin/AdminProducts'))
const AdminReviews = lazy(() => import('./pages/admin/AdminReviews'))
const AdminJobs = lazy(() => import('./pages/admin/AdminJobs'))
const AdminAnalytics = lazy(() => import('./pages/admin/AdminAnalytics'))
const AdminAuditLogs = lazy(() => import('./pages/admin/AdminAuditLogs'))
const AdminSettings = lazy(() => import('./pages/admin/AdminSettings'))

function Splash() {
  return <div className="grid min-h-screen place-items-center bg-canvas"><Logo size={36} className="animate-pulse" /></div>
}

/** Route guards are a UX convenience only - the backend enforces every permission. */
function RequireAuth({ children }) {
  const { user, ready } = useAuth()
  const loc = useLocation()
  if (!ready) return <Splash />
  return user ? children : <Navigate to={`/login?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace />
}

function RequireAdmin({ children }) {
  const { user, ready } = useAuth()
  if (!ready) return <Splash />
  if (!user) return <Navigate to="/admin/login" replace />
  if (user.role !== 'ADMIN') return <Navigate to="/admin/login?reason=forbidden" replace />
  return children
}

const page = (el) => <Suspense fallback={<LoadingSkeleton />}>{el}</Suspense>

export default function App() {
  return (
    <Suspense fallback={<Splash />}>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/admin/login" element={<AdminLogin />} />

        <Route path="/app" element={<RequireAuth><AppShell /></RequireAuth>}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={page(<Dashboard />)} />
          <Route path="datasets" element={page(<Datasets />)} />
          <Route path="aspects" element={page(<Aspects />)} />
          <Route path="compare" element={page(<Compare />)} />
          <Route path="reviews" element={page(<Reviews />)} />
          <Route path="analyzer" element={page(<Analyzer />)} />
          <Route path="reports" element={page(<Reports />)} />
        </Route>

        <Route path="/admin" element={<RequireAdmin><AdminLayout /></RequireAdmin>}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={page(<AdminDashboard />)} />
          <Route path="users" element={page(<AdminUsers />)} />
          <Route path="datasets" element={page(<AdminDatasets />)} />
          <Route path="products" element={page(<AdminProducts />)} />
          <Route path="reviews" element={page(<AdminReviews />)} />
          <Route path="analysis-jobs" element={page(<AdminJobs />)} />
          <Route path="analytics" element={page(<AdminAnalytics />)} />
          <Route path="audit-logs" element={page(<AdminAuditLogs />)} />
          <Route path="settings" element={page(<AdminSettings />)} />
        </Route>

        {['dashboard', 'datasets', 'compare', 'reviews', 'analyzer', 'reports'].map((p) => (
          <Route key={p} path={`/${p}`} element={<Navigate to={`/app/${p}`} replace />} />
        ))}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
