import axios from 'axios'

const TOKEN_KEY = 'shopsmart.tokens'

export const tokenStore = {
  get() {
    try { return JSON.parse(localStorage.getItem(TOKEN_KEY)) } catch { return null }
  },
  set(tokens) {
    try { localStorage.setItem(TOKEN_KEY, JSON.stringify(tokens)) } catch { /* storage may be blocked */ }
  },
  clear() {
    try { localStorage.removeItem(TOKEN_KEY) } catch { /* ignore */ }
  },
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL
  ? `${import.meta.env.VITE_API_BASE_URL.replace(/\/+$/, '')}/api`
  : '/api'

export const api = axios.create({ baseURL: apiBaseUrl })

api.interceptors.request.use((config) => {
  const t = tokenStore.get()
  if (t?.accessToken) config.headers.Authorization = `Bearer ${t.accessToken}`
  return config
})

let refreshing = null
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    const tokens = tokenStore.get()
    if (error.response?.status === 401 && tokens?.refreshToken && original && !original._retry
        && !original.url?.includes('/auth/')) {
      original._retry = true
      try {
        refreshing = refreshing || axios.post(`${apiBaseUrl}/auth/refresh`, { refreshToken: tokens.refreshToken })
        const { data } = await refreshing
        tokenStore.set({ accessToken: data.accessToken, refreshToken: data.refreshToken })
        return api(original)
      } catch {
        tokenStore.clear()
        window.dispatchEvent(new Event('shopsmart:logout'))
      } finally {
        refreshing = null
      }
    }
    return Promise.reject(error)
  },
)

/** Turns any API failure into a readable message (the backend always answers {code, message}). */
export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
  const data = error?.response?.data
  if (data?.details && Object.keys(data.details).length) return Object.values(data.details).join(', ')
  return data?.message || (error?.message === 'Network Error' ? 'Cannot reach the server.' : fallback)
}

export const errorCode = (error) => error?.response?.data?.code
