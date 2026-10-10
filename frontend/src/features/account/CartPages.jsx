import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { api, errorCode, errorMessage } from '../../api/client.js'
import { Badge, Button, Card, Empty, ErrorBox, PageHeader, Spinner, money, cx } from '../../shell/ui.jsx'
import { useAuth } from '../../shell/AuthContext.jsx'

const cartQuery = { queryKey: ['cart'], queryFn: () => api.get('/cart').then((r) => r.data) }

function Summary({ cart, tier }) {
  return (
    <Card className="lg:sticky lg:top-24">
      <h2 className="mb-3 text-lg font-semibold">Order summary</h2>
      <dl className="space-y-2 text-sm">
        <div className="flex justify-between"><dt className="text-slate-500">Subtotal</dt><dd>{money(cart.subtotal)}</dd></div>
        <div className="flex justify-between"><dt className="text-slate-500">Shipping</dt><dd>{Number(cart.shipping) === 0 ? 'Free' : money(cart.shipping)}</dd></div>
        {Number(cart.discount) > 0 && <div className="flex justify-between text-emerald-700"><dt>{tier} discount</dt><dd>-{money(cart.discount)}</dd></div>}
        <div className="flex justify-between border-t border-slate-200 pt-3 text-base font-bold"><dt>Total</dt><dd>{money(cart.total)}</dd></div>
      </dl>
      {tier === 'BASIC' && Number(cart.shipping) > 0 && <p className="mt-3 rounded-lg bg-brand-50 p-2 text-xs text-brand-700">Spend {money(Math.max(0, 9686.72 - Number(cart.subtotal)))} more for free shipping, or join Prime.</p>}
    </Card>
  )
}

export function CartPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const { data: cart, isLoading, error } = useQuery(cartQuery)
  const update = useMutation({
    mutationFn: ({ sku, quantity }) => api.put(`/cart/items/${sku}`, { quantity }),
    onSuccess: (r) => qc.setQueryData(['cart'], r.data),
  })
  const remove = useMutation({ mutationFn: (sku) => api.delete(`/cart/items/${sku}`), onSuccess: (r) => qc.setQueryData(['cart'], r.data) })

  if (isLoading) return <div className="py-20"><Spinner /></div>
  if (error) return <ErrorBox>{errorMessage(error)}</ErrorBox>
  const items = cart.items

  return (
    <div>
      <PageHeader title="Your cart" />
      {items.length === 0 ? <Empty title="Your cart is empty"><Link to="/" className="text-brand-600 underline">Start shopping</Link></Empty> : (
        <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
          <div className="space-y-3">
            <ErrorBox>{update.isError && errorMessage(update.error)}</ErrorBox>
            {items.map((i) => (
              <Card key={i.productId} className="flex gap-4">
                <img src={i.imageUrl} alt="" className="h-24 w-28 shrink-0 rounded-lg object-cover" />
                <div className="flex min-w-0 flex-1 flex-col">
                  <Link to={`/products/${i.productId}`} className="font-semibold text-slate-900 hover:text-brand-600">{i.name}</Link>
                  <p className="text-sm text-slate-500">{money(i.price)} each</p>
                  {!i.inStock && <div className="mt-1"><Badge color="red">Only {i.availableQty} available</Badge></div>}
                  <div className="mt-auto flex items-center justify-between pt-2">
                    <select value={i.quantity} onChange={(e) => update.mutate({ sku: i.productId, quantity: Number(e.target.value) })} className="rounded-lg border-0 py-1 pl-3 pr-8 text-sm ring-1 ring-slate-300" aria-label={`Quantity for ${i.name}`}>
                      {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((n) => <option key={n}>{n}</option>)}
                    </select>
                    <span className="font-bold">{money(i.lineTotal)}</span>
                  </div>
                </div>
                <button onClick={() => remove.mutate(i.productId)} className="self-start rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-red-600" aria-label={`Remove ${i.name}`}><Trash2 className="h-4 w-4" /></button>
              </Card>
            ))}
          </div>
          <div className="space-y-4">
            <Summary cart={cart} tier={user.tier} />
            <Button as={Link} to="/checkout" size="lg" className="w-full" aria-disabled={cart.hasStockProblem}>Checkout</Button>
          </div>
        </div>
      )}
    </div>
  )
}

export function CheckoutPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const cartQ = useQuery(cartQuery)
  const addr = useQuery({ queryKey: ['addresses'], queryFn: () => api.get('/me/addresses').then((r) => r.data) })
  const [chosen, setChosen] = useState(null)
  const place = useMutation({
    mutationFn: (addressId) => api.post('/orders/checkout', { addressId }).then((r) => r.data),
    onSuccess: (order) => { qc.invalidateQueries({ queryKey: ['cart'] }); qc.invalidateQueries({ queryKey: ['orders'] }); navigate(`/orders/${order.orderNumber}`, { state: { placed: true } }) },
    onError: (e) => { if (errorCode(e) === 'INSUFFICIENT_STOCK') qc.invalidateQueries({ queryKey: ['cart'] }) },
  })

  if (cartQ.isLoading || addr.isLoading) return <div className="py-20"><Spinner /></div>
  const cart = cartQ.data
  if (!cart || cart.items.length === 0) return <Empty title="Nothing to check out"><Link to="/" className="text-brand-600 underline">Back to the shop</Link></Empty>
  const addresses = addr.data || []
  const selected = chosen ?? addresses.find((a) => a.isDefault)?.id ?? addresses[0]?.id

  return (
    <div>
      <PageHeader title="Checkout" subtitle="Payments are simulated in this demo. No card is needed." />
      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-4">
          <Card>
            <h2 className="mb-3 text-lg font-semibold">Delivery address</h2>
            {addresses.length === 0 ? <p className="text-sm text-slate-500">You have no saved address. <Link to="/profile" className="text-brand-600 underline">Add one in your profile</Link>.</p> : (
              <ul className="space-y-2">
                {addresses.map((a) => (
                  <li key={a.id}>
                    <label className={cx('flex cursor-pointer gap-3 rounded-lg p-3 ring-1', selected === a.id ? 'bg-brand-50 ring-brand-500' : 'ring-slate-200 hover:bg-slate-50')}>
                      <input type="radio" name="address" checked={selected === a.id} onChange={() => setChosen(a.id)} className="mt-1 text-brand-600" />
                      <span className="text-sm"><b>{a.fullName}</b><br />{a.line1}, {a.city} {a.postalCode}, {a.country}</span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card>
            <h2 className="mb-3 text-lg font-semibold">Items</h2>
            <ul className="divide-y divide-slate-100 text-sm">
              {cart.items.map((i) => <li key={i.productId} className="flex justify-between py-2"><span>{i.quantity} × {i.name}</span><span>{money(i.lineTotal)}</span></li>)}
            </ul>
          </Card>
        </div>
        <div className="space-y-4">
          <Summary cart={cart} tier={user.tier} />
          <ErrorBox>{place.isError && errorMessage(place.error)}</ErrorBox>
          <Button size="lg" className="w-full" disabled={!selected || place.isPending || cart.hasStockProblem} onClick={() => place.mutate(selected)}>
            {place.isPending ? 'Placing order…' : `Place order · ${money(cart.total)}`}
          </Button>
        </div>
      </div>
    </div>
  )
}
