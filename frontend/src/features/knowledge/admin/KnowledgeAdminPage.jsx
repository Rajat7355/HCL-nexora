import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Trash2, UploadCloud } from 'lucide-react'
import { api, errorMessage } from '../../../api/client.js'
import { Button, Card, ErrorBox, Field, PageHeader, Spinner, StatusBadge, dateOnly, inputClass } from '../../../shell/ui.jsx'

const CATEGORIES = ['RETURN', 'SHIPPING', 'REFUND', 'WARRANTY', 'MEMBERSHIP', 'PRODUCT_MANUAL', 'SELLER_GUIDELINE', 'DELIVERY_SLA']
const today = () => new Date().toISOString().slice(0, 10)

function UploadForm() {
  const qc = useQueryClient()
  const [form, setForm] = useState({ title: '', category: 'RETURN', region: 'GLOBAL', version: '1.0', effectiveDate: today(), expiryDate: '', productSku: '' })
  const [file, setFile] = useState(null)
  const upload = useMutation({
    mutationFn: () => {
      const data = new FormData()
      data.append('file', file)
      Object.entries(form).forEach(([k, v]) => v && data.append(k, v))
      return api.post('/admin/knowledge/documents', data)
    },
    onSuccess: () => { setFile(null); qc.invalidateQueries({ queryKey: ['kb-docs'] }) },
  })
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  return (
    <Card>
      <h2 className="mb-1 text-lg font-semibold">Upload a document</h2>
      <p className="mb-4 text-sm text-slate-500">PDF, Markdown or JSON, up to 10 MB. It is chunked, embedded and searchable within seconds.</p>
      <form className="grid gap-3 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); if (file) upload.mutate() }}>
        <div className="sm:col-span-2"><ErrorBox>{upload.isError && errorMessage(upload.error)}</ErrorBox></div>
        <div className="sm:col-span-2"><Field label="File"><input type="file" accept=".pdf,.md,.markdown,.json" required onChange={(e) => setFile(e.target.files[0])} className={inputClass} /></Field></div>
        <Field label="Title" hint="Same title + region = new version of the same document"><input required className={inputClass} value={form.title} onChange={set('title')} /></Field>
        <Field label="Category"><select className={inputClass} value={form.category} onChange={set('category')}>{CATEGORIES.map((c) => <option key={c} value={c}>{c.replaceAll('_', ' ').toLowerCase()}</option>)}</select></Field>
        <Field label="Region"><input className={inputClass} value={form.region} onChange={set('region')} placeholder="GLOBAL, IN, US…" /></Field>
        <Field label="Version"><input required className={inputClass} value={form.version} onChange={set('version')} /></Field>
        <Field label="Effective from"><input type="date" required className={inputClass} value={form.effectiveDate} onChange={set('effectiveDate')} /></Field>
        <Field label="Expires (optional)"><input type="date" className={inputClass} value={form.expiryDate} onChange={set('expiryDate')} /></Field>
        {form.category === 'PRODUCT_MANUAL' && <Field label="Product SKU (manuals)"><input className={inputClass} value={form.productSku} onChange={set('productSku')} placeholder="LAP101" /></Field>}
        <div className="sm:col-span-2"><Button type="submit" disabled={!file || upload.isPending}><UploadCloud className="h-4 w-4" /> {upload.isPending ? 'Uploading…' : 'Upload and index'}</Button></div>
      </form>
    </Card>
  )
}

export default function KnowledgeAdminPage() {
  const qc = useQueryClient()
  const docs = useQuery({
    queryKey: ['kb-docs'],
    queryFn: () => api.get('/admin/knowledge/documents').then((r) => r.data),
    refetchInterval: (q) => (q.state.data?.some((d) => d.status === 'PROCESSING') ? 2000 : false),
  })
  const patch = useMutation({ mutationFn: ({ id, status }) => api.patch(`/admin/knowledge/documents/${id}`, { status }), onSuccess: () => qc.invalidateQueries({ queryKey: ['kb-docs'] }) })
  const remove = useMutation({ mutationFn: (id) => api.delete(`/admin/knowledge/documents/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['kb-docs'] }) })

  return (
    <div className="space-y-6">
      <PageHeader title="Knowledge base" subtitle="Policy documents that power the grounded Q&A. Newer versions replace older ones automatically." />
      <UploadForm />
      <ErrorBox>{(patch.isError && errorMessage(patch.error)) || (remove.isError && errorMessage(remove.error))}</ErrorBox>
      {docs.isLoading ? <Spinner /> : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[52rem] text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500"><tr><th className="p-3">Document</th><th className="p-3">Category</th><th className="p-3">Region</th><th className="p-3">Version</th><th className="p-3">Effective</th><th className="p-3">Status</th><th className="p-3 text-right">Chunks</th><th className="p-3" /></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {docs.data.map((d) => (
                <tr key={d.id}>
                  <td className="p-3"><span className="font-medium">{d.title}</span><br /><span className="text-xs text-slate-400">{d.filename} · {d.sourceType}</span>{d.error && <p className="text-xs text-red-600">{d.error}</p>}</td>
                  <td className="p-3 text-slate-600">{d.category.replaceAll('_', ' ').toLowerCase()}</td>
                  <td className="p-3 text-slate-600">{d.region}</td>
                  <td className="p-3">v{d.version}</td>
                  <td className="p-3 text-slate-500">{dateOnly(d.effectiveDate)}{d.expiryDate ? ` → ${dateOnly(d.expiryDate)}` : ''}</td>
                  <td className="p-3"><StatusBadge status={d.status} /></td>
                  <td className="p-3 text-right text-slate-500">{d.chunkCount}</td>
                  <td className="p-3">
                    <div className="flex justify-end gap-1">
                      {d.status === 'APPROVED' && <Button size="sm" variant="ghost" onClick={() => patch.mutate({ id: d.id, status: 'SUPERSEDED' })}>Retire</Button>}
                      {d.status === 'SUPERSEDED' && <Button size="sm" variant="ghost" onClick={() => patch.mutate({ id: d.id, status: 'APPROVED' })}>Reinstate</Button>}
                      <button onClick={() => window.confirm(`Delete "${d.title}" v${d.version}?`) && remove.mutate(d.id)} className="rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-red-600" aria-label={`Delete ${d.title}`}><Trash2 className="h-4 w-4" /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  )
}
