# Campaign Progression + Phase 1 Test Coverage — Plan Brief

> Full plan: `context/changes/s-05-campaign-progression/plan.md`
> Research: `context/changes/s-05-campaign-progression/research.md`

## What & Why

Wire the existing `highest_unlocked_stage` DB column (ready since V1, never written) to the game completion flow — so campaign progress persists in PostgreSQL, not localStorage. This is the final MVP slice (S-05) and the foundation for Phase 1 test coverage: proving progress survives session breaks (Risk #1) and pinning the API contract shape (Risk #2).

## Starting Point

Schema column `highest_unlocked_stage` exists since V1 but zero code writes to it. `/auth/me` returns only `{ username }`. Frontend uses `localStorage('loxley_highest_unlocked')` for campaign state. All game endpoints are `permitAll` with no user awareness. Frontend has zero tests.

## Desired End State

User wins stage 1 → progress persists to DB → stage 2 unlocked even after logout/login/browser restart. `/auth/me` returns `{ username, highestUnlockedStage }`. Frontend reads from server, not localStorage. A backend integration test proves persistence across sessions. A Playwright E2E test exercises the full user journey.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|----------|--------|-------------------|--------|
| Where to persist match win | Inline in `makeMove()` | Zero extra round-trip; impossible to forget to call; match result and persistence in same request | Plan |
| How frontend learns updated progress | `newHighestUnlockedStage` field piggybacked on `GameStateView` | Arrives in the same response that says "you won" — no extra fetch | Plan |
| Campaign progress endpoint | Extend existing `/auth/me` (no separate endpoint) | Frontend already calls `/auth/me` on refresh; one round-trip wins | Research → confirmed in Plan |
| Stage-unlock validation | `stageNumber <= highestUnlockedStage` | Correct semantics — `highestUnlockedStage=1` means only stage 1 is playable | Plan |
| Auth in GameController | Graceful — skip validation/persistence for anonymous users | Keeps `GameControllerTests` (addFilters=false) passing; dev compatibility | Plan |
| Playwright environment | Manual servers (dev starts backend + Vite), no auto-start | Simplest setup; CI orchestration is Phase 4 scope | Plan |
| Contract test scope | `/auth/me` shape only | Pins exactly what S-05 adds; GameStateView already tested in GameControllerTests | Plan |
| localStorage | Full removal | Server is authoritative; "frontend stays dumb" principle | Research → confirmed in Plan |

## Scope

**In scope:**
- Backend: extend AuthResponse, persist wins in makeMove(), stage validation in createGame()
- Frontend: replace localStorage with AuthContext server state, add data-testid attributes
- Tests: CampaignProgressionTests (MockMvc + H2), /auth/me contract assertion, one Playwright E2E test

**Out of scope:**
- SecurityConfig hardening (Phase 2), engine interaction tests (Phase 3), CI/CD wiring (separate change)
- Ownership checks (IDOR), secret leak tests, frontend unit tests (vitest)

## Architecture / Approach

```
Win in makeMove() → User.highestUnlockedStage persisted to DB
                  → newHighestUnlockedStage piggybacked on GameStateView response
                  → Frontend AuthContext updated → CampaignMap renders from context

/auth/me on login → { username, highestUnlockedStage } → AuthContext → CampaignMap
```

No new tables, no new endpoints, no new migrations. One field added to each of two existing DTOs. One helper method in GameController. Frontend deletes code (localStorage utilities) rather than adding complexity.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|-------|-----------------|----------|
| 1. Backend persistence + API | AuthResponse extended, wins persisted, stage validation | Existing GameControllerTests must pass unchanged (graceful auth check) |
| 2. Frontend server state | localStorage replaced with AuthContext, data-testid attributes | Campaign map must reflect server state immediately after win (no stale cache) |
| 3. Backend integration tests | 5 tests: contract, persistence, validation | Playing a game to completion in MockMvc requires JSON response parsing + retry for RNG |
| 4. Playwright E2E | Full user journey test with game-playing helper | Game outcomes are stochastic — retry mechanism needed for reliability |

**Prerequisites:** Backend + Vite dev server running for Phase 4. No external dependencies needed.

## Open Risks & Assumptions

- Game-playing in tests (Phases 3 + 4) depends on beating ultra_easy RandomBot — retry mechanisms handle RNG but tests are not fully deterministic
- `GameControllerTests` use `addFilters=false` — the graceful auth check pattern must preserve this or those tests break
- Stage 10 win edge case: capped at `highestUnlockedStage=10` (no stage 11 exists)

## Success Criteria (Summary)

- `cd backend && ./mvnw test` passes with new CampaignProgressionTests (5 tests)
- `cd frontend && npx playwright test` passes end-to-end (login → win → logout → login → stage 2 unlocked)
- Manual: clear localStorage, login — campaign progress intact from DB
