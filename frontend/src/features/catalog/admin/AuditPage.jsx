import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { api, errorMessage } from '../../../api/client.js'
import { Button, Card, ErrorBox, PageHeader, Pagination, Spinner, StatusBadge, dateTime, inputClass } from '../../../shell/ui.jsx'

const TASKS = ['', 'SHOPPING_RECOMMENDATION', 'RAG_ANSWER', 'ORDER_TRACKING', 'INTENT_ROUTING', 'AGENT_DECISION']

function Json({ title, value }) {
  if (value == null) return null
  return (
    <section className="mb-4">
      <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</h3>
      <pre className="max-h-72 overflow-auto rounded-lg bg-slate-900 p-3 text-xs leading-relaxed text-slate-100">{JSON.stringify(value, null, 2)}</pre>
    </section>
  )
}

export default function AuditPage() {
  const [taskType, setTaskType] = useState('')
  const [status, setStatus] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState(null)

  const list = useQuery({
    queryKey: ['audit', taskType, status, from, to, page],
    queryFn: () => api.get('/admin/audit', { params: { taskType, status, from, to, page, size: 15 } }).then((r) => r.data),
    placeholderData: keepPreviousData,
  })
  const detail = useQuery({ queryKey: ['audit-detail', selected], queryFn: () => api.get(`/admin/audit/${selected}`).then((r) => r.data), enabled: selected != null })

  return (
    <div>
      <PageHeader title="AI audit log" subtitle="Why did the AI answer that? Every recommendation, policy answer and agent decision is stored with its input, context and output." />
      <Card className="mb-4 flex flex-wrap items-end gap-3">
        <label className="text-sm">Task<select value={taskType} onChange={(e) => { setTaskType(e.target.value); setPage(0) }} className={`${inputClass} mt-1`}>{TASKS.map((t) => <option key={t} value={t}>{t ? t.replaceAll('_', ' ').toLowerCase() : 'All tasks'}</option>)}</select></label>
        <label className="text-sm">Outcome<select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className={`${inputClass} mt-1`}>{['', 'OK', 'REFUSED', 'ERROR'].map((t) => <option key={t} value={t}>{t || 'All'}</option>)}</select></label>
        <label className="text-sm">From<input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0) }} className={`${inputClass} mt-1`} /></label>
        <label className="text-sm">To<input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0) }} className={`${inputClass} mt-1`} /></label>
      </Card>
      <ErrorBox>{list.error && errorMessage(list.error)}</ErrorBox>
      {list.isLoading ? <Spinner /> : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[52rem] text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500"><tr><th className="p-3">When</th><th className="p-3">Task</th><th className="p-3">Input</th><th className="p-3">Confidence</th><th className="p-3">Outcome</th><th className="p-3">Model</th><th className="p-3" /></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {list.data?.items.map((r) => (
                <tr key={r.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap p-3 text-slate-500">{dateTime(r.createdAt)}</td>
                  <td className="p-3 font-medium">{r.taskType.replaceAll('_', ' ').toLowerCase()}</td>
                  <td className="max-w-xs truncate p-3 text-slate-600">{r.input}</td>
                  <td className="p-3">{r.confidence ? <StatusBadge status={r.confidence === 'HIGH' ? 'OK' : r.confidence === 'LOW' ? 'REFUSED' : 'MEDIUM'} /> : '—'} <span className="text-xs text-slate-400">{r.confidence}</span></td>
                  <td className="p-3"><StatusBadge status={r.status} /></td>
                  <td className="p-3 text-slate-500">{r.model || '—'}</td>
                  <td className="p-3 text-right"><Button size="sm" variant="secondary" onClick={() => setSelected(r.id)}>Explain</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
      {list.data && <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}

      {selected != null && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/40" onClick={() => setSelected(null)}>
          <aside className="h-full w-full max-w-xl overflow-y-auto bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()} aria-label="Audit detail">
            <div className="mb-4 flex items-start justify-between">
              <div><h2 className="text-lg font-semibold">Decision #{selected}</h2>{detail.data && <p className="text-sm text-slate-500">{detail.data.taskType.replaceAll('_', ' ').toLowerCase()} · {dateTime(detail.data.createdAt)} · {detail.data.latencyMs} ms · {detail.data.model}</p>}</div>
              <button onClick={() => setSelected(null)} aria-label="Close" className="rounded p-1 hover:bg-slate-100"><X className="h-5 w-5" /></button>
            </div>
            {detail.isLoading && <Spinner />}
            {detail.data && (<>
              <section className="mb-4"><h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Input</h3><p className="rounded-lg bg-slate-50 p-3 text-sm">{detail.data.input}</p></section>
              <Json title="How the request was interpreted" value={detail.data.interpreted} />
              <Json title="Retrieved context (what the answer was based on)" value={detail.data.retrievedContext} />
              <Json title="Output" value={detail.data.output} />
              {detail.data.error && <ErrorBox>{detail.data.error}</ErrorBox>}
            </>)}
          </aside>
        </div>
      )}
    </div>
  )
}
