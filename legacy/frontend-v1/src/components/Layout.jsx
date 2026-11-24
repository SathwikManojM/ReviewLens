import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth'

const I = {
  datasets: <path d="M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3Zm0 0v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3" />,
  dashboard: <path d="M4 4h7v9H4zM13 4h7v5h-7zM13 11h7v9h-7zM4 15h7v5H4z" />,
  compare: <path d="M6 20V10M12 20V4M18 20v-7" />,
  reviews: <path d="M4 5h16v11H8l-4 4V5Zm4 4h8M8 12h5" />,
  analyzer: <path d="M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm9 16-4-4M8 11h6M11 8v6" />,
  reports: <path d="M7 3h7l5 5v13H7zM14 3v5h5M10 13h6M10 17h6" />,
}

function Icon({ name }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {I[name]}
    </svg>
  )
}

export function Logo({ size = 26 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="var(--accent)" />
      <circle cx="14" cy="14" r="6.5" fill="none" stroke="#fff" strokeWidth="2.6" />
      <path d="M19 19l5.5 5.5" stroke="#fff" strokeWidth="2.6" strokeLinecap="round" />
      <path d="M11 15.5l2-2.5 2 1.5 2-3" fill="none" stroke="#fff" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

const links = [
  ['/datasets', 'Datasets', 'datasets'],
  ['/dashboard', 'Dashboard', 'dashboard'],
  ['/compare', 'Compare products', 'compare'],
  ['/reviews', 'Review explorer', 'reviews'],
  ['/analyzer', 'Live analyzer', 'analyzer'],
  ['/reports', 'Reports', 'reports'],
]

export default function Layout() {
  const { user, logout } = useAuth()
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <Logo />
          <div>ReviewLens<small>Voice of customer analytics</small></div>
        </div>
        <nav className="nav">
          {links.map(([to, label, icon]) => (
            <NavLink key={to} to={to}><Icon name={icon} />{label}</NavLink>
          ))}
        </nav>
        <div className="user">
          <div style={{ fontWeight: 600, color: 'var(--text)' }}>{user?.name}</div>
          <div style={{ marginBottom: 8, overflow: 'hidden', textOverflow: 'ellipsis' }}>{user?.email}</div>
          <button className="btn sm" onClick={logout}>Sign out</button>
        </div>
      </aside>
      <main className="main"><Outlet /></main>
    </div>
  )
}
