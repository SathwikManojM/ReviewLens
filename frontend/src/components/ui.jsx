import { forwardRef, useEffect, useId, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronDown, ChevronLeft, ChevronRight, Eye, EyeOff, Inbox, LoaderCircle, RotateCcw, Star, TriangleAlert, X } from 'lucide-react'
import { SENTIMENT, fmtNum } from '../lib/format'

export const cx = (...a) => a.filter(Boolean).join(' ')

const VARIANTS = {
  primary: 'bg-accent text-white shadow-sm shadow-accent/20 hover:brightness-110',
  secondary: 'border border-line bg-surface text-ink shadow-[0_1px_1px_rgb(0_0_0/0.03)] hover:bg-subtle',
  ghost: 'text-ink-2 hover:bg-muted hover:text-ink',
  subtle: 'bg-subtle text-ink hover:bg-muted',
  danger: 'bg-danger text-white hover:brightness-110',
  invert: 'bg-ink text-canvas hover:opacity-90',
}
const SIZES = {
  xs: 'h-7 gap-1.5 px-2.5 text-xs',
  sm: 'h-8 gap-1.5 px-3 text-[13px]',
  md: 'h-9 gap-2 px-3.5 text-sm',
  lg: 'h-11 gap-2 px-5 text-[15px]',
}

export const Button = forwardRef(function Button(
  { variant = 'secondary', size = 'md', loading, icon: Icon, iconRight: IconRight, className, children, disabled, type = 'button', ...rest }, ref) {
  return (
    <button ref={ref} type={type} {...rest} disabled={disabled || loading}
      className={cx('inline-flex shrink-0 select-none items-center justify-center whitespace-nowrap rounded-lg font-medium transition duration-150 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50',
        VARIANTS[variant], SIZES[size], className)}>
      {loading ? <LoaderCircle className="size-4 animate-spin" /> : Icon ? <Icon className="size-4" /> : null}
      {children}
      {IconRight && !loading && <IconRight className="size-4" />}
    </button>
  )
})

export function IconButton({ icon: Icon, label, className, ...rest }) {
  return (
    <button type="button" aria-label={label} title={label} {...rest}
      className={cx('inline-flex size-8 shrink-0 items-center justify-center rounded-lg text-ink-2 transition hover:bg-muted hover:text-ink disabled:opacity-40', className)}>
      <Icon className="size-[17px]" />
    </button>
  )
}

const FIELD = 'h-9 w-full rounded-lg border border-line bg-surface px-3 text-sm text-ink placeholder:text-ink-3 transition focus:border-accent focus:outline-none focus:ring-4 focus:ring-accent/15 disabled:opacity-60'

export const Input = forwardRef(function Input({ className, invalid, ...rest }, ref) {
  return <input ref={ref} aria-invalid={invalid || undefined} {...rest}
    className={cx(FIELD, invalid && 'border-danger focus:border-danger focus:ring-danger/15', className)} />
})

export function Select({ className, children, ...rest }) {
  return (
    <div className={cx('relative min-w-0', className)}>
      <select {...rest} className={cx(FIELD, 'cursor-pointer appearance-none truncate pr-8')}>{children}</select>
      <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 size-4 -translate-y-1/2 text-ink-3" />
    </div>
  )
}

export function Textarea({ className, ...rest }) {
  return <textarea {...rest} className={cx(FIELD, 'h-auto resize-y py-2.5 leading-relaxed', className)} />
}

export function Field({ label, hint, error, children, className, htmlFor }) {
  return (
    <div className={cx('flex min-w-0 flex-col gap-1.5', className)}>
      {label && <label htmlFor={htmlFor} className="text-[13px] font-medium text-ink-2">{label}</label>}
      {children}
      {error ? <p className="text-xs text-danger" role="alert">{error}</p> : hint ? <p className="text-xs text-ink-3">{hint}</p> : null}
    </div>
  )
}

