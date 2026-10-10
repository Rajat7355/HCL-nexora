import { Link, useLocation, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Circle, PackageCheck, Truck } from 'lucide-react'
import { api, errorMessage } from '../../api/client.js'
import { Button, Card, Empty, ErrorBox, PageHeader, Spinner, StatusBadge, dateOnly, dateTime, money, cx } from '../../shell/ui.jsx'

const STEPS = ['PLACED', 'CONFIRMED', 'PACKED', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED']
const stepLabel = (s) => s.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())

export function OrdersPage() {
  const { data, isLoading, error } = useQuery({ queryKey: ['orders'], queryFn: () => api.get('/orders').then((r) => r.data), refetchInterval: 20_000 })
  return (
    <div>
      <PageHeader title="Your orders" subtitle="Delivery updates refresh automatically." />
      {isLoading && <Spinner />}
      <ErrorBox>{error && errorMessage(error)}</ErrorBox>
      {data && data.length === 0 && <Empty title="No orders yet"><Link to="/" className="text-brand-600 underline">Find something you like</Link></Empty>}
      <div className="space-y-3">
        {data?.map((o) => (
          <Card key={o.orderNumber}>
            <Link to={`/orders/${o.orderNumber}`} className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="font-semibold text-slate-900">{o.orderNumber}</p>
                <p className="truncate text-sm text-slate-500">{o.itemNames}</p>
                <p className="text-xs text-slate-400">Placed {dateOnly(o.placedAt)} · {o.itemCount} item{o.itemCount > 1 ? 's' : ''}</p>
              </div>
              <div className="flex items-center gap-3">
                {o.shipmentStatus === 'DELAYED' && o.status !== 'DELIVERED' && <StatusBadge status="DELAYED" />}
                <StatusBadge status={o.status} />
                <span className="font-bold">{money(o.total, o.currency)}</span>
              </div>
            </Link>
          </Card>
        ))}
      </div>
    </div>
  )
}

