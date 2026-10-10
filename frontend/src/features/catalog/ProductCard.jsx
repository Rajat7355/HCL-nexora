import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { GitCompareArrows, Heart, ShoppingCart } from 'lucide-react'
import { useAuth } from '../../shell/AuthContext.jsx'
import { AvailabilityBadge, Rating, cx, money } from '../../shell/ui.jsx'
import { addToCart, addWishlist, fetchWishlist, removeWishlist } from './api.js'
import { compareStore, useCompare } from './compareStore.js'

/** Wishlist membership + toggle, shared by cards and the product page. */
export function useWishlist() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const { data = [] } = useQuery({ queryKey: ['wishlist'], queryFn: fetchWishlist, enabled: Boolean(user) })
  const ids = new Set(data.map((p) => p.productId))
  const toggle = useMutation({
    mutationFn: (sku) => (ids.has(sku) ? removeWishlist(sku) : addWishlist(sku)),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['wishlist'] }),
  })
  return { has: (sku) => ids.has(sku), toggle: toggle.mutate, items: data }
}

export function useAddToCart() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const qc = useQueryClient()
  const mutation = useMutation({
    mutationFn: ({ sku, quantity }) => addToCart(sku, quantity),
    onSuccess: (cart) => qc.setQueryData(['cart'], cart),
  })
  return {
    ...mutation,
    add: (sku, quantity = 1) => {
      if (!user) return navigate('/login', { state: { from: location.pathname + location.search } })
      return mutation.mutate({ sku, quantity })
    },
  }
}

export default function ProductCard({ product }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const wishlist = useWishlist()
  const cart = useAddToCart()
  const compare = useCompare()
  const inCompare = compare.some((p) => p.productId === product.productId)
  const out = product.availability === 'OUT_OF_STOCK'

  return (
    <article className="group flex flex-col overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200 transition hover:shadow-md">
      <Link to={`/products/${product.productId}`} className="relative block aspect-[4/3] overflow-hidden bg-slate-100">
        {product.imageUrl ? <img src={product.imageUrl} alt={product.name} loading="lazy" className="h-full w-full object-cover transition group-hover:scale-105" /> : <div role="img" aria-label={product.name} className="flex h-full w-full items-center justify-center text-xs text-slate-400">No image</div>}
      </Link>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{product.brand}</p>
            <Link to={`/products/${product.productId}`} className="line-clamp-2 font-semibold text-slate-900 hover:text-brand-600">{product.name}</Link>
          </div>
          <button
            onClick={() => (user ? wishlist.toggle(product.productId) : navigate('/login'))}
            className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-rose-500"
            aria-label={wishlist.has(product.productId) ? 'Remove from wishlist' : 'Add to wishlist'}
            aria-pressed={wishlist.has(product.productId)}
          >
            <Heart className={cx('h-5 w-5', wishlist.has(product.productId) && 'fill-rose-500 text-rose-500')} />
          </button>
        </div>
        <Rating value={product.rating} count={product.ratingCount} />
        <div className="mt-auto flex items-end justify-between pt-2">
          <span className="text-lg font-bold text-slate-900">{money(product.price, product.currency)}</span>
          <AvailabilityBadge availability={product.availability} qty={product.availableQty} />
        </div>
        <div className="flex items-center gap-2 pt-1">
          <button
            onClick={() => cart.add(product.productId, 1)}
            disabled={out || cart.isPending}
            className="inline-flex flex-1 items-center justify-center gap-2 rounded-lg bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            <ShoppingCart className="h-4 w-4" aria-hidden /> {out ? 'Unavailable' : 'Add to cart'}
          </button>
          <button
            onClick={() => compareStore.toggle(product)}
            className={cx('rounded-lg p-2 ring-1', inCompare ? 'bg-brand-50 text-brand-700 ring-brand-500' : 'text-slate-500 ring-slate-300 hover:bg-slate-50')}
            aria-label={inCompare ? 'Remove from comparison' : 'Add to comparison'}
            aria-pressed={inCompare}
            title="Compare"
          >
            <GitCompareArrows className="h-4 w-4" />
          </button>
        </div>
      </div>
    </article>
  )
}
