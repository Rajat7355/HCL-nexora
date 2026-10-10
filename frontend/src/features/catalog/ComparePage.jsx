import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Empty, ErrorBox, PageHeader, Rating, Spinner, AvailabilityBadge, money } from '../../shell/ui.jsx'
import { errorMessage } from '../../api/client.js'
import { fetchCompare } from './api.js'
import { compareStore } from './compareStore.js'
import { useAddToCart } from './ProductCard.jsx'

const label = (k) => k.replaceAll('_', ' ').replace(/^\w/, (c) => c.toUpperCase())
const show = (v) => (v === undefined || v === null ? '—' : v === true ? 'Yes' : v === false ? 'No' : String(v))

export default function ComparePage() {
  const [params] = useSearchParams()
  const skus = (params.get('skus') || '').split(',').filter(Boolean)
  const cart = useAddToCart()
  const { data, isLoading, error } = useQuery({ queryKey: ['compare', skus.join(',')], queryFn: () => fetchCompare(skus), enabled: skus.length >= 2 })

  if (skus.length < 2) return <Empty title="Pick at least two products to compare">Use the compare button on any product card. <Link className="text-brand-600 underline" to="/">Browse products</Link></Empty>
  if (isLoading) return <div className="py-24"><Spinner /></div>
  if (error) return <ErrorBox>{errorMessage(error)}</ErrorBox>

  const products = data.products
  const cheapest = Math.min(...products.map((p) => Number(p.price)))
  return (
    <div>
      <PageHeader title="Compare products" subtitle="Differences are highlighted. The best price is marked." actions={<Button variant="secondary" onClick={compareStore.clear}>Clear tray</Button>} />
      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[40rem] text-sm">
          <thead>
            <tr className="border-b border-slate-200 align-top">
              <th className="w-40 p-4 text-left font-medium text-slate-500" scope="col">Product</th>
              {products.map((p) => (
                <th key={p.productId} className="p-4 text-left" scope="col">
                  <img src={p.imageUrl} alt="" className="mb-3 aspect-[4/3] w-full rounded-lg object-cover" />
                  <Link to={`/products/${p.productId}`} className="font-semibold text-slate-900 hover:text-brand-600">{p.name}</Link>
                  <p className="text-xs font-normal text-slate-400">{p.brand}</p>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            <tr><th className="p-4 text-left font-medium text-slate-500" scope="row">Price</th>{products.map((p) => <td key={p.productId} className="p-4 text-lg font-bold">{money(p.price)}{Number(p.price) === cheapest && <span className="ml-2 rounded bg-emerald-50 px-1.5 py-0.5 text-xs font-medium text-emerald-700">Lowest</span>}</td>)}</tr>
            <tr><th className="p-4 text-left font-medium text-slate-500" scope="row">Rating</th>{products.map((p) => <td key={p.productId} className="p-4"><Rating value={p.rating} count={p.ratingCount} /></td>)}</tr>
            <tr><th className="p-4 text-left font-medium text-slate-500" scope="row">Availability</th>{products.map((p) => <td key={p.productId} className="p-4"><AvailabilityBadge availability={p.availability} qty={p.availableQty} /></td>)}</tr>
            {data.specKeys.map((k) => {
              const values = products.map((p) => p.specs?.[k])
              const differs = new Set(values.map((v) => JSON.stringify(v))).size > 1
              return (
                <tr key={k} className={differs ? 'bg-amber-50/50' : ''}>
                  <th className="p-4 text-left font-medium text-slate-500" scope="row">{label(k)}</th>
                  {values.map((v, i) => <td key={i} className={differs ? 'p-4 font-medium text-slate-900' : 'p-4 text-slate-600'}>{show(v)}</td>)}
                </tr>
              )
            })}
            <tr><th className="p-4" scope="row"><span className="sr-only">Actions</span></th>{products.map((p) => <td key={p.productId} className="p-4"><Button size="sm" disabled={p.availability === 'OUT_OF_STOCK'} onClick={() => cart.add(p.productId, 1)}>Add to cart</Button></td>)}</tr>
          </tbody>
        </table>
      </Card>
    </div>
  )
}
