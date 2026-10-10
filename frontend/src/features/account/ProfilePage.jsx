import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { api, errorMessage } from '../../api/client.js'
import { Badge, Button, Card, ErrorBox, Field, PageHeader, Spinner, inputClass, money } from '../../shell/ui.jsx'
import { useAuth } from '../../shell/AuthContext.jsx'

const TIERS = {
  BASIC: 'Free standard shipping on orders of ₹9,686.72 or more, 30 day returns.',
  PRIME: 'Free standard shipping on every order, free return pickup, late-delivery coupons.',
  PREMIUM: 'Free express shipping, 5% off every order, 24 month laptop and phone warranty, priority support.',
}
const PLANS = [
  { tier: 'BASIC', price: null, description: 'Free standard shipping on orders of ₹9,686.72 or more and 30-day returns.' },
  { tier: 'PRIME', price: 4746.49, description: 'Free standard shipping, free return pickup, late-delivery coupons and early sale access.' },
  { tier: 'PREMIUM', price: 9589.85, description: 'Free express shipping, 5% off orders, extended electronics warranty and priority support.' },
]

function Preferences() {
  const qc = useQueryClient()
  const prefs = useQuery({ queryKey: ['prefs'], queryFn: () => api.get('/me/preferences').then((r) => r.data) })
  const cats = useQuery({ queryKey: ['categories'], queryFn: () => api.get('/categories').then((r) => r.data), staleTime: 300_000 })
  const [form, setForm] = useState(null)
  useEffect(() => { if (prefs.data) setForm({ ...prefs.data, budgetMin: prefs.data.budgetMin ?? '', budgetMax: prefs.data.budgetMax ?? '' }) }, [prefs.data])
  const save = useMutation({
    mutationFn: (f) => api.put('/me/preferences', { ...f, budgetMin: f.budgetMin === '' ? null : Number(f.budgetMin), budgetMax: f.budgetMax === '' ? null : Number(f.budgetMax) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['prefs'] }),
  })
  if (!form) return <Spinner />
  const leafs = (cats.data || []).filter((c) => c.parentId)
  const toggleCat = (slug) => setForm({ ...form, categories: form.categories.includes(slug) ? form.categories.filter((s) => s !== slug) : [...form.categories, slug] })

  return (
    <Card>
      <h2 className="mb-1 text-lg font-semibold">Shopping preferences</h2>
      <p className="mb-4 text-sm text-slate-500">The AI assistant uses these to tailor recommendations.</p>
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); save.mutate(form) }}>
        <ErrorBox>{save.isError && errorMessage(save.error)}</ErrorBox>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Budget from (₹)"><input type="number" min="0" className={inputClass} value={form.budgetMin} onChange={(e) => setForm({ ...form, budgetMin: e.target.value })} /></Field>
          <Field label="Budget up to (₹)"><input type="number" min="0" className={inputClass} value={form.budgetMax} onChange={(e) => setForm({ ...form, budgetMax: e.target.value })} /></Field>
        </div>
        <Field label="Delivery">
          <select className={inputClass} value={form.deliveryPreference} onChange={(e) => setForm({ ...form, deliveryPreference: e.target.value })}><option value="STANDARD">Standard</option><option value="EXPRESS">Express</option></select>
        </Field>
        <fieldset>
          <legend className="mb-1 text-sm font-medium text-slate-700">Favourite categories</legend>
          <div className="flex flex-wrap gap-2">
            {leafs.map((c) => (
              <label key={c.slug} className={`cursor-pointer rounded-full px-3 py-1 text-sm ring-1 ${form.categories.includes(c.slug) ? 'bg-brand-600 text-white ring-brand-600' : 'bg-white ring-slate-300'}`}>
                <input type="checkbox" className="sr-only" checked={form.categories.includes(c.slug)} onChange={() => toggleCat(c.slug)} />{c.name}
              </label>
            ))}
          </div>
        </fieldset>
        <Field label="Notes for the assistant"><textarea rows={2} className={inputClass} value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} placeholder="e.g. I prefer lightweight laptops" /></Field>
        <div className="flex items-center gap-3"><Button type="submit" disabled={save.isPending}>Save preferences</Button>{save.isSuccess && <span className="text-sm text-emerald-600">Saved</span>}</div>
      </form>
    </Card>
  )
}

