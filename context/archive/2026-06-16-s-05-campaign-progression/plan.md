# Campaign Progression + Phase 1 Test Coverage — Implementation Plan

## Overview

Wire the existing `highest_unlocked_stage` DB column (ready since V1, never written by application code) to the game completion flow. Backend persists wins inline in `makeMove()`, `/auth/me` returns `highestUnlockedStage`, frontend replaces localStorage with server state. Then add Phase 1 tests: backend integration test for campaign persistence across sessions (Risk #1), `/auth/me` contract shape assertion (Risk #2), and a Playwright E2E test (login → win → logout → login → stage 2 unlocked).

## Current State Analysis

- **Schema ready**: `users.highest_unlocked_stage INT NOT NULL DEFAULT 1` exists since V1. No migration needed.
- **Engine signals match completion**: `GameState.matchEnded` + `matchWinner` are set by `RoundResolver.finalizeMatchWithWinner()` and exposed in `GameStateView`.
- **Zero code connects engine → DB**: nobody calls `user.setHighestUnlockedStage()`, no campaign progress endpoint, no stage-unlock validation.
- **Frontend relies on localStorage**: `campaignProgress.ts` reads/writes `loxley_highest_unlocked` key. `CampaignMap` reads `getHighestUnlocked()`. `GameBoard` calls `unlockStage(stageNumber + 1)` on win.
- **`/auth/me` returns only `{ username }`**: `AuthResponse` is a single-field record.
- **All endpoints `permitAll`**: stage-unlock validation lives in controller logic, not SecurityConfig (Phase 2 territory).
- **Test infrastructure**: MockMvc + H2 + Flyway + `@Transactional` pattern established in `AuthControllerTests`. JWT cookie extraction pattern at lines 115-116. Frontend has zero tests — no vitest, no Playwright.

### Key Discoveries:

- `GameSessionStore.findStageOrThrow(gameId)` returns `CampaignStage` with `stageNumber()` — needed to know which stage was won (`GameSessionStore.java:37-43`)
- `SecurityContextHolder.getContext().getAuthentication().getName()` → username → `userRepository.findByUsername()` → User entity (same pattern as `AuthController.me()`)
- `GameStateMapper.toView()` has a 6-param overload (`GameStateMapper.java:39-55`) that's used by `makeMove()` and `createGame()` — natural place to thread through new field
- H2 in-memory + Flyway runs real migrations in tests (`spring.flyway.enabled=true`, `ddl-auto=validate`) — campaign persistence test will use the actual schema

## Desired End State

A logged-in user plays stage 1, wins, and sees stage 2 unlocked on the campaign map. They close the browser, return the next day, login, and stage 2 is still unlocked — progress persists in PostgreSQL, not localStorage. The `/auth/me` endpoint returns `{ username, highestUnlockedStage }`. A backend integration test proves this survives session breaks. A contract assertion pins the `/auth/me` JSON shape. A Playwright E2E test exercises the full loop from the user's perspective.

**How to verify**: `cd backend && ./mvnw test` passes (including new integration tests). `cd frontend && npx playwright test` passes against running backend + frontend. Manual: register, win stage 1, logout, login — stage 2 is active.

## What We're NOT Doing

- SecurityConfig changes (moving `/api/**` to `authenticated()`) — Phase 2 scope (Risk #3)
- Ownership checks on game sessions (IDOR protection) — Phase 2 scope (Risk #5)
- Secret leak detection tests — Phase 2 scope (Risk #6)
- Engine interaction coverage — Phase 3 scope (Risk #4)
- CI/CD pipeline wiring (GitHub Actions) — separate follow-up change (Phase 4 of test-plan)
- Frontend unit test bootstrap (vitest) — Phase 4 of test-plan
- Mobile/responsive fixes — PRD non-goal

## Implementation Approach

Four phases in dependency order: (1) backend API changes, (2) frontend migration from localStorage to server state, (3) backend integration tests exercising the new persistence, (4) Playwright E2E test bootstrapped from scratch.

Phase 1 is the foundation — all subsequent phases depend on the backend contract being in place. Phase 2 can't render server state without it. Phase 3 tests the Phase 1 code. Phase 4 tests the Phase 1+2 combination end-to-end.

## Critical Implementation Details

### Auth check pattern in GameController

`GameController` currently has no auth awareness — it doesn't inject `UserRepository` or read `SecurityContext`. After Phase 1, it needs both. The auth check must be **graceful**: if no authenticated user (anonymous/dev usage), skip validation and persistence rather than failing — keeps `GameControllerTests` (which use `addFilters=false`) passing without modification. The pattern:

```java
// Helper: returns null if anonymous, User if authenticated
private User resolveAuthenticatedUser() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken) return null;
    return userRepository.findByUsername(auth.getName()).orElse(null);
}
```

This is used in both `createGame()` (for validation) and `makeMove()` (for persistence). Existing `GameControllerTests` pass unchanged because `addFilters=false` means no JWT → anonymous token → null user → validation and persistence skipped.

---

## Phase 1: Backend — Persistence + API Changes

### Overview

Extend `AuthResponse` with `highestUnlockedStage`. Persist match wins inline in `makeMove()`. Add stage-unlock validation in `createGame()`. Add `newHighestUnlockedStage` to `GameStateView` for frontend piggybacking.

### Changes Required:

#### 1. AuthResponse DTO

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/AuthResponse.java`

**Intent**: Add `highestUnlockedStage` field so `/auth/me`, `/auth/login`, and `/auth/register` all return campaign progress alongside identity.

**Contract**: `record AuthResponse(String username, int highestUnlockedStage)` — all three callers in `AuthController` must pass the value.

#### 2. AuthController — return campaign progress

**File**: `backend/app/src/main/java/cards/loxley/app/web/AuthController.java`

**Intent**: All three auth endpoints (`me`, `register`, `login`) return the user's `highestUnlockedStage` from DB. `me()` must load the User entity (currently it only reads `authentication.getName()`). `register()` already creates the User (default 1). `login()` already loads the User.

**Contract**:
- `me()` (line 52): load user via `userRepository.findByUsername(authentication.getName())`, construct `AuthResponse(username, user.getHighestUnlockedStage())`. Return 401 if user not found in DB (defensive — shouldn't happen with valid JWT).
- `register()` (line 74): `new AuthResponse(request.username(), 1)` — new user always starts at stage 1.
- `login()` (line 85): `new AuthResponse(user.getUsername(), user.getHighestUnlockedStage())` — user is already loaded.

#### 3. GameStateView DTO

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/GameStateView.java`

**Intent**: Add a nullable field that carries the updated highest unlocked stage on match-winning responses. Null on all other responses (mid-game, match lost, match drawn).

**Contract**: Add `Integer newHighestUnlockedStage` as the last field in the record. Nullable — Jackson serializes null fields by default.

#### 4. GameStateMapper — thread through new field

**File**: `backend/app/src/main/java/cards/loxley/app/web/GameStateMapper.java`

**Intent**: Both `toView()` overloads need to accept and pass through the new field. The 4-param overload (used by `getState()`) always passes null. The 6-param overload (used by `createGame()` and `makeMove()`) gains a 7th parameter.

**Contract**: Add `Integer newHighestUnlockedStage` parameter to both overloads. The 4-param delegates to the 6-param with `null, null, null`. Pass value through to `GameStateView` constructor.

#### 4b. DevTestController — update toView() call

**File**: `backend/app/src/main/java/cards/loxley/app/web/DevTestController.java`

**Intent**: DevTestController also calls the 6-param `mapper.toView()` (line 64). Must add `null` for the new `newHighestUnlockedStage` parameter or compilation breaks.

**Contract**: Pass `null` as the last argument to `mapper.toView()`. Dev endpoint doesn't track campaign progress.

#### 5. GameController — persistence + validation

**File**: `backend/app/src/main/java/cards/loxley/app/web/GameController.java`

**Intent**: Three changes: (a) inject `UserRepository`, (b) `createGame()` validates stage is unlocked for authenticated users, (c) `makeMove()` persists win and pipes `newHighestUnlockedStage` to response.

**Contract**:

- Constructor: add `UserRepository userRepository` parameter and field.
- Add private helper `resolveAuthenticatedUser()` (see Critical Implementation Details above).
- `createGame()`: after resolving the `CampaignStage`, call `resolveAuthenticatedUser()`. If user is non-null, check `request.stageNumber() <= user.getHighestUnlockedStage()`. If violated, throw `IllegalArgumentException("Stage " + stageNumber + " is locked")` — caught by existing `GlobalExceptionHandler` → 400. Pass `null` for `newHighestUnlockedStage` to mapper (no progression on game creation).
- `makeMove()`: after `driveBotMoves()`, before building the response, check match result. If `state.matchEnded()` and `state.matchWinner().isPresent()` and winner is `Player.P1`:
  - Call `resolveAuthenticatedUser()`
  - If user non-null: compute `newStage = Math.max(user.getHighestUnlockedStage(), stage.stageNumber() + 1)`, capped at 10 (max stage). Update only if `newStage > user.getHighestUnlockedStage()`.
  - `user.setHighestUnlockedStage(newStage)` + `userRepository.save(user)`
  - Pass `newStage` as `newHighestUnlockedStage` to mapper
- If user is null (anonymous) or P1 lost/drew: pass `null` to mapper.

Edge cases:
- Stage 10 win: `Math.min(stageNumber + 1, 10)` — don't create stage 11. User stays at `highestUnlockedStage=10` with all stages accessible.
- Concurrent match completion (tab duplication): `Math.max` ensures idempotency. Duplicate saves are harmless (same or higher value wins).

### Success Criteria:

#### Automated Verification:

- `cd backend && ./mvnw compile` — compiles cleanly
- `cd backend && ./mvnw test` — all existing 254+ tests pass (GameControllerTests unchanged because `addFilters=false` means anonymous user → validation/persistence skipped)

#### Manual Verification:

- `curl` register → response includes `highestUnlockedStage: 1`
- `curl` /auth/me with JWT → response includes `highestUnlockedStage`
- Start a game for stage 1, play to completion (win), inspect response — `newHighestUnlockedStage` is populated
- Try to create game for stage 2 before winning stage 1 → 400

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Frontend — Server-Side Campaign State

### Overview

Replace localStorage-based campaign progress with server state from `AuthContext`. CampaignMap reads from `user.highestUnlockedStage`. GameBoard updates AuthContext after a win using `newHighestUnlockedStage` from the API response. Delete `campaignProgress.ts`.

### Changes Required:

#### 1. AuthUser type

**File**: `frontend/src/api/types.ts`

**Intent**: Mirror the backend `AuthResponse` contract change.

**Contract**: `AuthUser` interface gains `highestUnlockedStage: number`.

#### 2. GameStateView type

**File**: `frontend/src/api/types.ts`

**Intent**: Mirror the new nullable field from backend.

**Contract**: `GameStateView` interface gains `newHighestUnlockedStage: number | null`.

#### 3. AuthContext — expose progress update

**File**: `frontend/src/contexts/AuthContext.tsx`

**Intent**: Add a method for GameBoard to update the cached `highestUnlockedStage` after a win, so CampaignMap shows the new state immediately without re-fetching `/auth/me`.

**Contract**: Add `updateHighestUnlockedStage(stage: number): void` to the context interface. Implementation: update the `user` state's `highestUnlockedStage` field. Add to the `useMemo` dependency (it's a `useCallback`).

#### 4. CampaignMap — read from AuthContext

**File**: `frontend/src/pages/CampaignMap.tsx`

**Intent**: Replace `getHighestUnlocked()` (localStorage) with `user.highestUnlockedStage` from auth context.

**Contract**: Remove the `getHighestUnlocked()` import and call. Use `const { user } = useAuth()` (already available for logout button). Replace `highestUnlocked` with `user.highestUnlockedStage` in the stage status computation. `user` is guaranteed non-null here because CampaignMap is inside `ProtectedRoute`.

#### 5. GameBoard — update context on win

**File**: `frontend/src/pages/GameBoard.tsx`

**Intent**: Replace the `unlockStage(stageNumber + 1)` localStorage call with an AuthContext update using the `newHighestUnlockedStage` value from the API response.

**Contract**: In the existing `useEffect` that handles `phase === 'match-ended'` (line 56-63): instead of calling `unlockStage()`, read `gameState.newHighestUnlockedStage`. If non-null, call `updateHighestUnlockedStage(gameState.newHighestUnlockedStage)`. Remove the `unlockStage` import.

#### 6. Delete campaignProgress.ts

**File**: `frontend/src/utils/campaignProgress.ts`

**Intent**: Remove the localStorage utility file entirely. All references are replaced by server state.

**Contract**: Delete file. Verify no imports remain (CampaignMap and GameBoard were the only consumers).

#### 7. Add data-testid attributes for Playwright

**Files**: `frontend/src/pages/CampaignMap.tsx`, `frontend/src/pages/GameBoard.tsx`, `frontend/src/components/game/PlayerHand.tsx` (or wherever card + row elements are rendered)

**Intent**: Add `data-testid` attributes to key interactive elements so the Playwright E2E test in Phase 4 can reliably locate them without depending on CSS classes or text content.

**Contract**: At minimum:
- Stage markers on campaign map: `data-testid="stage-{id}"` with a `data-status` attribute (`active`/`locked`/`completed`)
- Cards in player hand: `data-testid="hand-card"` (or similar)
- Board rows (target areas): `data-testid="target-row-{CLOSE|RANGED|SIEGE}"`
- Pass button: `data-testid="pass-button"`
- Leader button: `data-testid="leader-button"`
- Match end screen: `data-testid="match-end-screen"` with `data-result` attribute (`victory`/`defeat`/`draw`)
- "Back to Campaign" button: `data-testid="back-to-campaign"`

### Success Criteria:

#### Automated Verification:

- `cd frontend && npm run build` — TypeScript compiles, no errors
- `cd frontend && npm run lint` — no lint errors
- No references to `campaignProgress.ts`, `getHighestUnlocked`, `unlockStage`, or `loxley_highest_unlocked` remain in `frontend/src/`

#### Manual Verification:

- Login → CampaignMap shows stage 1 active (from `/auth/me` response, not localStorage)
- Win stage 1 → campaign map immediately shows stage 2 active (no page refresh needed)
- Refresh page → stage 2 still active (session restored via `/auth/me`)
- Clear localStorage → stage 2 still active (proves server is source of truth)
- Open DevTools → no reads/writes to `loxley_highest_unlocked` key
- data-testid attributes present on key interactive elements (stage markers, hand cards, board rows, pass/leader buttons, match end screen)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Backend Integration Tests

### Overview

Add `CampaignProgressionTests` — a MockMvc + H2 integration test class covering Risk #1 (campaign progress persists across sessions) and Risk #2 (API contract shape assertion). Follows the `AuthControllerTests` pattern: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`, security filters enabled, JWT cookie extraction.

### Changes Required:

#### 1. CampaignProgressionTests

**File**: `backend/app/src/test/java/cards/loxley/app/web/CampaignProgressionTests.java`

**Intent**: Integration test class with 4-5 test methods covering campaign persistence and contract.

**Contract**:
- Class annotations: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`
- Autowire: `MockMvc`, `UserRepository`
- Helper: `registerAndGetJwt(username, password)` — registers via MockMvc, extracts JWT from Set-Cookie header (copy pattern from AuthControllerTests lines 115-116)
- Helper: `playGameToCompletion(jwt, stageNumber)` — creates a game, plays all available UNIT/SPY/ROW moves from legalMoves, then PASS. Loops until `matchEnded=true`. Returns the final `GameStateView` JSON (parsed enough to read `matchWinner` and `newHighestUnlockedStage`). Needs JSON parsing of MockMvc responses — use `com.fasterxml.jackson.databind.ObjectMapper` (autowired or `new ObjectMapper()`).

Test methods:

- `authMe_newUser_returnsHighestUnlockedStage1()` — register user, GET /auth/me with JWT → assert `$.highestUnlockedStage` is 1. Pins the contract shape (Risk #2).
- `authMe_contractShape_matchesExpectedFields()` — register user, GET /auth/me → assert response has exactly 2 fields: `username` (string) and `highestUnlockedStage` (number). No extra fields, no missing fields. This is the explicit contract assertion.
- `winStage1_freshSession_progressPersisted()` — **Risk #1 core test**:
  1. Register user, get JWT-A
  2. Play game to completion on stage 1 (using helper). If P1 wins: continue. If P1 loses: try again (up to 5 retries — ultra_easy RandomBot loses most games).
  3. Assert response contains `newHighestUnlockedStage: 2`
  4. Logout (POST /auth/logout)
  5. Login again → get JWT-B (fresh session)
  6. GET /auth/me with JWT-B → assert `highestUnlockedStage: 2`
  This proves progress survives session breaks via DB, not just in-memory state.
- `createGame_lockedStage_returns400()` — register user (stage=1), POST /api/games with stageNumber=2 → assert 400. POST with stageNumber=1 → assert 200.
- `createGame_replayCompletedStage_allowed()` — register user, update `highestUnlockedStage=3` via UserRepository, POST /api/games with stageNumber=1 → assert 200 (replay allowed).

### Success Criteria:

#### Automated Verification:

- `cd backend && ./mvnw test` — all tests pass including new `CampaignProgressionTests` (5 tests)
- `cd backend && ./mvnw -pl app test -Dtest=CampaignProgressionTests` — runs only the new test class, all green

#### Manual Verification:

- Review test output: `winStage1_freshSession_progressPersisted` completes within retry limit (typically 1st attempt)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Playwright E2E Bootstrap + Test

### Overview

Bootstrap Playwright in `frontend/`, configure for manual server startup (backend + Vite dev), write one E2E test: register → campaign map → play stage 1 → win → stage 2 unlocked → logout → login → stage 2 still unlocked.

### Changes Required:

#### 1. Install Playwright

**File**: `frontend/package.json` (modified by npm install)

**Intent**: Add Playwright as a dev dependency and install browser binaries.

**Contract**: `npm init playwright@latest` in `frontend/` — creates `playwright.config.ts`, `tests/` directory, installs `@playwright/test`. Choose Chromium only (lightweight, sufficient for one test). Add `test:e2e` script to package.json: `"test:e2e": "playwright test"`.

#### 2. Playwright config

**File**: `frontend/playwright.config.ts`

**Intent**: Configure Playwright to use the already-running Vite dev server and backend. No `webServer` auto-start — servers started manually per decision.

**Contract**:
- `baseURL: 'http://localhost:5173'`
- `use.headless: true` (CI-friendly default; `--headed` flag for local debugging)
- `testDir: './tests'`
- `projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]`
- No `webServer` config (manual startup)
- `retries: 2` — handles RNG in game outcomes

#### 3. E2E test — campaign progression across sessions

**File**: `frontend/tests/campaign-progression.spec.ts`

**Intent**: The "test from user perspective" for Risk #1 — proves campaign progress persists between sessions through the full stack (browser → Vite → Spring Boot → H2/Postgres → back). This is the test required for the 10xDevs Builder certification.

**Contract**: Single test file with one test case (and a game-playing helper):

- Helper `playGameToWin(page)`: plays a full game by clicking cards and rows. Strategy: click first available hand card → click first highlighted target row → wait for bot response → repeat until no cards → click Pass. Continues across rounds until match ends. Returns match result (victory/defeat/draw). If defeat, returns false so the test can retry.

- Test `campaign progression persists across sessions`:
  1. Generate unique username (e.g., `test_${Date.now()}`)
  2. Navigate to `/login`
  3. Fill register form, submit → redirected to `/` (CampaignMap)
  4. Assert stage 1 has `data-status="active"` and stage 2 has `data-status="locked"`
  5. Click stage 1 marker → navigate to `/game/1` → wait for game board to load
  6. Play game using `playGameToWin(page)` helper
  7. If match lost: click restart / back to campaign → try again (up to 3 attempts)
  8. After victory: match end screen shows "VICTORY"
  9. Click "Back to Campaign" → CampaignMap renders
  10. Assert stage 2 has `data-status="active"` (immediate — from `newHighestUnlockedStage` piggybacked in response)
  11. Click logout button
  12. Login with same credentials
  13. Assert stage 2 still has `data-status="active"` (proves DB persistence, not just in-memory)

### Success Criteria:

#### Automated Verification:

- `cd frontend && npx playwright test` — passes (with backend + Vite running)
- Test completes within 60 seconds (including game play + retries)

#### Manual Verification:

- Run `npx playwright test --headed` to watch the test visually — game plays through, campaign map updates

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding. This is the final phase — after success, update test-plan.md §3 Phase 1 status to `complete` and fill in §6.1 cookbook pattern.

---

## Testing Strategy

### Backend Integration Tests (Phase 3):

- `/auth/me` contract shape: exactly `{ username, highestUnlockedStage }` — no extra/missing fields
- Win → fresh session → progress persists: the core Risk #1 scenario
- Stage validation: locked stage → 400, unlocked stage → 200, replay completed → 200
- Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` + JWT cookie extraction

### E2E Test (Phase 4):

- Full user journey: register → play → win → logout → login → verify
- Real backend (Spring Boot) + real frontend (Vite)
- Game-playing helper with retry for RNG tolerance
- `data-testid` attributes for reliable element selection

### What's NOT Tested Here:

- Auth invariants (permitAll vs authenticated()) — Phase 2 of test-plan
- Ownership checks (IDOR) — Phase 2 of test-plan
- Engine modifier interactions — Phase 3 of test-plan
- CI pipeline integration — Phase 4 of test-plan (separate change)

## Post-Completion Tasks

After all 4 phases pass:
1. Update `context/foundation/test-plan.md` §3 row 1: set Status to `complete`, Change folder to `s-05-campaign-progression`
2. Fill in `test-plan.md` §6.1 (cookbook: "Adding a backend integration test") with location, naming, reference test, run command from the tests written in Phase 3
3. Add a note to §6.5 with anything surprising the rollout phase taught

## References

- Research: `context/changes/s-05-campaign-progression/research.md`
- Prior auth pattern: `backend/app/src/test/java/cards/loxley/app/web/AuthControllerTests.java:107-121` (JWT cookie extraction)
- GameController current code: `backend/app/src/main/java/cards/loxley/app/web/GameController.java`
- CampaignMap localStorage usage: `frontend/src/pages/CampaignMap.tsx:17`
- GameBoard win handler: `frontend/src/pages/GameBoard.tsx:56-63`
- Test plan Risk #1: `context/foundation/test-plan.md` §2 row 1
- Test plan Risk #2: `context/foundation/test-plan.md` §2 row 2

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — Persistence + API Changes

#### Automated

- [x] 1.1 `cd backend && ./mvnw compile` compiles cleanly
- [x] 1.2 `cd backend && ./mvnw test` — all existing 254+ tests pass

#### Manual

- [ ] 1.3 curl register returns `highestUnlockedStage: 1`
- [ ] 1.4 curl /auth/me returns `highestUnlockedStage`
- [ ] 1.5 Win stage 1 → response contains `newHighestUnlockedStage`
- [ ] 1.6 Create game for locked stage → 400

### Phase 2: Frontend — Server-Side Campaign State

#### Automated

- [x] 2.1 `cd frontend && npm run build` — TypeScript compiles
- [x] 2.2 No references to localStorage campaign progress remain in `frontend/src/`

#### Manual

- [ ] 2.3 Login → CampaignMap shows stage 1 from server state
- [ ] 2.4 Win stage 1 → stage 2 immediately active
- [ ] 2.5 Refresh page → stage 2 still active
- [ ] 2.6 Clear localStorage → progress still intact
- [ ] 2.7 Open DevTools → no reads/writes to loxley_highest_unlocked key
- [ ] 2.8 data-testid attributes present on key elements

### Phase 3: Backend Integration Tests

#### Automated

- [x] 3.1 `cd backend && ./mvnw test` — all tests pass including CampaignProgressionTests
- [x] 3.2 `cd backend && ./mvnw -pl app test -Dtest=CampaignProgressionTests` — 5 tests green

#### Manual

- [ ] 3.3 Review test: `winStage1_freshSession_progressPersisted` completes within retry limit

### Phase 4: Playwright E2E Bootstrap + Test

#### Automated

- [x] 4.1 `cd frontend && npx playwright test` passes (with servers running)
- [x] 4.2 Test completes within 60 seconds

#### Manual

- [ ] 4.3 `npx playwright test --headed` — visual confirmation of full flow