export const PasswordInput = forwardRef(function PasswordInput({ className, ...props }, ref) {
  const [show, setShow] = useState(false)
  return (
    <div className="relative">
      <Input ref={ref} type={show ? 'text' : 'password'} className={cx('pr-10', className)} {...props} />
      <button type="button" onClick={() => setShow((s) => !s)} aria-label={show ? 'Hide password' : 'Show password'}
        className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-ink-3 transition hover:text-ink">
        {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
      </button>
    </div>
  )
})

const TONES = {
  neutral: 'bg-subtle text-ink-2 ring-line',
  accent: 'bg-accent-soft text-accent-ink ring-accent/20',
  pos: 'bg-pos-soft text-ink ring-pos/25',
  neg: 'bg-neg-soft text-ink ring-neg/25',
  success: 'bg-success-soft text-success ring-success/25',
  warning: 'bg-warning-soft text-warning ring-warning/25',
  danger: 'bg-neg-soft text-danger ring-danger/25',
}
const DOTS = { neutral: 'bg-ink-3', accent: 'bg-accent', pos: 'bg-pos', neg: 'bg-neg', success: 'bg-success', warning: 'bg-warning', danger: 'bg-danger' }

export function Badge({ tone = 'neutral', dot, children, className }) {
  return (
    <span className={cx('inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-[11.5px] font-medium ring-1 ring-inset', TONES[tone], className)}>
      {dot && <span className={cx('size-1.5 rounded-full', typeof dot === 'string' ? dot : DOTS[tone])} />}
      {children}
    </span>
  )
}

export function SentimentBadge({ value, children }) {
  const tone = value === 'POSITIVE' ? 'pos' : value === 'NEGATIVE' ? 'neg' : 'neutral'
  return <Badge tone={tone} dot={value === 'NEUTRAL' ? 'bg-neu' : true}>{children ?? SENTIMENT[value] ?? '—'}</Badge>
}

const STATUS = {
  COMPLETED: ['success', 'Completed'], READY: ['success', 'Ready'], UP: ['success', 'Operational'], OPERATIONAL: ['success', 'All systems operational'],
  RUNNING: ['accent', 'Running'], ANALYZING: ['accent', 'Analysing'], PARSING: ['accent', 'Reading file'], GENERATING: ['accent', 'Generating'],
  QUEUED: ['warning', 'Queued'], DEGRADED: ['warning', 'Degraded'], FAILED: ['danger', 'Failed'], DOWN: ['danger', 'Down'],
  CANCELLED: ['neutral', 'Cancelled'], ACTIVE: ['success', 'Active'], INACTIVE: ['neutral', 'Inactive'], ADMIN: ['accent', 'Admin'], USER: ['neutral', 'User'],
}
const SPINNING = new Set(['RUNNING', 'ANALYZING', 'PARSING', 'GENERATING'])

export function StatusBadge({ status, label }) {
  const [tone, text] = STATUS[status] || ['neutral', status]
  return (
    <Badge tone={tone}>
      {SPINNING.has(status) ? <LoaderCircle className="size-3 animate-spin" /> : <span className={cx('size-1.5 rounded-full', DOTS[tone])} />}
      {label ?? text}
    </Badge>
  )
}

export function Card({ className, children, as: Tag = 'div', ...rest }) {
  return <Tag className={cx('card', className)} {...rest}>{children}</Tag>
}

export function CardHeader({ title, description, action, className }) {
  return (
    <div className={cx('flex flex-wrap items-start justify-between gap-3 px-5 pt-5', className)}>
      <div className="min-w-0">
        <h3 className="text-[15px] font-semibold tracking-tight text-ink">{title}</h3>
        {description && <p className="mt-0.5 text-[13px] leading-snug text-ink-3">{description}</p>}
      </div>
      {action}
    </div>
  )
}

export function Skeleton({ className }) {
  return <div className={cx('animate-pulse rounded-lg bg-muted', className)} />
}

export function LoadingSkeleton({ variant = 'page' }) {
  if (variant === 'rows') return <div className="space-y-2 p-5">{[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-11" />)}</div>
  return (
    <div className="space-y-5" aria-busy="true" aria-label="Loading">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-28 rounded-2xl" />)}</div>
      <Skeleton className="h-20 rounded-2xl" />
      <div className="grid gap-4 lg:grid-cols-3"><Skeleton className="h-80 rounded-2xl lg:col-span-2" /><Skeleton className="h-80 rounded-2xl" /></div>
    </div>
  )
}

export function EmptyState({ icon: Icon = Inbox, title, children, action, className }) {
  return (
    <div className={cx('flex flex-col items-center justify-center px-6 py-14 text-center', className)}>
      <div className="mb-4 grid size-12 place-items-center rounded-2xl bg-subtle ring-1 ring-line"><Icon className="size-5 text-ink-3" /></div>
      <h3 className="text-[15px] font-semibold text-ink">{title}</h3>
      {children && <div className="mt-1.5 max-w-sm text-sm leading-relaxed text-ink-3">{children}</div>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry, className }) {
  return (
    <div className={cx('flex flex-col items-center gap-3 rounded-2xl border border-danger/25 bg-neg-soft/60 px-6 py-8 text-center', className)} role="alert">
      <TriangleAlert className="size-5 text-danger" />
      <p className="max-w-md text-sm text-ink">{message}</p>
      {onRetry && <Button size="sm" icon={RotateCcw} onClick={onRetry}>Try again</Button>}
    </div>
  )
}

export function Alert({ tone = 'danger', children, className }) {
  const styles = tone === 'danger' ? 'border-danger/25 bg-neg-soft/70' : tone === 'warning' ? 'border-warning/30 bg-warning-soft' : 'border-accent/25 bg-accent-soft'
  return <div role="alert" className={cx('flex items-start gap-2.5 rounded-xl border px-3.5 py-3 text-sm text-ink', styles, className)}>
    <TriangleAlert className={cx('mt-0.5 size-4 shrink-0', tone === 'danger' ? 'text-danger' : tone === 'warning' ? 'text-warning' : 'text-accent')} />
    <div className="min-w-0 flex-1">{children}</div>
  </div>
}

export function Pagination({ page, totalPages, totalElements, size, onChange }) {
  if (!totalElements) return null
  const start = page * size + 1
  const end = Math.min(totalElements, (page + 1) * size)
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3 text-[13px] text-ink-3">
      <span className="tnum">{fmtNum(start)}–{fmtNum(end)} of {fmtNum(totalElements)}</span>
      <div className="flex items-center gap-1">
        <IconButton icon={ChevronLeft} label="Previous page" disabled={page === 0} onClick={() => onChange(page - 1)} />
        <span className="tnum px-2 text-ink-2">{page + 1} / {Math.max(totalPages, 1)}</span>
        <IconButton icon={ChevronRight} label="Next page" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)} />
      </div>
    </div>
  )
}

export function Segmented({ value, onChange, options, className }) {
  const id = useId()
  return (
    <div role="tablist" className={cx('inline-flex shrink-0 rounded-lg bg-subtle p-0.5 ring-1 ring-inset ring-line', className)}>
      {options.map((o) => {
        const active = o.value === value
        return (
          <button key={o.value} type="button" role="tab" aria-selected={active} onClick={() => onChange(o.value)}
            className={cx('relative rounded-md px-3 py-1 text-[13px] font-medium transition-colors', active ? 'text-ink' : 'text-ink-3 hover:text-ink')}>
            {active && <motion.span layoutId={`seg-${id}`} className="absolute inset-0 rounded-md bg-surface shadow-sm ring-1 ring-line"
              transition={{ type: 'spring', bounce: 0.15, duration: 0.4 }} />}
            <span className="relative flex items-center gap-1.5">{o.icon && <o.icon className="size-3.5" />}{o.label}</span>
          </button>
        )
      })}
    </div>
  )
}

export function Switch({ checked, onChange, label, description, disabled }) {
  return (
    <label className={cx('flex cursor-pointer items-start justify-between gap-4', disabled && 'cursor-not-allowed opacity-60')}>
      <span className="min-w-0">
        <span className="block text-sm font-medium text-ink">{label}</span>
        {description && <span className="mt-0.5 block text-[13px] text-ink-3">{description}</span>}
      </span>
      <button type="button" role="switch" aria-checked={checked} aria-label={label} disabled={disabled} onClick={() => onChange(!checked)}
        className={cx('relative mt-0.5 h-6 w-10 shrink-0 rounded-full transition-colors', checked ? 'bg-accent' : 'bg-line-strong')}>
        <motion.span layout transition={{ type: 'spring', bounce: 0.25, duration: 0.3 }}
          className={cx('absolute top-0.5 size-5 rounded-full bg-white shadow', checked ? 'right-0.5' : 'left-0.5')} />
      </button>
    </label>
  )
}

export function Dialog({ open, onClose, title, description, children, footer, size = 'md' }) {
  const panel = useRef(null)
  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => e.key === 'Escape' && onClose?.()
    window.addEventListener('keydown', onKey)
    const t = setTimeout(() => panel.current?.querySelector('input,textarea,select,button[data-autofocus]')?.focus() ?? panel.current?.focus(), 30)
    return () => { window.removeEventListener('keydown', onKey); clearTimeout(t) }
  }, [open, onClose])
  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[70] flex items-end justify-center p-3 sm:items-center sm:p-6">
          <motion.div className="absolute inset-0 bg-black/45 backdrop-blur-[2px]" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.div ref={panel} tabIndex={-1} role="dialog" aria-modal="true" aria-label={title}
            initial={{ opacity: 0, y: 14, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 10, scale: 0.98 }}
            transition={{ type: 'spring', bounce: 0.12, duration: 0.35 }}
            className={cx('relative max-h-[90vh] w-full overflow-y-auto rounded-2xl border border-line bg-surface shadow-pop outline-none', size === 'lg' ? 'max-w-2xl' : 'max-w-md')}>
            <div className="flex items-start justify-between gap-4 px-5 pt-5">
              <div>
                <h2 className="text-[17px] font-semibold tracking-tight">{title}</h2>
                {description && <p className="mt-1 text-sm leading-relaxed text-ink-2">{description}</p>}
              </div>
              <IconButton icon={X} label="Close" onClick={onClose} className="-mr-1 -mt-1" />
            </div>
            {children && <div className="px-5 pt-4">{children}</div>}
            {footer && <div className="flex flex-wrap justify-end gap-2 px-5 pb-5 pt-5">{footer}</div>}
            {!footer && <div className="pb-5" />}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  )
}

