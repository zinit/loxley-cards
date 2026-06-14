# Password Login UI — Plan Brief

> Full plan: `context/changes/s-04-password-login/plan.md`

## What & Why

Build the frontend login/register/logout flow so users can create an account and authenticate before playing. The backend auth (F-03) is done — this slice adds the UI and route protection. Prerequisite for S-05 (server-side campaign progression per user).

## Starting Point

Backend has `POST /auth/register`, `/auth/login`, `/auth/logout` with JWT in HTTPOnly cookie, BCrypt, validation, and error responses. Frontend has zero auth infrastructure — no login page, no auth context, no protected routes, and the Vite dev proxy doesn't cover `/auth`.

## Desired End State

User opens the app → redirected to a Sherwood-themed login/register page → authenticates → lands on CampaignMap with their username in the top-right corner and a logout link. Page refresh preserves auth. Direct URLs to game routes while logged out redirect to login.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
|---|---|---|
| Login page structure | Single page with login/register toggle | Minimal routing, familiar UX, one component — sufficient for 5-10 users |
| Unauthenticated user flow | Redirect to `/login` | Clean separation — game behind auth wall, matches PRD US-02 |
| Auth state verification | `GET /auth/me` backend endpoint | HTTPOnly cookie invisible to JS — server-side check is the only reliable method |
| Logout placement | Campaign map only (top-right) | Simple, no mid-game disruption; user exits game first via existing "Back to map" |
| Styling | Sherwood theme (dark wood, Cinzel, cream/gold) | Cohesive experience from first screen; reuses existing CSS tokens |
| Client-side validation | Minimal (non-empty fields) + server error display | Single source of truth for rules; fast to implement |
| Username display | Show near logout on CampaignMap | Confirms identity when friends share a device |

## Scope

**In scope:**
- `GET /auth/me` backend endpoint + test
- Auth API client (`authApi.ts`) + Vite proxy for `/auth`
- `AuthContext` with user state + login/register/logout actions
- `ProtectedRoute` wrapper redirecting to `/login`
- Sherwood-themed login/register page with toggle
- Username + logout in CampaignMap top-right corner

**Out of scope:**
- Game endpoint auth enforcement (S-05)
- User-game binding in GameSessionStore (S-05)
- Campaign progress migration from localStorage to server (S-05)
- Password reset, refresh tokens, rate limiting

## Architecture / Approach

Backend-first (trivial `/auth/me`), then frontend bottom-up. `AuthProvider` wraps the router, calls `GET /auth/me` on mount to restore session. `ProtectedRoute` checks auth context and redirects. Login page calls auth context functions which call `authApi.ts` which hits the proxied backend. JWT cookie management is fully server-side (Set-Cookie headers) — frontend never touches the token directly.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Backend: `/auth/me` | Endpoint for frontend to verify auth state | None — 5 lines reading existing SecurityContext |
| 2. Auth API + Vite proxy | `authApi.ts` + `/auth` proxy | Proxy misconfiguration (easy to verify) |
| 3. Auth context + routes | `AuthContext`, `ProtectedRoute`, router rewire | Flash of login page during auth check (mitigated by loading state) |
| 4. Login page | Sherwood-themed form with toggle + validation | CSS styling effort; server error message clarity |
| 5. CampaignMap chrome | Username display + logout button | Readability over map background (text-shadow) |

**Prerequisites:** F-03 done (backend auth), backend + frontend dev servers running
**Estimated effort:** ~1 session across 5 phases

## Open Risks & Assumptions

- CORS credentials: Vite proxy handles dev; production nginx must set `credentials: include` for cross-origin cookie sending (`loxley.cards` → `api.loxley.cards`)
- `AuthProvider` outside `RouterProvider` means it cannot use `useNavigate` — navigation lives in `ProtectedRoute` and page components only
- localStorage campaign progress remains independent of auth until S-05

## Success Criteria (Summary)

- User can register, log in, and log out through the UI
- Unauthenticated access to game routes redirects to login
- Page refresh preserves authenticated session (no re-login needed)
