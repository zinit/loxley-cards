# Playable Game API — Implementation Plan

## Overview

Expose the game engine (F-01) as REST API so a frontend can play a full match vs bot through HTTP. Backend: 3 endpoints (`POST /api/games`, `GET /api/games/{id}`, `POST /api/games/{id}/moves`) with view-model DTOs (hiding opponent hand), sync bot autoreply, inline legal moves, in-memory session store, CORS, error handling, integration tests. Frontend: fetch wrappers + thin `/debug-game` page to validate the contract end-to-end. Existing S-01 routes (`/`, `/game/:stageId`) stay untouched. Zero changes to `acommon-game-engine/`.

## Current State Analysis

- **Backend `app/` module**: bare `LoxleyCardsApplication` with `@SpringBootApplication` (no explicit `scanBasePackages` — scans `cards.loxley.*`, so engine beans under `cards.loxley.game.*` are auto-discovered). POM already has `spring-boot-starter-webmvc` + `loxley-cards-game-engine` dependency. No controllers, no config classes, `application.properties` has only `spring.application.name`. One passing test (`contextLoads`).
- **Engine API**: verified — `GameStateFactory`, `TurnOrchestrator`, `MoveGenerator`, `MoveValidator`, `CampaignStageRegistry`, `OpponentProfileRegistry`, `BotStrategyResolver`, `CardScorer`, `RowScorer`, `BoardScorer` all exist as `@Component` beans. Sealed `Move` hierarchy (`PlayCardMove`, `PassMove`, `UseLeaderMove`) with factory methods. `IllegalMoveException` extends `RuntimeException`. 200 engine tests green.
- **Frontend**: React 19 + React Router 7 + Vite 8 + Tailwind. Routes: `/` → `CampaignMap`, `/game/:stageId` → `GameBoard`. All data is mock (`MOCK_GAME_STATE` from `finalDeck.ts`). No API client, no fetch calls, no vite proxy.

### Key Discoveries:

- `LoxleyCardsApplication.java:6` — bare `@SpringBootApplication`, component scan covers `cards.loxley.*` including engine beans automatically
- `app/pom.xml:19` — `spring-boot-starter-webmvc` already present, no need to add
- `app/pom.xml:24` — `loxley-cards-game-engine` already a dependency
- Engine integration guide (`context/foundation/engine-integration-guide.md`) provides compiling sample controller, DTO design proposal, Move mapping, thread safety patterns, edge cases — written specifically for this slice
- `vite.config.ts` — no proxy configured; needs `/api` proxy to `localhost:8080`
- `frontend/src/main.tsx:9-10` — existing routes must not be modified (S-01 constraint)

## Desired End State

After this plan is complete:

1. `cd backend && ./mvnw -pl app spring-boot:run` starts Spring Boot on `:8080` with 3 working REST endpoints under `/api/games`.
2. `cd frontend && npm run dev` starts Vite on `:5173` with proxy forwarding `/api` to `:8080`.
3. Opening `http://localhost:5173/debug-game` shows a debug page where a human can play a full best-of-3 match vs bot: select campaign stage (1-10), see game state as formatted JSON, click legal move buttons, see bot auto-reply, see round/match results.
4. Existing S-01 routes (`/` campaign map, `/game/:stageId` mock board) continue to work unchanged.
5. `cd backend && ./mvnw -pl app test` passes with integration tests covering all endpoints.

**Verification**: play 2-3 full matches on the debug page across different stages, confirming all move types work (unit play, spy, special, horn row selection, leader, pass, decoy), round resolution is correct, and match ends properly.

## What We're NOT Doing

- **No changes to `acommon-game-engine/`** — engine is atomic foundation (F-01 done), S-02 only consumes its public API
- **No changes to S-01 UI** — `/game/:stageId` with `MOCK_GAME_STATE` stays untouched; `/debug-game` is a new parallel route
- **No WebSocket** — bot replies synchronously in the same HTTP response; "bot thinking" animation deferred to S-03
- **No persistence** — in-memory `GameSessionStore` (games lost on restart); database comes in F-02
- **No auth** — any client with a `gameId` UUID can play that game; auth comes in F-03/S-04
- **No deploy** — local `localhost:8080` + `localhost:5173` only
- **No pretty game UI** — debug page shows JSON + buttons, not cards/board; pretty UI is S-03

