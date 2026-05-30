# Polished Game UI Improvements — Plan Brief

> Full plan: `context/changes/s-03b-polished-game-ui-improvements/plan.md`

## What & Why

Polish pass on the S-03 baseline — 8 issues observed during playtesting were consciously deferred to keep the north-star slice scope tight. Two are gameplay-correctness bugs (horn doubling + round resolution), the rest are UX gaps (invisible opponent moves, missing passed indicator, unverified visual effects, rough match-end overlay).

## Starting Point

S-03 shipped a fully playable game (archived 2026-05-29): click-to-select cards, all move types, round/match overlays, campaign progression via localStorage. The board renders weather/horn CSS indicators and power color-coding, but these were never empirically observed during playtests (RNG). The API doesn't include last-move info, so opponent actions are invisible. MetaPanel ignores the `opponent.passed` boolean.

## Desired End State

7 of 8 issues resolved (emoji→SVG consciously deferred). Horn doubling works visually, round resolution is correct, every move produces a symmetric toast ("You played X on CLOSE" / "Opponent passed"), opponent pass is visible in MetaPanel, weather/horn visuals are confirmed working via a reusable dev test endpoint, and the match-end overlay has up to 3 targeted polish fixes.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
|----------|--------|-------------------|
| Scope | All 8 items (7 original + round resolution bug) | Full polish pass in one change — all items are small and frontend-heavy. |
| Horn + round bug approach | Diagnose-first via DevTools Network | Prevents wasted work — fix the actual bug, not a guess. |
| Toast data source | Backend DTO (`yourLastMove` + `opponentLastMove`) | Single source of truth — two fields enable symmetric toasts; frontend diffing is fragile with spy/scorch/medic edge cases. |
| Toast symmetry | Player + opponent (both sides) | Confirmation feedback for player, visibility for bot moves — roadmap spec. |
| `LastMoveView.rowKind` | Included from the start | Enables "played Horn on CLOSE" copy — avoids a follow-up DTO change. |
| Emoji vs SVG | Keep emoji for now | Zero design work, acceptable at MVP fidelity level. |
| Weather/horn validation | `@Profile("dev")` backend endpoint | Deterministic (no RNG), reusable, no debug button in frontend (anti-pattern). |
| Match-end polish | Playtest-driven, max 3 fixes, last phase | Evaluates full polished experience; hard cap prevents scope creep. |

## Scope

**In scope:** Horn doubling fix, round resolution fix, `LastMoveView` backend DTO, symmetric move toast, opponent-passed indicator, round transition cleanup, weather/horn visual validation via dev endpoint, match-end overlay polish (max 3 fixes)

**Out of scope:** Emoji→SVG replacement, deck building, campaign progression, auth, mobile/responsive, optimistic UI, match-end fixes beyond 3

## Architecture / Approach

Backend gets two additions: `LastMoveView` field in `GameStateView` (built from the move object in the controller layer after `orchestrator.playTurn()` + `driveBotMoves()`), and a `@Profile("dev")` controller for forced-hand game creation. Frontend gets a `MoveToast` component (3s auto-dismiss, same pattern as `RoundOverlay`), a `passed` prop on `MetaPanel`, and bug fixes based on Phase 1 diagnosis. All changes are additive — no architectural refactoring.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|-------|-----------------|----------|
| 1. Gameplay correctness diagnosis | Horn + round resolution bugs diagnosed and fixed | Root cause could be backend (expands scope) |
| 2. Backend DTO + dev endpoint | `LastMoveView` in API response + forced-hand test endpoint | `driveBotMoves()` refactor to return last move |
| 3. Frontend toast + passed + cleanup | Symmetric move toast, OPPONENT PASSED indicator, round transition cleanup | Toast timing interaction with round/match overlays |
| 4. Weather/horn visual validation | Empirical confirmation of all visual indicators | May surface new rendering bugs |
| 5. Match-end overlay polish | Up to 3 targeted fixes from playtest | Open-ended (mitigated by hard cap) |

**Prerequisites:** S-03 baseline (done, archived 2026-05-29)
**Estimated effort:** ~3-4 sessions across 5 phases

## Open Risks & Assumptions

- Horn or round resolution bugs may be in the backend engine, expanding scope beyond "frontend-only polish"
- `driveBotMoves()` currently returns void — refactoring to return the last bot move touches the controller's core game flow
- Match-end polish is intentionally open-ended with a 3-fix hard cap — surplus issues deferred to next slice
- Dev test endpoint needs careful hand-seeding logic that may require engine internals access

## Success Criteria (Summary)

- Play 5+ full matches: horn doubles correctly, round 3 triggers on 1-1, every move shows a toast, opponent pass is visible
- Weather/horn visuals empirically confirmed via forced scenario (dev endpoint)
- Match-end overlay has up to 3 polish fixes from concrete playtest findings
