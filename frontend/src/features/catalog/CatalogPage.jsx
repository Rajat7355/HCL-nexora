import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { Sparkles, X } from 'lucide-react'
import { Button, Empty, ErrorBox, Pagination, Spinner, cx } from '../../shell/ui.jsx'
import { errorMessage } from '../../api/client.js'
import { fetchCategories, fetchFilters, fetchProducts } from './api.js'
import ProductCard from './ProductCard.jsx'

const SORTS = [
  ['', 'Best match'], ['rating', 'Top rated'], ['price_asc', 'Price: low to high'], ['price_desc', 'Price: high to low'], ['newest', 'Newest'],
]

export default function CatalogPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') || ''
  const category = params.get('category') || ''
  const brands = params.getAll('brand')
  const minPrice = params.get('minPrice') || ''
  const maxPrice = params.get('maxPrice') || ''
  const minRating = params.get('minRating') || ''
  const inStock = params.get('inStock') === 'true'
  const sort = params.get('sort') || ''
  const page = Number(params.get('page') || 0)

  const update = (changes) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => {
      next.delete(k)
      if (Array.isArray(v)) v.forEach((x) => next.append(k, x))
      else if (v !== '' && v != null && v !== false) next.set(k, v)
    })
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  const categories = useQuery({ queryKey: ['categories'], queryFn: fetchCategories, staleTime: 300_000 })
  const filters = useQuery({ queryKey: ['filters', category], queryFn: () => fetchFilters(category), staleTime: 60_000 })
  const products = useQuery({
    queryKey: ['products', { q, category, brands, minPrice, maxPrice, minRating, inStock, sort, page }],
    queryFn: () => fetchProducts({ q, category, brand: brands, minPrice, maxPrice, minRating, inStock: inStock || undefined, sort, page, size: 12 }),
    placeholderData: keepPreviousData,
  })

  const tree = useMemo(() => {
    const all = categories.data || []
    return all.filter((c) => !c.parentId).map((p) => ({ ...p, children: all.filter((c) => c.parentId === p.id) }))
  }, [categories.data])

  const anyFilter = q || category || brands.length || minPrice || maxPrice || minRating || inStock
  const data = products.data

  return (
    <div>
      {!anyFilter && (
        <section className="mb-8 overflow-hidden rounded-2xl bg-gradient-to-br from-brand-600 to-indigo-800 p-8 text-white md:p-12">
          <p className="mb-2 inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-medium"><Sparkles className="h-3.5 w-3.5" /> AI shopping assistant</p>
          <h1 className="max-w-2xl text-3xl font-bold tracking-tight md:text-4xl">Tell us what you need. We'll find the right product.</h1>
          <p className="mt-3 max-w-xl text-indigo-100">Try "a gaming laptop under ₹100000" or "noise cancelling headphones for travel". Every suggestion comes with a reason.</p>
          <Button variant="secondary" size="lg" className="mt-6" onClick={() => window.dispatchEvent(new CustomEvent('assistant:open', { detail: { message: '' } }))}>
            <Sparkles className="h-4 w-4 text-brand-600" /> Ask the assistant
          </Button>
        </section>
      )}

      <div className="grid gap-6 lg:grid-cols-[16rem_1fr]">
        <aside className="space-y-6 lg:sticky lg:top-24 lg:self-start" aria-label="Filters">
          <div>
            <h2 className="mb-2 text-sm font-semibold text-slate-900">Categories</h2>
            <ul className="space-y-1 text-sm">
              <li><button onClick={() => update({ category: '' })} className={cx('w-full rounded-md px-2 py-1.5 text-left', !category ? 'bg-brand-50 font-medium text-brand-700' : 'hover:bg-slate-100')}>All products</button></li>
              {tree.map((p) => (
                <li key={p.id}>
                  <button onClick={() => update({ category: p.slug })} className={cx('flex w-full justify-between rounded-md px-2 py-1.5 text-left', category === p.slug ? 'bg-brand-50 font-medium text-brand-700' : 'hover:bg-slate-100')}>
                    <span>{p.name}</span><span className="text-slate-400">{p.productCount}</span>
                  </button>
                  <ul className="ml-3 border-l border-slate-200 pl-2">
                    {p.children.map((c) => (
                      <li key={c.id}>
                        <button onClick={() => update({ category: c.slug })} className={cx('flex w-full justify-between rounded-md px-2 py-1 text-left', category === c.slug ? 'bg-brand-50 font-medium text-brand-700' : 'text-slate-600 hover:bg-slate-100')}>
                          <span>{c.name}</span><span className="text-slate-400">{c.productCount}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h2 className="mb-2 text-sm font-semibold text-slate-900">Price (₹)</h2>
            <div className="flex items-center gap-2">
              <input type="number" min="0" placeholder={filters.data?.price?.min ? Math.floor(filters.data.price.min) : 'Min'} defaultValue={minPrice} key={`min${minPrice}`}
                onBlur={(e) => e.target.value !== minPrice && update({ minPrice: e.target.value })} className="w-full rounded-md border-0 px-2 py-1.5 text-sm ring-1 ring-slate-300" aria-label="Minimum price" />
              <span className="text-slate-400">–</span>
              <input type="number" min="0" placeholder={filters.data?.price?.max ? Math.ceil(filters.data.price.max) : 'Max'} defaultValue={maxPrice} key={`max${maxPrice}`}
                onBlur={(e) => e.target.value !== maxPrice && update({ maxPrice: e.target.value })} className="w-full rounded-md border-0 px-2 py-1.5 text-sm ring-1 ring-slate-300" aria-label="Maximum price" />
            </div>
          </div>

          <div>
            <h2 className="mb-2 text-sm font-semibold text-slate-900">Brand</h2>
            <ul className="max-h-48 space-y-1 overflow-y-auto pr-1 text-sm">
              {(filters.data?.brands || []).map((b) => (
                <li key={b.slug}>
                  <label className="flex cursor-pointer items-center gap-2 rounded px-1 py-0.5 hover:bg-slate-100">
                    <input type="checkbox" checked={brands.includes(b.slug)} onChange={() => update({ brand: brands.includes(b.slug) ? brands.filter((x) => x !== b.slug) : [...brands, b.slug] })} className="rounded text-brand-600" />
                    <span className="flex-1">{b.name}</span><span className="text-slate-400">{b.count}</span>
                  </label>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h2 className="mb-2 text-sm font-semibold text-slate-900">Rating</h2>
            <div className="flex gap-2">
              {['', '4', '4.5'].map((r) => (
                <button key={r} onClick={() => update({ minRating: r })} className={cx('rounded-full px-3 py-1 text-sm ring-1', minRating === r ? 'bg-brand-600 text-white ring-brand-600' : 'bg-white ring-slate-300 hover:bg-slate-50')}>
                  {r ? `${r}+ ★` : 'Any'}
                </button>
              ))}
            </div>
          </div>

          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input type="checkbox" checked={inStock} onChange={(e) => update({ inStock: e.target.checked })} className="rounded text-brand-600" /> In stock only
          </label>
        </aside>

        <section aria-live="polite">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">{q ? `Results for "${q}"` : category ? (categories.data || []).find((c) => c.slug === category)?.name || 'Products' : 'Popular products'}</h2>
              <p className="text-sm text-slate-500">{data ? `${data.total} product${data.total === 1 ? '' : 's'}` : ''}</p>
            </div>
            <div className="flex items-center gap-2">
              {anyFilter && <Button variant="ghost" size="sm" onClick={() => setParams({})}><X className="h-4 w-4" /> Clear filters</Button>}
              <select value={sort} onChange={(e) => update({ sort: e.target.value })} className="rounded-lg border-0 py-1.5 pl-3 pr-8 text-sm ring-1 ring-slate-300" aria-label="Sort by">
                {SORTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            </div>
          </div>

          <ErrorBox>{products.isError && errorMessage(products.error, 'Could not load products.')}</ErrorBox>
          {products.isLoading && <div className="py-20"><Spinner label="Loading products" /></div>}
          {data && data.items.length === 0 && <Empty title="No products match your filters">Try removing a filter or searching for something else.</Empty>}
          {data && data.items.length > 0 && (
            <div className={cx('grid gap-4 sm:grid-cols-2 xl:grid-cols-3', products.isFetching && 'opacity-60 transition')}>
              {data.items.map((p) => <ProductCard key={p.productId} product={p} />)}
            </div>
          )}
          {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => { update({ page: p || '' }); window.scrollTo({ top: 0, behavior: 'smooth' }) }} />}
        </section>
      </div>
    </div>
  )
}
