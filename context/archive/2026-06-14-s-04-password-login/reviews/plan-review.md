<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Password Login UI

- **Plan**: context/changes/s-04-password-login/plan.md
- **Mode**: Deep
- **Date**: 2026-06-14
- **Verdict**: REVISE → SOUND (after F1 fix)
- **Findings**: 1 critical, 1 warning, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | FAIL → PASS (F1 fixed) |
| Plan Completeness | WARNING |

## Grounding

8/8 paths verified, 1/1 symbols confirmed (anonymous auth NOT disabled), brief↔plan consistent.

## Findings

### F1 — /auth/me returns "anonymousUser" instead of 401

- **Severity**: CRITICAL
- **Impact**: MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 1 — Auth me endpoint contract
- **Detail**: SecurityConfig doesn't disable anonymous auth. Spring's AnonymousAuthenticationFilter sets principal="anonymousUser" with isAuthenticated()=true when no JWT cookie is present. The plan's original null/isAuthenticated check would return 200 {"username":"anonymousUser"} instead of 401.
- **Fix A Recommended**: Add instanceof AnonymousAuthenticationToken check in /auth/me. Minimal change, no SecurityConfig side effects.
  - Strength: Minimal change (one extra condition), no side effects on existing endpoints.
  - Tradeoff: Couples the endpoint to a Spring Security internal type.
  - Confidence: HIGH — standard Spring Security pattern.
  - Blind spot: None significant.
- **Fix B**: Disable anonymous auth globally in SecurityConfig via .anonymous(anon -> anon.disable()).
  - Strength: Plan's original null-check works unchanged.
  - Tradeoff: Global change may conflict with S-05 if it wants to distinguish anon vs logged-in.
  - Confidence: MEDIUM — works for S-04, untested S-05 interaction.
  - Blind spot: S-05 scope unclear re anonymous game access.
- **Decision**: FIXED via Fix A — plan updated with instanceof AnonymousAuthenticationToken check and rationale.

### F2 — Progress section consolidates manual verification bullets

- **Severity**: WARNING
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: ## Progress — Phases 3, 4, 5
- **Detail**: Phase 3 has 4 manual bullets → 2 Progress items. Phase 4 has 7 → 4. Phase 5 has 5 → 3. Progress items summarize correctly but don't match 1:1.
- **Fix**: Expand Progress or consolidate Phase success criteria to match.
- **Decision**: SKIPPED

### F3 — parseError will be duplicated in authApi.ts

- **Severity**: OBSERVATION
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Lean Execution
- **Location**: Phase 2 — authApi.ts contract
- **Detail**: gameApi.ts has a private parseError() (7 lines). authApi.ts following "same pattern" will duplicate it.
- **Fix**: Extract parseError() to shared api/apiUtils.ts.
- **Decision**: SKIPPED
