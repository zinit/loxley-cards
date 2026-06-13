<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Password Auth Scaffold

- **Plan**: `context/changes/f-03-password-auth-scaffold/plan.md`
- **Mode**: Deep
- **Date**: 2026-06-13
- **Verdict**: REVISE → SOUND (after fixes)
- **Findings**: 2 critical, 1 warning, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS (after F1, F2 fixes) |
| Plan Completeness | PASS (after F3 fix) |

## Grounding

11/11 paths ✓, 3/3 symbols ✓, brief↔plan ✓

## Findings

### F1 — V3 migration fails on H2 (acommon-db tests)

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 1 — V3 Flyway migration
- **Detail**: V3 uses `DROP CONSTRAINT users_email_key` — correct for Postgres but H2 auto-generates different constraint names. acommon-db tests run Flyway against H2 (`flyway.enabled=true`), so V3 would fail with "constraint not found".
- **Fix A ⭐ Recommended**: Drop the unique constraint drop entirely. Keep email's unique index in DB (harmless — email is nullable; uniqueness still sensible). Only change JPA annotation to `@Column(nullable = true)` without `unique = true`. V3 reduces to 3 portable SQL statements.
  - Strength: Zero H2/Postgres divergence; pure portable SQL.
  - Tradeoff: email unique index stays in DB (costs ~0 for tiny table).
  - Confidence: HIGH — `ALTER COLUMN ... DROP NOT NULL` works identically in H2 and Postgres.
  - Blind spot: None significant.
- **Fix B**: Use Flyway vendor-specific migration directories.
  - Strength: Each DB gets correct syntax.
  - Tradeoff: Doubles migration maintenance — overkill for hobby project.
  - Confidence: MEDIUM.
  - Blind spot: Flyway vendor directory config in SB4 not verified.
- **Decision**: FIXED (Fix A) — removed DROP CONSTRAINT from V3 contract, updated JPA annotation spec.

### F2 — Tests break from Phase 2: JWT_SECRET not in test properties

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 1 → Phase 2 → Phase 4 sequencing
- **Detail**: Phase 1 adds `app.jwt.secret=${JWT_SECRET}` to main application.properties. Phase 2 creates JwtTokenProvider with `@Value("${app.jwt.secret}")`. But test fallback was deferred to Phase 4. Between Phase 2 and Phase 4, every @SpringBootTest fails because `${JWT_SECRET}` env var is unset.
- **Fix**: Move test properties from Phase 4 §2 into Phase 1 §6.
- **Decision**: FIXED — test properties moved to Phase 1 §6; Phase 4 §2 removed.

### F3 — AuthControllerTests need DB schema + cleanup strategy

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Completeness
- **Location**: Phase 4 — AuthControllerTests
- **Detail**: (a) App test profile has `ddl-auto=none` + `flyway.enabled=false` — no `users` table in H2 for AuthControllerTests. (b) `@SpringBootTest` is not `@Transactional` — test data persists across methods.
- **Fix A ⭐ Recommended**: Enable Flyway in app test properties (`flyway.enabled=true`, `ddl-auto=validate`) + add `@Transactional` to AuthControllerTests.
  - Strength: Tests exercise real schema; automatic cleanup.
  - Tradeoff: Flyway runs migrations on H2 per test class (~100ms).
  - Confidence: HIGH — standard Spring Boot test pattern.
  - Blind spot: @Transactional can mask commit-time constraint violations.
- **Fix B**: Use `ddl-auto=create-drop` + @DirtiesContext.
  - Strength: No migration H2-compatibility needed.
  - Tradeoff: @DirtiesContext = slow; schema diverges from Flyway.
  - Confidence: MEDIUM.
- **Decision**: FIXED (Fix A) — Flyway enabled in app test properties (moved to Phase 1 §6), @Transactional added to AuthControllerTests contract.
