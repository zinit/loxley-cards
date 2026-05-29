# Polished Game UI — Plan Brief

> Full plan: `context/changes/s-03-polished-game-ui/plan.md`

## What & Why

Connect the static game UI from S-01 to the REST API from S-02, turning the visual prototype into a fully playable card game. This is the roadmap's north star — the first time a player can click cards, see the bot respond, win rounds, and progress through the campaign. Without this, all prior work (engine, API, UI shell) remains disconnected.

## Starting Point

- **GameBoard.tsx** renders a mock board from `MOCK_GAME_STATE` (random cards, hardcoded scores, no interactivity)
- **API layer** (`gameApi.ts` + `types.ts`) is ready with typed `createGame()`, `getGameState()`, `makeMove()` wrappers — proven working via the debug page
- **Card images** are 28 WebP files imported in `finalDeck.ts`, keyed by `id` that matches backend's `cardId`
- **CampaignMap** has 10 stages with hardcoded statuses — no persistence
- **No state management** exists in the frontend

## Desired End State

Player opens the game, sees the campaign map with dynamically unlocked stages (persisted in localStorage). Clicks an unlocked stage → board animates in → real game begins via API → player clicks cards, sees legal targets glow, submits moves → bot responds after a brief "thinking" pause → weather/horn effects are visually indicated → round-end banners and match-end overlays guide the flow → winning unlocks the next stage. The debug page is gone.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
|---|---|---|
| Card play interaction | Click-to-select + click-target (2 clicks) | Precise and consistent across all card types; matches Gwent's tactical pace. |
| State management | useReducer + React Context | Zero new dependencies; reducer maps naturally to game state machine. |
| Component structure | Split as we integrate | Extract BoardRow, MetaPanel, PlayerHand, overlays during integration for manageable file sizes. |
| Optimistic vs pessimistic UI | Pessimistic (wait for API) | Always shows truth; no rollback complexity; bot reply included in same response. |
| Bot turn visualization | Brief pause (600ms) + state update | Simple — one state update; pause gives cognitive space without animation complexity. |
| Card image resolution | 28-entry backend-cardId→WebP static map | IDs don't match (Polish kebab vs English snake_case) — hand-built mapping table, pure frontend. |
| Special card targeting | Inline highlights on valid targets | Consistent with click-to-select model; uses legalMoves from API directly. |
| Round-end display | Center banner with scores, auto-dismiss 3s | Quick, non-intrusive, keeps gameplay flowing. |
| Match-end display | Full overlay with result + Play Again / Back to Campaign | Clear closure; covers FR-010 (restart) and FR-011 (replay). |
| Campaign persistence | localStorage `highest_unlocked` number | Dead simple for linear 10-stage progression; S-05 adds server-side. |
| Error handling | Simple error banner for all errors | One error path — banner with dismiss; sufficient for MVP. |
| Debug page | Remove | Polished game UI replaces its purpose entirely. |
| PASS/LEADER UI | Persistent action buttons below hand | Always discoverable; no hidden interactions. |
| Row effect visuals | Weather tint + horn glow + power color-coding | Critical for gameplay understanding — player must see why scores changed. |
| Turn state indicator | Disable hand + "Bot thinking..." text | Clear turn boundary; player knows to wait. |

## Scope

**In scope:**
- Wire GameBoard to API (create, render, play moves, receive state)
- All 7 move kinds: UNIT, SPY, SPECIAL, ROW, UNIT_TARGET, PASS, LEADER
- Card selection → targeting → submission flow with cancel (ESC)
- Weather/horn row visual indicators + power color-coding
- Round-end banner + match-end overlay
- Campaign stage unlocking in localStorage
- Component extraction (BoardRow, MetaPanel, PlayerHand, overlays)
- Debug page removal + mock data cleanup

**Out of scope:**
- Drag-and-drop, optimistic UI, WebSocket, individual bot move animations
- Server-side campaign persistence (S-05), auth (S-04), mobile/responsive
- Keyboard accessibility, deck building, faction choice

## Architecture / Approach

Pure frontend change — no backend modifications. `useReducer` + React Context manages a state machine: `idle → loading → selecting-target → waiting-for-bot → round-ended → match-ended`. API calls are pessimistic (wait for response before updating). `cardImageMap.ts` bridges API's `cardId` strings to bundled WebP images. Campaign progression stored as a single `loxley_highest_unlocked` number in localStorage.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. State & Data Foundation | Reducer, context, image map, localStorage util | Data shape mismatch between API and UI types |
| 2. Core Gameplay Loop | Real game via API, card select → play → bot reply | First real integration — API/engine bugs surface here |
| 3. Targeting & Special Cards | Horn/decoy/weather/scorch targeting flows | Complex move-kind branching logic |
| 4. Visual Feedback & Effects | Weather/horn indicators, power colors, bot pause, errors | CSS overlay complexity for row effects |
| 5. Overlays & Campaign Integration | Round/match overlays, stage unlocking, debug page removal | State transitions between overlays and gameplay |
| 6. Polish & Cleanup | Component extraction, animation refinement, playtesting | Visual regressions from S-01's polish level |

**Prerequisites:** Backend running (`cd backend && ./mvnw -pl app spring-boot:run`), S-01 and S-02 both done (confirmed).
**Estimated effort:** ~3-4 sessions across 6 phases.

## Open Risks & Assumptions

- **Card ID mapping correctness** — 28-entry hand-built map from backend IDs (English snake_case) to frontend WebP imports (Polish kebab-case). If a backend card ID is misspelled in the map, that card shows placeholder. Verifiable in Phase 2.
- **Bot deck cards not in FINAL_DECK** — opponent may use cards with cardIds not present in the player's 28-card `FINAL_DECK`. These will show robin-placeholder. Acceptable for MVP (both sides use the same card pool in practice).
- **In-memory session store 30-min timeout** — long idle periods during a match will lose the game. Acceptable for MVP. Error banner handles this gracefully.

## Success Criteria (Summary)

- Play 5-10 full matches across stages 1-3 using all card types — game feels like a product, not a prototype
- Campaign progression persists across page reloads
- Bot responds within 2 seconds consistently (NFR)
