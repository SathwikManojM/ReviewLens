import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import { errorMessage } from '../api'
import { Logo } from '../components/Layout'

const pill = (bg, text) => <span className="pill" style={{ background: bg, color: '#fff' }}>{text}</span>

export default function AuthPage({ mode }) {
  const { login, register } = useAuth()
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const isLogin = mode === 'login'
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (isLogin) await login(form.email, form.password)
      else await register(form.name, form.email, form.password)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth">
      <section className="auth-hero">
        <div className="row" style={{ fontWeight: 700, fontSize: 18 }}><Logo size={30} /> ReviewLens</div>
        <div>
          <h1>Know exactly what customers love - and what they don't.</h1>
          <p>Aspect-based sentiment analysis for Amazon product reviews. Upload a dataset and see how customers feel about the camera, battery, display, price and more.</p>
        </div>
        <div className="auth-demo">
          <div style={{ opacity: 0.85, marginBottom: 8 }}>"The camera quality is excellent, but the battery drains quickly. The display is beautiful, although the phone is overpriced."</div>
          {pill('#2a78d6', 'Camera · positive')}{pill('#e34948', 'Battery · negative')}{pill('#2a78d6', 'Display · positive')}{pill('#e34948', 'Price · negative')}
        </div>
      </section>
      <section className="auth-form">
        <form onSubmit={submit}>
          <div>
            <h1>{isLogin ? 'Welcome back' : 'Create your account'}</h1>
            <p className="muted" style={{ margin: '6px 0 0' }}>{isLogin ? 'Sign in to your ReviewLens workspace.' : 'Start analysing customer reviews in minutes.'}</p>
          </div>
          {error && <div className="alert">{error}</div>}
          {!isLogin && <label className="field">Full name<input required value={form.name} onChange={set('name')} autoComplete="name" /></label>}
          <label className="field">Email<input required type="email" value={form.email} onChange={set('email')} autoComplete="email" /></label>
          <label className="field">Password<input required type="password" minLength={isLogin ? undefined : 6} value={form.password} onChange={set('password')} autoComplete={isLogin ? 'current-password' : 'new-password'} /></label>
          <button className="btn primary" disabled={busy} style={{ justifyContent: 'center', padding: '10px' }}>{busy ? 'Please wait…' : isLogin ? 'Sign in' : 'Create account'}</button>
          <div className="muted small" style={{ textAlign: 'center' }}>
            {isLogin ? <>New to ReviewLens? <Link to="/register">Create an account</Link></> : <>Already have an account? <Link to="/login">Sign in</Link></>}
          </div>
        </form>
      </section>
    </div>
  )
}
