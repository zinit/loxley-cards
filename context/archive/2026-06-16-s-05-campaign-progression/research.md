---
date: 2026-06-16T12:00:00+02:00
researcher: Claude
git_commit: f33a887763c8f2b0e40ffd3c937972be1c0c0f3f
branch: main
repository: loxley-cards
topic: "S-05 campaign progression + Phase 1 test coverage"
tags: [research, campaign-progression, persistence, testing, integration-tests, contract-tests]
status: complete
last_updated: 2026-06-16
last_updated_by: Claude
---

# Research: S-05 Campaign Progression + Phase 1 Test Coverage

**Date**: 2026-06-16
**Researcher**: Claude
**Git Commit**: f33a887
**Branch**: main
**Repository**: loxley-cards

## Research Question

What exists today for campaign persistence, what's missing for S-05, and what test infrastructure supports the Phase 1 test coverage (Risk #1: campaign progress disappears between sessions; Risk #2: API contract drift)?

## Summary

The **schema is ready** (`users.highest_unlocked_stage INT DEFAULT 1` exists since V1) and the **engine signals match completion** (`GameState.matchEnded` + `matchWinner`), but **zero code connects them** — no listener persists outcomes to DB, no endpoint returns campaign progress, and no validation checks stage unlock before creating a game. The frontend uses `localStorage` exclusively for progress (`loxley_highest_unlocked` key). `/auth/me` returns only `{ username }`. All game endpoints are `permitAll` — no ownership checks.

