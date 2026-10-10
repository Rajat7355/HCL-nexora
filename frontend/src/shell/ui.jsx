import { Loader2, Star, AlertCircle, ChevronLeft, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

export const cx = (...c) => c.filter(Boolean).join(' ')

export const money = (v, currency = 'INR') =>
  v == null ? '' : new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(Number(v))

export const dateTime = (v) =>
  v ? new Date(v).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : ''

export const dateOnly = (v) => (v ? new Date(v).toLocaleDateString(undefined, { dateStyle: 'medium' }) : '')

export function Spinner({ label = 'Loading' }) {
  return (
    <div className="flex items-center justify-center gap-2 text-slate-500" role="status">
      <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
      <span className="text-sm">{label}…</span>
    </div>
  )
}

const buttonStyles = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 disabled:bg-brand-500/50',
  secondary: 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50 disabled:opacity-50',
  danger: 'bg-red-600 text-white hover:bg-red-700 disabled:bg-red-600/50',
  ghost: 'text-slate-600 hover:bg-slate-100 disabled:opacity-50',
}

export function Button({ variant = 'primary', size = 'md', className, as: Tag = 'button', ...props }) {
  const sizes = { sm: 'px-3 py-1.5 text-sm', md: 'px-4 py-2 text-sm', lg: 'px-5 py-3 text-base' }
  return (
    <Tag
      className={cx('inline-flex items-center justify-center gap-2 rounded-lg font-medium transition disabled:cursor-not-allowed',
        buttonStyles[variant], sizes[size], className)}
      {...props}
    />
  )
}

export function Card({ className, children }) {
  return <div className={cx('rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200', className)}>{children}</div>
}

const badgeColors = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  amber: 'bg-amber-50 text-amber-700 ring-amber-200',
  red: 'bg-red-50 text-red-700 ring-red-200',
  blue: 'bg-sky-50 text-sky-700 ring-sky-200',
  indigo: 'bg-brand-50 text-brand-700 ring-brand-100',
  slate: 'bg-slate-100 text-slate-600 ring-slate-200',
}
export function Badge({ color = 'slate', children }) {
  return <span className={cx('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset', badgeColors[color])}>{children}</span>
}

export function AvailabilityBadge({ availability, qty }) {
  if (availability === 'OUT_OF_STOCK') return <Badge color="red">Out of stock</Badge>
  if (availability === 'LOW_STOCK') return <Badge color="amber">Only {qty} left</Badge>
  return <Badge color="green">In stock</Badge>
}

export function Rating({ value, count }) {
  const v = Number(value || 0)
  return (
    <span className="inline-flex items-center gap-1 text-sm text-slate-600">
      <Star className="h-4 w-4 fill-amber-400 text-amber-400" aria-hidden />
      <span className="font-medium">{v.toFixed(1)}</span>
      {count != null && <span className="text-slate-400">({Number(count).toLocaleString()})</span>}
    </span>
  )
}

export function ErrorBox({ children }) {
  if (!children) return null
  return (
    <div role="alert" className="flex items-start gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-700 ring-1 ring-red-200">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <span>{children}</span>
    </div>
  )
}

export function Empty({ title, children }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 bg-white p-10 text-center">
      <p className="font-medium text-slate-700">{title}</p>
      {children && <div className="mt-2 text-sm text-slate-500">{children}</div>}
    </div>
  )
}

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions}
    </div>
  )
}

export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null
  return (
    <nav className="mt-6 flex items-center justify-center gap-3" aria-label="Pagination">
      <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        <ChevronLeft className="h-4 w-4" aria-hidden /> Previous
      </Button>
      <span className="text-sm text-slate-600">Page {page + 1} of {totalPages}</span>
      <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next <ChevronRight className="h-4 w-4" aria-hidden />
      </Button>
    </nav>
  )
}

export function Field({ label, error, children, hint }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-slate-400">{hint}</span>}
      {error && <span className="mt-1 block text-xs text-red-600">{error}</span>}
    </label>
  )
}

export const inputClass =
  'w-full rounded-lg border-0 bg-white px-3 py-2 text-sm text-slate-900 ring-1 ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-brand-500'

export function NotFound() {
  return (
    <div className="py-24 text-center">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="mt-2 text-slate-500">The page you are looking for does not exist.</p>
      <Link to="/" className="mt-4 inline-block text-brand-600 hover:underline">Back to the shop</Link>
    </div>
  )
}

const statusColors = {
  PLACED: 'blue', CONFIRMED: 'blue', PACKED: 'indigo', SHIPPED: 'indigo', OUT_FOR_DELIVERY: 'indigo',
  DELIVERED: 'green', CANCELLED: 'slate', RETURN_REQUESTED: 'amber', RETURNED: 'slate',
  LABEL_CREATED: 'slate', PICKED_UP: 'blue', IN_TRANSIT: 'indigo', DELAYED: 'amber', EXCEPTION: 'red',
  OPEN: 'blue', IN_PROGRESS: 'amber', RESOLVED: 'green', CLOSED: 'slate', ACKNOWLEDGED: 'amber',
  APPROVED: 'green', PROCESSING: 'amber', FAILED: 'red', SUPERSEDED: 'slate',
  RUNNING: 'blue', COMPLETED: 'green', AWAITING_APPROVAL: 'amber', REJECTED: 'red',
  LOW: 'slate', MEDIUM: 'blue', HIGH: 'amber', URGENT: 'red', CRITICAL: 'red',
  OK: 'green', REFUSED: 'amber', ERROR: 'red',
}
export function StatusBadge({ status }) {
  if (!status) return null
  return <Badge color={statusColors[status] || 'slate'}>{String(status).replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}</Badge>
}