## Implementation Approach

Backend-first, then frontend. Phase 1 builds the data contract (DTOs + mapper) that both the controller and debug page depend on. Phase 2 builds the REST layer (controller + session store + config). Phase 3 adds integration tests to lock the contract. Phase 4 builds the frontend API client and debug page. Each phase is independently verifiable.

## Critical Implementation Details

### Component scan & bean discovery

`LoxleyCardsApplication` lives in `cards.loxley` package — Spring Boot's default component scan covers all `cards.loxley.*` sub-packages. Engine beans (`cards.loxley.game.*`) are auto-discovered without any explicit `scanBasePackages`. The `app/` POM already depends on `loxley-cards-game-engine`. No wiring configuration needed — just inject engine beans via constructor.

### Thread safety

Engine `GameState` is mutable and not thread-safe. The controller must serialize access per game. Pattern: `synchronized` on a per-game lock object from `ConcurrentHashMap`. At MVP scale (1 user, 1 game) this is sufficient. Integration guide §4.2 Option 1.

---

## Phase 1: Backend DTOs & Mapper

### Overview

Define the wire format (view-model records) and the mapper that converts engine domain objects to DTOs. This is the data contract that the REST controller (Phase 2) and frontend debug page (Phase 4) both depend on. Key design decision: `PlayerView` shows full hand; `OpponentView` shows only `handSize` (anti-cheat).

### Changes Required:

#### 1. Game state view-model records

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/GameStateView.java`

**Intent**: Top-level response DTO for every endpoint that returns game state. Contains the game ID, round/turn metadata, `you` (PlayerView), `opponent` (OpponentView), round history, legal moves, and match result. Legal moves are inlined so the client never needs a second request.

**Contract**: Record with fields: `gameId` (String), `roundNumber` (int), `currentTurn` (String — "P1"/"P2"), `yourTurn` (boolean), `matchEnded` (boolean), `matchWinner` (String nullable), `you` (PlayerView), `opponent` (OpponentView), `roundHistory` (List\<RoundResultView\>), `legalMoves` (List\<MoveView\>).

#### 2. Player and opponent view records

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/PlayerView.java`

**Intent**: Your side — shows full hand (list of CardInstanceView), board, deck/graveyard sizes, leader status, pass status, rounds won, total strength.

**Contract**: Record. `hand` is `List<CardInstanceView>` (full card details). `deckSize` and `graveyardSize` are int counts only.

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/OpponentView.java`

**Intent**: Opponent side — hides hand contents, shows only `handSize` count.

**Contract**: Record. `handSize` (int) instead of hand list. Board is fully visible (cards on board are public info in Gwint). Same deck/graveyard size, leader, pass, rounds won, strength fields as PlayerView.

#### 3. Board/row/card view records

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/BoardSideView.java`

**Intent**: Board state for one side — three rows plus total strength.

**Contract**: Record with `close`, `ranged`, `siege` (RowView) and `totalStrength` (int).

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/RowView.java`

**Intent**: Single row — list of units, weather/horn flags, row strength.

**Contract**: Record with `units` (List\<CardInstanceView\>), `weatherActive` (boolean), `hornActive` (boolean), `strength` (int).

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/CardInstanceView.java`

**Intent**: Single card instance on board or in hand — everything the frontend needs to render and identify the card.

**Contract**: Record with `instanceId` (String), `cardId` (String), `name` (String), `cardType` (String — "UNIT"/"SPECIAL"/"LEADER"), `row` (String nullable — "CLOSE"/"RANGED"/"SIEGE"), `basePower` (Integer nullable), `currentStrength` (int), `abilities` (List\<String\>), `playTarget` (String).

