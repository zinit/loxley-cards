# Polished Game UI — Implementation Plan

## Overview

Connect the static game UI from S-01 to the REST API from S-02, turning the visual prototype into a fully playable Gwent-inspired card game. This is the north star of the roadmap — the first time a player can click cards, see the bot respond, win rounds, and progress through the campaign.

## Current State Analysis

**Frontend (S-01):**
- `GameBoard.tsx` (255 lines, monolithic) renders a mock game board from `MOCK_GAME_STATE` — random cards from `FINAL_DECK`, hardcoded scores, no interactivity beyond entrance/exit animations
- `CampaignMap.tsx` reads from hardcoded `CAMPAIGN_STAGES` array (stages 1-3 active, 4-10 locked) — no persistence
- `Card.tsx` component accepts `FinalCard` props (S-01 shape: `id`, `name`, `power`, `row`, `image`, `ability`)
- No state management, no API calls from GameBoard, no gameplay interaction

**Backend (S-02):**
- 3 REST endpoints: `POST /api/games`, `GET /api/games/{id}`, `POST /api/games/{id}/moves`
- Returns `GameStateView` with `PlayerView` (full hand), `OpponentView` (handSize only, anti-cheat), `legalMoves: MoveView[]`
- Bot auto-replies synchronously in the same response
- Move kinds: `PASS`, `LEADER`, `UNIT`, `SPY`, `SPECIAL`, `ROW`, `UNIT_TARGET`
- In-memory session store with 30-min cleanup

**Key gap:** S-01 uses `FinalCard` type (with bundled WebP `image` field), S-02 API returns `CardInstanceView` (with `cardId` string, no image). An adapter layer + image lookup map bridges these.

## Desired End State

Player opens `loxley.cards`, sees the campaign map. Clicks an unlocked stage → game board animates in → `POST /api/games` creates a game → board renders real state from API → player clicks a card in hand → legal target rows glow → player clicks a row → `POST /api/games/{id}/moves` → board updates with bot's reply after a brief "thinking" pause → play continues until best-of-3 resolves → round-end banners and match-end overlay → campaign stage unlocked in localStorage → player returns to map.

**Verification:** Play 5-10 full matches across stages 1-3. Confirm all move types work (unit, spy, special, horn, decoy, scorch, weather, clear weather, leader, pass). Confirm round/match end flows. Confirm campaign unlocking persists across page reloads.

### Key Discoveries:

- API `MoveView.kind` values are UPPER case: `PASS`, `LEADER`, `UNIT`, `SPY`, `SPECIAL`, `ROW`, `UNIT_TARGET` — frontend `MoveRequest.kind` must match exactly
- `CardInstanceView.cardId` maps to `FinalCard.id` in `finalDeck.ts` — this is the bridge for image lookup
- `RowView` has `weatherActive: boolean` and `hornActive: boolean` — row-level effect indicators are directly available from API
- `CardInstanceView.currentStrength` vs `basePower` — difference indicates active modifiers, usable for color-coding power numbers
- Bot moves come bundled in the same response (sync autoreply) — no need for polling or WebSocket
- `legalMoves` list is empty when match has ended — use `matchEnded` flag to trigger end screen

## What We're NOT Doing

- **No drag-and-drop** — click-to-select only
- **No optimistic UI** — wait for API response before updating board
- **No individual bot move animations** — show brief pause then final state
- **No WebSocket** — REST request-response is sufficient for sync bot
- **No server-side campaign persistence** — localStorage only (S-05 adds Supabase)
- **No mobile/responsive** — desktop-only per PRD
- **No keyboard accessibility** — mouse interaction only for MVP
- **No deck building / faction choice** — prebuilt decks per campaign stage

## Implementation Approach

Six phases, each producing a testable increment:

1. **State & Data Foundation** — build the data layer (reducer, context, image map, localStorage) without changing the visual appearance
2. **Core Gameplay Loop** — wire GameBoard to API, render real state, implement the basic play-a-card flow
3. **Targeting & Special Cards** — handle cards that need row or unit targeting (horn, decoy, weather)
4. **Visual Feedback & Effects** — weather/horn row indicators, power color-coding, bot thinking state, error banner
5. **Overlays & Campaign Integration** — round-end banner, match-end screen, stage unlocking, debug page removal
6. **Polish & Cleanup** — component extraction, animation refinement, playtesting

