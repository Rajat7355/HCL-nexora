import CatalogPage from './CatalogPage.jsx'
import ProductPage from './ProductPage.jsx'
import ComparePage from './ComparePage.jsx'
import WishlistPage from './WishlistPage.jsx'
import InventoryPage from './admin/InventoryPage.jsx'
import AuditPage from './admin/AuditPage.jsx'

export default [
  { path: '/', element: <CatalogPage /> },
  { path: '/products/:sku', element: <ProductPage /> },
  { path: '/compare', element: <ComparePage /> },
  { path: '/wishlist', element: <WishlistPage />, auth: true },
  { path: '/admin/inventory', element: <InventoryPage />, staff: true },
  { path: '/admin/audit', element: <AuditPage />, staff: true },
]

export const nav = [
  { label: 'Shop', to: '/' },
  { label: 'Inventory', to: '/admin/inventory', staff: true },
  { label: 'AI audit', to: '/admin/audit', staff: true },
]
