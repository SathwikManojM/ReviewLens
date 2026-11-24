import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('rl_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401 && !err.config.url.startsWith('/auth/')) {
      localStorage.removeItem('rl_token')
      localStorage.removeItem('rl_user')
      window.location.assign('/login')
    }
    return Promise.reject(err)
  },
)

export function errorMessage(err) {
  return err?.response?.data?.message || err?.message || 'Something went wrong'
}

/** Downloads a protected file (PDF/CSV) using the auth header. */
export async function download(url, params, fallbackName) {
  const res = await api.get(url, { params, responseType: 'blob', paramsSerializer: { indexes: null } })
  const cd = res.headers['content-disposition'] || ''
  const name = /filename="?([^"]+)"?/.exec(cd)?.[1] || fallbackName
  const href = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = href
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(href), 1000)
}

export default api
