# Playable Game API — Plan Brief

> Full plan: `context/changes/s-02-playable-game-api/plan.md`

## What & Why

Expose the game engine (F-01) as REST API so a frontend client can play a full Gwint match vs bot through HTTP. This is the first client-server integration — proving the engine works over the wire before connecting the pretty UI in S-03. A thin debug page validates the contract cheaply.

## Starting Point

Backend `app/` module is a bare Spring Boot 4.0.6 skeleton — `LoxleyCardsApplication` with no controllers, no config. POM already has `spring-boot-starter-webmvc` + game engine dependency. Engine (F-01) is complete with 200 tests: `GameStateFactory`, `TurnOrchestrator`, `MoveGenerator`, sealed `Move` hierarchy, 3 bot strategies, campaign stages. Frontend has mock-only routes (`/` campaign map, `/game/:stageId` mock board) — no API client exists.

## Desired End State

A developer starts backend (`:8080`) and frontend (`:5173`), opens `/debug-game`, selects a campaign stage, and plays a full best-of-3 match vs bot by clicking legal move buttons. State updates include bot auto-replies. The API returns view-model DTOs (opponent hand hidden). Existing S-01 UI continues working unchanged alongside the debug page.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
|---|---|---|
| DTO design | View-model DTOs (PlayerView + OpponentView) | Anti-cheat correct from day 1; S-03 consumes same contract without rewrite |
| Bot reply flow | Synchronous in same response | One round-trip, simplest client code; bot is <2s per NFR |
| Legal moves delivery | Inline in state response | Client always has everything needed to render an actionable board in one request |
| Legal move format | Structured move objects with kind/fields | Debug page renders labels directly; S-03 can filter by kind for UI logic |
| Debug page scope | Minimal functional (JSON dump + buttons + stage selector) | Enough to validate contract; ~100 lines React, not S-03 territory |
| Game start contract | Stage number only | Matches PRD (campaign-only); server owns game configuration |
| Testing | MockMvc integration tests | Catches serialization, HTTP codes, error handling at the web boundary |

## Scope

**In scope:**
- 3 REST endpoints: `POST /api/games`, `GET /api/games/{id}`, `POST /api/games/{id}/moves`
- View-model DTOs hiding opponent hand
- In-memory GameSessionStore with 30-min cleanup
- CORS config for local dev
- Error handling (IllegalMoveException → 400, unknown game → 404, invalid state → 409)
- Frontend: Vite proxy, fetch wrappers, TypeScript types, `/debug-game` page
- Integration tests (MockMvc)

**Out of scope:**
- Changes to game engine (`acommon-game-engine/`)
- Changes to S-01 UI (existing routes untouched)
- WebSocket / bot thinking animation
- Database persistence (F-02)
- Auth (F-03/S-04)
- Deployment
- Pretty game UI (S-03)

## Architecture / Approach

```
Frontend (Vite :5173)              Backend (Spring Boot :8080)
┌──────────────────┐               ┌─────────────────────────────┐
│ /debug-game      │──/api proxy──▶│ GameController              │
│  - stage selector│               │   POST /api/games           │
│  - JSON state    │               │   GET  /api/games/{id}      │
│  - move buttons  │               │   POST /api/games/{id}/moves│
│                  │               │         │                   │
│ src/api/         │               │    GameSessionStore          │
│  - gameApi.ts    │               │    (ConcurrentHashMap)       │
│  - types.ts      │               │         │                   │
└──────────────────┘               │    GameStateMapper ──▶ DTOs  │
                                   │         │                   │
Existing S-01 routes               │    Engine beans (F-01)      │
(/, /game/:stageId)                │    (auto-discovered)        │
remain untouched                   └─────────────────────────────┘
```

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Backend DTOs & Mapper | Data contract (view-model records + mapper) | Field naming mismatch with frontend expectations |
| 2. Backend REST & Infrastructure | Callable API (controller + session store + CORS + error handling) | Thread safety on concurrent access to GameState |
| 3. Backend Integration Tests | Contract locked via MockMvc tests | Test context startup with engine beans |
| 4. Frontend API Client & Debug Page | End-to-end validation via debug page | Vite proxy config; TypeScript type sync with Java records |

**Prerequisites:** F-01 (game engine) done. Backend and frontend dev environments working (`./mvnw spring-boot:run`, `npm run dev`).
**Estimated effort:** ~1-2 sessions across 4 phases.

## Open Risks & Assumptions

- In-memory session store means games are lost on backend restart — accepted for local MVP
- No auth — anyone with a gameId UUID can play that game — accepted for local dev
- `app/` POM depends on `loxley-cards-db` (empty scaffold) — if F-02 adds JPA before S-02 ships, app startup may require a datasource; sequence accordingly
- Assumes P1 is always the human player — engine supports P1/P2 symmetrically but API hardcodes perspective to P1

## Success Criteria (Summary)

- A full best-of-3 match is playable through the debug page via REST API
- Opponent hand is never visible in API responses
- All existing S-01 routes continue working unchanged
