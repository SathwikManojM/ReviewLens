import { useMemo, useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowLeft, Lock, ShieldCheck } from 'lucide-react'
import { errorMessage } from '../../lib/api'
import { useAuth, useStatus } from '../../lib/providers'
import { ThemeToggle, Wordmark } from '../../components/layout'
import { Alert, Button, Field, Input, PasswordInput, cx } from '../../components/ui'

const REASONS = {
  expired: 'Your session has ended. Please sign in again.',
  disabled: 'This account has been deactivated. Contact an administrator.',
  forbidden: 'An administrator account is required for the admin portal.',
}

function AuthLayout({ children, admin, footer }) {
  return (
    <div data-portal={admin ? 'admin' : undefined} className="relative flex min-h-screen flex-col bg-canvas">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(50%_40%_at_50%_0%,color-mix(in_srgb,var(--accent)_10%,transparent),transparent)]" />
      <header className="relative flex items-center justify-between px-5 py-5 sm:px-8">
        <Wordmark admin={admin} />
        <ThemeToggle />
      </header>
      <main className="relative flex flex-1 items-start justify-center px-5 pb-16 pt-6 sm:items-center sm:pt-0">
        <motion.div initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }} className="w-full max-w-[400px]">
          {children}
          {footer && <div className="mt-6 text-center text-[13px] text-ink-3">{footer}</div>}
        </motion.div>
      </main>
    </div>
  )
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function useNextPath(fallback) {
  const [params] = useSearchParams()
  const next = params.get('next')
  return next && next.startsWith('/') && !next.startsWith('//') ? next : fallback
}

