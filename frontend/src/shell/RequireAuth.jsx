import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext.jsx'
import { Spinner } from './ui.jsx'

export function RequireAuth({ children, staff = false }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <div className="py-24"><Spinner /></div>
  if (!user) return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
  if (staff && !user.staff) {
    return (
      <div className="mx-auto max-w-lg py-24 text-center">
        <h1 className="text-xl font-semibold">Staff only</h1>
        <p className="mt-2 text-slate-500">Your account does not have access to this page.</p>
      </div>
    )
  }
  return children
}
