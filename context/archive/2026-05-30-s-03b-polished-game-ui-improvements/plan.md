# Polished Game UI Improvements — Implementation Plan

## Overview

Polish pass on the S-03 baseline: diagnose and fix two gameplay-correctness bugs (horn doubling + round resolution), add symmetric move notification toast with backend DTO, add opponent-passed indicator, validate weather/horn visuals via dev test endpoint, clean up round transitions, and playtest-driven match-end overlay polish (max 3 fixes, hard cap).

## Current State Analysis

S-03 shipped a fully playable game UI (archived 2026-05-29). During manual playtesting, 8 issues were observed and consciously deferred to this follow-up polish change.

### Key Discoveries:

- **Horn backend logic looks correct in code** — `CardScorer.currentStrength()` at `acommon-game-engine/.../scoring/CardScorer.java:26-28` doubles strength when `row.hornActive()`. `GameStateMapper` serializes both `hornActive` and computed `currentStrength` per card. Bug is likely frontend rendering (card element not rendering) or a subtle backend edge case.
- **Round resolution logic looks correct in code** — `RoundResolver.resolveEndOfRound()` at `acommon-game-engine/.../execution/RoundResolver.java:64-87` properly checks `roundsToWin >= 2` before ending match, advances to round 3 on 1-1. Bug needs Network-level diagnosis.
- **No move history in engine** — `GameState` doesn't track last move. `driveBotMoves()` in `GameController.java:100-118` doesn't return bot's last move. Both need changes for `LastMoveView`.
- **MetaPanel ignores `opponent.passed`** — boolean exists in `api/types.ts:31` but `MetaPanel.tsx` never reads or renders it.
- **All weather/horn CSS is code-complete** — `board-row-weather`/`board-row-horn` classes with emoji pseudo-elements in `index.css:720-764`, power color-coding in `Card.tsx:38-43`. Never empirically verified (RNG).
- **RoundOverlay has 3s auto-dismiss** (`RoundOverlay.tsx:11`), no visible leftover 2s hotfix in current code.
- **MatchEndScreen** shows VICTORY/DEFEAT based on `matchWinner === 'P1'` with round-by-round history (`MatchEndScreen.tsx:16-37`).

## Desired End State

7 of 8 known issues from S-03 playtesting are resolved (emoji→SVG consciously deferred). The game plays correctly (horn doubles visible, round resolution accurate), provides clear feedback (symmetric move toast on every action, opponent-passed indicator), and all visual effects (weather tint, horn glow, power colors) are empirically verified. Match-end overlay is polished based on concrete playtest findings (max 3 fixes). The dev test endpoint stays available under `@Profile("dev")` for future testing.

**Verification:** Play 5+ full matches across stages 1-3 with all fixes active. Every move shows a toast, opponent pass is visible, horn doubling renders correctly, round 3 triggers on 1-1, weather/horn visuals confirmed via forced scenario.

## What We're NOT Doing

- No emoji→SVG replacement (decision: keep emoji for now)
- No backend architectural changes beyond `LastMoveView` DTO + dev endpoint
- No deck building, campaign progression, or auth work
- No optimistic UI changes (pessimistic model stays)
- No mobile/responsive improvements
- Match-end polish capped at 3 fixes — surplus deferred to next slice

## Implementation Approach

Diagnose-first for the two gameplay bugs (Phase 1) via DevTools Network inspection during actual play. Fix based on findings — could be backend, frontend, or both. Then backend DTO work for the toast (Phase 2), frontend UI additions (Phase 3), visual validation via dev endpoint (Phase 4), and playtest-driven match-end polish last (Phase 5) so it evaluates the full polished experience.

## Critical Implementation Details

### State sequencing

Phase 1 diagnosis must complete before Phase 2-3 work begins — if horn or round resolution bugs are in the backend, the fix may touch files that Phase 2 also modifies (`GameController`, `GameStateMapper`, engine classes). Diagnosis findings determine whether Phase 2 scope expands.

### Backend DTO contract for toast

`LastMoveView` includes `rowKind` (nullable, filled for UNIT/HORN/WEATHER moves) so toast copy can say "Opponent played Horn on CLOSE" instead of just "Opponent played Horn". The controller must capture both the player's move AND the bot's auto-reply move — `driveBotMoves()` needs to return the last bot move instead of void.

---

## Phase 1: Gameplay Correctness Diagnosis (Horn + Round Resolution)

### Overview

