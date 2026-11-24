import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import Layout from './components/Layout'
import AuthPage from './pages/AuthPage'
import DatasetsPage from './pages/DatasetsPage'
import DashboardPage from './pages/DashboardPage'
import ComparePage from './pages/ComparePage'
import ReviewsPage from './pages/ReviewsPage'
import AnalyzerPage from './pages/AnalyzerPage'
import ReportsPage from './pages/ReportsPage'

function Protected({ children }) {
  const { user } = useAuth()
  return user ? children : <Navigate to="/login" replace />
}

export default function App() {
  const { user } = useAuth()
  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to="/" /> : <AuthPage mode="login" />} />
      <Route path="/register" element={user ? <Navigate to="/" /> : <AuthPage mode="register" />} />
      <Route element={<Protected><Layout /></Protected>}>
        <Route path="/" element={<Navigate to="/datasets" replace />} />
        <Route path="/datasets" element={<DatasetsPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/compare" element={<ComparePage />} />
        <Route path="/reviews" element={<ReviewsPage />} />
        <Route path="/analyzer" element={<AnalyzerPage />} />
        <Route path="/reports" element={<ReportsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  )
}
