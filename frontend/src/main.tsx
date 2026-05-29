import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import './index.css'
import CampaignMap from './pages/CampaignMap'
import GameBoard from './pages/GameBoard'
import DebugGame from './pages/DebugGame'

const router = createBrowserRouter([
  { path: '/', element: <CampaignMap /> },
  { path: '/game/:stageId', element: <GameBoard /> },
  { path: '/debug-game', element: <DebugGame /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
