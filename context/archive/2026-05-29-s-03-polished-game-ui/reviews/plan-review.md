<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Polished Game UI (S-03)

- **Plan**: context/changes/s-03-polished-game-ui/plan.md
- **Mode**: Deep
- **Date**: 2026-05-29
- **Verdict**: SOUND (after fixes)
- **Findings**: [1 critical] [2 warnings] [1 observation]

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS (after F1 fix) |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS (after F3 fix) |
| Plan Completeness | PASS (after F2 fix) |

## Grounding

10/10 paths ✓, 3/3 symbols ✓, brief↔plan ✓

## Findings

### F1 — Card IDs don't match between frontend and backend

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: End-State Alignment
- **Location**: Critical Implementation Details → Card ID mapping; Phase 1 → cardImageMap.ts
- **Detail**: Frontend uses Polish kebab-case IDs (e.g. "maly-john", "rog"), backend uses English snake_case (e.g. "little_john", "sherwood_horn"). Zero overlap. The image lookup map as originally designed would return placeholder for every card.
- **Fix A ⭐ Recommended**: Build a backend→frontend mapping table in cardImageMap.ts (28-entry static map, e.g. "little_john" → malyJohnImage). Pure frontend, no backend changes.
- **Fix B**: Add imageId field to backend Card record — single source of truth but requires engine JSON + backend changes (out of S-03 scope).
- **Decision**: FIXED via Fix A — plan updated: Critical Implementation Details and Phase 1 item 1 now document the ID mismatch and specify a hand-built 28-entry mapping table keyed by backend card IDs.

### F2 — Card.tsx prop adaptation is sequenced too late

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Completeness
- **Location**: Phase 6 item 4 vs Phase 2 item 1
- **Detail**: Phase 2 needs to render API data through Card.tsx, but current Card.tsx accepts `power` (not `currentStrength`) and `image` (not available from API). The plan put Card.tsx prop updates in Phase 6.
- **Fix**: Move Card.tsx prop update from Phase 6 to Phase 2.
- **Decision**: FIXED — Card.tsx prop update is now Phase 2 item 1. Phase 6 item 4 removed. Progress section updated.

### F3 — SPY targeting requires explicit opponent-row logic

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 3 item 1
- **Detail**: API returns `kind: "SPY", targetRow: "CLOSE"` with no side indicator. Frontend must check `kind === "SPY"` to highlight opponent rows instead of player rows.
- **Fix**: Add explicit note in Phase 3 that SPY moves highlight opponent-side rows, keyed on `kind === "SPY"`.
- **Decision**: FIXED — Phase 3 item 1 now explicitly separates UNIT (player-side rows) and SPY (opponent-side rows) with the kind-check documented.

### F4 — useMapTransform imports from campaignStages.ts

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — no action needed
- **Dimension**: Plan Completeness
- **Location**: Phase 5 item 3
- **Detail**: `useMapTransform.ts` imports `MAP_WIDTH, MAP_HEIGHT` from `campaignStages.ts`. The plan modifies status derivation but not these constants — no breakage expected.
- **Fix**: No action needed.
- **Decision**: SKIPPED
