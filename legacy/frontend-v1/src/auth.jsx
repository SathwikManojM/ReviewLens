import { createContext, useContext, useState } from 'react'
import api from './api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('rl_user')) } catch { return null }
  })

  const save = ({ token, user }) => {
    localStorage.setItem('rl_token', token)
    localStorage.setItem('rl_user', JSON.stringify(user))
    setUser(user)
  }

  const login = async (email, password) => save((await api.post('/auth/login', { email, password })).data)
  const register = async (name, email, password) => save((await api.post('/auth/register', { name, email, password })).data)
  const logout = () => {
    localStorage.removeItem('rl_token')
    localStorage.removeItem('rl_user')
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, login, register, logout }}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