## Critical Implementation Details

### Card ID mapping

The backend card IDs (from `robin_logic.json`) use English snake_case (e.g. `"little_john"`, `"sherwood_horn"`, `"leader_robin_hood_sherwood_hunter"`). The frontend `FINAL_DECK` in `finalDeck.ts` uses Polish kebab-case (e.g. `"maly-john"`, `"rog"`, `"robin-hood-leader"`). **These do NOT match** — zero overlap. The image lookup map must be a static 28-entry table mapping backend `cardId` → imported WebP URL (e.g. `"little_john" → malyJohnImage`). If the API returns a `cardId` not in the map (e.g. opponent's cards from a different deck variant), fall back to `robin-placeholder.webp`.

### Move submission logic

The frontend determines which `MoveRequest` to send based on the `MoveView` objects from `legalMoves`. The interaction flow:

1. Player clicks a card in hand → filter `legalMoves` by `handInstanceId` matching the clicked card's `instanceId`
2. Group filtered moves by what targeting they need:
   - If all filtered moves have `kind: "SPECIAL"` (no targeting) → submit immediately
   - If moves have `targetRow` set → highlight those rows, wait for row click
   - If moves have `targetInstanceId` set → highlight those units on board, wait for unit click
3. Player clicks target → find the matching `MoveView` → send as `MoveRequest`

This means the UI doesn't need to understand card abilities — it follows `legalMoves` blindly. The engine/API is the source of truth for what's legal.

---

## Phase 1: State & Data Foundation

### Overview

Build the data infrastructure: game state reducer, React context, card image lookup map, localStorage persistence utility, and the adapter that maps API types to what components need. No visual changes yet — GameBoard still renders, just backed by new types.

### Changes Required:

#### 1. Card image lookup map

**File**: `frontend/src/data/cardImageMap.ts` (new)

**Intent**: Create a static 28-entry mapping from backend card IDs (English snake_case from `robin_logic.json`) to the corresponding WebP image imports from `finalDeck.ts`. Backend IDs don't match frontend IDs (e.g. `"little_john"` vs `"maly-john"`), so this is a hand-built lookup table, not a programmatic derivation from `FINAL_DECK`. Export a `getCardImage(cardId: string): string` function with fallback to robin-placeholder.

**Contract**: `getCardImage(cardId: string): string` — takes a backend `cardId` (e.g. `"little_john"`), returns the WebP URL, or robin-placeholder URL if not found. The 28 mappings must be verified against `backend/acommon-game-engine/src/main/resources/data/sherwood_reference_ruleset.json` card IDs.

#### 2. Game state reducer and context

**File**: `frontend/src/hooks/useGameReducer.ts` (new)

**Intent**: Define the game state shape, action types, and reducer function. State includes: `gameState: GameStateView | null`, `selectedCardInstanceId: string | null`, `validTargets: MoveView[]`, `phase: 'idle' | 'loading' | 'selecting-target' | 'waiting-for-bot' | 'round-ended' | 'match-ended'`, `error: string | null`, `roundResult: RoundResultView | null`. Actions cover the full lifecycle: `GAME_LOADED`, `CARD_SELECTED`, `CARD_DESELECTED`, `MOVE_SUBMITTING`, `MOVE_RESULT`, `ROUND_DISMISSED`, `RESTART_GAME` (resets state for "Play Again" — clears gameState/selection/error and returns phase to `'loading'` so caller can re-issue `createGame()`), `ERROR`, `ERROR_DISMISSED`.

**Contract**: `useGameReducer()` hook returns `[state, dispatch]`. The reducer is a pure function mapping `(GameUIState, GameAction) → GameUIState`.

#### 3. Game context provider

**File**: `frontend/src/contexts/GameContext.tsx` (new)

**Intent**: Wrap the reducer in a React context so child components (BoardRow, PlayerHand, MetaPanel) can access game state and dispatch without prop drilling.

**Contract**: `GameProvider` component wraps children; `useGame()` hook returns `{ state, dispatch }`.

#### 4. Campaign persistence utility

**File**: `frontend/src/utils/campaignProgress.ts` (new)

**Intent**: Read/write `loxley_highest_unlocked: number` in localStorage. On first visit defaults to 1 (stage 1 unlocked). Provides `getHighestUnlocked(): number` and `unlockStage(stageId: number): void` (sets to `max(current, stageId)`).

**Contract**: `getHighestUnlocked(): number`, `unlockStage(stageId: number): void`.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npm run build` succeeds with no TypeScript errors
- All new files are importable — no circular dependencies

#### Manual Verification:

- GameBoard still renders the existing mock UI (no visual regression)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Core Gameplay Loop

### Overview

Wire GameBoard to the API. On route entry, create a game via `POST /api/games`. Render the real game state. Implement the click-to-select card → click row → submit move flow for basic UNIT cards. Add PASS and LEADER buttons.

### Changes Required:

#### 1. Update Card component for API data

**File**: `frontend/src/components/game/Card.tsx`

**Intent**: Adapt Card.tsx props from S-01's `FinalCard` shape to the API's `CardInstanceView` shape. Card should accept `cardId` (for image lookup via `getCardImage()`), `name`, `currentStrength`, `basePower`, `row`, and render accordingly. Remove dependency on `FinalCard` type. The `CardRow` type (`'CLOSE' | 'RANGED' | 'SIEGE'`) can be defined locally or in a shared types file instead of importing from `finalDeck.ts`.

**Contract**: Card props: `cardId: string`, `name: string`, `currentStrength: number`, `basePower: number | null`, `row: string | null`, `size?: number`, `onClick?: () => void`, `selected?: boolean`, `isValidTarget?: boolean`. Card uses `getCardImage(cardId)` internally for the image.

#### 2. GameBoard integration with API and reducer

**File**: `frontend/src/pages/GameBoard.tsx`

**Intent**: Replace `MOCK_GAME_STATE` usage with real API calls. On mount (after entrance animation), call `createGame(stageNumber)` using the `:stageId` route param. Store the response in the reducer via `GAME_LOADED` dispatch. Render board rows, hand, and meta panels from `GameStateView` data — Card component now accepts API data directly via the updated props. The existing entrance/exit animation logic stays intact.

**Contract**: GameBoard becomes a `GameProvider` wrapper. The `stageId` param from the route drives `createGame()`. Board renders `state.gameState.you.board` for player rows and `state.gameState.opponent.board` for opponent rows. Hand renders `state.gameState.you.hand`.

#### 2. Card selection and move submission

**File**: `frontend/src/pages/GameBoard.tsx` (continued)

**Intent**: When player clicks a card in hand: dispatch `CARD_SELECTED` with the card's `instanceId`. The reducer filters `legalMoves` for that card and sets `validTargets`. If the card is a simple UNIT (moves have `targetRow`, no `targetInstanceId`), highlight the valid rows. When player clicks a highlighted row, find the matching `MoveView` and call `makeMove()`. On response, dispatch `MOVE_RESULT` with the new `GameStateView`.

**Contract**: `onClick` handler on hand cards dispatches `CARD_SELECTED`. Rows with matching `targetRow` in `validTargets` get a CSS class `board-row-valid-target`. Click on a valid-target row calls `makeMove()` with the corresponding `MoveRequest`.

#### 3. PASS and LEADER action buttons

**File**: `frontend/src/pages/GameBoard.tsx` (continued)

**Intent**: Add two buttons below the player hand: "Pass" (visible when `legalMoves` contains a PASS move and it's your turn) and "Use Leader: [ability name]" (visible when `legalMoves` contains a LEADER move). Clicking either submits the move directly (no targeting needed).

**Contract**: Buttons rendered conditionally based on `legalMoves.some(m => m.kind === 'PASS')` and `legalMoves.some(m => m.kind === 'LEADER')`. Leader button disabled/hidden after `gameState.you.leaderUsed === true`.

#### 4. Turn indicator

**File**: `frontend/src/pages/GameBoard.tsx` (continued)

**Intent**: Show whose turn it is. When `yourTurn === false` (waiting for bot / between moves), disable all interaction (hand cards non-clickable, buttons disabled). Display a "Bot is thinking..." indicator near the opponent meta panel.

**Contract**: When `phase === 'waiting-for-bot'`, hand cards get `pointer-events: none` and reduced opacity. A text element "Bot is thinking..." appears in the opponent meta area with a subtle pulse animation.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npm run build` succeeds
- Backend must be running: `cd backend && ./mvnw -pl app spring-boot:run`

#### Manual Verification:

- Click stage 1 on campaign → game board loads with real cards from API
- Click a UNIT card in hand → card highlights, valid row(s) glow
- Click a valid row → card appears on board, bot replies after brief pause
- Click "Pass" → round progresses
- Click "Use Leader" → leader ability fires, button disappears
- Play through a full round (both sides pass) → round resolves correctly

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Targeting & Special Cards

### Overview

Handle all move types that need targeting beyond simple row selection: SPY (row targeting but on opponent's side), SPECIAL (no targeting), ROW (row targeting for horn/weather), UNIT_TARGET (unit targeting for decoy). Add cancel (ESC / click empty space).

### Changes Required:

#### 1. Full targeting logic by move kind

**File**: `frontend/src/pages/GameBoard.tsx` (or extracted targeting helper)

**Intent**: Extend the card selection logic to handle all `MoveView.kind` values:

- `UNIT`: highlight player-side rows matching `targetRow` from `validTargets`. Player clicks row → submit.
- `SPY`: highlight **opponent-side** rows matching `targetRow`. The API returns `kind: "SPY"` with the same `targetRow` field as UNIT, but no side indicator — the frontend must use `kind === "SPY"` to determine that the target is on the opponent's board. Player clicks opponent row → submit.
- `SPECIAL`: no targeting needed. When selected card's moves are all `SPECIAL` kind → submit immediately on card click.
- `ROW`: highlight rows from `validTargets` (for horn, weather-on-row). Player clicks row → submit.
- `UNIT_TARGET`: highlight specific units on the board whose `instanceId` appears in `validTargets[].targetInstanceId`. Player clicks a glowing unit → submit.

**Contract**: After `CARD_SELECTED`, the reducer analyzes `validTargets` to determine targeting mode. If all moves are `SPECIAL` → auto-submit. If moves have `targetRow` → set phase to `'selecting-target'` with mode `'row'`. If moves have `targetInstanceId` → set phase to `'selecting-target'` with mode `'unit'`. Board units matching `targetInstanceId` get a CSS class `board-card-valid-target`.

#### 2. Cancel selection

**File**: `frontend/src/pages/GameBoard.tsx`

**Intent**: Allow the player to cancel a card selection. Pressing ESC or clicking on empty board space (not a card, not a valid target) dispatches `CARD_DESELECTED`, clearing `selectedCardInstanceId` and `validTargets`.

**Contract**: `useEffect` listens for `keydown` ESC when `selectedCardInstanceId !== null`. Click handler on `.game-board` background dispatches `CARD_DESELECTED` if target is not a card or row.

#### 3. Visual feedback for valid targets

**File**: `frontend/src/index.css`

**Intent**: Add CSS for valid target highlighting. Rows: golden pulsing border/glow when `board-row-valid-target` class is present. Units: golden outline glow when `board-card-valid-target` class is present. Both should clearly communicate "click me".

**Contract**: `.board-row-valid-target` — animated border glow (gold, pulsing). `.board-card-valid-target .game-card` — golden outline, slight scale-up.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npm run build` succeeds

#### Manual Verification:

- Play a SPY card → goes to opponent's row, player draws 2 cards (spy effect)
- Play a horn → row selector appears, click row → horn applies, row score doubles
- Play a weather card (if SPECIAL kind with no targeting) → submits immediately
- Play a weather-on-row → row selector, click row → weather applies
- Play decoy → own units on board glow, click one → unit returns to hand, decoy takes its place
- Press ESC during targeting → selection cancels
- Play scorch → submits immediately (SPECIAL, no targeting)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Visual Feedback & Effects

### Overview

Add visual indicators for row effects (weather, horn), color-coded power numbers for modified cards, the bot-thinking pause animation, and a simple error banner.

### Changes Required:

#### 1. Weather and horn row indicators

**File**: `frontend/src/index.css` + row rendering in GameBoard

**Intent**: When `RowView.weatherActive === true`, add a frost/blue tint to the row (CSS overlay + icon). When `RowView.hornActive === true`, add a golden glow + horn icon. These are purely visual — the scores already reflect the modifiers from the API.

**Contract**: Rows get conditional CSS classes: `board-row-weather` (blue-tinted overlay, snowflake/rain icon) and `board-row-horn` (golden glow, horn icon). Classes applied based on `RowView.weatherActive` and `RowView.hornActive`.

#### 2. Modified power color-coding on cards

**File**: `frontend/src/components/game/Card.tsx`

**Intent**: When a card's `currentStrength` differs from `basePower`, show the power number in a different color. `currentStrength < basePower` → red (weakened by weather). `currentStrength > basePower` → green (boosted by horn/morale/tight bond). Equal → default cream color.

**Contract**: Card component receives `currentStrength` and `basePower` props. `.game-card-power` gets additional class `power-reduced` (red) or `power-boosted` (green) based on comparison.

#### 3. Bot thinking pause

**File**: `frontend/src/pages/GameBoard.tsx`

**Intent**: After `makeMove()` returns the API response (which includes bot's reply), don't update the board immediately. Instead: dispatch `MOVE_SUBMITTING` → API call → on response, set `phase: 'waiting-for-bot'` → show "Bot is thinking..." → after 600ms timeout → dispatch `MOVE_RESULT` with the response → board updates.

**Contract**: The 600ms pause is a `setTimeout` between receiving the API response and dispatching `MOVE_RESULT`. During this time, `phase === 'waiting-for-bot'` and the opponent meta area shows the thinking indicator.

#### 4. Error banner

**File**: `frontend/src/pages/GameBoard.tsx` + `frontend/src/index.css`

**Intent**: When an API call fails, display a red banner at the top of the game board with the error message and a "Dismiss" or "Retry" button. The banner appears on `ERROR` dispatch and is dismissed on `ERROR_DISMISSED`.

**Contract**: Error banner renders when `state.error !== null`. Styled as a fixed-position red banner at top of the game board area. "Dismiss" button dispatches `ERROR_DISMISSED`. For "New Game" recovery, navigate back to campaign map.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npm run build` succeeds

#### Manual Verification:

- Play a weather card → affected row shows blue tint, unit power numbers turn red (showing 1 instead of base)
- Play a horn → affected row shows golden glow, unit power numbers turn green
- After submitting a move, "Bot is thinking..." appears briefly before board updates
- Stop the backend → make a move → error banner appears with message
- Hero cards show power unchanged (immune to weather) — power stays cream color

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 5: Overlays & Campaign Integration

### Overview

Add the round-end banner, match-end screen with "Back to Campaign" / "Play Again" actions, wire campaign stage unlocking to localStorage, and remove the debug page.

### Changes Required:

#### 1. Round-end overlay

**File**: `frontend/src/components/game/RoundOverlay.tsx` (new)

**Intent**: After each round resolves (detected by comparing `roundHistory.length` before and after a `MOVE_RESULT`), show a center banner overlay: "Round N — Victory/Defeat/Draw! (X vs Y)". Semi-transparent backdrop, auto-dismiss after 3 seconds or click anywhere. The round pip indicators update simultaneously.

**Contract**: Component renders when `phase === 'round-ended'`. Displays the latest `RoundResultView` from `roundHistory`. Calls `dispatch({ type: 'ROUND_DISMISSED' })` on timeout or click. The reducer detects round end in `MOVE_RESULT` by comparing `roundHistory.length` of old vs new state.

#### 2. Match-end screen

**File**: `frontend/src/components/game/MatchEndScreen.tsx` (new)

**Intent**: When `matchEnded === true`, show a full dark overlay with VICTORY or DEFEAT text, round score summary (R1: X-Y ✓/✗, R2: ..., R3: ...), and two buttons: "Back to Campaign" (navigates to `/`) and "Play Again" (calls `createGame()` with same stageId to restart). On victory, call `unlockStage(stageId + 1)` to persist progression.

**Contract**: Component renders when `phase === 'match-ended'`. `matchWinner === 'P1'` → VICTORY, otherwise DEFEAT. "Back to Campaign" uses `navigate('/')`. "Play Again" dispatches a `RESTART_GAME` action that triggers a new `createGame()` call.

#### 3. Campaign map reads localStorage

**File**: `frontend/src/pages/CampaignMap.tsx`

**Intent**: Replace the hardcoded `status` field in `CAMPAIGN_STAGES` with dynamic status derived from `getHighestUnlocked()`. Stages ≤ highestUnlocked are `'active'` (or `'completed'` for stages < highestUnlocked), stages > highestUnlocked are `'locked'`. Re-read on every mount (so returning from a won game shows the newly unlocked stage).

**Contract**: `CampaignMap` calls `getHighestUnlocked()` on mount. Maps stage statuses: `stage.id < highest → 'completed'`, `stage.id === highest → 'active'`, `stage.id > highest → 'locked'`.

#### 4. Remove debug page

**File**: `frontend/src/pages/DebugGame.tsx` (delete), `frontend/src/main.tsx` (edit)

**Intent**: Delete `DebugGame.tsx` and remove the `/debug-game` route from the router config. The polished game UI replaces its purpose.

**Contract**: `main.tsx` router has 2 routes: `/` (CampaignMap) and `/game/:stageId` (GameBoard).

### Success Criteria:

#### Automated Verification:

- `cd frontend && npm run build` succeeds
- `/debug-game` route no longer exists in the build output

#### Manual Verification:

- Win a round → center banner shows "Round 1 — Victory! (X vs Y)" with updated pips, auto-dismisses
- Win a match (2 rounds) → full VICTORY overlay with round summary and both action buttons
- Click "Play Again" → new game starts on same stage
- Click "Back to Campaign" → campaign map shows next stage unlocked
- Refresh page → unlocked stage persists (localStorage)
- Lose a match → DEFEAT overlay, "Play Again" works (FR-010: restart)
- Navigate to completed stage, click play → game starts (FR-011: replay)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 6: Polish & Cleanup

### Overview

Extract sub-components from the now-large GameBoard, refine animations and transitions, and do final playtesting.

### Changes Required:

#### 1. Extract BoardRow component

**File**: `frontend/src/components/game/BoardRow.tsx` (new)

**Intent**: Extract the row rendering logic (row label, cards, score, weather/horn indicators, valid-target highlight) into a reusable `BoardRow` component. Used 6 times in GameBoard (3 player rows + 3 opponent rows).

**Contract**: `BoardRow` accepts `row: RowView`, `rowId: string`, `side: 'player' | 'opponent'`, `isValidTarget: boolean`, `onRowClick: () => void`, `validTargetUnitIds: string[]`.

#### 2. Extract MetaPanel component

**File**: `frontend/src/components/game/MetaPanel.tsx` (new)

**Intent**: Extract the meta panel (leader slot, round pips, score, hand count, deck/grave counts) into a `MetaPanel` component. Used twice (player + opponent).

**Contract**: `MetaPanel` accepts the relevant view data (`PlayerView | OpponentView`), leader info, and side identifier.

#### 3. Extract PlayerHand component

**File**: `frontend/src/components/game/PlayerHand.tsx` (new)

**Intent**: Extract the hand bar (cards + pass/leader buttons) into a `PlayerHand` component. Encapsulates card click handling, button rendering, and disabled state.

**Contract**: `PlayerHand` reads from `useGame()` context. Renders hand cards and action buttons. Handles card click → dispatch.

#### 4. Remove dead mock data

**File**: `frontend/src/data/mockGameState.ts` (delete)

**Intent**: Delete the mock game state file — no longer used. `finalDeck.ts` stays (it's the image source for `cardImageMap.ts`).

**Contract**: No imports of `mockGameState.ts` remain in the codebase.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npm run build` succeeds with no TypeScript errors
- No unused imports or dead code warnings

#### Manual Verification:

- Play 5 full matches across stages 1-3 — all move types work correctly
- Entrance/exit animations between campaign map and game board are smooth
- Round-end banners and match-end overlays display correctly
- Campaign progression persists across page reloads
- Bot responds within 2 seconds (NFR validation)
- No visual regressions from S-01's polish (card glow, stump animations, meta-strip symmetry)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding. This is the final phase — completion means S-03 is done.

---

## Testing Strategy

### Unit Tests:

- Reducer function: test each action type produces correct state transitions
- `getCardImage()`: test known cardId returns URL, unknown returns placeholder
- `campaignProgress`: test localStorage read/write/default behavior

### Integration Tests:

- Full game flow: create game → play moves → round end → match end (with backend running)
- All 7 move kinds submit correctly and update state

### Manual Testing Steps:

1. Open campaign map → click stage 1 → game loads with real cards
2. Play a full match (3 rounds) using all card types: unit, spy, hero, weather, horn, decoy, scorch, leader, pass
3. Win → verify "Victory" overlay → "Back to Campaign" → stage 2 is unlocked
4. Refresh page → stage 2 still unlocked
5. Click stage 2 → play a new match → verify different bot strategy
6. Lose a match → "Defeat" overlay → "Play Again" → new match on same stage
7. Replay a completed stage (FR-011)
8. Kill backend mid-game → verify error banner appears
9. Restart backend → navigate back → start a new game (old game expired is expected)
10. Verify bot responds within 2 seconds consistently

## Performance Considerations

- Card images are already optimized (WebP, q=78-90) and bundled by Vite — no runtime loading concern
- `cardImageMap` is built once at module load — O(1) lookups during gameplay
- useReducer re-renders are scoped to GameBoard subtree via Context — acceptable for the component count
- API calls are sequential (one move at a time) — no concurrent request concerns
- 600ms bot-thinking pause is cosmetic, not a performance issue

## References

- Engine integration guide: `context/foundation/engine-integration-guide.md`
- API types: `frontend/src/api/types.ts`
- Game API wrappers: `frontend/src/api/gameApi.ts`
- S-01 archive: `context/archive/2026-05-28-s-01-static-ui-prototype/`
- S-02 archive: `context/archive/2026-05-29-s-02-playable-game-api/`
- Roadmap S-03 spec: `context/foundation/roadmap.md` (lines 150-162)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: State & Data Foundation

#### Automated

- [x] 1.1 `npm run build` succeeds with no TypeScript errors
- [x] 1.2 All new files importable — no circular dependencies

#### Manual

- [x] 1.3 GameBoard still renders the existing mock UI (no visual regression)

### Phase 2: Core Gameplay Loop

#### Automated

- [x] 2.1 `npm run build` succeeds
- [x] 2.2 Card.tsx accepts API-shape props (cardId, currentStrength, basePower)

#### Manual

- [x] 2.3 Click stage 1 → game board loads with real cards from API
- [x] 2.4 Click UNIT card → card highlights, valid row(s) glow → click row → card placed, bot replies
- [x] 2.5 Click "Pass" → round progresses
- [x] 2.6 Click "Use Leader" → ability fires, button disappears
- [x] 2.7 Full round plays through correctly (both sides pass → round resolves)

### Phase 3: Targeting & Special Cards

#### Automated

- [x] 3.1 `npm run build` succeeds

#### Manual

- [x] 3.2 SPY card plays to opponent's row, player draws 2
- [x] 3.3 Horn targeting: row selector → click row → horn applies
- [x] 3.4 Weather cards submit correctly (auto or row-targeted)
- [x] 3.5 Decoy targeting: own units glow → click unit → returns to hand
- [x] 3.6 ESC cancels targeting
- [x] 3.7 Scorch submits immediately (no targeting)

### Phase 4: Visual Feedback & Effects

#### Automated

- [x] 4.1 `npm run build` succeeds

#### Manual

- [x] 4.2 Weather-affected row shows blue tint, power numbers turn red
- [x] 4.3 Horn-affected row shows golden glow, power numbers turn green
- [x] 4.4 "Bot is thinking..." appears briefly before board updates
- [x] 4.5 Error banner appears on API failure
- [x] 4.6 Hero card power unchanged (immune to weather)

### Phase 5: Overlays & Campaign Integration

#### Automated

- [x] 5.1 `npm run build` succeeds
- [x] 5.2 `/debug-game` route removed

#### Manual

- [x] 5.3 Round-end banner shows with scores and auto-dismisses
- [x] 5.4 Match-end VICTORY overlay with round summary and action buttons
- [x] 5.5 "Play Again" starts new game on same stage
- [x] 5.6 "Back to Campaign" shows newly unlocked stage
- [x] 5.7 Refresh page → unlocked stage persists
- [x] 5.8 DEFEAT overlay + "Play Again" works (FR-010)
- [x] 5.9 Completed stage replay works (FR-011)

### Phase 6: Polish & Cleanup

#### Automated

- [x] 6.1 `npm run build` succeeds with no TypeScript errors or dead code
- [x] 6.2 No unused imports or mockGameState references

#### Manual

- [x] 6.3 5 full matches across stages 1-3 — all move types correct
- [x] 6.4 Entrance/exit animations smooth
- [x] 6.5 Bot responds within 2 seconds (NFR)
- [x] 6.6 No visual regressions from S-01