export function OrderDetailPage() {
  const { orderNumber } = useParams()
  const location = useLocation()
  const qc = useQueryClient()
  const { data: o, isLoading, error } = useQuery({
    queryKey: ['order', orderNumber],
    queryFn: () => api.get(`/orders/${orderNumber}`).then((r) => r.data),
    refetchInterval: 15_000,
  })
  const cancel = useMutation({
    mutationFn: () => api.post(`/orders/${orderNumber}/cancel`).then((r) => r.data),
    onSuccess: (data) => { qc.setQueryData(['order', orderNumber], data); qc.invalidateQueries({ queryKey: ['orders'] }) },
  })

  if (isLoading) return <div className="py-20"><Spinner /></div>
  if (error) return <ErrorBox>{errorMessage(error, 'Order not found.')}</ErrorBox>

  const t = o.tracking
  const reached = STEPS.indexOf(o.status)
  const cancelled = o.status === 'CANCELLED'

  return (
    <div>
      {location.state?.placed && (
        <div className="mb-6 flex items-center gap-3 rounded-xl bg-emerald-50 p-4 text-emerald-800 ring-1 ring-emerald-200" role="status">
          <PackageCheck className="h-6 w-6" /> <div><p className="font-semibold">Order placed. Thank you!</p><p className="text-sm">You can follow it below. Delivery updates are simulated for the demo.</p></div>
        </div>
      )}
      <PageHeader title={`Order ${o.orderNumber}`} subtitle={`Placed ${dateTime(o.placedAt)}`} actions={<StatusBadge status={o.status} />} />

      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-6">
          <Card>
            <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold"><Truck className="h-5 w-5 text-brand-600" /> Tracking</h2>
            {cancelled ? <p className="text-sm text-slate-600">This order was cancelled{o.cancelledAt ? ` on ${dateOnly(o.cancelledAt)}` : ''}. Stock was released and you will be refunded in full.</p> : (
              <>
                <ol className="mb-5 grid grid-cols-3 gap-y-4 sm:grid-cols-6" aria-label="Order progress">
                  {STEPS.map((s, i) => (
                    <li key={s} className="flex flex-col items-center gap-1 text-center text-xs">
                      {i <= reached ? <CheckCircle2 className="h-6 w-6 text-emerald-500" aria-hidden /> : <Circle className="h-6 w-6 text-slate-300" aria-hidden />}
                      <span className={cx(i <= reached ? 'font-medium text-slate-800' : 'text-slate-400')}>{stepLabel(s)}</span>
                    </li>
                  ))}
                </ol>
                {t?.delayReason && <div className="mb-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-800 ring-1 ring-amber-200"><b>Delayed:</b> {t.delayReason}. New estimate {dateOnly(t.estimatedDelivery)}.</div>}
                {t?.carrier && <p className="mb-4 text-sm text-slate-600">{t.carrier} · tracking <span className="font-mono">{t.trackingNumber}</span>{t.estimatedDelivery && o.status !== 'DELIVERED' && <> · estimated delivery <b>{dateOnly(t.estimatedDelivery)}</b></>}</p>}
              </>
            )}
            <ul className="space-y-3 border-l-2 border-slate-200 pl-4">
              {(t?.events || []).map((e, i) => (
                <li key={i} className="relative text-sm">
                  <span className={cx('absolute -left-[1.4rem] top-1.5 h-2.5 w-2.5 rounded-full', i === 0 ? 'bg-brand-600' : 'bg-slate-300')} />
                  <p className="font-medium text-slate-800">{e.description}</p>
                  <p className="text-xs text-slate-400">{dateTime(e.occurredAt)}{e.location ? ` · ${e.location}` : ''}</p>
                </li>
              ))}
            </ul>
          </Card>

          <Card>
            <h2 className="mb-3 text-lg font-semibold">Items</h2>
            <ul className="divide-y divide-slate-100 text-sm">
              {o.items.map((i) => (
                <li key={i.productId} className="flex justify-between py-2"><Link to={`/products/${i.productId}`} className="hover:text-brand-600">{i.quantity} × {i.name}</Link><span>{money(i.lineTotal)}</span></li>
              ))}
            </ul>
          </Card>
        </div>

        <div className="space-y-4">
          <Card>
            <h2 className="mb-3 text-lg font-semibold">Summary</h2>
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between"><dt className="text-slate-500">Subtotal</dt><dd>{money(o.subtotal)}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-500">Shipping</dt><dd>{Number(o.shippingFee) === 0 ? 'Free' : money(o.shippingFee)}</dd></div>
              {Number(o.discount) > 0 && <div className="flex justify-between text-emerald-700"><dt>Discount</dt><dd>-{money(o.discount)}</dd></div>}
              <div className="flex justify-between border-t border-slate-200 pt-2 font-bold"><dt>Total</dt><dd>{money(o.total)}</dd></div>
            </dl>
            {o.shippingAddress && <p className="mt-4 text-xs text-slate-500"><b className="text-slate-600">Ship to</b><br />{o.shippingAddress.full_name}, {o.shippingAddress.line1}, {o.shippingAddress.city} {o.shippingAddress.postal_code}</p>}
          </Card>
          {o.cancellable ? (
            <>
              <ErrorBox>{cancel.isError && errorMessage(cancel.error)}</ErrorBox>
              <Button variant="danger" className="w-full" disabled={cancel.isPending} onClick={() => window.confirm('Cancel this order?') && cancel.mutate()}>Cancel order</Button>
            </>
          ) : !cancelled && o.status !== 'DELIVERED' && <p className="text-xs text-slate-500">This order has shipped, so it can no longer be cancelled. You can return it after delivery.</p>}
          <button className="w-full rounded-lg px-3 py-2 text-sm text-brand-700 ring-1 ring-brand-100 hover:bg-brand-50" onClick={() => window.dispatchEvent(new CustomEvent('assistant:open', { detail: { message: `Where is my order ${o.orderNumber}?` } }))}>
            Ask the assistant about this order
          </button>
        </div>
      </div>
    </div>
  )
}