Diagnose two gameplay-correctness bugs via DevTools Network inspection during actual gameplay. Both are diagnose-first: no speculative fixes until the root cause is identified from API responses.

### Changes Required:

#### 1. Horn Doubling Bug Diagnosis

**Manual diagnostic procedure — no code changes until root cause identified.**

**Steps:**
1. Start a game, open DevTools Network tab
2. Play units on a CLOSE row (e.g. two unit cards with known basePower)
3. Play a Commander's Horn targeting CLOSE row
4. Inspect the POST `/api/games/{gameId}/moves` response JSON:
   - Check `you.board.close.units` — are all played cards present? (array length)
   - Check `you.board.close.hornActive` — is it `true`?
   - Check each unit's `currentStrength` — is it `2 × basePower`?
   - Check `you.board.close.strength` — does it equal sum of `currentStrength` values?
5. Compare API response with what the UI renders — if API is correct but UI drops a card, it's a frontend rendering bug. If API has wrong values, it's a backend scoring bug.

**Possible fix locations based on findings:**
- Frontend rendering: `BoardRow.tsx` (card list mapping) or `useGameReducer.ts` (state update losing cards)
- Backend scoring: `CardScorer.java:26-28` (horn modifier) or `CommandersHornEffect.java` (horn application)
- Backend serialization: `GameStateMapper.toRowView()` at `GameStateMapper.java:89-98` (units list construction)

#### 2. Round Resolution Bug Diagnosis

**Manual diagnostic procedure — no code changes until root cause identified.**

**Steps:**
1. Play a game, win round 1, open DevTools Network tab
2. In round 2, click PASS
3. Inspect the POST response for the PASS move:
   - Check `roundHistory[]` — does round 1 have `winner: "P1"`?
   - Check `matchEnded` — is it `true` (should be `false` after round 2 with 1-1)?
   - Check `matchWinner` — should be `null` if match isn't over
   - Check `roundNumber` — should be `3` if round 2 ended 1-1
   - Check `you.roundsWon` and `opponent.roundsWon`
4. If bot auto-plays after player passes and wins round 2 → check if `driveBotMoves()` loop runs correctly (bot should play remaining cards, then pass when it passes, triggering round resolution)
5. If API response shows correct data but frontend shows wrong overlay → bug is in `useGameReducer.ts` detectRoundEnd or in `MatchEndScreen.tsx` rendering logic

**Possible fix locations based on findings:**
- Frontend reducer: `useGameReducer.ts:42-53` (`detectRoundEnd` logic) or `useGameReducer.ts:99-114` (phase detection)
- Frontend overlay: `MatchEndScreen.tsx:16` (victory condition `matchWinner === 'P1'`)
- Backend round resolution: `RoundResolver.java:64-87` (match-end conditions)
- Backend bot loop: `GameController.java:100-118` (`driveBotMoves` — bot may not be getting enough turns after player passes)

#### 3. Apply Fixes Based on Diagnosis

**Intent:** Fix root causes identified in steps 1 and 2. The specific files and changes depend entirely on diagnosis findings.

**Contract:** Each fix must be verified by replaying the exact scenario that exposed the bug and confirming correct behavior in both the API response and the UI.

### Success Criteria:

#### Automated Verification:

- Backend builds cleanly: `cd backend && ./mvnw clean install`
- Frontend builds cleanly: `cd frontend && npm run build`
- All existing tests pass: `cd backend && ./mvnw test`

#### Manual Verification:

- Horn: play 2+ unit cards on same row, play horn → all cards visible, all show doubled `currentStrength` (green power number), row total reflects doubling
- Round resolution: win round 1, play round 2 to completion → if 1-1, round 3 starts (no premature match-end); if 0-2 or 2-0, match ends correctly with right winner
- No regressions in other move types (spy, weather, decoy, scorch, leader, pass)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 2: Backend — LastMoveView DTO + Dev Test Endpoint

### Overview

Add `LastMoveView` to the API response for the symmetric move toast, and add a `@Profile("dev")` test endpoint that creates games with guaranteed weather+horn cards in hand for visual validation.

### Changes Required:

#### 1. LastMoveView DTO

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/LastMoveView.java`

**Intent**: New record DTO representing the last move made (by either player or bot), used by the frontend toast.

**Contract**: `record LastMoveView(String player, String kind, String cardName, String rowKind)` — `player` is `"you"` or `"opponent"` (perspective-aware), `kind` matches existing `MoveView.kind` values (UNIT, PASS, LEADER, SPY, SPECIAL, ROW, UNIT_TARGET), `cardName` nullable (null for PASS), `rowKind` nullable (CLOSE/RANGED/SIEGE, filled only for UNIT/HORN/WEATHER moves).

#### 2. Add lastMove field to GameStateView

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/GameStateView.java`

