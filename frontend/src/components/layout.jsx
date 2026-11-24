import { useEffect, useMemo, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { ArrowRight, LogOut, Menu, Monitor, Moon, Search, Shield, Sun, TriangleAlert, X } from 'lucide-react'
import { useAuth, useStatus, useTheme } from '../lib/providers'
import { Kbd, cx } from './ui'

export function Logo({ size = 28, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" className={cx('shrink-0', className)} aria-hidden="true">
      <rect width="32" height="32" rx="8.5" className="fill-ink" />
      <circle cx="14" cy="14" r="6.4" fill="none" className="stroke-canvas" strokeWidth="2.5" />
      <path d="M18.9 18.9 24.6 24.6" className="stroke-accent" strokeWidth="2.6" strokeLinecap="round" />
      <path d="M10.9 15.2 13 12.9l1.8 1.4 2.3-2.9" fill="none" className="stroke-canvas" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function Wordmark({ admin, to = '/' }) {
  return (
    <Link to={to} className="flex items-center gap-2.5 rounded-lg">
      <Logo />
      <span className="text-[15px] font-semibold tracking-tight">ReviewLens</span>
      {admin && <span className="rounded-md bg-accent/15 px-1.5 py-0.5 text-[11px] font-semibold text-accent-ink">Admin</span>}
    </Link>
  )
}

export function ThemeToggle({ className }) {
  const { mode, setMode } = useTheme()
  const next = { light: 'dark', dark: 'system', system: 'light' }[mode]
  const Icon = { light: Sun, dark: Moon, system: Monitor }[mode]
  return (
    <button type="button" onClick={() => setMode(next)} aria-label={`Theme: ${mode}. Switch to ${next}.`} title={`Theme: ${mode}`}
      className={cx('relative inline-flex size-8 items-center justify-center overflow-hidden rounded-lg text-ink-2 transition hover:bg-muted hover:text-ink', className)}>
      <AnimatePresence mode="wait" initial={false}>
        <motion.span key={mode} initial={{ rotate: -80, opacity: 0, scale: 0.6 }} animate={{ rotate: 0, opacity: 1, scale: 1 }}
          exit={{ rotate: 80, opacity: 0, scale: 0.6 }} transition={{ duration: 0.18 }}>
          <Icon className="size-[17px]" />
        </motion.span>
      </AnimatePresence>
    </button>
  )
}

export function PageHeader({ eyebrow, title, description, actions }) {
  return (
    <div className="mb-7 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {eyebrow && <div className="eyebrow mb-2">{eyebrow}</div>}
        <h1 className="text-[28px] font-semibold leading-tight tracking-[-0.025em] text-ink sm:text-[32px]">{title}</h1>
        {description && <p className="mt-1.5 max-w-2xl text-[15px] leading-relaxed text-ink-2">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

export function MaintenanceBanner() {
  const { maintenanceMode, maintenanceMessage } = useStatus()
  const { user } = useAuth()
  if (!maintenanceMode) return null
  return (
    <div className="flex items-center gap-2.5 border-b border-warning/30 bg-warning-soft px-5 py-2.5 text-[13px] text-ink">
      <TriangleAlert className="size-4 shrink-0 text-warning" />
      <span><b className="font-semibold">Maintenance mode.</b> {maintenanceMessage || 'Some features are temporarily unavailable.'}
        {user?.role === 'ADMIN' && ' Administrators keep full access.'}</span>
    </div>
  )
}

function NavItems({ items, variant, onNavigate, groupId }) {
  return (
    <nav className="flex flex-col gap-0.5" aria-label="Main">
      {items.map((it) => (
        <NavLink key={it.to} to={it.to} onClick={onNavigate} end={it.end}
          className={({ isActive }) => cx('group relative flex items-center gap-3 rounded-lg px-2.5 py-[7px] text-[13.5px] font-medium transition-colors',
            variant === 'admin'
              ? (isActive ? 'text-white' : 'text-white/55 hover:bg-white/[0.06] hover:text-white')
              : (isActive ? 'text-ink' : 'text-ink-2 hover:bg-muted hover:text-ink'))}>
          {({ isActive }) => (
            <>
              {isActive && <motion.span layoutId={`nav-${groupId}`} transition={{ type: 'spring', bounce: 0.15, duration: 0.4 }}
                className={cx('absolute inset-0 rounded-lg', variant === 'admin' ? 'bg-white/[0.1]' : 'bg-muted ring-1 ring-line')} />}
              <it.icon className={cx('relative size-[17px] shrink-0', isActive ? (variant === 'admin' ? 'text-accent-ink' : 'text-accent') : '')} />
              <span className="relative truncate">{it.label}</span>
              {it.badge != null && <span className="relative ml-auto rounded-full bg-accent/15 px-1.5 text-[11px] text-accent-ink">{it.badge}</span>}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}

/** Generic application frame shared by the user workspace (AppShell) and the admin portal (AdminLayout). */
export function Shell({ nav, variant = 'app', brandTo, sectionLabel, sidebarFooter, topbarExtras }) {
  const [open, setOpen] = useState(false)
  const [cmd, setCmd] = useState(false)
  const location = useLocation()
  useEffect(() => setOpen(false), [location.pathname])
  useEffect(() => {
    const onKey = (e) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setCmd((v) => !v) } }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])
  const admin = variant === 'admin'
  const sidebar = (
    <div className={cx('flex h-full flex-col px-3 py-4', admin ? 'bg-[#0d0d10] text-white' : 'bg-surface/70 backdrop-blur-xl')}>
      <div className={cx('mb-6 flex items-center justify-between px-1.5', admin && '[&_.fill-ink]:fill-white [&_.stroke-canvas]:stroke-[#0d0d10]')}>
        <Wordmark admin={admin} to={brandTo} />
      </div>
      <div className={cx('mb-2 px-2.5 text-[11px] font-semibold uppercase tracking-[0.08em]', admin ? 'text-white/35' : 'text-ink-3')}>{sectionLabel}</div>
      <NavItems items={nav} variant={variant} groupId={variant} />
      <div className="mt-auto pt-6">{sidebarFooter}</div>
    </div>
  )
  return (
    <div data-portal={admin ? 'admin' : undefined} className="min-h-screen bg-canvas">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[90] focus:rounded-lg focus:bg-surface focus:px-3 focus:py-2 focus:shadow-pop">Skip to content</a>
      <aside className={cx('fixed inset-y-0 left-0 z-30 hidden w-[248px] border-r lg:block', admin ? 'border-white/[0.06]' : 'border-line')}>{sidebar}</aside>
      <AnimatePresence>
        {open && (
          <div className="fixed inset-0 z-50 lg:hidden">
            <motion.div className="absolute inset-0 bg-black/40" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setOpen(false)} />
            <motion.aside initial={{ x: -280 }} animate={{ x: 0 }} exit={{ x: -280 }} transition={{ type: 'spring', bounce: 0, duration: 0.32 }}
              className={cx('absolute inset-y-0 left-0 w-[264px] border-r shadow-pop', admin ? 'border-white/10' : 'border-line bg-surface')}>{sidebar}</motion.aside>
          </div>
        )}
      </AnimatePresence>
      <div className="lg:pl-[248px]">
        <TopNavigation onMenu={() => setOpen(true)} onCommand={() => setCmd(true)} extras={topbarExtras} admin={admin} />
        <MaintenanceBanner />
        <main id="main" className="mx-auto w-full max-w-[1320px] px-4 pb-16 pt-7 sm:px-6 lg:px-10">
          <AnimatePresence mode="wait">
            <motion.div key={location.pathname} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -4 }}
              transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}>
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
      <CommandMenu open={cmd} onClose={() => setCmd(false)} nav={nav} />
    </div>
  )
}

export function TopNavigation({ onMenu, onCommand, extras, admin }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [menu, setMenu] = useState(false)
  const initials = (user?.name || '?').split(/\s+/).map((s) => s[0]).slice(0, 2).join('').toUpperCase()
  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-2 border-b border-line bg-canvas/80 px-4 backdrop-blur-xl sm:px-6 lg:px-10">
      <button type="button" onClick={onMenu} className="-ml-1 rounded-lg p-1.5 text-ink-2 hover:bg-muted lg:hidden" aria-label="Open navigation"><Menu className="size-5" /></button>
      <button type="button" onClick={onCommand}
        className="flex h-8 w-full max-w-xs items-center gap-2 rounded-lg border border-line bg-surface px-2.5 text-[13px] text-ink-3 transition hover:border-line-strong">
        <Search className="size-4" /><span className="flex-1 text-left">Search or jump to…</span><Kbd>Ctrl K</Kbd>
      </button>
      <div className="ml-auto flex items-center gap-1">
        {extras}
        <ThemeToggle />
        <div className="relative">
          <button type="button" onClick={() => setMenu((m) => !m)} aria-expanded={menu} aria-haspopup="menu"
            className="ml-1 grid size-8 place-items-center rounded-full bg-accent/15 text-[12px] font-semibold text-accent-ink ring-1 ring-accent/20 transition hover:ring-accent/40">{initials}</button>
          <AnimatePresence>
            {menu && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setMenu(false)} />
                <motion.div role="menu" initial={{ opacity: 0, y: -6, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -4 }}
                  transition={{ duration: 0.14 }} className="absolute right-0 z-20 mt-2 w-60 rounded-xl border border-line bg-surface p-1.5 shadow-pop">
                  <div className="px-2.5 py-2">
                    <div className="truncate text-sm font-medium">{user?.name}</div>
                    <div className="truncate text-xs text-ink-3">{user?.email}</div>
                  </div>
                  <div className="my-1 h-px bg-line" />
                  {user?.role === 'ADMIN' && (
                    <button role="menuitem" onClick={() => { setMenu(false); navigate(admin ? '/app/dashboard' : '/admin/dashboard') }}
                      className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] text-ink-2 hover:bg-muted hover:text-ink">
                      {admin ? <ArrowRight className="size-4" /> : <Shield className="size-4" />}{admin ? 'Open user workspace' : 'Open admin portal'}
                    </button>
                  )}
                  <button role="menuitem" onClick={async () => { setMenu(false); await logout(); navigate(admin ? '/admin/login' : '/login') }}
                    className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] text-ink-2 hover:bg-muted hover:text-ink">
                    <LogOut className="size-4" />Sign out
                  </button>
                </motion.div>
              </>
            )}
          </AnimatePresence>
        </div>
      </div>
    </header>
  )
}

function CommandMenu({ open, onClose, nav }) {
  const navigate = useNavigate()
  const { setMode, resolved } = useTheme()
  const { logout } = useAuth()
  const [q, setQ] = useState('')
  const [active, setActive] = useState(0)
  const items = useMemo(() => [
    ...nav.map((n) => ({ label: n.label, icon: n.icon, hint: 'Go to', run: () => navigate(n.to) })),
    { label: resolved === 'dark' ? 'Switch to light theme' : 'Switch to dark theme', icon: resolved === 'dark' ? Sun : Moon, hint: 'Action', run: () => setMode(resolved === 'dark' ? 'light' : 'dark') },
    { label: 'Sign out', icon: LogOut, hint: 'Action', run: async () => { await logout(); navigate('/login') } },
  ].filter((i) => i.label.toLowerCase().includes(q.toLowerCase())), [nav, q, resolved, navigate, setMode, logout])
  useEffect(() => { if (open) { setQ(''); setActive(0) } }, [open])
  const run = (i) => { onClose(); i?.run() }
  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[75] flex items-start justify-center px-4 pt-[14vh]">
          <motion.div className="absolute inset-0 bg-black/40" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.div role="dialog" aria-label="Command menu" initial={{ opacity: 0, scale: 0.97, y: -8 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.98 }}
            transition={{ duration: 0.16 }} className="relative w-full max-w-lg overflow-hidden rounded-2xl border border-line bg-surface shadow-pop">
            <div className="flex items-center gap-2.5 border-b border-line px-4">
              <Search className="size-4 text-ink-3" />
              <input autoFocus value={q} onChange={(e) => { setQ(e.target.value); setActive(0) }} placeholder="Type a command or page…"
                onKeyDown={(e) => {
                  if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, items.length - 1)) }
                  if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)) }
                  if (e.key === 'Enter') run(items[active])
                  if (e.key === 'Escape') onClose()
                }}
                className="h-12 flex-1 bg-transparent text-sm outline-none placeholder:text-ink-3" />
              <button onClick={onClose} aria-label="Close" className="rounded-md p-1 text-ink-3 hover:text-ink"><X className="size-4" /></button>
            </div>
            <ul className="max-h-80 overflow-y-auto p-1.5">
              {items.length === 0 && <li className="px-3 py-6 text-center text-sm text-ink-3">No results</li>}
              {items.map((i, idx) => (
                <li key={i.label}>
                  <button onMouseEnter={() => setActive(idx)} onClick={() => run(i)}
                    className={cx('flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm', idx === active ? 'bg-muted text-ink' : 'text-ink-2')}>
                    <i.icon className="size-4" /><span className="flex-1">{i.label}</span><span className="text-xs text-ink-3">{i.hint}</span>
                  </button>
                </li>
              ))}
            </ul>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  )
}