#### 4. Move view record

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/MoveView.java`

**Intent**: Structured representation of a legal move — enough for the debug page to render a descriptive button and for S-03 to filter by kind for UI logic.

**Contract**: Record with `kind` (String — "PASS"/"LEADER"/"UNIT"/"SPY"/"SPECIAL"/"ROW"/"UNIT_TARGET"), `handInstanceId` (String nullable), `cardName` (String nullable — for display), `targetRow` (String nullable), `targetInstanceId` (String nullable), `description` (String — human-readable from `MoveDescriber`). All enum-derived string values use UPPER case consistently across all DTOs (matching Java enum names: "P1"/"P2", "CLOSE"/"RANGED"/"SIEGE", "UNIT"/"SPECIAL"/"LEADER").

#### 5. Round result view record

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/RoundResultView.java`

**Intent**: Round history entry.

**Contract**: Record with `roundNumber` (int), `p1Score` (int), `p2Score` (int), `winner` (String nullable).

#### 6. Create game request record (client → server)

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/CreateGameRequest.java`

**Intent**: Inbound DTO for `POST /api/games`. Client sends the campaign stage number to start a new game.

**Contract**: Record with `stageNumber` (int).

#### 7. Move request record (client → server)

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/MoveRequest.java`

**Intent**: Inbound DTO for `POST /api/games/{id}/moves`. Client sends the move kind and relevant IDs.

**Contract**: Record with `kind` (String — "PASS"/"LEADER"/"UNIT"/"SPY"/"SPECIAL"/"ROW"/"UNIT_TARGET"), `handInstanceId` (String nullable), `targetRow` (String nullable), `targetInstanceId` (String nullable). Mapping to engine `Move` sealed types via switch on `kind` — per integration guide §6.5. Same UPPER casing convention as MoveView.