**Intent**: Include last move info in the API response so frontend can render the toast.

**Contract**: Add two nullable fields: `LastMoveView yourLastMove` and `LastMoveView opponentLastMove`. Both null on game creation. After a move: `yourLastMove` reflects the player's move, `opponentLastMove` reflects the bot's auto-reply (null if bot didn't play, e.g. bot already passed). This enables symmetric toasts — both "You played X" and "Opponent played Y" per turn.

#### 3. Modify GameController to capture last moves

**File**: `backend/app/src/main/java/cards/loxley/app/web/GameController.java`

**Intent**: Track both the player's move and the bot's auto-reply move so the mapper can build `LastMoveView`. The bot's move is currently lost inside `driveBotMoves()`.

**Contract**: 
- `driveBotMoves()` returns `Move` (the last bot move executed) instead of `void`. Returns `null` if bot made no moves (e.g. already passed).
- `makeMove()` passes BOTH the player's move AND the bot's last move (from `driveBotMoves()` return value) to the mapper — two separate arguments.
- `createGame()` and `getState()` pass `null` for both move arguments.

#### 4. Extend GameStateMapper with LastMoveView building

**File**: `backend/app/src/main/java/cards/loxley/app/web/GameStateMapper.java`

**Intent**: Build `LastMoveView` from the raw `Move` object, applying perspective (P1 = "you", P2 = "opponent") and extracting card name + row kind.

**Contract**: 
- `toView()` gains two `Move` parameters: `Move playerMove` and `Move botMove` (both nullable).
- New `toLastMoveView(Move move, Player perspective)` method maps `PlayCardMove` → card name lookup from game state (board/graveyard, since card is already played), `PassMove` → kind=PASS with null cardName, `UseLeaderMove` → kind=LEADER. `rowKind` extracted from `PlayCardMove.targetRow()` for applicable move kinds.
- Mapper builds `yourLastMove` from `playerMove` and `opponentLastMove` from `botMove`, applying perspective (`player` field = "you"/"opponent").

#### 5. Dev test endpoint for forced weather+horn games

**File**: `backend/app/src/main/java/cards/loxley/app/web/DevTestController.java` (new file)

**Intent**: Create a `@Profile("dev")` REST controller that creates games with guaranteed weather and horn cards in the player's starting hand, for deterministic visual validation.

**Contract**:
- `@RestController`, `@Profile("dev")`, `@RequestMapping("/api/dev")`
- `POST /api/dev/games/forced-hand` — creates a game where P1's hand is seeded with at least one weather card (any of WEATHER_CLOSE/WEATHER_RANGED/WEATHER_SIEGE), one Commander's Horn, and regular unit cards for each row type. Returns `GameStateView`.
- Implementation: reuses existing `GameStateFactory` but overrides the hand-dealing step (either via a flag or by post-creation hand manipulation).
- Not accessible in production (Spring profile guard).

#### 6. Update frontend types

**File**: `frontend/src/api/types.ts`

**Intent**: Add `LastMoveView` interface and the `lastMove` field to `GameStateView`.

**Contract**:
```typescript
export interface LastMoveView {
  player: string    // "you" | "opponent"
  kind: string      // UNIT, PASS, LEADER, SPY, etc.
  cardName: string | null
  rowKind: string | null  // CLOSE, RANGED, SIEGE
}

// Add to GameStateView:
yourLastMove: LastMoveView | null
opponentLastMove: LastMoveView | null
```

### Success Criteria:

#### Automated Verification:

- Backend builds cleanly: `cd backend && ./mvnw clean install`
- All backend tests pass: `cd backend && ./mvnw test`
- Frontend builds cleanly: `cd frontend && npm run build`

#### Manual Verification:

- Play a move → API response includes `yourLastMove` with correct `player`, `kind`, `cardName`, `rowKind`
- Play a PASS → `yourLastMove.kind === "PASS"`, `cardName === null`
- Bot auto-replies → response includes BOTH `yourLastMove` (player's move) AND `opponentLastMove` (bot's reply)
- Dev endpoint: `curl -X POST http://localhost:8080/api/dev/games/forced-hand` returns a game where hand contains weather + horn cards (verify in response JSON)
- Dev endpoint not accessible without `dev` profile active

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Frontend — Toast + Passed Indicator + Cleanup

### Overview

Build the symmetric move notification toast, add opponent-passed indicator in MetaPanel, and verify/clean up round transition logic.

### Changes Required:

#### 1. MoveToast component

**File**: `frontend/src/components/game/MoveToast.tsx` (new file)

**Intent**: A 3-second auto-dismiss toast showing what the last move was. Symmetric — shows for both player actions ("You played Little John on CLOSE") and bot actions ("Opponent played Horn on RANGED" / "Opponent passed"). Provides feedback that was previously invisible, especially for bot moves like weather/decoy that have no obvious board change.

**Contract**: 
- Props: `lastMove: LastMoveView`, `onDismiss: () => void`
- 3s auto-dismiss via `useEffect` + `setTimeout` (same pattern as `RoundOverlay.tsx:10-13`)
- Copy format: `"{Player} played {cardName} on {rowKind}"` when rowKind present, `"{Player} played {cardName}"` when no rowKind, `"{Player} passed"` for PASS, `"{Player} used leader ability"` for LEADER
- Player label: "You" when `lastMove.player === "you"`, "Opponent" otherwise
- Positioned as a non-blocking toast (doesn't cover game board), styled to match existing overlay aesthetic

#### 2. Wire toast to game reducer

**File**: `frontend/src/hooks/useGameReducer.ts`

**Intent**: Store `lastMove` from API response in state and add a dismiss action for the toast.

**Contract**:
- Add `yourLastMove: LastMoveView | null` and `opponentLastMove: LastMoveView | null` to `GameUIState`
- `MOVE_RESULT` action: extract both from `action.payload` and store in state
- New `TOAST_DISMISSED` action: clears both last move fields
- `GAME_LOADED` / `RESTART_GAME` / `ROUND_DISMISSED`: clear both last move fields (prevents stale toast appearing after round overlay dismissal)

#### 3. Render toast in GameBoard

**File**: `frontend/src/pages/GameBoard.tsx`

**Intent**: Render `MoveToast` when either `state.yourLastMove` or `state.opponentLastMove` is present and phase is `idle` (not during round-ended/match-ended overlays).

**Contract**: Conditional render of `MoveToast` with `onDismiss` dispatching `TOAST_DISMISSED`. Show player's move first, then opponent's move (sequential or combined into one line — implementer decides based on readability). Toast should not appear simultaneously with `RoundOverlay` or `MatchEndScreen`.

#### 4. Opponent passed indicator in MetaPanel

**File**: `frontend/src/components/game/MetaPanel.tsx`

**Intent**: Show a static "OPPONENT PASSED" label in the opponent's MetaPanel when `opponent.passed === true`, so the player understands why the opponent's board is empty/unchanged.

**Contract**:
- Add `passed?: boolean` prop to MetaPanel
- When `side === 'opponent'` and `passed === true`, render a visible "PASSED" indicator (prominent text, styled to stand out)
- GameBoard passes `passed={gameState.opponent.passed}` to the opponent MetaPanel

#### 5. Round transition cleanup verification

**File**: `frontend/src/hooks/useGameReducer.ts` and `frontend/src/pages/GameBoard.tsx`

**Intent**: Verify that no leftover 2s hotfix code from S-03 Phase 3 remains. Current code shows RoundOverlay with 3s auto-dismiss — confirm this is the only round transition mechanism.

**Contract**: Search for any `2000` or `2_000` timeout values, any `setTimeout` related to round transitions outside of RoundOverlay, any duplicate phase transition logic. Remove if found; document as "confirmed clean" if not found.

#### 6. Toast CSS styling

**File**: `frontend/src/index.css`

**Intent**: Style the move toast to be non-blocking, positioned at the top or bottom of the game area, with auto-fade animation.

**Contract**: Toast positioned fixed/absolute, doesn't overlap board interaction areas. Brief fade-in/fade-out animation. Styled consistently with existing overlay aesthetic (semi-transparent dark background, readable text).

### Success Criteria:

#### Automated Verification:

- Frontend builds cleanly: `cd frontend && npm run build`
- No TypeScript errors: `cd frontend && npx tsc --noEmit`

#### Manual Verification:

- Play a unit card → toast shows "You played [card name] on [row]" for ~3s, then disappears
- Bot plays → toast shows "Opponent played [card name] on [row]" or "Opponent passed"
- Play horn → toast shows "You played Commander's Horn on [row]"
- Opponent passes → "PASSED" label appears in opponent MetaPanel and persists until round ends
- Toast does NOT appear during round-ended or match-ended overlays
- Round transition: RoundOverlay appears for ~3s, auto-dismisses, no duplicate or conflicting transitions
- No visual regressions in existing board layout

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Weather/Horn Visual Validation

### Overview

Use the dev test endpoint to create a game with forced weather+horn cards, then manually verify all visual indicators render correctly.

### Changes Required:

#### 1. Create forced test game and validate visuals

**Manual verification procedure using the dev endpoint from Phase 2.**

**Steps:**
1. Start backend with `dev` profile: `cd backend && ./mvnw -pl app spring-boot:run -Dspring-boot.run.profiles=dev`
2. Create forced game: `curl -X POST http://localhost:8080/api/dev/games/forced-hand`
3. Open game in frontend, play through with weather and horn cards
4. Verify each visual indicator:

**Weather row indicators:**
- Play weather card (e.g. WEATHER_CLOSE) → target row on opponent's side gets blue tint background `rgba(80, 130, 180, 0.2)`
- ❄ emoji appears on the left of the affected row
- All non-hero units in the row show `currentStrength = 1` (red power number via `.power-reduced` class)
- Hero units in the row keep original `basePower` (cream power number, no color change)
- Row total strength reflects reduced values

**Horn row indicators:**
- Play Commander's Horn on a row → row gets warm orange tint `rgba(255, 200, 80, 0.15)`
- 🎺 emoji appears on the left of the row
- All units in the row show doubled `currentStrength` (green power number via `.power-boosted` class)
- Row total strength reflects doubled values

**Combined weather + horn:**
- If both active on same row → gradient background blending blue and orange
- Units show `currentStrength = 1 × 2 = 2` (weather reduces to 1, horn doubles to 2)

**Power color coding:**
- `.power-reduced` (red): `currentStrength < basePower`
- `.power-boosted` (green): `currentStrength > basePower`
- Default (cream): `currentStrength === basePower` (hero with weather, or no modifiers)

#### 2. Fix any visual bugs found during validation

**Intent**: Fix rendering issues discovered during the validation above. Specific changes depend on findings.

**Contract**: Each fix verified by replaying the scenario in the forced game.

### Success Criteria:

#### Automated Verification:

- Frontend builds cleanly: `cd frontend && npm run build`

#### Manual Verification:

- Weather row: blue tint + ❄ + red power numbers on non-hero units — all confirmed visually
- Horn row: orange tint + 🎺 + green power numbers on all units — all confirmed visually
- Combined: gradient background + correct power calculation — confirmed visually
- Hero immunity: hero unit in weather row keeps cream power number — confirmed
- Clear weather card: removes weather effect from all rows — confirmed visually
- Move toast shows correct info for weather/horn plays (card name + row)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 5: Match-End Overlay Polish (Playtest-Driven)

### Overview

Play full games end-to-end with all fixes from phases 1-4 active. Evaluate the match-end overlay with fresh eyes on the complete, polished experience. Identify and fix a maximum of 3 concrete issues. Any additional issues are deferred to the next polish slice (noted in memory).

### Changes Required:

#### 1. Playtest and identify issues

**Manual playtest procedure:**

1. Play 3-5 full matches across different stages (1, 2, 3)
2. Focus on the match-end experience: what happens after the final round, the overlay appearance, the transition, the copy, the buttons
3. Note up to 3 concrete, actionable issues (not vague feelings — specific problems with specific fixes)
4. If more than 3 issues are found, prioritize by impact and defer the rest

**Current match-end overlay** (`MatchEndScreen.tsx`):
- Shows VICTORY (golden) or DEFEAT (red) title
- Round-by-round history with ✓/—/✗ marks
- "Play Again" and "Back to Campaign" buttons
- Full-screen dark overlay (z-index 130, opacity 0.85)

#### 2. Fix identified issues (max 3)

**Intent**: Fix the 3 highest-priority match-end overlay issues found during playtest. Specific changes determined by playtest findings.

**Files likely affected**: `MatchEndScreen.tsx`, `index.css` (match-end styling section lines 879-995), possibly `GameBoard.tsx` (overlay trigger logic).

**Contract**: Each fix is small and targeted. No structural redesigns — polish only (typography, spacing, animation timing, copy, button placement).

### Success Criteria:

#### Automated Verification:

- Frontend builds cleanly: `cd frontend && npm run build`

#### Manual Verification:

- Play a full match → match-end overlay feels polished with the 3 fixes applied
- No regressions in round overlay, toast, or board rendering
- Victory and defeat paths both tested

**Implementation Note**: After completing this phase, if more than 3 issues were found, document the deferred issues in a memory note for the next polish slice.

---

## Testing Strategy

### Unit Tests:

- Backend: existing engine tests cover horn scoring, round resolution, bot play — no new unit tests unless a backend bug is found and fixed (in which case, add a regression test for the specific scenario)
- Frontend: no unit test infrastructure currently — testing is manual

### Integration Tests:

- Backend `./mvnw clean install` exercises all engine tests end-to-end
- Frontend `npm run build` + `npx tsc --noEmit` catches type errors

### Manual Testing Steps:

1. Play 5+ full matches with DevTools Network tab open
2. Verify horn doubling renders correctly (all cards visible, doubled strength)
3. Verify round 3 triggers on 1-1 (no premature match-end)
4. Verify toast appears for every move (player + opponent, with card name + row)
5. Verify OPPONENT PASSED indicator when bot passes
6. Verify weather/horn visuals via forced game (dev endpoint)
7. Verify match-end overlay polish fixes

## Performance Considerations

- Toast auto-dismiss (3s `setTimeout`) is lightweight — no performance concern
- `LastMoveView` adds one small object to API response — negligible
- Dev endpoint is `@Profile("dev")` only — zero production impact

## References

- S-03 archive: `context/archive/2026-05-29-s-03-polished-game-ui/plan.md`
- Deferred issues memory: `~/.claude/projects/.../memory/project_s03_followup_polish.md`
- Roadmap S-03b section: `context/foundation/roadmap.md:165-177`
- Backend scoring: `acommon-game-engine/.../scoring/CardScorer.java`
- Round resolution: `acommon-game-engine/.../execution/RoundResolver.java`
- Frontend reducer: `frontend/src/hooks/useGameReducer.ts`
- Frontend types: `frontend/src/api/types.ts`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Gameplay correctness diagnosis (horn + round resolution)

#### Automated

- [x] 1.1 Backend builds cleanly after fixes
- [x] 1.2 Frontend builds cleanly after fixes
- [x] 1.3 All existing backend tests pass

#### Manual

- [x] 1.4 Horn: all cards visible after horn, doubled strength, correct row total
- [x] 1.5 Round resolution: round 3 triggers on 1-1, correct match-end on 2-0 or 2-1
- [x] 1.6 No regressions in other move types

### Phase 2: Backend — LastMoveView DTO + dev test endpoint

#### Automated

- [x] 2.1 Backend builds cleanly
- [x] 2.2 All backend tests pass
- [x] 2.3 Frontend builds cleanly with updated types

#### Manual

- [x] 2.4 API response includes correct yourLastMove after player move
- [x] 2.5 API response includes both yourLastMove + opponentLastMove when bot replies
- [x] 2.6 Dev endpoint returns game with weather + horn in hand
- [x] 2.7 Dev endpoint not accessible without dev profile

### Phase 3: Frontend — toast + passed indicator + cleanup

#### Automated

- [x] 3.1 Frontend builds cleanly
- [x] 3.2 No TypeScript errors

#### Manual

- [x] 3.3 Toast shows for player moves with card name + row
- [x] 3.4 Toast shows for opponent moves and passes
- [x] 3.5 OPPONENT PASSED indicator visible when bot passes
- [x] 3.6 Toast suppressed during round-ended / match-ended overlays
- [x] 3.7 Round transition clean — no duplicate or conflicting timing

### Phase 4: Weather/horn visual validation

#### Automated

- [x] 4.0 Frontend builds cleanly

#### Manual

- [x] 4.1 Weather row: blue tint + ❄ + red power on non-hero units
- [x] 4.2 Horn row: orange tint + 🎺 + green power on all units
- [x] 4.3 Combined weather + horn: gradient + correct power calc
- [x] 4.4 Hero immunity in weather row
- [x] 4.5 Clear weather removes effect from all rows
- [x] 4.6 Toast shows correct info for weather/horn plays
- [x] 4.7 Horn bug verified: 2 units + horn → both visible, doubled, bot scorch confirms Phase 1 hypothesis

### Phase 5: Match-end overlay polish

#### Automated

- [x] 5.1 Frontend builds cleanly

#### Manual

- [x] 5.2 Up to 3 identified issues fixed and verified
- [x] 5.3 No regressions in round overlay, toast, or board
