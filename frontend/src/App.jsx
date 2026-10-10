import { Route, Routes } from 'react-router-dom'
import Layout from './shell/Layout.jsx'
import { RequireAuth } from './shell/RequireAuth.jsx'
import { NotFound } from './shell/ui.jsx'

// Every feature folder owns a routes.jsx. They are discovered here, so nobody edits this file to add a page.
// routes.jsx exports: default = [{ path, element, auth?, staff? }], and optionally nav = [{ label, to, auth?, staff? }]
const modules = import.meta.glob('./features/*/routes.jsx', { eager: true })
const routes = Object.values(modules).flatMap((m) => m.default || [])

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {routes.map((r) => (
          <Route
            key={r.path}
            path={r.path}
            element={r.auth || r.staff ? <RequireAuth staff={r.staff}>{r.element}</RequireAuth> : r.element}
          />
        ))}
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
