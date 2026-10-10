import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, errorMessage } from '../../../api/client.js'
import { Card, Empty, ErrorBox, PageHeader, Spinner, StatusBadge, dateTime, inputClass } from '../../../shell/ui.jsx'

function StatusSelect({ value, options, onChange, label }) {
  return (
    <select aria-label={label} value={value} onChange={(e) => onChange(e.target.value)} className={inputClass + ' w-auto'}>
      {options.map((o) => <option key={o} value={o}>{o.replaceAll('_', ' ')}</option>)}
    </select>
  )
}

export function AlertsPage() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['alerts'], queryFn: () => api.get('/admin/alerts').then((r) => r.data) })
  const patch = useMutation({ mutationFn: ({ id, status }) => api.patch(`/admin/alerts/${id}`, { status }), onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }) })
  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader title="Stock alerts" subtitle="Raised by the inventory agent from sales velocity and days of cover." />
      <ErrorBox>{(q.isError && errorMessage(q.error)) || (patch.isError && errorMessage(patch.error))}</ErrorBox>
      {q.isLoading && <Spinner />}
      {q.data?.length === 0 && <Empty title="No alerts">Run the inventory agent to scan stock.</Empty>}
      <div className="space-y-3">
        {q.data?.map((a) => (
          <Card key={a.id} className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="font-medium text-slate-900">{a.name} <span className="text-xs text-slate-400">{a.productId}</span> <StatusBadge status={a.severity} /></p>
              <p className="text-sm text-slate-600">{a.warehouse} · {a.daysOfCover != null ? `${a.daysOfCover} days of cover` : 'no sales velocity'}{a.predictedStockoutDate ? ` · stock-out ${a.predictedStockoutDate}` : ''}</p>
              <p className="text-sm text-slate-500">{a.recommendedAction}</p>
            </div>
            <StatusSelect label="Alert status" value={a.status} options={['OPEN', 'ACKNOWLEDGED', 'RESOLVED']} onChange={(status) => patch.mutate({ id: a.id, status })} />
          </Card>
        ))}
      </div>
    </div>
  )
}

export function TicketsPage() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['tickets'], queryFn: () => api.get('/admin/tickets').then((r) => r.data) })
  const patch = useMutation({ mutationFn: ({ id, status }) => api.patch(`/admin/tickets/${id}`, { status }), onSuccess: () => qc.invalidateQueries({ queryKey: ['tickets'] }) })
  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader title="Support tickets" subtitle="Classified and routed by the triage agent." />
      <ErrorBox>{(q.isError && errorMessage(q.error)) || (patch.isError && errorMessage(patch.error))}</ErrorBox>
      {q.isLoading && <Spinner />}
      {q.data?.length === 0 && <Empty title="No tickets" />}
      <div className="space-y-3">
        {q.data?.map((t) => (
          <Card key={t.id} className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="font-medium text-slate-900">#{t.id} {t.subject} <StatusBadge status={t.priority} /></p>
              <p className="text-sm text-slate-600">{t.category} → {t.team}{t.orderNumber ? ` · order ${t.orderNumber}` : ''} · {t.customerEmail}</p>
              <p className="mt-1 line-clamp-2 text-sm text-slate-500">{t.description}</p>
              <p className="text-xs text-slate-400">{dateTime(t.createdAt)}</p>
            </div>
            <StatusSelect label="Ticket status" value={t.status} options={['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']} onChange={(status) => patch.mutate({ id: t.id, status })} />
          </Card>
        ))}
      </div>
    </div>
  )
}
