import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, tokenStore } from '../api/client.js'

const AuthContext = createContext(null)
export const useAuth = () => useContext(AuthContext)

export function AuthProvider({ children }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(Boolean(tokenStore.get()))

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.get()?.refreshToken
    try {
      if (refreshToken) await api.post('/auth/logout', { refreshToken })
    } catch (error) {
      console.error('Server logout failed; clearing the local session.', error)
    } finally {
      tokenStore.clear()
      setUser(null)
      queryClient.clear()
    }
  }, [queryClient])

  useEffect(() => {
    if (!tokenStore.get()) return
    api.get('/me').then((r) => setUser(r.data)).catch(() => tokenStore.clear()).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    window.addEventListener('shopsmart:logout', logout)
    return () => window.removeEventListener('shopsmart:logout', logout)
  }, [logout])

  const acceptSession = useCallback((data) => {
    tokenStore.set({ accessToken: data.accessToken, refreshToken: data.refreshToken })
    setUser(data.user)
    queryClient.invalidateQueries()
  }, [queryClient])

  const updateUser = useCallback((nextUser) => setUser(nextUser), [])

  const login = useCallback(async (email, password) => {
    const { data } = await api.post('/auth/login', { email, password })
    acceptSession(data)
    return data.user
  }, [acceptSession])

  const register = useCallback(async (payload) => {
    const { data } = await api.post('/auth/register', payload)
    acceptSession(data)
    return data.user
  }, [acceptSession])

  const value = useMemo(() => ({ user, loading, login, register, logout, updateUser, isStaff: Boolean(user?.staff) }),
    [user, loading, login, register, logout, updateUser])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
