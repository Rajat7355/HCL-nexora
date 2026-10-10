import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Heart, LogIn, LogOut, Menu, Search, ShoppingBag, ShoppingCart, X } from 'lucide-react'
import { api } from '../api/client.js'
import { useAuth } from './AuthContext.jsx'
import { cx } from './ui.jsx'

// Features contribute menu links (routes.jsx -> nav) and floating widgets (widgets.jsx -> default array of components).
const routeModules = import.meta.glob('../features/*/routes.jsx', { eager: true })
const navItems = Object.values(routeModules).flatMap((m) => m.nav || [])
const widgetModules = import.meta.glob('../features/*/widgets.jsx', { eager: true })
const widgets = Object.values(widgetModules).flatMap((m) => m.default || [])

export default function Layout() {
  const { user, logout, isStaff } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [term, setTerm] = useState('')

  const { data: cart } = useQuery({ queryKey: ['cart'], queryFn: () => api.get('/cart').then((r) => r.data), enabled: Boolean(user) })
  const count = (cart?.items || []).reduce((n, i) => n + i.quantity, 0)

  const visible = navItems.filter((n) => (!n.auth || user) && (!n.staff || isStaff))
  const publicLinks = visible.filter((n) => !n.staff)
  const staffLinks = visible.filter((n) => n.staff)

  const submit = (e) => {
    e.preventDefault()
    navigate(term.trim() ? `/?q=${encodeURIComponent(term.trim())}` : '/')
    setOpen(false)
  }

  const linkClass = ({ isActive }) =>
    cx('rounded-md px-3 py-2 text-sm font-medium', isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100')

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center gap-3 px-4 py-3">
          <button className="rounded-md p-2 text-slate-600 hover:bg-slate-100 lg:hidden" onClick={() => setOpen(!open)} aria-label="Toggle menu">
            {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
          <Link to="/" className="flex items-center gap-2 text-lg font-bold tracking-tight text-slate-900">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-white"><ShoppingBag className="h-4 w-4" /></span>
            ShopSmart <span className="text-brand-600">AI</span>
          </Link>

          <form onSubmit={submit} className="relative hidden flex-1 md:block md:max-w-xl" role="search">
            <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" aria-hidden />
            <input value={term} onChange={(e) => setTerm(e.target.value)} placeholder="Search products, brands…"
              className="w-full rounded-full border-0 bg-slate-100 py-2 pl-9 pr-4 text-sm ring-1 ring-transparent focus:bg-white focus:ring-2 focus:ring-brand-500"
              aria-label="Search products" />
          </form>

          <nav className="ml-auto hidden items-center gap-1 lg:flex" aria-label="Main">
            {publicLinks.map((n) => <NavLink key={n.to} to={n.to} className={linkClass} end={n.to === '/'}>{n.label}</NavLink>)}
          </nav>

          <div className="ml-auto flex items-center gap-1 lg:ml-0">
            {user && (
              <Link to="/wishlist" className="rounded-md p-2 text-slate-600 hover:bg-slate-100" aria-label="Wishlist"><Heart className="h-5 w-5" /></Link>
            )}
            <Link to="/cart" className="relative rounded-md p-2 text-slate-600 hover:bg-slate-100" aria-label={`Cart, ${count} items`}>
              <ShoppingCart className="h-5 w-5" />
              {count > 0 && <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-600 px-1 text-xs font-semibold text-white">{count}</span>}
            </Link>
            {user ? (
              <div className="flex items-center gap-1">
                <Link to="/profile" className="hidden rounded-md px-2 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 sm:block">
                  {user.firstName} <span className="ml-1 rounded bg-brand-50 px-1.5 py-0.5 text-xs text-brand-700">{user.tier}</span>
                </Link>
                <button onClick={() => { void logout().finally(() => navigate('/', { replace: true })) }} className="rounded-md p-2 text-slate-600 hover:bg-slate-100" aria-label="Log out" title="Log out"><LogOut className="h-5 w-5" /></button>
              </div>
            ) : (
              <Link to="/login" className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700"><LogIn className="h-4 w-4" /> Log in</Link>
            )}
          </div>
        </div>

        {staffLinks.length > 0 && (
          <div className="hidden border-t border-slate-100 bg-slate-900 lg:block">
            <nav className="mx-auto flex max-w-7xl items-center gap-1 px-4 py-1.5" aria-label="Staff">
              <span className="mr-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Staff</span>
              {staffLinks.map((n) => (
                <NavLink key={n.to} to={n.to} className={({ isActive }) => cx('rounded px-2.5 py-1 text-sm', isActive ? 'bg-slate-700 text-white' : 'text-slate-300 hover:text-white')}>{n.label}</NavLink>
              ))}
            </nav>
          </div>
        )}

        {open && (
          <div className="border-t border-slate-200 bg-white px-4 py-3 lg:hidden">
            <form onSubmit={submit} className="mb-3" role="search">
              <input value={term} onChange={(e) => setTerm(e.target.value)} placeholder="Search products…" className="w-full rounded-lg bg-slate-100 px-3 py-2 text-sm" aria-label="Search products" />
            </form>
            <nav className="flex flex-col gap-1" onClick={() => setOpen(false)}>
              {visible.map((n) => <NavLink key={n.to} to={n.to} className={linkClass} end={n.to === '/'}>{n.staff ? `Staff: ${n.label}` : n.label}</NavLink>)}
            </nav>
          </div>
        )}
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white py-6 text-center text-xs text-slate-400">
        ShopSmart AI · Demo store. Payments are simulated and delivery events are mocked.
      </footer>

      {widgets.map((W, i) => <W key={i} />)}
    </div>
  )
}
