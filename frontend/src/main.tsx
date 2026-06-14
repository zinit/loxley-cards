import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import './index.css'
import { AuthProvider } from './contexts/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import CampaignMap from './pages/CampaignMap'
import GameBoard from './pages/GameBoard'
import LoginPage from './pages/LoginPage'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/', element: <ProtectedRoute><CampaignMap /></ProtectedRoute> },
  { path: '/game/:stageId', element: <ProtectedRoute><GameBoard /></ProtectedRoute> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </StrictMode>,
)
