import PolicyPage from './PolicyPage.jsx'
import KnowledgeAdminPage from './admin/KnowledgeAdminPage.jsx'

export default [
  { path: '/help', element: <PolicyPage /> },
  { path: '/admin/knowledge', element: <KnowledgeAdminPage />, staff: true },
]

export const nav = [
  { label: 'Help centre', to: '/help' },
  { label: 'Knowledge base', to: '/admin/knowledge', staff: true },
]
