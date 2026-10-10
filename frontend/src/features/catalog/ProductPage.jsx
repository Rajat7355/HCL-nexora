import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { GitCompareArrows, Heart, MapPin, ShoppingCart, Sparkles } from 'lucide-react'
import { AvailabilityBadge, Button, Card, ErrorBox, Rating, Spinner, cx, money } from '../../shell/ui.jsx'
import { errorMessage } from '../../api/client.js'
import { fetchProduct } from './api.js'
import ProductCard, { useAddToCart, useWishlist } from './ProductCard.jsx'
import { compareStore, useCompare } from './compareStore.js'

const label = (k) => k.replaceAll('_', ' ').replace(/^\w/, (c) => c.toUpperCase())
const show = (v) => (v === true ? 'Yes' : v === false ? 'No' : String(v))

export default function ProductPage() {
  const { sku } = useParams()
  const { data: p, isLoading, error } = useQuery({ queryKey: ['product', sku], queryFn: () => fetchProduct(sku) })
  const [active, setActive] = useState(0)
  const [qty, setQty] = useState(1)
  const wishlist = useWishlist()
  const cart = useAddToCart()
  const compare = useCompare()

  if (isLoading) return <div className="py-24"><Spinner /></div>
  if (error) return <div className="mx-auto max-w-lg py-16"><ErrorBox>{errorMessage(error, 'We could not find that product.')}</ErrorBox><Link to="/" className="mt-4 inline-block text-brand-600">Back to the shop</Link></div>

  const images = p.images?.length ? p.images : [{ url: p.imageUrl, altText: p.name }]
  const out = p.availability === 'OUT_OF_STOCK'
  const inCompare = compare.some((c) => c.productId === p.productId)

  return (
    <div>
      <nav className="mb-4 text-sm text-slate-500" aria-label="Breadcrumb">
        <Link to="/" className="hover:text-brand-600">Shop</Link> / <Link to={`/?category=${p.categorySlug}`} className="hover:text-brand-600">{p.category}</Link> / <span className="text-slate-700">{p.name}</span>
      </nav>

      <div className="grid gap-8 lg:grid-cols-2">
        <div>
          <div className="aspect-[4/3] overflow-hidden rounded-2xl bg-slate-100 ring-1 ring-slate-200">
            <img src={images[active]?.url} alt={images[active]?.altText || p.name} className="h-full w-full object-cover" />
          </div>
          {images.length > 1 && (
            <div className="mt-3 flex gap-2">
              {images.map((img, i) => (
                <button key={i} onClick={() => setActive(i)} className={cx('h-16 w-20 overflow-hidden rounded-lg ring-2', i === active ? 'ring-brand-500' : 'ring-transparent')} aria-label={`Show image ${i + 1}`}>
                  <img src={img.url} alt="" className="h-full w-full object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>

        <div>
          <p className="text-sm font-medium uppercase tracking-wide text-slate-400">{p.brand}</p>
          <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-900">{p.name}</h1>
          <div className="mt-2 flex items-center gap-3"><Rating value={p.rating} count={p.ratingCount} /><AvailabilityBadge availability={p.availability} qty={p.availableQty} /></div>
          <p className="mt-4 text-3xl font-bold text-slate-900">{money(p.price, p.currency)}</p>
          <p className="mt-4 text-slate-600">{p.description}</p>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <label className="flex items-center gap-2 text-sm">
              Qty
              <select value={qty} onChange={(e) => setQty(Number(e.target.value))} className="rounded-lg border-0 py-2 pl-3 pr-8 ring-1 ring-slate-300">
                {[1, 2, 3, 4, 5].map((n) => <option key={n}>{n}</option>)}
              </select>
            </label>
            <Button size="lg" disabled={out || cart.isPending} onClick={() => cart.add(p.productId, qty)}>
              <ShoppingCart className="h-5 w-5" /> {out ? 'Out of stock' : 'Add to cart'}
            </Button>
            <Button variant="secondary" size="lg" onClick={() => wishlist.toggle(p.productId)} aria-pressed={wishlist.has(p.productId)}>
              <Heart className={cx('h-5 w-5', wishlist.has(p.productId) && 'fill-rose-500 text-rose-500')} /> {wishlist.has(p.productId) ? 'Saved' : 'Wishlist'}
            </Button>
            <Button variant="secondary" size="lg" onClick={() => compareStore.toggle(p)} aria-pressed={inCompare}>
              <GitCompareArrows className="h-5 w-5" /> {inCompare ? 'In comparison' : 'Compare'}
            </Button>
          </div>
          {cart.isSuccess && <p className="mt-3 text-sm text-emerald-600">Added to your cart. <Link to="/cart" className="underline">View cart</Link></p>}
          <ErrorBox>{cart.isError && errorMessage(cart.error)}</ErrorBox>

          <button
            onClick={() => window.dispatchEvent(new CustomEvent('assistant:open', { detail: { message: `Is the ${p.name} a good choice? What are the alternatives?` } }))}
            className="mt-6 flex w-full items-center gap-3 rounded-xl bg-brand-50 p-4 text-left ring-1 ring-brand-100 hover:bg-brand-100"
          >
            <Sparkles className="h-5 w-5 shrink-0 text-brand-600" />
            <span className="text-sm"><b className="text-brand-700">Ask AI about this product.</b> <span className="text-slate-600">Get alternatives and a recommendation for your budget.</span></span>
          </button>
        </div>
      </div>

      <div className="mt-10 grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h2 className="mb-3 text-lg font-semibold">Specifications</h2>
          <dl className="divide-y divide-slate-100">
            {Object.entries(p.specs || {}).map(([k, v]) => (
              <div key={k} className="grid grid-cols-3 gap-4 py-2 text-sm"><dt className="text-slate-500">{label(k)}</dt><dd className="col-span-2 font-medium text-slate-800">{show(v)}</dd></div>
            ))}
          </dl>
        </Card>
        <Card>
          <h2 className="mb-3 text-lg font-semibold">Availability</h2>
          <ul className="space-y-3 text-sm">
            {(p.stockByWarehouse || []).map((w) => (
              <li key={w.warehouse} className="flex items-center justify-between">
                <span className="flex items-center gap-2 text-slate-600"><MapPin className="h-4 w-4 text-slate-400" />{w.city}</span>
                <span className={cx('font-medium', w.available <= 0 ? 'text-red-600' : w.available <= 10 ? 'text-amber-600' : 'text-emerald-600')}>{w.available > 0 ? `${w.available} units` : 'None'}</span>
              </li>
            ))}
          </ul>
        </Card>
      </div>

      {p.related?.length > 0 && (
        <section className="mt-10">
          <h2 className="mb-4 text-lg font-semibold">You may also like</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{p.related.map((r) => <ProductCard key={r.productId} product={r} />)}</div>
        </section>
      )}
    </div>
  )
}
