# Data Persistence Layer — Plan Brief

> Full plan: `context/changes/f-02-data-persistence/plan.md`

## What & Why

Build the persistence foundation in the empty `acommon-db` Maven module: JPA entity for User (with campaign progress), Flyway migration for PostgreSQL, Spring Data repository, and datasource configuration pointing to Supabase. This is a foundation change — F-03 (magic-link auth) and S-05 (campaign progression) both depend on having a User entity and a working DB connection.

## Starting Point

The `acommon-db` module is a skeleton: POM exists with only `spring-boot-starter-test`, empty `cards.loxley.db` package, no resources directory. The `app` module already depends on `acommon-db` but has no datasource configuration. Campaign progress is tracked only in frontend `localStorage` (a single integer: highest unlocked stage). Game sessions live in an in-memory HashMap (`GameSessionStore`), which stays in-memory.

## Desired End State

The app connects to Supabase PostgreSQL on startup, Flyway creates the `users` table automatically, and `UserRepository` provides `findByEmail()` for downstream auth and campaign features. `mvn test` runs repo tests against H2 in-memory (fast, offline). The full reactor build passes with all existing 238+ tests still green.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
|---|---|---|
| Data model | Single `User` entity with `highest_unlocked_stage` INT column | PRD's linear unlock model (stage N → all 1..N playable) needs only one number, not a separate progress table. |
| Game session persistence | Keep in-memory, out of scope | PRD guardrail is about campaign progress not mid-game state; GameState serialization is complex and unnecessary. |
| Local dev DB | Single Supabase instance for dev and pre-prod | Daniel's decision: one DB now, migrate to fresh production instance after S-05. |
| Test DB | H2 in-memory via `@ActiveProfiles("test")` | Fast offline tests; schema is simple enough that H2/Postgres dialect differences are irrelevant. |
| User PK | UUID (generated) + email UNIQUE | PK never changes; safe for foreign keys; URL-safe for API paths. |

## Scope

**In scope:**
- Maven dependencies in `acommon-db` (JPA, PostgreSQL, Flyway, H2 test)
- Datasource + HikariCP + JPA config in `app/application.properties`
- `User` JPA entity with UUID PK, email, highest_unlocked_stage, timestamps
- `UserRepository` (Spring Data JPA, `findByEmail`)
- Flyway `V1__create_users_table.sql`
- H2 test configuration + `UserRepositoryTests`

**Out of scope:**
- GameSession DB persistence (stays in-memory)
- Separate CampaignProgress entity
- Docker Compose / Spring profiles
- Frontend changes (S-05)
- Auth/security (F-03)
- Match history / per-stage stats

## Architecture / Approach

Library module pattern: `acommon-db` declares entities, repos, and Flyway migrations; `app` provides datasource config and Spring Boot bootstrap. Flyway migrations in `acommon-db/src/main/resources/db/migration/` auto-discovered on classpath. Connection via `DATABASE_URL` env var (Supabase pgbouncer transaction-mode on port 6543). HikariCP capped at 10 connections (Supabase Free limit: 15).

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Dependencies & Configuration | POM deps + datasource config + H2 test config | Flyway DB-specific module (`flyway-database-postgresql`) needed for Flyway 10+ |
| 2. Entity, Repository & Migration | `User` entity + `UserRepository` + V1 SQL | H2/Postgres UUID generation compatibility (mitigated by JPA-level UUID gen) |
| 3. Verification & Tests | `UserRepositoryTests` + full reactor green | `@DataJpaTest` needs a test `@SpringBootApplication` class in the library module |

**Prerequisites:** Supabase project provisioned in `aws-eu-central-1` (Frankfurt) with connection string available as `DATABASE_URL`.
**Estimated effort:** ~1 session, 3 phases.

## Open Risks & Assumptions

- Supabase project must be provisioned and connection string available before Phase 2 manual verification
- Flyway 10+ ships with Spring Boot 4.x and requires `flyway-database-postgresql` module (not just `flyway-core`) — verify during Phase 1
- H2 doesn't support `gen_random_uuid()` — UUID generation handled by JPA in Java, not DB default

## Success Criteria (Summary)

- `mvn clean install` passes (all modules, all tests, including new repo tests)
- App starts against Supabase, Flyway creates `users` table, Hibernate validates schema
- `UserRepository.findByEmail()` works — ready for F-03 auth and S-05 campaign progression
