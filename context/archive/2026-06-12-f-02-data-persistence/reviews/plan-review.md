<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Data Persistence Layer

- **Plan**: context/changes/f-02-data-persistence/plan.md
- **Mode**: Deep
- **Date**: 2026-06-12
- **Verdict**: SOUND (after fixes)
- **Findings**: 1 critical, 1 warning, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS (after F1 fix) |
| Plan Completeness | PASS (after F2, F3 fixes) |

## Grounding

7/7 paths verified, brief-plan consistency confirmed.

## Findings

### F1 — App module tests will break when datasource config is added

- **Severity**: CRITICAL
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1, Change #2 (App datasource configuration)
- **Detail**: Adding `spring.datasource.url=${DATABASE_URL}` to `application.properties` will crash `LoxleyCardsApplicationTests` and `GameControllerTests` (8 tests) during `mvn test` — no test resources exist in app module, so `${DATABASE_URL}` placeholder can't resolve. Breaks Desired End State ("all modules green").
- **Fix**: Add `backend/app/src/test/resources/application.properties` with H2 datasource config + add H2 test-scope dependency to `app/pom.xml`.
- **Decision**: FIXED — added as Phase 1 Changes #4 and #5

### F2 — Migration SQL uses gen_random_uuid() incompatible with H2

- **Severity**: WARNING
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2, Change #1 (Flyway migration) + Migration Notes
- **Detail**: Phase 2 contract specified `DEFAULT gen_random_uuid()` which H2 doesn't support. Migration Notes discussed two options but didn't decide. Also `DEFAULT now()` has same H2 compat risk.
- **Fix**: Updated Phase 2 contract to pure structural SQL (no DB-level DEFAULTs except `DEFAULT 1` for highest_unlocked_stage). JPA handles UUID generation and timestamps. Migration Notes updated to reflect resolved decision.
- **Decision**: FIXED — Phase 2 contract and Migration Notes updated

### F3 — DATABASE_URL format not specified

- **Severity**: OBSERVATION
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1, Change #2
- **Detail**: `spring.datasource.url` expects JDBC format but Supabase shows both URI and JDBC formats. Plan didn't specify which. Spring does NOT auto-convert URI to JDBC.
- **Fix**: Added note specifying JDBC format requirement (Supabase JDBC tab, transaction-mode pooler URL on port 6543).
- **Decision**: FIXED — note added to Phase 1 Change #2 contract