#### 8. Error response record

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/ErrorResponse.java`

**Intent**: Uniform error envelope for 400/404/409 responses.

**Contract**: Record with `code` (String — "ILLEGAL_MOVE"/"INVALID_STATE"/"NOT_FOUND"/"BAD_REQUEST"), `message` (String).

#### 9. GameStateMapper component

**File**: `backend/app/src/main/java/cards/loxley/app/web/GameStateMapper.java`

**Intent**: Converts engine `GameState` + game metadata into `GameStateView` for a given player perspective. Uses `CardScorer`, `RowScorer`, `BoardScorer` for live strength calculations. Converts engine `Move` list into `MoveView` list using `MoveDescriber` for descriptions and pattern-matching on sealed `Move` subtypes for `kind`/field extraction.

**Contract**: `@Component` with constructor-injected scoring beans + `MoveDescriber`. Main method: `GameStateView toView(GameState state, String gameId, Player perspective, List<Move> legalMoves)`. Helper methods for PlayerView, OpponentView, BoardSideView, RowView, CardInstanceView, MoveView conversions. Note: `CardScorer.currentStrength(card, row)` requires a `RowState` — for cards on the board, pass the containing row; for cards in hand (no row context), use `card.card().basePower()` directly as `currentStrength`.

### Success Criteria:

#### Automated Verification:

- Reactor builds: `cd backend && ./mvnw clean install` — zero compilation errors
- Existing tests pass: `cd backend && ./mvnw test` — 230+ tests green (200 engine + 29 cli + 1 app)

#### Manual Verification:

- Review DTO field names match what the debug page will consume (Phase 4)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Backend REST Controller & Infrastructure

### Overview

Build the REST controller (3 endpoints), in-memory game session store, CORS configuration, error handling, and application properties. This phase makes the API callable.

### Changes Required:

#### 1. GameSessionStore service

**File**: `backend/app/src/main/java/cards/loxley/app/web/GameSessionStore.java`

**Intent**: In-memory storage for active games. Maps `gameId` (UUID string) → `GameState` + `CampaignStage` metadata. Scheduled cleanup removes stale games after 30 minutes of inactivity.

**Contract**: `@Service` with `ConcurrentHashMap` backing. Methods: `String create(GameState state, CampaignStage stage)` → returns generated UUID; `GameState findOrThrow(String gameId)` → returns state or throws `GameNotFoundException`; `CampaignStage findStageOrThrow(String gameId)` → same; `Object lock(String gameId)` → per-game lock object for synchronized access. `@Scheduled(fixedDelay = 60_000)` cleanup method. Requires `@EnableScheduling` somewhere in config.

#### 2. GameController REST endpoints

**File**: `backend/app/src/main/java/cards/loxley/app/web/GameController.java`

**Intent**: Three REST endpoints that wire HTTP to the engine via the session store and mapper. Bot auto-replies synchronously in the move endpoint (loop until player's turn or match end).

**Contract**: `@RestController @RequestMapping("/api/games")`.

Endpoints:
- `POST /api/games` — accepts `CreateGameRequest` body (`{stageNumber: int}`), creates game via `GameStateFactory.newCampaignGame(stage)`, stores in session, calls `driveBotMoves` (bot may go first), returns `GameStateView` (perspective P1). Returns 400 if stage number invalid.
- `GET /api/games/{id}` — returns current `GameStateView` (perspective P1). Returns 404 if game not found.
- `POST /api/games/{id}/moves` — accepts `MoveRequest`, validates via `synchronized(lock)`, executes player move via `TurnOrchestrator.playTurn`, then loops bot moves (P2) until player's turn or match ends, returns updated `GameStateView`. Returns 400 for illegal move, 404 for unknown game, 409 for invalid state (match ended, not your turn).

Bot loop: extract a private `driveBotMoves(GameState state, String gameId)` method that runs the bot while it's P2's turn. Called from **both** `POST /api/games` (bot may start first — `GameStateFactory` randomizes via `rng.nextBoolean()`) and `POST /api/games/{id}/moves` (bot replies after player's move). Pattern (inside synchronized block):
```java
private void driveBotMoves(GameState state, String gameId) {
    while (!state.matchEnded() && state.currentTurn() == Player.P2) {
        List<Move> legal = generator.legalMoves(state, Player.P2);
        if (legal.isEmpty()) break;
        OpponentProfile profile = profileRegistry.findById(
            sessionStore.findStageOrThrow(gameId).opponentProfileId()).orElseThrow();
        BotStrategy bot = botResolver.resolve(profile.strategyName());
        Move botMove = bot.chooseMove(state, Player.P2, legal);
        orchestrator.playTurn(state, botMove);
    }
}
```

#### 3. Custom exceptions

**File**: `backend/app/src/main/java/cards/loxley/app/web/GameNotFoundException.java`

**Intent**: Thrown by `GameSessionStore.findOrThrow()` when a gameId doesn't exist.

**Contract**: `extends RuntimeException` with constructor taking `String gameId`.

**File**: `backend/app/src/main/java/cards/loxley/app/web/GameStateException.java`

**Intent**: Wraps `IllegalStateException` from engine calls (match ended, not your turn) so the global handler can distinguish engine state errors from Spring MVC's own `IllegalStateException`.

**Contract**: `extends RuntimeException` with constructor taking `String message` and `Throwable cause`.

#### 4. Global exception handler

**File**: `backend/app/src/main/java/cards/loxley/app/web/GlobalExceptionHandler.java`

**Intent**: Maps game-layer exceptions to proper HTTP status codes with `ErrorResponse` body.

**Contract**: `@RestControllerAdvice`. Handlers:
- `GameNotFoundException` → 404 with code `NOT_FOUND`
- `IllegalMoveException` → 400 with code `ILLEGAL_MOVE`
- `GameStateException` → 409 with code `INVALID_STATE` (custom wrapper — see below)
- `IllegalArgumentException` → 400 with code `BAD_REQUEST`

Note: do NOT catch bare `IllegalStateException` globally — Spring MVC itself throws it (e.g., `HttpMediaTypeNotAcceptableException`). Instead, wrap engine `IllegalStateException` in a custom `GameStateException` at the controller call site (catch `IllegalStateException` from `TurnOrchestrator.playTurn` → rethrow as `GameStateException`).

#### 5. CORS configuration

**File**: `backend/app/src/main/java/cards/loxley/app/web/WebConfig.java`

**Intent**: Allow Vite dev server (`localhost:5173`) to call the API during local development.

**Contract**: `@Configuration implements WebMvcConfigurer`. Override `addCorsMappings`: `/api/**` allows origins `http://localhost:5173`, methods GET/POST, credentials true.

#### 6. Application properties

**File**: `backend/app/src/main/resources/application.properties`

**Intent**: Add Jackson serialization config for clean JSON output.

**Contract**: Add `spring.jackson.serialization.write-dates-as-timestamps=false` and `spring.jackson.default-property-inclusion=non_null`. Keep existing `spring.application.name`.

#### 7. Enable scheduling

**File**: `backend/app/src/main/java/cards/loxley/app/web/WebConfig.java` (same file as CORS)

**Intent**: Enable `@Scheduled` for `GameSessionStore` cleanup.

**Contract**: Add `@EnableScheduling` annotation to `WebConfig`.

### Success Criteria:

#### Automated Verification:

- Reactor builds: `cd backend && ./mvnw clean install`
- All tests pass: `cd backend && ./mvnw test`
- App starts: `cd backend && ./mvnw -pl app spring-boot:run` boots without errors

#### Manual Verification:

- `curl -X POST http://localhost:8080/api/games -H 'Content-Type: application/json' -d '{"stageNumber":1}'` returns JSON with `gameId`, `roundNumber`, player hand, opponent `handSize`, legal moves
- `curl http://localhost:8080/api/games/{id}` returns current state
- `curl -X POST http://localhost:8080/api/games/{id}/moves -H 'Content-Type: application/json' -d '{"kind":"pass"}'` returns updated state with bot auto-reply
- Opponent hand is NOT visible in any response (only `handSize`)
- Invalid move returns 400 with `ILLEGAL_MOVE` code
- Unknown gameId returns 404

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Backend Integration Tests

### Overview

MockMvc integration tests covering all endpoints, error cases, and the bot auto-reply loop. These lock the API contract before the frontend is built.

### Changes Required:

#### 1. GameController integration tests

**File**: `backend/app/src/test/java/cards/loxley/app/web/GameControllerTests.java`

**Intent**: Integration tests using `@SpringBootTest` + `MockMvc` that exercise the full request-response cycle including JSON serialization, engine wiring, and error handling.

**Contract**: Test cases:
- `createGame_validStage_returnsGameState` — POST /api/games with `{stageNumber:1}`, expect 200, body has `gameId`, `roundNumber`=1, `you.hand` non-empty, `opponent.handSize` > 0, `legalMoves` non-empty
- `createGame_invalidStage_returns400` — POST with `{stageNumber:99}`, expect 400
- `getState_existingGame_returnsState` — create game, GET /api/games/{id}, expect 200 with matching `gameId`
- `getState_unknownGame_returns404` — GET /api/games/nonexistent, expect 404
- `makeMove_pass_returnsUpdatedState` — create game, POST move `{kind:"pass"}`, expect 200, verify state changed (bot may have replied)
- `makeMove_illegalMove_returns400` — POST an invalid move (e.g., wrong `handInstanceId`), expect 400 with `ILLEGAL_MOVE` code
- `makeMove_unknownGame_returns404` — POST move to nonexistent game, expect 404
- `opponentHandHidden` — create game, verify response has `opponent.handSize` but NO `opponent.hand` field

### Success Criteria:

#### Automated Verification:

- All integration tests pass: `cd backend && ./mvnw -pl app test`
- Full reactor still green: `cd backend && ./mvnw test`

#### Manual Verification:

- Review test coverage: all 3 endpoints covered, both happy and error paths

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Frontend API Client & Debug Page

### Overview

Add Vite API proxy, fetch wrappers, and a `/debug-game` route with a minimal functional debug page. Existing S-01 routes (`/`, `/game/:stageId`) stay completely untouched.

### Changes Required:

#### 1. Vite proxy configuration

**File**: `frontend/vite.config.ts`

**Intent**: Proxy `/api` requests from Vite dev server to Spring Boot backend so the frontend can call the API without CORS issues during development.

**Contract**: Add `server.proxy` config: `/api` → `http://localhost:8080`, `changeOrigin: true`.

#### 2. API client module

**File**: `frontend/src/api/gameApi.ts`

**Intent**: Typed fetch wrappers for the 3 game endpoints. Returns parsed JSON matching the backend DTO shape.

**Contract**: Three async functions:
- `createGame(stageNumber: number): Promise<GameStateView>` — POST /api/games
- `getGameState(gameId: string): Promise<GameStateView>` — GET /api/games/{id}
- `makeMove(gameId: string, move: MoveRequest): Promise<GameStateView>` — POST /api/games/{id}/moves

Plus TypeScript interfaces matching backend DTOs: `GameStateView`, `PlayerView`, `OpponentView`, `BoardSideView`, `RowView`, `CardInstanceView`, `MoveView`, `MoveRequest`, `RoundResultView`, `ErrorResponse`.

#### 3. API types file

**File**: `frontend/src/api/types.ts`

**Intent**: TypeScript interfaces matching backend DTOs. Shared between API client and debug page.

**Contract**: Interfaces for `GameStateView`, `PlayerView`, `OpponentView`, `BoardSideView`, `RowView`, `CardInstanceView`, `MoveView`, `MoveRequest`, `RoundResultView`, `ErrorResponse`. Field names and types mirror the Java records exactly.

#### 4. Debug game page

**File**: `frontend/src/pages/DebugGame.tsx`

**Intent**: Thin debug page for validating the API contract. Not pretty — functional. Shows JSON state dump with collapsible sections, clickable legal move buttons, stage selector dropdown, "New Game" button, round/match status header, error display.

**Contract**: React component at route `/debug-game`. Sections:
- Header: round number, current turn indicator, match status (ongoing/ended + winner)
- Stage selector: dropdown 1-10 + "New Game" button → calls `createGame(stage)`
- Your hand: text-only list of card names with power numbers (`<ul>` / `<div>`)
- Board: text-only lists of card names with power numbers per row, weather/horn flags as text labels — NO styled card components, NO visual board rendering, NO miniature S-03. Just names + numbers.
- Legal moves: list of buttons, each labeled with move description, clicking calls `makeMove` → re-renders with new state
- Round history: list of past rounds with scores
- JSON dump: collapsible `<pre>` with `JSON.stringify(state, null, 2)` as fallback/full view
- Error banner: shows API errors when they occur

**Anti-drift constraint**: this page is a debug tool, not a game UI. Zero styled card components, zero layout grids mimicking the game board, zero CSS beyond basic readability. If it looks like a miniature GameBoard, scope has crept. S-03 owns the pretty UI.

#### 5. Route registration

**File**: `frontend/src/main.tsx`

**Intent**: Add `/debug-game` route alongside existing routes. Do NOT modify existing routes.

**Contract**: Add `{ path: '/debug-game', element: <DebugGame /> }` to the `createBrowserRouter` array. Import `DebugGame` from `./pages/DebugGame`. Existing `/` and `/game/:stageId` routes unchanged.

### Success Criteria:

#### Automated Verification:

- Frontend builds: `cd frontend && npm run build` — no TypeScript errors
- Frontend lints: `cd frontend && npm run lint` — no lint errors

#### Manual Verification:

- Start backend (`cd backend && ./mvnw -pl app spring-boot:run`) and frontend (`cd frontend && npm run dev`)
- Navigate to `http://localhost:5173/debug-game` — page loads, stage selector visible
- Click "New Game" with stage 1 — game state appears, legal moves shown as buttons
- Click a legal move button — state updates, bot auto-replies, new legal moves appear
- Play through a full best-of-3 match — round transitions work, match ends correctly
- Navigate to `http://localhost:5173/` — campaign map still works (S-01 untouched)
- Navigate to `http://localhost:5173/game/1` — mock game board still works (S-01 untouched)
- Test at least 2 different stages to verify campaign stage selection works
- Test all move types during play: unit play, pass, leader ability, special card (weather/horn)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- None planned — the mapper and session store are exercised through integration tests. Engine logic has its own 200 tests.

### Integration Tests:

- MockMvc tests in `GameControllerTests.java` covering all 3 endpoints
- Happy path: create game, get state, make valid move with bot autoreply
- Error paths: invalid stage, unknown game, illegal move, match-ended state
- Contract validation: opponent hand hidden, legal moves present, field types correct

### Manual Testing Steps:

1. Start backend + frontend, open `/debug-game`
2. Play a full match on stage 1 (easiest bot) — verify round resolution, match end
3. Play a match on stage 5+ (harder bot) — verify different bot strategies work
4. Verify all move types: unit play to each row, pass, leader ability, spy, weather card, horn card, decoy (if available in hand)
5. Verify error handling: try to move after match ends (should show error)
6. Verify S-01 routes still work unchanged

## Performance Considerations

- Bot reply is synchronous in the same HTTP request. NFR requires < 2s. Engine bot is CPU-only (no I/O), measured at sub-100ms in CLI eval. No risk.
- `MoveGenerator.legalMoves` is recalculated on every response. Pure function, no caching needed at MVP scale.
- `GameSessionStore` cleanup runs every 60s, evicts games idle > 30min. `ConcurrentHashMap` — no contention at MVP scale.

## Migration Notes

Not applicable — no existing data to migrate. This is a greenfield addition to `app/` module.

## References

- Engine integration guide: `context/foundation/engine-integration-guide.md` (written for this slice)
- Roadmap S-02 spec: `context/foundation/roadmap.md` lines 136-148
- Engine public API surface: `backend/acommon-game-engine/src/main/java/cards/loxley/game/`
- Sample controller: integration guide §8 (~70 lines compiling code)
- DTO design proposal: integration guide §6.4
- Move mapping: integration guide §6.5
- Error handling: integration guide §7.1
- Thread safety: integration guide §4.2 (Option 1 — per-game synchronized lock)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend DTOs & Mapper

#### Automated

- [x] 1.1 Reactor builds: `cd backend && ./mvnw clean install`
- [x] 1.2 All tests pass: `cd backend && ./mvnw test`

#### Manual

- [x] 1.3 Review DTO field names match frontend consumption expectations

### Phase 2: Backend REST Controller & Infrastructure

#### Automated

- [x] 2.1 Reactor builds: `cd backend && ./mvnw clean install`
- [x] 2.2 All tests pass: `cd backend && ./mvnw test`
- [x] 2.3 App starts: `cd backend && ./mvnw -pl app spring-boot:run`

#### Manual

- [x] 2.4 curl POST /api/games returns valid GameStateView with hidden opponent hand
- [x] 2.5 curl GET /api/games/{id} returns current state
- [x] 2.6 curl POST /api/games/{id}/moves with pass returns updated state with bot reply
- [x] 2.7 Invalid move returns 400, unknown game returns 404

### Phase 3: Backend Integration Tests

#### Automated

- [x] 3.1 All integration tests pass: `cd backend && ./mvnw -pl app test`
- [x] 3.2 Full reactor green: `cd backend && ./mvnw test`

#### Manual

- [x] 3.3 Review test coverage: all endpoints covered, happy + error paths

### Phase 4: Frontend API Client & Debug Page

#### Automated

- [x] 4.1 Frontend builds: `cd frontend && npm run build`
- [x] 4.2 Frontend lints: `cd frontend && npm run lint`

#### Manual

- [x] 4.3 Debug page loads at /debug-game, stage selector works
- [x] 4.4 Full match playable through debug page (all move types, round transitions, match end)
- [x] 4.5 S-01 routes (/ and /game/:stageId) still work unchanged
- [x] 4.6 Multiple stages tested (different bot difficulties)
