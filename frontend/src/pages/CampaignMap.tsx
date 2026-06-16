import { useCallback, useRef } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../contexts/AuthContext'
import { useMapTransform } from '../hooks/useMapTransform'
import { CAMPAIGN_STAGES } from '../data/campaignStages'
import StumpMarker from '../components/campaign/StumpMarker'
import mapImage from '../assets/sherwood-map-4k.webp'

const BASE_MARKER_SIZE = 200

function CampaignMap() {
  const containerRef = useRef<HTMLDivElement>(null)
  const transform = useMapTransform(containerRef)
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const highestUnlocked = user?.highestUnlockedStage ?? 1

  const handleLogout = useCallback(async () => {
    await logout()
    navigate('/login', { replace: true })
  }, [logout, navigate])

  const markerSize = BASE_MARKER_SIZE * transform.scale

  return (
    <div
      ref={containerRef}
      className="relative w-screen h-screen overflow-hidden bg-black"
    >
      <div className="campaign-auth-chrome">
        <span className="campaign-username">{user?.username}</span>
        <button type="button" className="campaign-logout" onClick={handleLogout}>
          Logout
        </button>
      </div>

      <img
        src={mapImage}
        alt="Sherwood campaign map"
        className="absolute inset-0 w-full h-full object-cover object-center select-none pointer-events-none"
        draggable={false}
      />

      {CAMPAIGN_STAGES.map((stage) => {
        const left = transform.offsetX + stage.position.x * transform.scale
        const top = transform.offsetY + stage.position.y * transform.scale

        let status: 'active' | 'locked' | 'completed'
        if (stage.id < highestUnlocked) {
          status = 'completed'
        } else if (stage.id === highestUnlocked) {
          status = 'active'
        } else {
          status = 'locked'
        }

        return (
          <div
            key={stage.id}
            className="absolute"
            style={{
              left: `${left}px`,
              top: `${top}px`,
              transform: 'translate(-50%, -50%)',
            }}
          >
            <StumpMarker
              number={stage.id}
              size={markerSize}
              status={status}
              onClick={() => {
                if (status === 'active' || status === 'completed') {
                  navigate(`/game/${stage.id}`)
                }
              }}
            />
          </div>
        )
      })}
    </div>
  )
}

export default CampaignMap
