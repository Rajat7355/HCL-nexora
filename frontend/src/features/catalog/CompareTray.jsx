import { Link } from 'react-router-dom'
import { GitCompareArrows, X } from 'lucide-react'
import { compareStore, useCompare } from './compareStore.js'

export default function CompareTray() {
  const items = useCompare()
  if (items.length === 0) return null
  return (
    <div className="fixed bottom-4 left-1/2 z-40 w-[min(46rem,calc(100%-2rem))] -translate-x-1/2 rounded-xl bg-slate-900 p-3 text-white shadow-xl" role="region" aria-label="Compare products">
      <div className="flex items-center gap-3">
        <GitCompareArrows className="h-5 w-5 shrink-0 text-brand-100" aria-hidden />
        <ul className="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto">
          {items.map((p) => (
            <li key={p.productId} className="flex shrink-0 items-center gap-2 rounded-lg bg-slate-800 py-1 pl-1 pr-2 text-sm">
              <img src={p.imageUrl} alt="" className="h-8 w-8 rounded object-cover" />
              <span className="max-w-32 truncate">{p.name}</span>
              <button onClick={() => compareStore.remove(p.productId)} aria-label={`Remove ${p.name}`} className="text-slate-400 hover:text-white"><X className="h-4 w-4" /></button>
            </li>
          ))}
        </ul>
        <button onClick={compareStore.clear} className="text-xs text-slate-400 hover:text-white">Clear</button>
        {items.length >= 2
          ? <Link to={`/compare?skus=${items.map((i) => i.productId).join(',')}`} className="rounded-lg bg-brand-600 px-3 py-1.5 text-sm font-medium hover:bg-brand-500">Compare ({items.length})</Link>
          : <span className="text-xs text-slate-400">Pick one more</span>}
      </div>
    </div>
  )
}