Test infrastructure is solid for backend integration: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` pattern with H2 + Flyway, JWT cookie extraction from `Set-Cookie` header. Frontend has **zero tests** — no vitest, no Playwright, no test runner.

## Detailed Findings

### 1. Backend persistence layer — what's ready

**User entity** (`backend/acommon-db/src/main/java/cards/loxley/db/User.java`):
- `int highestUnlockedStage` (line 32) — NOT NULL, default 1, column `highest_unlocked_stage`
- Getter `getHighestUnlockedStage()` (line 80), setter `setHighestUnlockedStage(int)` (line 84)
- `@PreUpdate void onUpdate()` (line 55) — auto-updates `updatedAt` on save

**Schema** (3 Flyway migrations in `backend/acommon-db/src/main/resources/db/migration/`):
- V1: `highest_unlocked_stage INT NOT NULL DEFAULT 1` — **column exists since day one**
- V2: timestamp precision fix
- V3: added `username` + `password_hash`, relaxed `email` to nullable

**UserRepository** (`backend/acommon-db/src/main/java/cards/loxley/db/UserRepository.java`):
- `findByUsername(String)` — returns `Optional<User>` — can look up user from JWT subject
- No `findById` needed (JPA's built-in `findById(UUID)` works via `JpaRepository<User, UUID>`)

**UserRepositoryTests** confirms `setHighestUnlockedStage()` round-trips through DB (lines 71-84).

### 2. Engine match completion signals

**GameState** (`backend/acommon-game-engine/src/main/java/cards/loxley/game/domain/state/GameState.java`):
- `boolean matchEnded` (line 14) — true when match is over
- `Player matchWinner` (line 15) — P1, P2, or null (draw)
- `endMatchWith(Player)` (line 91) — sets both fields
- `endMatchAsDraw()` (line 96) — matchEnded=true, matchWinner=null

**RoundResolver** (`backend/acommon-game-engine/src/main/java/cards/loxley/game/engine/execution/RoundResolver.java`):
- `finalizeMatchWithWinner(state, player)` (line 90) — calls `state.endMatchWith(winner)` + publishes `MatchEnded` event
- Event bus exists (`MatchEventBus`) but **no listeners are registered**

**Wire format** — `GameStateView` exposes both fields:
- `matchEnded` (boolean)
- `matchWinner` (string: "P1" / "P2" / null)

### 3. What does NOT exist (the S-05 gap)

| Missing piece | Impact |
|---------------|--------|
| No code calls `user.setHighestUnlockedStage()` from app module | Progress never persists to DB |
| No campaign progress endpoint (GET) | Frontend can't read server-side progress |
| No match result persistence (POST/callback) | Win doesn't flow from engine → DB |
| `/auth/me` returns only `{ username }` | No campaign state on session restore |
| No stage-unlock validation in `GameController.createGame()` | Any stage playable regardless of progress |
| No user ownership on game sessions | `GameSessionStore` is user-agnostic (no userId stored) |
| Security context has only username (not User entity) | Must do `userRepository.findByUsername()` to get User for updates |

### 4. Frontend current flow (localStorage-only)

**campaignProgress.ts** (`frontend/src/utils/campaignProgress.ts`):
- `getHighestUnlocked()` (line 3) — reads `localStorage.getItem('loxley_highest_unlocked')`, defaults to 1
- `unlockStage(stageId)` (line 14) — writes to localStorage only if `stageId > current`

**GameBoard.tsx** (`frontend/src/pages/GameBoard.tsx`, lines 56-63):
```typescript
useEffect(() => {
  if (phase !== 'match-ended' || !gameState?.matchEnded) return
  if (gameState.matchWinner === 'P1') {
    const stageNumber = parseInt(stageId ?? '1', 10)
    unlockStage(stageNumber + 1)
  }
}, [phase, gameState, stageId])
```
- Detects `matchWinner === 'P1'` → unlocks next stage in localStorage
- **No server call** — pure client-side

**CampaignMap.tsx** (`frontend/src/pages/CampaignMap.tsx`, line 17):
- `const highestUnlocked = getHighestUnlocked()` — reads localStorage on mount
- Stages with `id < highestUnlocked` → completed, `id === highestUnlocked` → active, `id > highestUnlocked` → locked

**AuthContext** (`frontend/src/contexts/AuthContext.tsx`):
- `AuthUser` type: `{ username: string }` only
- `fetchCurrentUser()` calls `GET /auth/me` on mount — no campaign data returned

### 5. API contract surface (for Risk #2 contract tests)

**Endpoints to pin:**

| Endpoint | Request | Response shape | Notes |
|----------|---------|---------------|-------|
| `GET /auth/me` | — | `{ username }` or 401 | **Needs** `highestUnlockedStage` for S-05 |
| `POST /api/games` | `{ stageNumber }` | `GameStateView` (full) | **Needs** stage-unlock validation |
| `POST /api/games/{id}/moves` | `MoveRequest` | `GameStateView` (full) | Match completion signal: `matchEnded` + `matchWinner` |

**GameStateView shape** (13 fields total, from `backend/app/src/main/java/cards/loxley/app/web/dto/GameStateView.java`):
- `gameId`, `roundNumber`, `currentTurn`, `yourTurn`, `matchEnded`, `matchWinner`, `you` (PlayerView), `opponent` (OpponentView), `roundHistory`, `legalMoves`, `yourLastMove`, `opponentLastMove`

**Frontend type mirror** (`frontend/src/api/types.ts`, lines 12-25) matches backend 1:1.

### 6. Test infrastructure — backend

**AuthControllerTests pattern** (`backend/app/src/test/java/cards/loxley/app/web/AuthControllerTests.java`):
```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
```
- Security filters **enabled** (JWT auth active)
- `@Transactional` for test isolation (user creation auto-rolled-back)
- JWT cookie extraction pattern (lines 113-126): parse `Set-Cookie` header → extract `jwt=...;` → use as `new Cookie("jwt", jwtValue)` in subsequent requests

**GameControllerTests pattern** (`backend/app/src/test/java/cards/loxley/app/web/GameControllerTests.java`):
```java
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
```
- Security filters **disabled** — no auth needed
- No @Transactional (games are in-memory, no DB cleanup needed)

**Test config** (`backend/app/src/test/resources/application.properties`):
- H2 in-memory (`jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`)
- Flyway enabled, `ddl-auto=validate`
- JWT test secret hardcoded

**Method naming**: `verb_condition_expectation` — e.g., `register_validCredentials_returns201WithCookie()`

### 7. Test infrastructure — frontend

**Zero tests.** No vitest, no jest, no Playwright. No `test` script in `package.json`. No `*.test.*` or `*.spec.*` files anywhere in `frontend/src/`.

**For Playwright E2E** (per change notes scope):
- Must install Playwright from scratch
- CORS allows `localhost:5173` and `localhost:3000` — local dev E2E is feasible
- Backend must be running with H2 or Supabase for E2E
- Vite proxy routes `/api` and `/auth` → `localhost:8080`

### 8. GameSessionStore — user-agnostic

**GameSessionStore** (`backend/app/src/main/java/cards/loxley/app/web/GameSessionStore.java`):
- Stores `Map<String, GameState>` and `Map<String, CampaignStage>` keyed by gameId (UUID)
- **No userId field** — games are not associated with users
- 30-minute cleanup cycle
- Per-game `synchronized` lock for thread safety
- `findStageOrThrow(gameId)` returns `CampaignStage` (has `stageNumber`) — **needed to know which stage was won**

### 9. Campaign stage data

**Backend** (`backend/acommon-game-engine/src/main/resources/data/campaign_stages.json`):
- 10 stages, stageNumber 1-10
- OpponentProfileIds: ultra_easy, easy, easy, medium, medium, medium, hard, hard, hard, top_hard

**Frontend** (`frontend/src/data/campaignStages.ts`):
- 10 stages with `id`, `displayName`, `description`, `position` (map coordinates)
- `status` field is static/unused — actual status computed from `highestUnlocked`

### 10. Security config

**SecurityConfig** (`backend/app/src/main/java/cards/loxley/app/security/SecurityConfig.java`):
- ALL endpoints are `permitAll` — `/auth/**`, `/api/**`, and everything else
- `JwtAuthenticationFilter` added before `UsernamePasswordAuthenticationFilter` in chain
- JWT extracts only `username` from token subject — no user ID or roles
- Session management: STATELESS
- CSRF: disabled

## Code References

- `backend/acommon-db/src/main/java/cards/loxley/db/User.java:32` — `highestUnlockedStage` field
- `backend/acommon-db/src/main/java/cards/loxley/db/UserRepository.java:10` — `findByUsername()`
- `backend/acommon-game-engine/src/main/java/cards/loxley/game/domain/state/GameState.java:14-15` — `matchEnded` + `matchWinner`
- `backend/acommon-game-engine/src/main/java/cards/loxley/game/engine/execution/RoundResolver.java:90-104` — match finalization
- `backend/app/src/main/java/cards/loxley/app/web/GameController.java:53-99` — game CRUD + moves
- `backend/app/src/main/java/cards/loxley/app/web/AuthController.java:50-57` — `/auth/me` (username only)
- `backend/app/src/main/java/cards/loxley/app/web/GameSessionStore.java:15-18` — in-memory maps (no userId)
- `backend/app/src/main/java/cards/loxley/app/web/dto/GameStateView.java:5-19` — wire DTO shape
- `backend/app/src/main/java/cards/loxley/app/web/dto/AuthResponse.java:1-4` — `{ username }` only
- `frontend/src/utils/campaignProgress.ts:3-23` — localStorage read/write
- `frontend/src/pages/GameBoard.tsx:56-63` — match win → localStorage unlock
- `frontend/src/pages/CampaignMap.tsx:17` — reads `getHighestUnlocked()`
- `frontend/src/api/types.ts:1-3` — `AuthUser { username }`
- `frontend/src/contexts/AuthContext.tsx:6-12` — context interface (no campaign data)
- `backend/app/src/test/java/cards/loxley/app/web/AuthControllerTests.java:1-134` — MockMvc + JWT pattern
- `backend/app/src/test/java/cards/loxley/app/web/GameControllerTests.java:1-117` — MockMvc no-auth pattern
- `backend/app/src/test/resources/application.properties` — H2 + Flyway test config

## Architecture Insights

1. **Schema-first readiness**: `highest_unlocked_stage` exists since V1 but has never been written by application code — the column was anticipatory. No new migration needed for the core field.

2. **Two-sided unlock needed**: S-05 must persist to DB (server-side source of truth) AND update frontend to read from server instead of localStorage. The transition: localStorage becomes a cache/fallback, server is authoritative.

3. **Match result → campaign update must happen in the controller**, not via engine event bus. The engine lives in `acommon-game-engine` (no Spring context, no UserRepository access). The `GameController.makeMove()` method is the natural place — after `driveBotMoves()`, check `state.matchEnded()` and `state.matchWinner()`, then update User if P1 won.

4. **GameSessionStore needs stage number**: Already stores `CampaignStage` per gameId via `findStageOrThrow(gameId)` — can retrieve `stageNumber` at match completion to know which stage to unlock.

5. **Username from SecurityContext → User entity**: `SecurityContextHolder.getContext().getAuthentication().getName()` returns username → `userRepository.findByUsername(username)` → update `highestUnlockedStage`. This is the same pattern AuthController uses.

6. **Contract test anchor**: The critical shape to pin is the `/auth/me` response after it gains `highestUnlockedStage` — plus the existing `GameStateView.matchEnded` + `matchWinner` fields that the frontend already consumes.

7. **All endpoints are permitAll**: New campaign endpoints should be `authenticated()` since they read/write user-specific data. But this is a SecurityConfig change that touches Risk #3/#5 territory (Phase 2 scope). Minimum viable: validate JWT presence in the endpoint logic itself, defer SecurityConfig hardening.

## Historical Context (from prior changes)

- `context/archive/2026-06-12-f-02-data-persistence/plan.md` — F-02 established User entity with `highest_unlocked_stage` and H2 test profile. The test profile was a critical plan-review fix (existing app tests would break without it).
- `context/archive/2026-06-13-f-03-password-auth-scaffold/plan.md` — F-03 added V3 migration (username + password_hash), JWT auth, and the AuthControllerTests pattern with cookie extraction.
- `context/archive/2026-06-14-s-04-password-login/plan.md` — S-04 added `/auth/me` endpoint (username only), AuthContext, ProtectedRoute, login page.
- `context/archive/2026-05-29-s-03-polished-game-ui/plan.md` — S-03 introduced localStorage campaign progress (`campaignProgress.ts`), the `useGameReducer` state machine, and MatchEndScreen.

## Open Questions

1. **Should `/auth/me` gain `highestUnlockedStage`** or should there be a separate `GET /api/campaign/progress` endpoint? Adding to `/auth/me` is simpler (one call on app mount restores both identity and progress) but mixes auth and game concerns. Separate endpoint is cleaner but requires an extra fetch on every page load.

2. **Stage-unlock validation**: Should `POST /api/games` reject `stageNumber > user.highestUnlockedStage`? Prevents playing locked stages server-side. But this requires auth in the game creation flow — currently games are created without knowing the user. This is a Risk #5 concern (Phase 2) but a natural fit for S-05.

3. **Replay completed stages**: PRD FR-011 says users can replay completed stages. The unlock check must be `stageNumber <= user.highestUnlockedStage`, not `stageNumber === user.highestUnlockedStage`.

4. **Concurrent match completion**: If user finishes two games rapidly (tab duplication?), the `highestUnlockedStage` update must be idempotent — `Math.max(current, newStage)` not just `set(newStage)`.

5. **Playwright E2E scope**: The change notes request "one E2E Playwright test" but test-plan.md Phase 1 says "backend integration (MockMvc + H2), contract shape assertions" with no e2e. The Playwright test is an addition from the change brief, not from the test plan. Need to decide: bootstrap Playwright here (Phase 1) or defer to Phase 4?