/** Confirmation for destructive or high-impact actions; optional typed confirmation and audit reason. */
export function ConfirmationDialog({ open, onClose, onConfirm, title, description, confirmLabel = 'Confirm', tone = 'danger', requireText, reasonField, loading, children }) {
  const [text, setText] = useState('')
  const [reason, setReason] = useState('')
  useEffect(() => { if (open) { setText(''); setReason('') } }, [open])
  const blocked = Boolean(requireText) && text.trim() !== requireText
  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} title={title} description={description}
      footer={<>
        <Button onClick={onClose} disabled={loading}>Cancel</Button>
        <Button variant={tone === 'danger' ? 'danger' : 'primary'} loading={loading} disabled={blocked} onClick={() => onConfirm(reason.trim())}>{confirmLabel}</Button>
      </>}>
      {(children || reasonField || requireText) && (
        <div className="space-y-4">
          {children}
          {reasonField && <Field label="Reason (recorded in the audit log)"><Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Optional" maxLength={300} /></Field>}
          {requireText && <Field label={<>Type <span className="font-semibold text-ink">{requireText}</span> to confirm</>}><Input value={text} onChange={(e) => setText(e.target.value)} autoComplete="off" /></Field>}
        </div>
      )}
    </Dialog>
  )
}

export function Drawer({ open, onClose, title, subtitle, children, actions }) {
  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => e.key === 'Escape' && onClose?.()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])
  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[65]">
          <motion.div className="absolute inset-0 bg-black/35" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} />
          <motion.aside role="dialog" aria-modal="true" aria-label={title}
            initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }} transition={{ type: 'spring', bounce: 0, duration: 0.38 }}
            className="absolute inset-y-0 right-0 flex w-full max-w-xl flex-col border-l border-line bg-surface shadow-pop">
            <header className="flex items-start justify-between gap-4 border-b border-line px-6 py-5">
              <div className="min-w-0">
                <h2 className="truncate text-[17px] font-semibold tracking-tight">{title}</h2>
                {subtitle && <p className="mt-1 text-[13px] text-ink-3">{subtitle}</p>}
              </div>
              <div className="flex items-center gap-1">{actions}<IconButton icon={X} label="Close" onClick={onClose} /></div>
            </header>
            <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
          </motion.aside>
        </div>
      )}
    </AnimatePresence>
  )
}

export function Stars({ rating, className }) {
  if (!rating) return <span className="text-xs text-ink-3">No rating</span>
  return (
    <span className={cx('inline-flex items-center gap-px', className)} aria-label={`${rating} of 5 stars`}>
      {[1, 2, 3, 4, 5].map((i) => <Star key={i} className={cx('size-3.5', i <= rating ? 'fill-amber-400 text-amber-400' : 'text-line-strong')} />)}
    </span>
  )
}

export function Kbd({ children }) {
  return <kbd className="rounded-md border border-line bg-subtle px-1.5 py-0.5 font-sans text-[11px] font-medium text-ink-3">{children}</kbd>
}

export const TH = 'whitespace-nowrap px-4 py-2.5 text-left text-xs font-medium text-ink-3'
export const TD = 'border-t border-line px-4 py-3 align-middle'
