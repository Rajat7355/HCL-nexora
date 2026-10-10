import { useSyncExternalStore } from 'react'

// Tiny external store so any page can add products to the compare tray without a provider.
const KEY = 'shopsmart.compare'
const MAX = 4
let items = (() => { try { return JSON.parse(localStorage.getItem(KEY)) || [] } catch { return [] } })()
const listeners = new Set()

function commit(next) {
  items = next
  try { localStorage.setItem(KEY, JSON.stringify(items)) } catch { /* ignore */ }
  listeners.forEach((l) => l())
}

export const compareStore = {
  toggle(product) {
    if (items.some((p) => p.productId === product.productId)) return commit(items.filter((p) => p.productId !== product.productId))
    if (items.length >= MAX) return false
    commit([...items, { productId: product.productId, name: product.name, imageUrl: product.imageUrl }])
    return true
  },
  remove: (sku) => commit(items.filter((p) => p.productId !== sku)),
  clear: () => commit([]),
  max: MAX,
}

const subscribe = (cb) => { listeners.add(cb); return () => listeners.delete(cb) }
export const useCompare = () => useSyncExternalStore(subscribe, () => items)