function Addresses() {
  const qc = useQueryClient()
  const list = useQuery({ queryKey: ['addresses'], queryFn: () => api.get('/me/addresses').then((r) => r.data) })
  const empty = { fullName: '', phone: '', line1: '', city: '', state: '', postalCode: '', country: 'IN' }
  const [form, setForm] = useState(empty)
  const add = useMutation({
    mutationFn: (f) => api.post('/me/addresses', f),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['addresses'] }); setForm(empty) },
  })
  const remove = useMutation({ mutationFn: (id) => api.delete(`/me/addresses/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }) })
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  return (
    <Card>
      <h2 className="mb-4 text-lg font-semibold">Delivery addresses</h2>
      {list.isLoading && <Spinner />}
      <ul className="mb-4 space-y-2">
        {(list.data || []).map((a) => (
          <li key={a.id} className="flex items-start justify-between rounded-lg bg-slate-50 p-3 text-sm">
            <div><p className="font-medium">{a.fullName} {a.isDefault && <Badge color="indigo">Default</Badge>}</p><p className="text-slate-500">{a.line1}, {a.city} {a.postalCode}, {a.country}</p></div>
            <button onClick={() => remove.mutate(a.id)} className="rounded p-1 text-slate-400 hover:bg-white hover:text-red-600" aria-label="Delete address"><Trash2 className="h-4 w-4" /></button>
          </li>
        ))}
      </ul>
      <form className="grid grid-cols-2 gap-3" onSubmit={(e) => { e.preventDefault(); add.mutate(form) }}>
        <div className="col-span-2"><ErrorBox>{add.isError && errorMessage(add.error)}</ErrorBox></div>
        <Field label="Full name"><input required className={inputClass} value={form.fullName} onChange={set('fullName')} /></Field>
        <Field label="Phone"><input className={inputClass} value={form.phone} onChange={set('phone')} /></Field>
        <div className="col-span-2"><Field label="Address"><input required className={inputClass} value={form.line1} onChange={set('line1')} /></Field></div>
        <Field label="City"><input required className={inputClass} value={form.city} onChange={set('city')} /></Field>
        <Field label="State"><input className={inputClass} value={form.state} onChange={set('state')} /></Field>
        <Field label="Postal code"><input required className={inputClass} value={form.postalCode} onChange={set('postalCode')} /></Field>
        <Field label="Country (2 letters)"><input required maxLength={2} className={inputClass} value={form.country} onChange={set('country')} /></Field>
        <div className="col-span-2"><Button type="submit" variant="secondary" disabled={add.isPending}>Add address</Button></div>
      </form>
    </Card>
  )
}

export default function ProfilePage() {
  const { user, updateUser } = useAuth()
  const qc = useQueryClient()
  const membership = useMutation({
    mutationFn: (tier) => api.put('/me/tier', { tier }).then((r) => r.data),
    onSuccess: (updatedUser) => {
      updateUser(updatedUser)
      qc.invalidateQueries()
    },
  })
  return (
    <div>
      <PageHeader title={`Hi ${user.firstName}`} subtitle={user.email} />
      <Card className="mb-6 flex flex-wrap items-center gap-3 bg-gradient-to-r from-brand-50 to-white">
        <Badge color="indigo">{user.tier} member</Badge>
        <p className="text-sm text-slate-600">{TIERS[user.tier]}</p>
      </Card>
      <Card className="mb-6">
        <h2 className="mb-1 text-lg font-semibold">Membership plans</h2>
        <p className="mb-4 text-sm text-slate-500">Choose the benefits that fit you. Membership changes apply immediately; no payment is collected in this version.</p>
        <ErrorBox>{membership.isError && errorMessage(membership.error)}</ErrorBox>
        <div className="grid gap-4 md:grid-cols-3">
          {PLANS.map((plan) => (
            <div key={plan.tier} className="flex flex-col rounded-xl border border-slate-200 p-4">
              <div className="flex items-center justify-between gap-2">
                <h3 className="font-semibold">{plan.tier}</h3>
                {user.tier === plan.tier && <Badge color="green">Current plan</Badge>}
              </div>
              <p className="mt-2 text-xl font-bold">{plan.price == null ? 'Free' : `${money(plan.price)} / year`}</p>
              <p className="mt-2 flex-1 text-sm text-slate-600">{plan.description}</p>
              <Button className="mt-4 w-full" variant={user.tier === plan.tier ? 'secondary' : 'primary'}
                disabled={user.tier === plan.tier || membership.isPending}
                onClick={() => membership.mutate(plan.tier)}>
                {user.tier === plan.tier ? 'Selected' : `Choose ${plan.tier}`}
              </Button>
            </div>
          ))}
        </div>
      </Card>
      <div className="grid gap-6 lg:grid-cols-2"><Preferences /><Addresses /></div>
    </div>
  )
}
