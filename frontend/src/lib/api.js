import axios from 'axios'

/** REST client for the Spring Boot API. The browser never connects to MongoDB. */
const api = axios.create({ baseURL: '/api' })
const KEY = 'rl_token'

export const session = {
  get: () => { try { return localStorage.getItem(KEY) } catch { return null } },
  set: (t) => { try { localStorage.setItem(KEY, t) } catch { /* private mode */ } },
  clear: () => { try { localStorage.removeItem(KEY) } catch { /* ignore */ } },
}

const PUBLIC = ['/auth/login', '/auth/admin/login', '/auth/register', '/public/']

api.interceptors.request.use((c) => {
  const t = session.get()
  if (t) c.headers.Authorization = `Bearer ${t}`
  return c
})

api.interceptors.response.use(
  (r) => r,
  (err) => {
    const status = err.response?.status
    const code = err.response?.data?.code
    const url = err.config?.url || ''
    if (status === 401 && !PUBLIC.some((p) => url.startsWith(p))) {
      session.clear()
      const admin = window.location.pathname.startsWith('/admin')
      const target = admin ? '/admin/login' : '/login'
      if (window.location.pathname !== target) {
        window.location.assign(`${target}?reason=${code === 'ACCOUNT_DISABLED' ? 'disabled' : 'expired'}`)
      }
    }
    if (status === 503 && code === 'MAINTENANCE') {
      window.dispatchEvent(new CustomEvent('rl:maintenance', { detail: err.response.data.message }))
    }
    return Promise.reject(err)
  },
)

export function errorMessage(e) {
  if (e?.response?.data?.message) return e.response.data.message
  if (e?.response) return `Request failed (${e.response.status})`
  if (e?.message === 'Network Error') return 'Cannot reach the server. Check that the backend is running.'
  return e?.message || 'Something went wrong'
}

/** Downloads a protected file with the Authorization header (no tokens in URLs). */
export async function download(url, params, fallbackName) {
  try {
    const res = await api.get(url, { params, responseType: 'blob', paramsSerializer: { indexes: null } })
    const cd = res.headers['content-disposition'] || ''
    const name = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd)?.[1] || fallbackName
    const href = URL.createObjectURL(res.data)
    const a = Object.assign(document.createElement('a'), { href, download: decodeURIComponent(name) })
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(href), 1500)
  } catch (e) {
    if (e?.response?.data instanceof Blob) {
      try { e.response.data = JSON.parse(await e.response.data.text()) } catch { /* not JSON */ }
    }
    throw e
  }
}

export default api
