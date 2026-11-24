import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { CircleCheck, CircleX, Info, X } from 'lucide-react'
import api, { session } from './api'

/* ------------------------------------------------------------------ theme */
const ThemeCtx = createContext(null)

export function ThemeProvider({ children }) {
  const [mode, setMode] = useState(() => { try { return localStorage.getItem('rl_theme') || 'system' } catch { return 'system' } })
  const [systemDark, setSystemDark] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches)
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const on = (e) => setSystemDark(e.matches)
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [])
  const resolved = mode === 'system' ? (systemDark ? 'dark' : 'light') : mode
  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolved === 'dark')
    try { localStorage.setItem('rl_theme', mode) } catch { /* ignore */ }
  }, [mode, resolved])
  const value = useMemo(() => ({ mode, resolved, setMode }), [mode, resolved])
  return <ThemeCtx.Provider value={value}>{children}</ThemeCtx.Provider>
}
export const useTheme = () => useContext(ThemeCtx)

/* ------------------------------------------------------------------ auth */
const AuthCtx = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(false)
  useEffect(() => {
    if (!session.get()) { setReady(true); return }
    api.get('/auth/me').then((r) => setUser(r.data)).catch(() => session.clear()).finally(() => setReady(true))
  }, [])
  const finish = ({ token, user: u }) => { session.set(token); setUser(u); return u }
  const value = useMemo(() => ({
    user,
    ready,
    login: async (email, password) => finish((await api.post('/auth/login', { email, password })).data),
    adminLogin: async (email, password) => finish((await api.post('/auth/admin/login', { email, password })).data),
    register: async (name, email, password) => finish((await api.post('/auth/register', { name, email, password })).data),
    logout: async () => {
      try { await api.post('/auth/logout') } catch { /* already invalid */ }
      session.clear()
      setUser(null)
    },
  }), [user, ready])
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}
export const useAuth = () => useContext(AuthCtx)

/* ------------------------------------------------------------------ public platform status (maintenance, flags, limits) */
const StatusCtx = createContext(null)

export function StatusProvider({ children }) {
  const [status, setStatus] = useState({ maintenanceMode: false, features: {}, limits: null })
  const refresh = useCallback(() => api.get('/public/status').then((r) => setStatus(r.data)).catch(() => {}), [])
  useEffect(() => {
    refresh()
    const t = setInterval(refresh, 60_000)
    const onMaintenance = (e) => setStatus((s) => ({ ...s, maintenanceMode: true, maintenanceMessage: e.detail }))
    window.addEventListener('rl:maintenance', onMaintenance)
    return () => { clearInterval(t); window.removeEventListener('rl:maintenance', onMaintenance) }
  }, [refresh])
  const value = useMemo(() => ({
    ...status,
    feature: (k) => status.features?.[k] !== false,
    refresh,
  }), [status, refresh])
  return <StatusCtx.Provider value={value}>{children}</StatusCtx.Provider>
}
export const useStatus = () => useContext(StatusCtx)

/* ------------------------------------------------------------------ toast notifications */
const ToastCtx = createContext(null)
const ICONS = { success: CircleCheck, error: CircleX, info: Info }

export function ToastNotifications({ children }) {
  const [items, setItems] = useState([])
  const id = useRef(0)
  const dismiss = useCallback((k) => setItems((s) => s.filter((t) => t.id !== k)), [])
  const push = useCallback((type, message) => {
    const k = ++id.current
    setItems((s) => [...s.slice(-3), { id: k, type, message }])
    setTimeout(() => dismiss(k), type === 'error' ? 6000 : 3800)
  }, [dismiss])
  const api_ = useMemo(() => ({
    success: (m) => push('success', m), error: (m) => push('error', m), info: (m) => push('info', m),
  }), [push])
  return (
    <ToastCtx.Provider value={api_}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-[80] flex w-[min(92vw,380px)] flex-col gap-2">
        <AnimatePresence initial={false}>
          {items.map((t) => {
            const Icon = ICONS[t.type]
            return (
              <motion.div key={t.id} layout initial={{ opacity: 0, y: 16, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, x: 24, transition: { duration: 0.15 } }}
                className="pointer-events-auto flex items-start gap-3 rounded-xl border border-line bg-surface/95 p-3.5 pr-2.5 shadow-pop backdrop-blur">
                <Icon className={`mt-0.5 size-[18px] shrink-0 ${t.type === 'success' ? 'text-success' : t.type === 'error' ? 'text-danger' : 'text-accent'}`} />
                <p className="flex-1 text-sm leading-snug text-ink">{t.message}</p>
                <button onClick={() => dismiss(t.id)} className="rounded-md p-1 text-ink-3 hover:bg-muted hover:text-ink" aria-label="Dismiss">
                  <X className="size-3.5" />
                </button>
              </motion.div>
            )
          })}
        </AnimatePresence>
      </div>
    </ToastCtx.Provider>
  )
}
export const useToast = () => useContext(ToastCtx)
