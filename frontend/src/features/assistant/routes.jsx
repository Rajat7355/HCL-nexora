import AgentsPage from './admin/AgentsPage.jsx'
import { AlertsPage, TicketsPage } from './admin/TriagePages.jsx'

export default [
  { path: '/admin/agents', element: <AgentsPage />, staff: true },
  { path: '/admin/alerts', element: <AlertsPage />, staff: true },
  { path: '/admin/tickets', element: <TicketsPage />, staff: true },
]

export const nav = [
  { label: 'Agents', to: '/admin/agents', staff: true },
  { label: 'Alerts', to: '/admin/alerts', staff: true },
  { label: 'Tickets', to: '/admin/tickets', staff: true },
]