export function Login() {
  const { login, user } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = useNextPath('/app/dashboard')
  const [form, setForm] = useState({ email: '', password: '' })
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  if (user) return <Navigate to={next} replace />
  const invalid = { email: !EMAIL.test(form.email), password: !form.password }
  const submit = async (e) => {
    e.preventDefault()
    setTouched(true)
    if (invalid.email || invalid.password) return
    setBusy(true)
    setError('')
    try { await login(form.email, form.password); navigate(next, { replace: true }) } catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }
  return (
    <AuthLayout footer={<>New to ReviewLens? <Link to="/register" className="font-medium text-accent-ink hover:underline">Create an account</Link></>}>
      <h1 className="text-[28px] font-semibold tracking-[-0.03em]">Sign in</h1>
      <p className="mt-1.5 text-[15px] text-ink-2">Welcome back to your review analytics workspace.</p>
      <form onSubmit={submit} noValidate className="card mt-7 space-y-4 p-6">
        {(error || REASONS[params.get('reason')]) && <Alert tone={error ? 'danger' : 'info'}>{error || REASONS[params.get('reason')]}</Alert>}
        <Field label="Email" htmlFor="email" error={touched && invalid.email ? 'Enter a valid email address' : ''}>
          <Input id="email" type="email" autoComplete="email" autoFocus value={form.email} invalid={touched && invalid.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="you@company.com" />
        </Field>
        <Field label="Password" htmlFor="password" error={touched && invalid.password ? 'Enter your password' : ''}>
          <PasswordInput id="password" autoComplete="current-password" value={form.password} invalid={touched && invalid.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </Field>
        <Button type="submit" variant="primary" size="lg" loading={busy} className="w-full">Sign in</Button>
      </form>
      <p className="mt-4 text-center text-xs text-ink-3"><Link to="/" className="inline-flex items-center gap-1 hover:text-ink"><ArrowLeft className="size-3" />Back to home</Link></p>
    </AuthLayout>
  )
}

function strength(p) {
  let s = 0
  if (p.length >= 8) s++
  if (p.length >= 12) s++
  if (/[A-Z]/.test(p) && /[a-z]/.test(p)) s++
  if (/\d/.test(p)) s++
  if (/[^A-Za-z0-9]/.test(p)) s++
  return Math.min(s, 4)
}

export function Register() {
  const { register, user } = useAuth()
  const { feature } = useStatus()
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' })
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const score = useMemo(() => strength(form.password), [form.password])
  if (user) return <Navigate to="/app/dashboard" replace />
  const errors = {
    name: form.name.trim() ? '' : 'Enter your name',
    email: EMAIL.test(form.email) ? '' : 'Enter a valid email address',
    password: form.password.length >= 8 && /[A-Za-z]/.test(form.password) && /\d/.test(form.password) ? '' : 'At least 8 characters with a letter and a number',
    confirm: form.confirm && form.confirm === form.password ? '' : 'Passwords do not match',
  }
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const submit = async (e) => {
    e.preventDefault()
    setTouched(true)
    if (Object.values(errors).some(Boolean)) return
    setBusy(true)
    setError('')
    try { await register(form.name.trim(), form.email, form.password); navigate('/app/datasets', { replace: true }) } catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }
  const closed = !feature('registrationOpen')
  return (
    <AuthLayout footer={<>Already have an account? <Link to="/login" className="font-medium text-accent-ink hover:underline">Sign in</Link></>}>
      <h1 className="text-[28px] font-semibold tracking-[-0.03em]">Create your account</h1>
      <p className="mt-1.5 text-[15px] text-ink-2">Start analysing customer reviews in minutes.</p>
      <form onSubmit={submit} noValidate className="card mt-7 space-y-4 p-6">
        {closed && <Alert tone="warning">Registration is currently closed by an administrator.</Alert>}
        {error && <Alert>{error}</Alert>}
        <Field label="Full name" htmlFor="name" error={touched && errors.name}>
          <Input id="name" autoComplete="name" autoFocus value={form.name} onChange={set('name')} invalid={touched && !!errors.name} maxLength={100} />
        </Field>
        <Field label="Email" htmlFor="email" error={touched && errors.email}>
          <Input id="email" type="email" autoComplete="email" value={form.email} onChange={set('email')} invalid={touched && !!errors.email} />
        </Field>
        <Field label="Password" htmlFor="password" error={touched && errors.password}>
          <PasswordInput id="password" autoComplete="new-password" value={form.password} onChange={set('password')} invalid={touched && !!errors.password} />
          <div className="mt-1 flex gap-1" aria-hidden="true">
            {[0, 1, 2, 3].map((i) => <span key={i} className={cx('h-1 flex-1 rounded-full transition-colors', i < score ? (score <= 1 ? 'bg-danger' : score <= 2 ? 'bg-warning' : 'bg-success') : 'bg-muted')} />)}
          </div>
        </Field>
        <Field label="Confirm password" htmlFor="confirm" error={touched && errors.confirm}>
          <PasswordInput id="confirm" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} invalid={touched && !!errors.confirm} />
        </Field>
        <Button type="submit" variant="primary" size="lg" loading={busy} disabled={closed} className="w-full">Create account</Button>
      </form>
    </AuthLayout>
  )
}

export function AdminLogin() {
  const { adminLogin, user } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  if (user?.role === 'ADMIN') return <Navigate to="/admin/dashboard" replace />
  const submit = async (e) => {
    e.preventDefault()
    if (!EMAIL.test(form.email) || !form.password) { setError('Enter your administrator email and password.'); return }
    setBusy(true)
    setError('')
    try { await adminLogin(form.email, form.password); navigate('/admin/dashboard', { replace: true }) } catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }
  return (
    <AuthLayout admin footer={<Link to="/login" className="inline-flex items-center gap-1 hover:text-ink"><ArrowLeft className="size-3" />Workspace sign-in</Link>}>
      <span className="grid size-11 place-items-center rounded-2xl bg-accent text-white shadow-sm shadow-accent/30"><ShieldCheck className="size-5" /></span>
      <h1 className="mt-5 text-[28px] font-semibold tracking-[-0.03em]">Administrator sign-in</h1>
      <p className="mt-1.5 text-[15px] text-ink-2">Manage users, datasets, analysis jobs and platform settings.</p>
      <form onSubmit={submit} noValidate className="card mt-7 space-y-4 p-6">
        {(error || REASONS[params.get('reason')]) && <Alert tone={error ? 'danger' : 'info'}>{error || REASONS[params.get('reason')]}</Alert>}
        <Field label="Administrator email" htmlFor="aemail">
          <Input id="aemail" type="email" autoComplete="username" autoFocus value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Password" htmlFor="apassword">
          <PasswordInput id="apassword" autoComplete="current-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </Field>
        <Button type="submit" variant="primary" size="lg" loading={busy} className="w-full">Sign in to admin portal</Button>
        <p className="flex items-center justify-center gap-1.5 text-xs text-ink-3"><Lock className="size-3" />Restricted access. Every sign-in attempt is audited.</p>
      </form>
    </AuthLayout>
  )
}
