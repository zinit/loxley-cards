<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Playable Game API

- **Plan**: context/changes/s-02-playable-game-api/plan.md
- **Mode**: Deep
- **Date**: 2026-05-29
- **Verdict**: SOUND
- **Findings**: 0 critical, 5 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS (WARNING after user-reported F7) |
| Architectural Fitness | PASS (WARNING after user-reported F6 + F4) |
| Blind Spots | WARNING (F2 + user-reported F5) |
| Plan Completeness | WARNING (F1 + F3) |

## Grounding

7/7 paths verified, 4/4 symbols confirmed (`GameStateFactory`, `TurnOrchestrator`, `MoveDescriber`, `IllegalMoveException`), brief-plan consistency confirmed. Sub-agent deep verification: 5/5 claims confirmed (CardScorer signature, MoveDescriber signature, no circular references in GameState, CampaignStageRegistry.findByNumber exists, loxley-cards-db is empty scaffold).

## Findings

### F1 — Missing CreateGameRequest DTO for POST /api/games

- **Severity**: WARNING
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — item 6
- **Detail**: Phase 1 defined 7 DTO records but omitted the request body for POST /api/games. Phase 2 said the endpoint "accepts {stageNumber: int}" but no record deserializes it.
- **Fix**: Add `CreateGameRequest.java` record with single `stageNumber` field to Phase 1 DTO list.
- **Decision**: FIXED — added as Phase 1 item 6, renumbered subsequent items.

### F2 — Bot loop needed in POST /api/games too

- **Severity**: WARNING
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 2 — item 2
- **Detail**: Plan described "if bot goes first, auto-play bot moves" for POST /api/games but the bot loop code pattern was only shown under POST /api/games/{id}/moves. Engine randomizes who starts (~50% P2 first).
- **Fix**: Added explicit `driveBotMoves(state, gameId)` private method pattern, called from both endpoints.
- **Decision**: FIXED — bot loop code pattern updated to show shared private method, POST /api/games endpoint description updated to reference `driveBotMoves`.

### F3 — CardInstanceView.currentStrength undefined for hand cards

- **Severity**: OBSERVATION
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — item 9 (GameStateMapper)
- **Detail**: Mapper uses `CardScorer.currentStrength(card, row)` but hand cards have no RowState. Mapper needs to branch.
- **Fix**: Added note to mapper contract: hand cards use `card.card().basePower()` directly; `CardScorer` only for cards on board.
- **Decision**: FIXED — clarification added to mapper contract.

### F4 — Broad IllegalStateException handler

- **Severity**: OBSERVATION
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 2 — item 4 (GlobalExceptionHandler)
- **Detail**: Catching all `IllegalStateException` globally could mask Spring MVC's own exceptions.
- **Fix**: Added custom `GameStateException` wrapper. Controller catches `IllegalStateException` from engine and rethrows as `GameStateException`. Handler catches `GameStateException` → 409.
- **Decision**: FIXED — `GameStateException` added as Phase 2 item 3, handler updated.

### F5 — Missing 404 handler in GlobalExceptionHandler (user-reported)

- **Severity**: WARNING
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2 — items 2-4
- **Detail**: Phase 2 promised "404 if game not found" for GET/POST but the exception handler only mapped IllegalMoveException→400, IllegalStateException→409, IllegalArgumentException→400. No 404 handler.
- **Fix**: Added custom `GameNotFoundException` thrown by `GameSessionStore.findOrThrow()` + handler → 404 with code `NOT_FOUND`.
- **Decision**: FIXED — `GameNotFoundException` added as Phase 2 item 3, `findOrThrow` replaces `find` in store contract, handler updated.

### F6 — kind casing inconsistency across DTOs (user-reported)

- **Severity**: WARNING
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 1 — items 4/7
- **Detail**: MoveView.kind used lowercase ("pass"/"unit") while all other enum-derived strings used UPPER ("P1"/"P2", "CLOSE", "UNIT"). Inconsistent for frontend consumption.
- **Fix**: Standardized all kind values to UPPER ("PASS"/"UNIT"/"LEADER"/"SPY"/"SPECIAL"/"ROW"/"UNIT_TARGET").
- **Decision**: FIXED — MoveView and MoveRequest contracts updated with UPPER kind values and consistency note.

### F7 — Debug page "board layout" wording invites S-03 drift (user-reported)

- **Severity**: WARNING
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Phase 4 — item 4
- **Detail**: "Board: simple layout showing your rows and opponent rows with card names + strengths" could be interpreted as styled card components or a mini-S-03.
- **Fix**: Replaced with explicit "text-only lists of card names with power numbers" + anti-drift constraint paragraph.
- **Decision**: FIXED — Phase 4 item 4 contract updated with explicit anti-drift language.
