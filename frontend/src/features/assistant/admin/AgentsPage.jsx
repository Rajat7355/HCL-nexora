import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, ChevronDown, ChevronRight, Play, X } from 'lucide-react'
import { api, errorMessage } from '../../../api/client.js'
import { Button, Card, Empty, ErrorBox, PageHeader, Spinner, StatusBadge, dateTime } from '../../../shell/ui.jsx'

function Steps({ id }) {
  const { data, isLoading } = useQuery({ queryKey: ['agent-run', id], queryFn: () => api.get(`/admin/agents/runs/${id}`).then((r) => r.data) })
  if (isLoading) return <Spinner />
  const steps = data?.steps || []
  return (
    <ol className="mt-3 space-y-2 border-l-2 border-slate-200 pl-4">
      {steps.map((s) => (
        <li key={s.stepNo} className="text-sm">
          <p className="font-medium text-slate-800">{s.stepNo}. {s.tool} <StatusBadge status={s.status} /></p>
          {s.reasoning && <p className="text-slate-600">{s.reasoning}</p>}
          {s.output && <pre className="mt-1 max-h-40 overflow-auto rounded bg-slate-50 p-2 text-xs text-slate-600">{s.output}</pre>}
        </li>
      ))}
      {steps.length === 0 && <li className="text-sm text-slate-500">No steps recorded.</li>}
    </ol>
  )
}

export default function AgentsPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(null)
  const runs = useQuery({ queryKey: ['agent-runs'], queryFn: () => api.get('/admin/agents/runs').then((r) => r.data), refetchInterval: 8000 })
  const refresh = () => qc.invalidateQueries({ queryKey: ['agent-runs'] })
  const start = useMutation({ mutationFn: (t) => api.post(`/admin/agents/${t}/run`), onSuccess: refresh })
  const decide = useMutation({ mutationFn: ({ id, ok }) => api.post(`/admin/agents/runs/${id}/${ok ? 'approve' : 'reject'}`), onSuccess: () => { refresh(); qc.invalidateQueries({ queryKey: ['agent-run'] }) } })

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader title="Ops agents" subtitle="Autonomous agents with allow-listed tools. Every step is logged; risky actions wait for your approval."
        actions={<div className="flex gap-2">
          <Button variant="secondary" onClick={() => start.mutate('inventory')} disabled={start.isPending}><Play className="h-4 w-4" /> Run inventory agent</Button>
          <Button variant="secondary" onClick={() => start.mutate('fulfillment')} disabled={start.isPending}><Play className="h-4 w-4" /> Run fulfilment agent</Button>
        </div>} />
      <ErrorBox>{(start.isError && errorMessage(start.error)) || (decide.isError && errorMessage(decide.error)) || (runs.isError && errorMessage(runs.error))}</ErrorBox>
      {runs.isLoading && <Spinner />}
      {runs.data?.length === 0 && <Empty title="No runs yet">Start an agent above, or wait for the scheduler.</Empty>}
      <div className="mt-4 space-y-3">
        {runs.data?.map((r) => (
          <Card key={r.id}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <button className="flex items-start gap-2 text-left" onClick={() => setOpen(open === r.id ? null : r.id)} aria-expanded={open === r.id}>
                {open === r.id ? <ChevronDown className="mt-1 h-4 w-4" /> : <ChevronRight className="mt-1 h-4 w-4" />}
                <span>
                  <span className="font-medium text-slate-900">#{r.id} · {r.agentType}</span> <StatusBadge status={r.status} />
                  <span className="block text-sm text-slate-600">{r.summary || r.goal}</span>
                  <span className="block text-xs text-slate-400">{r.trigger} · {dateTime(r.startedAt)} · {r.stepCount} steps</span>
                </span>
              </button>
              {r.status === 'AWAITING_APPROVAL' && (
                <div className="flex gap-2">
                  <Button size="sm" onClick={() => decide.mutate({ id: r.id, ok: true })} disabled={decide.isPending}><Check className="h-4 w-4" /> Approve</Button>
                  <Button size="sm" variant="secondary" onClick={() => decide.mutate({ id: r.id, ok: false })} disabled={decide.isPending}><X className="h-4 w-4" /> Reject</Button>
                </div>
              )}
            </div>
            {open === r.id && <Steps id={r.id} />}
          </Card>
        ))}
      </div>
    </div>
  )
}
