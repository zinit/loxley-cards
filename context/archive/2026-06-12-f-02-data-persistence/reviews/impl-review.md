<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Data Persistence Layer

- **Plan**: context/changes/f-02-data-persistence/plan.md
- **Scope**: All phases (1-3 of 3)
- **Date**: 2026-06-12
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS (after F1 fix) |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — TIMESTAMP vs TIMESTAMPTZ for Instant fields

- **Severity**: WARNING
- **Impact**: MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: V1__create_users_table.sql:4-5
- **Detail**: Migration used `TIMESTAMP` (no timezone) for created_at/updated_at but entity uses `java.time.Instant` (inherently UTC). PostgreSQL's `TIMESTAMP` stores values without zone context. `TIMESTAMP WITH TIME ZONE` is the correct mapping.
- **Fix**: Added V2 migration `V2__timestamps_to_timestamptz.sql` with `ALTER COLUMN ... TYPE TIMESTAMP WITH TIME ZONE` (standard SQL syntax compatible with both PostgreSQL and H2).
- **Decision**: FIXED — V2 migration added, tests pass

### F2 — Unplanned prepareThreshold=0 + tech-stack.md update

- **Severity**: OBSERVATION
- **Impact**: LOW — quick decision
- **Dimension**: Scope Discipline
- **Location**: application.properties:9, tech-stack.md:52
- **Detail**: Two extras not in original plan: (1) prepareThreshold=0 for pgbouncer transaction-mode, (2) tech-stack.md documentation of pgbouncer gotcha + SB4 autoconfig split. Both arose from runtime bugs during implementation.
- **Decision**: ACKNOWLEDGED — legitimate operational fixes

### F3 — SB4 test starter adaptation

- **Severity**: OBSERVATION
- **Impact**: LOW — quick decision
- **Dimension**: Plan Adherence
- **Location**: acommon-db/pom.xml:49
- **Detail**: Plan said keep `spring-boot-starter-test`. Implementation replaced with `spring-boot-starter-data-jpa-test` (SB4 per-tech test starter providing @DataJpaTest, TestEntityManager). Import paths in tests also use SB4 package locations. Correct and necessary adaptation.
- **Decision**: ACKNOWLEDGED — correct SB4 adaptation

## Implementation notes

Two significant Spring Boot 4 gotchas discovered during implementation (not in plan, not in AI training data):
1. **SB4 per-tech autoconfig split**: `spring-boot-flyway` artifact required for FlywayAutoConfiguration (no longer in monolithic `spring-boot-autoconfigure`). Same pattern applies to test starters (`spring-boot-starter-data-jpa-test` vs `spring-boot-starter-test`).
2. **Supabase pgbouncer transaction-mode**: requires `prepareThreshold=0` in JDBC driver to avoid "prepared statement S_X already exists" errors on connection reuse.

Both documented in `context/foundation/tech-stack.md` for future slices.
