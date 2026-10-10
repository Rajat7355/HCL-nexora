import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bot, MessageCircle, Send, X, Truck } from 'lucide-react'
import { api, errorMessage } from '../../api/client.js'
import { useAuth } from '../../shell/AuthContext.jsx'
import { Badge, Button, cx, dateOnly, money, StatusBadge } from '../../shell/ui.jsx'
import { AnswerCard } from '../knowledge/PolicyPage.jsx'

const SUGGESTIONS = [
  'Laptop under ₹100000 for programming',
  'What is the return window for laptops?',
  'Where is my order?',
]

function RecommendationList({ payload, onFollowUp }) {
  const recs = payload.recommendations || []
  return (
    <div className="mt-2 space-y-2">
      {payload.interpretedRequirement && <p className="text-xs text-slate-500">Understood: {payload.interpretedRequirement}</p>}
      {recs.map((r) => (
        <Link key={r.productId} to={`/products/${r.productId}`} className="flex gap-3 rounded-lg bg-white p-2 ring-1 ring-slate-200 hover:ring-brand-500">
          {r.imageUrl && <img src={r.imageUrl} alt="" className="h-14 w-14 shrink-0 rounded object-cover" />}
          <div className="min-w-0 text-left">
            <p className="truncate text-sm font-medium text-slate-900">{r.productName}</p>
            <p className="text-xs text-slate-600">{money(r.price)} · {r.availability === 'IN_STOCK' ? 'In stock' : r.availability?.replaceAll('_', ' ').toLowerCase()}</p>
            <p className="mt-0.5 text-xs text-slate-500">{r.reason}</p>
          </div>
        </Link>
      ))}
      {payload.clarifyingQuestion && (
        <button onClick={() => onFollowUp(payload.clarifyingQuestion)} className="text-left text-xs text-brand-700 underline-offset-2 hover:underline">
          {payload.clarifyingQuestion}
        </button>
      )}
    </div>
  )
}

function TrackingCard({ t }) {
  return (
    <div className="mt-2 rounded-lg bg-white p-3 text-left text-sm ring-1 ring-slate-200">
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5 font-medium"><Truck className="h-4 w-4" /> {t.orderNumber}</span>
        <StatusBadge status={t.shipmentStatus || t.orderStatus} />
      </div>
      {t.estimatedDelivery && <p className="mt-1 text-xs text-slate-600">Estimated delivery {dateOnly(t.estimatedDelivery)}</p>}
      {t.delayReason && <p className="mt-1 text-xs text-amber-700">Delayed: {t.delayReason}</p>}
    </div>
  )
}

function Bubble({ m, onFollowUp }) {
  const mine = m.role === 'user'
  const p = m.payload || {}
  return (
    <div className={cx('flex', mine ? 'justify-end' : 'justify-start')}>
      <div className={cx('max-w-[92%] rounded-2xl px-3 py-2 text-sm', mine ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-800')}>
        {p.policy ? <div className="-m-1 text-left"><AnswerCard answer={p.policy} /></div> : <p className="whitespace-pre-line text-left">{m.text}</p>}
        {p.recommendations && <RecommendationList payload={p} onFollowUp={onFollowUp} />}
        {p.tracking && <TrackingCard t={p.tracking} />}
        {p.ticket && <p className="mt-2"><Badge color="blue">Ticket #{p.ticket.id} · {p.ticket.team}</Badge></p>}
      </div>
    </div>
  )
}

export default function ChatWidget() {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [sessionId, setSessionId] = useState(null)
  const [messages, setMessages] = useState([])
  const end = useRef(null)

  useEffect(() => { end.current?.scrollIntoView({ block: 'end' }) }, [messages, busy, open])

  const send = async (raw) => {
    const message = raw.trim()
    if (!message || busy) return
    setMessages((m) => [...m, { role: 'user', text: message }])
    setText(''); setBusy(true)
    try {
      if (user) {
        const { data } = await api.post('/assistant/chat', { sessionId, message })
        setSessionId(data.sessionId)
        setMessages((m) => [...m, { role: 'assistant', text: data.text, payload: data.payload }])
      } else {
        const { data } = await api.post('/assistant/recommend', { query: message })
        const n = data.recommendations?.length || 0
        setMessages((m) => [...m, { role: 'assistant', text: n ? 'Here is what I found:' : 'I could not find a match. Could you tell me more?', payload: data }])
      }
    } catch (e) {
      setMessages((m) => [...m, { role: 'assistant', text: errorMessage(e) }])
    } finally { setBusy(false) }
  }

  useEffect(() => {
    const h = (e) => { setOpen(true); if (e.detail?.message) send(e.detail.message) }
    window.addEventListener('assistant:open', h)
    return () => window.removeEventListener('assistant:open', h)
  })
  useEffect(() => { setMessages([]); setSessionId(null) }, [user?.id])

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} aria-label="Open shopping assistant"
        className="fixed bottom-5 right-5 z-40 flex h-14 w-14 items-center justify-center rounded-full bg-brand-600 text-white shadow-lg hover:bg-brand-700">
        <MessageCircle className="h-6 w-6" />
      </button>
    )
  }
  return (
    <section aria-label="Shopping assistant" className="fixed bottom-5 right-5 z-40 flex h-[34rem] max-h-[85vh] w-[22rem] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-slate-200">
      <header className="flex items-center justify-between bg-brand-600 px-4 py-3 text-white">
        <span className="flex items-center gap-2 font-semibold"><Bot className="h-5 w-5" /> ShopSmart assistant</span>
        <button onClick={() => setOpen(false)} aria-label="Close assistant"><X className="h-5 w-5" /></button>
      </header>
      <div className="flex-1 space-y-3 overflow-y-auto p-3" aria-live="polite">
        {messages.length === 0 && (
          <div className="space-y-2 text-sm text-slate-600">
            <p>Hi! Ask me to find products, explain a policy{user ? ' or track an order' : ''}.</p>
            {!user && <p className="text-xs text-slate-500"><Link to="/login" className="text-brand-700 underline">Sign in</Link> for policy answers and order tracking.</p>}
            {SUGGESTIONS.filter((s) => user || !/order|return/i.test(s)).map((s) => (
              <button key={s} onClick={() => send(s)} className="block w-full rounded-lg bg-slate-50 px-3 py-2 text-left ring-1 ring-slate-200 hover:bg-brand-50">{s}</button>
            ))}
          </div>
        )}
        {messages.map((m, i) => <Bubble key={i} m={m} onFollowUp={setText} />)}
        {busy && <p className="text-xs text-slate-400">Thinking…</p>}
        <div ref={end} />
      </div>
      <form onSubmit={(e) => { e.preventDefault(); send(text) }} className="flex gap-2 border-t border-slate-200 p-3">
        <input value={text} onChange={(e) => setText(e.target.value)} maxLength={1000} aria-label="Message" placeholder="Type a message"
          className="min-w-0 flex-1 rounded-lg border-0 px-3 py-2 text-sm ring-1 ring-slate-300 focus:ring-2 focus:ring-brand-500" />
        <Button type="submit" size="sm" disabled={busy} aria-label="Send"><Send className="h-4 w-4" /></Button>
      </form>
    </section>
  )
}
