import { useState } from 'react'
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { api, errorMessage } from '../../../api/client.js'
import { Badge, Button, Card, ErrorBox, PageHeader, Pagination, Spinner, inputClass } from '../../../shell/ui.jsx'

export default function InventoryPage() {
  const qc = useQueryClient()
  const [q, setQ] = useState('')
  const [lowOnly, setLowOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [amounts, setAmounts] = useState({})
  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-inventory', q, lowOnly, page],
    queryFn: () => api.get('/admin/inventory', { params: { q, lowOnly, page, size: 15 } }).then((r) => r.data),
    placeholderData: keepPreviousData,
  })
  const restock = useMutation({
    mutationFn: ({ sku, warehouseId, quantity }) => api.post('/admin/inventory/restock', { sku, warehouseId, quantity }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-inventory'] }),
  })

  return (
    <div>
      <PageHeader title="Inventory" subtitle="Stock per warehouse. Orders take stock automatically through a database trigger; restock here." />
      <Card className="mb-4 flex flex-wrap items-center gap-4">
        <input value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Search by name or SKU" className={`${inputClass} max-w-xs`} aria-label="Search inventory" />
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={lowOnly} onChange={(e) => { setLowOnly(e.target.checked); setPage(0) }} className="rounded text-brand-600" /> Low stock only</label>
      </Card>
      <ErrorBox>{error && errorMessage(error)}</ErrorBox>
      <ErrorBox>{restock.isError && errorMessage(restock.error)}</ErrorBox>
      {isLoading ? <Spinner /> : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[56rem] text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr><th className="p-3">Product</th><th className="p-3">Warehouse</th><th className="p-3 text-right">Available</th><th className="p-3 text-right">Reorder at</th><th className="p-3 text-right">Sold (30d)</th><th className="p-3 text-right">Days of cover</th><th className="p-3">Restock</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data?.items.map((r) => {
                const key = `${r.productId}-${r.warehouseId}`
                return (
                  <tr key={key}>
                    <td className="p-3"><span className="font-medium text-slate-900">{r.name}</span><span className="ml-2 text-xs text-slate-400">{r.productId}</span></td>
                    <td className="p-3 text-slate-600">{r.warehouse}</td>
                    <td className="p-3 text-right">{r.low ? <Badge color={r.available <= 0 ? 'red' : 'amber'}>{r.available}</Badge> : r.available}</td>
                    <td className="p-3 text-right text-slate-500">{r.reorderLevel}</td>
                    <td className="p-3 text-right text-slate-500">{r.unitsSold30d ?? 0}</td>
                    <td className="p-3 text-right text-slate-500">{r.daysOfCover ?? '—'}</td>
                    <td className="p-3">
                      <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); const n = Number(amounts[key]); if (n > 0) { restock.mutate({ sku: r.productId, warehouseId: r.warehouseId, quantity: n }); setAmounts({ ...amounts, [key]: '' }) } }}>
                        <input type="number" min="1" value={amounts[key] || ''} onChange={(e) => setAmounts({ ...amounts, [key]: e.target.value })} className="w-20 rounded-md border-0 px-2 py-1 text-sm ring-1 ring-slate-300" aria-label={`Restock quantity for ${r.name} at ${r.warehouse}`} placeholder="Qty" />
                        <Button size="sm" variant="secondary" type="submit" disabled={restock.isPending}>Add</Button>
                      </form>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </Card>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  )
}
