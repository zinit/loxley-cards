# Password Login UI Implementation Plan

## Overview

Build the frontend login/register/logout flow and wire it to the backend auth endpoints shipped in F-03. Add a lightweight `GET /auth/me` endpoint so the frontend can verify auth state on page load. Protect game routes behind auth — unauthenticated users get redirected to `/login`.

## Current State Analysis

- **Backend auth**: fully implemented in F-03 — `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`. JWT in HTTPOnly cookie named `jwt`. Validation: username regex `^[a-zA-Z][a-zA-Z0-9_-]{2,29}$`, password min 8 chars. Error responses: 409 `USERNAME_TAKEN`, 401 `BAD_CREDENTIALS`, 400 `BAD_REQUEST`. `JwtAuthenticationFilter` already populates `SecurityContextHolder` from the cookie on every request.
- **Frontend**: zero auth infrastructure. React Router v7 with two routes (`/` → CampaignMap, `/game/:stageId` → GameBoard). No auth context, no login page, no protected routes. Vite proxy covers `/api` only — `/auth` is not proxied.
- **State management**: React Context + useReducer for game state (`GameContext`). No global app-level context.
- **Styling**: hybrid plain CSS (`index.css`, 1097 lines) + Tailwind utilities. Sherwood theme: `#fff5e0` cream, `Cinzel` font, gold glows, dark wood backgrounds.
- **Campaign progress**: `localStorage` via `campaignProgress.ts` — stays client-side until S-05 migrates to server persistence.

### Key Discoveries:

- `JwtAuthenticationFilter` already sets `SecurityContextHolder.getContext().getAuthentication()` with the username as principal — the `/auth/me` endpoint just reads it (5 lines of code)
- The HTTPOnly cookie is invisible to JavaScript (`document.cookie` can't read it) — server-side `/auth/me` is the only reliable way to check auth state
- Vite proxy needs `/auth` added alongside `/api` for dev to work (production will use nginx reverse proxy for both)
- CampaignMap is a self-contained fullscreen component with no header/chrome — username + logout go in absolute-positioned top-right corner

## Desired End State

User opens `loxley.cards` → redirected to `/login` → sees Sherwood-themed form → registers or logs in → lands on CampaignMap with their username displayed in the top-right corner next to a "Logout" link → plays the game → can log out from the campaign map.

**Verification**: `cd backend && ./mvnw clean install` passes. Frontend `npm run build` succeeds with zero TS errors. Manual flow: register → redirect to `/` → see username → refresh page → still logged in → logout → redirected to `/login` → direct URL `/game/1` while logged out → redirected to `/login`.

## What We're NOT Doing

- **No game endpoint protection** — `/api/games/**` stays permitAll; auth enforcement on game endpoints comes in S-05
- **No GameSessionStore user association** — games remain anonymous; user-game binding is S-05
- **No password reset** — explicit PRD non-goal for MVP (manual DB intervention)
- **No refresh tokens** — single JWT with 7-day expiry
- **No rate limiting** — brute-force protection deferred (5–10 friends)
- **No campaign progress migration** — localStorage stays; S-05 moves it to server

## Implementation Approach

Backend-first (trivial `/auth/me` endpoint), then frontend bottom-up: API client → auth context → route protection → login page → campaign map chrome. Each phase is independently verifiable.

## Critical Implementation Details

### Vite proxy for `/auth`

The existing Vite proxy only covers `/api`. Auth endpoints live at `/auth/*` (no `/api` prefix). Without adding `/auth` to the proxy, all auth fetch calls from `localhost:5173` will 404 in dev. This must land in the same phase as the auth API client.

---

## Phase 1: Backend — `/auth/me` endpoint

### Overview

Add `GET /auth/me` to `AuthController` that returns the current authenticated user's username (from the JWT cookie, already parsed by `JwtAuthenticationFilter`) or 401 if not authenticated. Add a test.

### Changes Required:

#### 1. Auth me endpoint

**File**: `backend/app/src/main/java/cards/loxley/app/web/AuthController.java`

**Intent**: Add a `GET /auth/me` endpoint that reads the authenticated principal from `SecurityContextHolder` and returns `AuthResponse(username)`. If no authentication is present (no cookie or expired JWT), return 401.

**Contract**: `@GetMapping("/me")` method. Reads `SecurityContextHolder.getContext().getAuthentication()`. If authentication is null OR is an `AnonymousAuthenticationToken` (import from `org.springframework.security.authentication`), return `ResponseEntity.status(401).build()`. Otherwise return `ResponseEntity.ok(new AuthResponse(authentication.getName()))`. Rationale: Spring Security's `AnonymousAuthenticationFilter` runs after `JwtAuthenticationFilter` and sets principal="anonymousUser" with `isAuthenticated()=true` when no JWT cookie is present — a plain null/isAuthenticated check would return 200 with username "anonymousUser" instead of 401. No new dependencies — uses existing `AuthResponse` DTO and Spring Security's context.

#### 2. Test for /auth/me

**File**: `backend/app/src/test/java/cards/loxley/app/web/AuthControllerTests.java`

**Intent**: Add tests for the new `/auth/me` endpoint: authenticated returns 200 + username, unauthenticated returns 401.

**Contract**: Two new test methods:
- `me_withValidCookie_returns200WithUsername` — register a user, extract the `Set-Cookie` header from register response, send `GET /auth/me` with that cookie, assert 200 + `{"username":"..."}`.
- `me_withoutCookie_returns401` — send `GET /auth/me` with no cookie, assert 401.

### Success Criteria:

#### Automated Verification:

- Full reactor green: `cd backend && ./mvnw clean install`

#### Manual Verification:

- `curl -v http://localhost:8080/auth/me` without cookie → 401
- Register via curl, capture Set-Cookie, `curl -v http://localhost:8080/auth/me --cookie 'jwt=...'` → 200 `{"username":"robin"}`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Frontend — Auth API client + Vite proxy

### Overview

Add `/auth` to the Vite proxy, create `authApi.ts` with fetch wrappers for register/login/logout/me, and add auth-related TypeScript types.

### Changes Required:

#### 1. Vite proxy

**File**: `frontend/vite.config.ts`

**Intent**: Proxy `/auth` requests to the backend dev server, same as `/api`.

**Contract**: Add `/auth` entry to `server.proxy` with same config as `/api` (`target: 'http://localhost:8080'`, `changeOrigin: true`).

#### 2. Auth API client

**File**: `frontend/src/api/authApi.ts`

**Intent**: Fetch wrappers for the 4 auth endpoints. Same pattern as `gameApi.ts` (async functions, `parseError` for error messages).

**Contract**: Four exported async functions:
- `register(username: string, password: string): Promise<AuthUser>` — `POST /auth/register`, returns `{ username }` on success, throws on error with server error message.
- `login(username: string, password: string): Promise<AuthUser>` — `POST /auth/login`, same pattern.
- `logout(): Promise<void>` — `POST /auth/logout`.
- `fetchCurrentUser(): Promise<AuthUser | null>` — `GET /auth/me`, returns `{ username }` on 200, returns `null` on 401 (does NOT throw — 401 is expected when not logged in).

#### 3. Auth types

**File**: `frontend/src/api/types.ts`

**Intent**: Add `AuthUser` type used by auth API client and auth context.

**Contract**: Add `export interface AuthUser { username: string }` to the existing types file.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npx tsc --noEmit` — zero TS errors
- `cd frontend && npm run build` — builds successfully

#### Manual Verification:

- Start backend + frontend dev servers, open browser console, manually call `fetch('/auth/register', ...)` from console — verify proxy works (no CORS error, no 404)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Frontend — Auth context + route protection

### Overview

Create `AuthContext` providing current user state + login/register/logout actions. Create a `ProtectedRoute` wrapper that redirects to `/login` if not authenticated. Wire into the router in `main.tsx`.

### Changes Required:

#### 1. Auth context

**File**: `frontend/src/contexts/AuthContext.tsx`

**Intent**: App-level context that holds the current user (username or null), provides login/register/logout functions that call `authApi` and update state, and checks auth on initial mount via `fetchCurrentUser()`.

**Contract**: 
- `AuthProvider` component wrapping children. On mount, calls `fetchCurrentUser()` — if returns `AuthUser`, sets user state; if null, user stays null. Shows a loading state during the initial check (prevents flash of login page for authenticated users).
- Context value: `{ user: AuthUser | null, loading: boolean, login(username, password): Promise<void>, register(username, password): Promise<void>, logout(): Promise<void> }`.
- `login` and `register` call the API, on success set user state (from the response `{ username }`), on failure re-throw the error (let the login page handle display).
- `logout` calls the API, clears user state.
- `useAuth()` hook exported for consuming the context.

#### 2. Protected route wrapper

**File**: `frontend/src/components/ProtectedRoute.tsx`

**Intent**: Wrapper component that checks `useAuth()` — if loading, shows nothing (or a minimal spinner); if no user, redirects to `/login`; if user exists, renders children.

**Contract**: `function ProtectedRoute({ children }: { children: React.ReactNode })`. Uses `useAuth()`. If `loading`, returns null (blank screen during auth check — sub-second). If `!user`, returns `<Navigate to="/login" replace />`. Otherwise returns `<>{children}</>`.

#### 3. Router integration

**File**: `frontend/src/main.tsx`

**Intent**: Wrap the app in `AuthProvider`, add `/login` route, protect existing routes with `ProtectedRoute`.

**Contract**: 
- Import `AuthProvider`, `ProtectedRoute`, and the new `LoginPage` component (created in Phase 4 — for now use a placeholder `<div>Login placeholder</div>` to keep Phase 3 self-contained).
- Router structure:
  - `/login` → `LoginPage` (no protection)
  - `/` → `<ProtectedRoute><CampaignMap /></ProtectedRoute>`
  - `/game/:stageId` → `<ProtectedRoute><GameBoard /></ProtectedRoute>`
- `AuthProvider` wraps the entire `RouterProvider` (needs to be outside the router so `useAuth` is available in all routes).

**Note on AuthProvider placement**: `AuthProvider` must wrap the router, but it also needs to call `fetchCurrentUser()` which uses `fetch('/auth/me')`. Since `AuthProvider` is outside the router, it cannot use React Router's `useNavigate`. This is fine — `AuthProvider` only manages state; navigation (redirect to `/login`) happens inside `ProtectedRoute` which IS inside the router.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npx tsc --noEmit` — zero TS errors
- `cd frontend && npm run build` — builds successfully

#### Manual Verification:

- Open `http://localhost:5173/` in browser → redirected to `/login` (placeholder page)
- Open `http://localhost:5173/game/1` → redirected to `/login`
- Open `http://localhost:5173/login` → shows placeholder
- Register via curl (to set JWT cookie), refresh browser → CampaignMap shown (cookie recognized by `/auth/me`)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Frontend — Login page

### Overview

Build the Sherwood-themed login/register page with a single form and toggle between modes. Minimal client-side validation (non-empty fields) + server error display.

### Changes Required:

#### 1. Login page component

**File**: `frontend/src/pages/LoginPage.tsx`

**Intent**: Single page at `/login` with a form (username + password inputs), a submit button ("Log in" or "Register"), a toggle link ("Don't have an account? Register" / "Already have an account? Log in"), and server error display. On successful login/register, navigate to `/`.

**Contract**:
- Local state: `mode` ('login' | 'register'), `username`, `password`, `error` (string | null), `submitting` (boolean).
- On submit: if either field is empty, set error "Please fill in all fields" (minimal client-side validation per Q&A decision). Otherwise call `auth.login()` or `auth.register()` depending on mode. On success, `navigate('/', { replace: true })`. On failure, set error from the caught error message (server error message like "Username must be 3-30 characters..." or "Username already taken").
- Toggle link switches `mode` and clears `error`.
- Submit button disabled while `submitting`.
- Uses `useAuth()` for login/register functions, `useNavigate()` for redirect.
- If user is already authenticated (`useAuth().user` is set), redirect to `/` immediately (handles case where user navigates to `/login` while already logged in).

#### 2. Login page styles

**File**: `frontend/src/index.css`

**Intent**: Sherwood-themed styles for the login page — dark wood background, Cinzel font headings, cream/gold color scheme, centered form card with semi-transparent dark background.

**Contract**: CSS classes for the login page. Key visual tokens from existing Sherwood theme:
- Background: dark wood (`#1a0f05` or similar dark brown)
- Font: `'Cinzel', 'Trajan Pro', Georgia, serif` for heading
- Text color: `#fff5e0` cream
- Input fields: semi-transparent dark background with cream text, subtle border
- Button: gold/warm accent matching stump-active glow colors (`rgba(255, 190, 80, ...)`)
- Error text: red/warm warning color
- Toggle link: cream with underline, subtler than the button
- Centered on screen, max-width ~400px for the form card

#### 3. Replace placeholder in router

**File**: `frontend/src/main.tsx`

**Intent**: Replace the Phase 3 placeholder with the real `LoginPage` import.

**Contract**: Change the placeholder `<div>Login placeholder</div>` to `<LoginPage />` with proper import.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npx tsc --noEmit` — zero TS errors
- `cd frontend && npm run build` — builds successfully

#### Manual Verification:

- Open `http://localhost:5173/login` → Sherwood-themed login form
- Register with username "robin" + password "sherwood1" → redirected to CampaignMap
- Open new incognito window → `/login` shown → login with "robin" / "sherwood1" → CampaignMap
- Try registering "robin" again → see "Username already taken" error
- Try login with wrong password → see "Bad credentials" error
- Try submitting empty form → see "Please fill in all fields"
- Refresh page while logged in → stay on CampaignMap (no flash of login page)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 5: Frontend — CampaignMap auth chrome

### Overview

Add username display and logout button to the top-right corner of CampaignMap, wired to `AuthContext.logout()`. On logout, redirect to `/login`.

### Changes Required:

#### 1. CampaignMap auth UI

**File**: `frontend/src/pages/CampaignMap.tsx`

**Intent**: Add a small absolute-positioned element in the top-right corner showing the logged-in username and a "Logout" link/button. On logout click, call `auth.logout()` and navigate to `/login`.

**Contract**: Import `useAuth()` and `useNavigate()`. Add a `<div>` positioned `absolute` top-right with the username text and a logout button/link. Logout handler: `await auth.logout()` then `navigate('/login', { replace: true })`. Style consistent with Sherwood theme — cream text, subtle, doesn't compete with the map. Similar positioning to GameBoard's "← Back to map" button (top-left) but mirrored to top-right.

#### 2. Campaign auth chrome styles

**File**: `frontend/src/index.css`

**Intent**: Styles for the username + logout element on the campaign map.

**Contract**: Absolute positioned top-right, `z-index` above the map image but below any potential overlays. Cream text (`#fff5e0`), Cinzel font for username, smaller/lighter weight for the logout link. Subtle text-shadow for readability against the map background (same technique as `.stump-number`). Logout link/button styled as text (no button chrome), with hover underline.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npx tsc --noEmit` — zero TS errors
- `cd frontend && npm run build` — builds successfully

#### Manual Verification:

- Log in → CampaignMap shows username in top-right corner
- Click "Logout" → redirected to `/login`
- After logout, direct URL to `/` → redirected to `/login`
- Username is readable against the map background
- Logout element doesn't interfere with stage markers or map interaction

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- `AuthControllerTests` (backend): 2 new test methods for `/auth/me` (authenticated + unauthenticated) added to existing 8 tests

### Integration Tests:

- Existing `AuthControllerTests` (8 tests) + `GameControllerTests` (8 tests) remain green
- Full reactor build validates all backend modules

### Manual Testing Steps:

1. Start backend + frontend dev servers
2. Open browser → verify redirect to `/login`
3. Register new user → verify redirect to CampaignMap with username shown
4. Refresh page → verify still logged in (no flash of login page)
5. Click "Logout" → verify redirect to `/login`
6. Login with same credentials → verify CampaignMap + username
7. Try registering duplicate username → verify error message
8. Try login with wrong password → verify error message
9. Navigate to `/game/1` while logged out → verify redirect to `/login`
10. Log in, start a game, play → verify game still works end-to-end with auth in place

## Performance Considerations

- `fetchCurrentUser()` on every page load adds one `GET /auth/me` round-trip (~10ms local, ~40ms production). Sub-100ms — no perceivable delay.
- Auth context loading state prevents flash of login page for authenticated users.

## References

- F-03 archive (backend auth): `context/archive/2026-06-13-f-03-password-auth-scaffold/plan.md`
- Roadmap S-04 definition: `context/foundation/roadmap.md:40`
- AuthController: `backend/app/src/main/java/cards/loxley/app/web/AuthController.java`
- SecurityConfig: `backend/app/src/main/java/cards/loxley/app/security/SecurityConfig.java`
- JwtAuthenticationFilter: `backend/app/src/main/java/cards/loxley/app/security/JwtAuthenticationFilter.java`
- Frontend router: `frontend/src/main.tsx`
- CampaignMap: `frontend/src/pages/CampaignMap.tsx`
- Game API pattern: `frontend/src/api/gameApi.ts`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — /auth/me endpoint

#### Automated

- [x] 1.1 Full reactor green

#### Manual

- [ ] 1.2 GET /auth/me without cookie returns 401
- [ ] 1.3 GET /auth/me with valid cookie returns 200 + username

### Phase 2: Frontend — Auth API client + Vite proxy

#### Automated

- [x] 2.1 TypeScript compiles with zero errors
- [x] 2.2 Frontend builds successfully

#### Manual

- [ ] 2.3 Vite proxy forwards /auth requests to backend

### Phase 3: Frontend — Auth context + route protection

#### Automated

- [x] 3.1 TypeScript compiles with zero errors
- [x] 3.2 Frontend builds successfully

#### Manual

- [ ] 3.3 Unauthenticated user redirected to /login
- [ ] 3.4 Authenticated user sees CampaignMap

### Phase 4: Frontend — Login page

#### Automated

- [x] 4.1 TypeScript compiles with zero errors
- [x] 4.2 Frontend builds successfully

#### Manual

- [ ] 4.3 Register flow works end-to-end
- [ ] 4.4 Login flow works end-to-end
- [ ] 4.5 Server errors displayed correctly
- [ ] 4.6 Page refresh preserves auth state

### Phase 5: Frontend — CampaignMap auth chrome

#### Automated

- [x] 5.1 TypeScript compiles with zero errors
- [x] 5.2 Frontend builds successfully

#### Manual

- [ ] 5.3 Username displayed in top-right of CampaignMap
- [ ] 5.4 Logout redirects to /login
- [ ] 5.5 Full end-to-end flow works (register → play → logout → login)
