import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { useAuth } from '../contexts/AuthContext'
import heroImage from '../assets/landing-hero.webp'

type Mode = 'login' | 'register'

export default function LoginPage() {
  const { user, login, register } = useAuth()
  const navigate = useNavigate()

  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/" replace />

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username.trim() || !password.trim()) {
      setError('Please fill in all fields')
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      if (mode === 'register') {
        await register(username, password)
      } else {
        await login(username, password)
      }
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  const toggleMode = () => {
    setMode(mode === 'login' ? 'register' : 'login')
    setError(null)
  }

  return (
    <div className="login-page">
      <img src={heroImage} alt="" className="login-bg" draggable={false} />
      <div className="login-overlay" />
      <footer className="login-credit">A final project for the 10xDevs 3.0 course.</footer>
      <div className="login-content">
        <header className="login-hero-text">
          <h1 className="login-title">LOXLEY CARDS</h1>
          <p className="login-tagline">A tactical card game inspired by Gwent.</p>
          <p className="login-description">
            A single-player card game set deep in Sherwood Forest.
            <br />
            Lead Robin of Loxley and his outlaws across ten campaign stages —
            <br />
            best-of-three duels won by bluffs, weather magic, and well-timed passes.
          </p>
        </header>
        <div className="login-card">
          <form onSubmit={handleSubmit} className="login-form">
            <input
              type="text"
              placeholder="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="login-input"
              autoComplete="username"
              autoFocus
            />
            <input
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="login-input"
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
            />
            {error && <div className="login-error">{error}</div>}
            <button type="submit" className="login-button" disabled={submitting}>
              {submitting
                ? (mode === 'register' ? 'Registering...' : 'Logging in...')
                : (mode === 'register' ? 'Register' : 'Log in')}
            </button>
          </form>
          <button type="button" className="login-toggle" onClick={toggleMode}>
            {mode === 'login'
              ? "Don't have an account? Register"
              : 'Already have an account? Log in'}
          </button>
        </div>
      </div>
    </div>
  )
}
