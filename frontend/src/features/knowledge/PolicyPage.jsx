import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { FileText, HelpCircle, Send, ShieldCheck } from 'lucide-react'
import { api, errorMessage } from '../../api/client.js'
import { Badge, Button, Card, ErrorBox, PageHeader, Spinner, inputClass } from '../../shell/ui.jsx'

const EXAMPLES = [
  'What is the return window for laptops?',
  'Can I cancel my order after it has shipped?',
  'How long do refunds take?',
  'What does Premium membership include?',
  'What happens if my delivery is late?',
]
const confidenceColor = { HIGH: 'green', MEDIUM: 'amber', LOW: 'red' }

export function AnswerCard({ answer }) {
  const refused = answer.sources.length === 0
  return (
    <Card className={refused ? 'ring-amber-200' : ''}>
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <Badge color={confidenceColor[answer.confidence]}>{answer.confidence} confidence</Badge>
        {refused && <Badge color="amber">Not in our documents</Badge>}
        {answer.followUpRequired && !refused && <Badge color="blue">May need a follow-up</Badge>}
      </div>
      <p className="whitespace-pre-line text-slate-800">{answer.answer}</p>
      {answer.sources.length > 0 && (
        <div className="mt-4 border-t border-slate-100 pt-3">
          <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500"><ShieldCheck className="h-3.5 w-3.5" /> Sources</p>
          <ul className="flex flex-wrap gap-2">
            {answer.sources.map((s, i) => (
              <li key={i} className="inline-flex items-center gap-1.5 rounded-full bg-slate-50 px-3 py-1 text-xs text-slate-600 ring-1 ring-slate-200"><FileText className="h-3.5 w-3.5 text-slate-400" /> {s.document} · {s.section} · v{s.version}</li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  )
}

export default function PolicyPage() {
  const [question, setQuestion] = useState('')
  const [asked, setAsked] = useState('')
  const ask = useMutation({ mutationFn: (q) => api.post('/knowledge/ask', { question: q }).then((r) => r.data) })
  const submit = (q) => { if (q.trim().length < 3) return; setAsked(q); ask.mutate(q) }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Help centre" subtitle="Ask about returns, shipping, refunds, warranty or membership. Answers come only from our policy documents, with sources." />
      <form onSubmit={(e) => { e.preventDefault(); submit(question) }} className="flex gap-2">
        <input value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="e.g. Can I return opened headphones?" className={inputClass} aria-label="Your question" maxLength={1000} />
        <Button type="submit" disabled={ask.isPending}><Send className="h-4 w-4" /> Ask</Button>
      </form>
      <div className="mt-3 flex flex-wrap gap-2">
        {EXAMPLES.map((e) => <button key={e} onClick={() => { setQuestion(e); submit(e) }} className="rounded-full bg-white px-3 py-1 text-xs text-slate-600 ring-1 ring-slate-300 hover:bg-brand-50">{e}</button>)}
      </div>
      <div className="mt-6 space-y-3" aria-live="polite">
        {ask.isPending && <Spinner label="Searching policies" />}
        <ErrorBox>{ask.isError && errorMessage(ask.error)}</ErrorBox>
        {ask.data && (<>
          <p className="flex items-center gap-2 text-sm text-slate-500"><HelpCircle className="h-4 w-4" /> {asked}</p>
          <AnswerCard answer={ask.data} />
        </>)}
      </div>
    </div>
  )
}
