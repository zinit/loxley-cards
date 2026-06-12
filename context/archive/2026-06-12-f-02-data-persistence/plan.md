# Data Persistence Layer Implementation Plan

## Overview

Build the persistence foundation in the empty `acommon-db` module: Maven dependencies (JPA, PostgreSQL, Flyway, H2 for tests), a single `User` JPA entity (UUID PK, email unique, highest_unlocked_stage), Flyway migration for the `users` table, Spring Data repository, and datasource configuration reading `DATABASE_URL` from the environment (pointing to the shared Supabase Free instance in Frankfurt).

## Current State Analysis

- `acommon-db` module is an empty skeleton: POM has only `spring-boot-starter-test`, package `cards.loxley.db` exists but is empty, no `src/main/resources/` directory
- `app` module already declares a dependency on `loxley-cards-db` — entities and repos will be transitively available
- `LoxleyCardsApplication` scans `cards.loxley.*` — entities in `cards.loxley.db` will be auto-discovered by JPA and Spring Data
- `application.properties` in `app` has only `spring.application.name=loxley-cards`
- `GameSessionStore` is in-memory (HashMap), stays in-memory — out of scope
- Campaign stages loaded from JSON (`campaign_stages.json`) — no DB persistence needed
- Campaign progress currently tracked in frontend `localStorage` (`highest_unlocked` integer) — backend persistence for this value is the purpose of F-02; wiring to frontend happens in S-05

### Key Discoveries:

- `GameController.java` will be the future integration point (S-05) for reading/writing campaign progress after game completion — not touched in F-02
- `CampaignStage` is a record: `(int stageNumber, String opponentProfileId, String description)` — stage numbers are 1–10, linear unlock
- Parent POM (`backend/pom.xml`) already declares `loxley-cards-db` in `<dependencyManagement>` — the app module pulls it via `<groupId>cards.loxley</groupId><artifactId>loxley-cards-db</artifactId>`
- Supabase Free pgbouncer limit: 15 connections; infrastructure.md prescribes `maximum-pool-size=10` (leave 5 reserve)
- No Spring profiles for dev/prod — single configuration, single Supabase instance for dev and pre-prod (Daniel's decision)

## Desired End State

After this plan is complete:
- `mvn clean install` from `backend/` passes with all modules green (including new repo tests in `acommon-db`)
- `mvn -pl app spring-boot:run` starts the app, connects to Supabase PostgreSQL via `DATABASE_URL`, and Flyway applies `V1__create_users_table.sql` automatically on startup
- The `users` table exists in Supabase with columns: `id` (UUID PK), `email` (VARCHAR unique, not null), `highest_unlocked_stage` (INT, default 1), `created_at` (TIMESTAMP), `updated_at` (TIMESTAMP)
- `UserRepository` provides `findByEmail(String email)` for auth (F-03) and campaign progress (S-05)
- `mvn test` in `acommon-db` runs against H2 in-memory (no Supabase dependency for tests)

## What We're NOT Doing

- **No GameSession persistence** — `GameSessionStore` stays in-memory; game state serialization to JSONB is out of scope
- **No CampaignProgress as separate entity** — single `highest_unlocked_stage` column on `User` is sufficient for PRD's linear unlock model
- **No Docker Compose for local Postgres** — dev and pre-prod both use the shared Supabase instance
- **No Spring profiles** — single configuration, `DATABASE_URL` env var for all environments
- **No frontend changes** — wiring `localStorage` to server-side persistence is S-05 scope
- **No auth/security** — magic-link auth is F-03 scope
- **No match history / per-stage stats** — can be added later if needed; PRD only requires "unlock next stage"

## Implementation Approach

The `acommon-db` module is a library — it declares JPA entities, repositories, and Flyway migrations, but the datasource configuration lives in the `app` module's `application.properties` (since `app` is the Spring Boot bootstrap that owns the runtime context). This follows Spring Boot multi-module convention: library modules provide `@Entity` and `@Repository` beans, the bootstrap module provides connection config.

Flyway migrations live in `acommon-db/src/main/resources/db/migration/` — Spring Boot auto-discovers them on the classpath when the app starts. H2 test configuration lives in `acommon-db/src/test/resources/application-test.properties` and is activated via `@ActiveProfiles("test")` on test classes.

---

## Phase 1: Dependencies & Configuration

### Overview

Add all Maven dependencies to `acommon-db` POM and configure datasource properties in the `app` module. After this phase, `mvn clean install` compiles and the app can connect to Supabase (even though there are no entities or migrations yet).

### Changes Required:

#### 1. acommon-db POM dependencies

**File**: `backend/acommon-db/pom.xml`

**Intent**: Add Spring Data JPA, PostgreSQL driver, and Flyway as compile-scope dependencies. Add H2 as test-scope dependency for fast offline unit tests.

**Contract**: Dependencies to add:
- `spring-boot-starter-data-jpa` (brings JPA, Hibernate, HikariCP transitively)
- `org.postgresql:postgresql` (runtime scope — only needed at runtime, not compile)
- `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql` (Flyway 10+ requires the DB-specific module)
- `org.springframework.boot:spring-boot-flyway` — **Spring Boot 4 per-tech autoconfig split**: SB4 moved FlywayAutoConfiguration out of the monolithic `spring-boot-autoconfigure` into a separate `spring-boot-flyway` artifact. Without it, Flyway libs are on classpath but autoconfig never activates → migrations don't run → Hibernate validate fails. [Bug fix discovered during Phase 2 implementation]
- `com.h2database:h2` (test scope)

#### 2. App datasource configuration

**File**: `backend/app/src/main/resources/application.properties`

**Intent**: Configure Spring datasource to read connection URL from `DATABASE_URL` env var (Supabase pgbouncer transaction-mode URL on port 6543). Set HikariCP max pool size to 10 per infrastructure.md. Configure JPA/Hibernate for PostgreSQL dialect and validation-only DDL (Flyway owns schema). Configure Flyway to run migrations on startup.

**Contract**: Properties to add:
- `spring.datasource.url=${DATABASE_URL}` — must be JDBC format: `jdbc:postgresql://host:6543/postgres?user=...&password=...` (Supabase dashboard → Settings → Database → Connection string → **JDBC tab**, transaction-mode pooler URL on port 6543). Spring does NOT auto-convert URI format (`postgresql://...`) [F3 plan-review fix]
- `spring.datasource.hikari.maximum-pool-size=10` — per infrastructure.md (Supabase Free pgbouncer limit 15, leave 5 reserve)
- `spring.jpa.hibernate.ddl-auto=validate` — Flyway owns DDL; Hibernate only validates entity ↔ schema match
- `spring.jpa.open-in-view=false` — disable OSIV (best practice, no lazy-loading in controllers)

#### 3. Test configuration for H2 (acommon-db)

**File**: `backend/acommon-db/src/test/resources/application-test.properties`

**Intent**: Override datasource to use H2 in-memory for `mvn test`. This makes tests fast, offline, and independent of Supabase.

**Contract**: Properties:
- `spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`
- `spring.datasource.driver-class-name=org.h2.Driver`
- `spring.jpa.hibernate.ddl-auto=validate` (Flyway runs first, then Hibernate validates)
- `spring.flyway.enabled=true` (default, but explicit for clarity)

#### 4. Test configuration for H2 (app module) — [F1 plan-review fix]

**File**: `backend/app/src/test/resources/application.properties`

**Intent**: Existing app tests (`LoxleyCardsApplicationTests`, `GameControllerTests` — 8 MockMvc tests) load the full Spring context via `@SpringBootTest` without profiles. Adding `spring.datasource.url=${DATABASE_URL}` to main `application.properties` would crash these tests when `DATABASE_URL` is not set. This test-scope properties file provides an H2 fallback so `mvn test` works offline.

**Contract**: Properties (same H2 config as acommon-db tests):
- `spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`
- `spring.datasource.driver-class-name=org.h2.Driver`
- `spring.jpa.hibernate.ddl-auto=none` (no entity validation — app tests don't test persistence, just controllers)
- `spring.flyway.enabled=false` (app test context doesn't need migrations)

#### 5. H2 test dependency for app module — [F1 plan-review fix]

**File**: `backend/app/pom.xml`

**Intent**: Add H2 as test-scope dependency so the app module's test properties file can use the H2 driver.

**Contract**: Add `com.h2database:h2` (test scope).

### Success Criteria:

#### Automated Verification:

- Reactor builds cleanly: `cd backend && ./mvnw clean install -DskipTests`
- Dependencies resolve: `cd backend && ./mvnw -pl acommon-db dependency:tree` shows JPA, PostgreSQL, Flyway, H2

#### Manual Verification:

- Review `application.properties` for correct Supabase config pattern

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Entity, Repository & Migration

### Overview

Create the `User` JPA entity, `UserRepository` interface, and the Flyway migration SQL that creates the `users` table. After this phase, starting the app against Supabase creates the table and Hibernate validates the schema match.

### Changes Required:

#### 1. Flyway migration

**File**: `backend/acommon-db/src/main/resources/db/migration/V1__create_users_table.sql`

**Intent**: Create the `users` table with UUID primary key, unique email, campaign progress tracking, and timestamps. This is the first and only migration in F-02.

**Contract**: Table `users` with columns — [F2 plan-review fix: no DB-level DEFAULTs; JPA handles all value generation via `@GeneratedValue` and `@PrePersist`/`@PreUpdate` callbacks, ensuring H2 compatibility]:
- `id UUID PRIMARY KEY` — JPA generates UUID in Java (`@GeneratedValue(strategy = GenerationType.UUID)`)
- `email VARCHAR(255) NOT NULL UNIQUE` — login identifier, the only PII
- `highest_unlocked_stage INT NOT NULL DEFAULT 1` — linear campaign progress (stages 1–10); safe `DEFAULT 1` is standard SQL, supported by both Postgres and H2
- `created_at TIMESTAMP NOT NULL` — set by `@PrePersist` JPA callback
- `updated_at TIMESTAMP NOT NULL` — set by `@PrePersist` and `@PreUpdate` JPA callbacks

#### 2. User entity

**File**: `backend/acommon-db/src/main/java/cards/loxley/db/User.java`

**Intent**: JPA entity mapping to the `users` table. Provides the domain object for auth (F-03) and campaign progress (S-05).

**Contract**: Entity `User` mapped to table `users`:
- `id` — `UUID`, `@Id`, `@GeneratedValue(strategy = GenerationType.UUID)`
- `email` — `String`, `@Column(nullable = false, unique = true)`
- `highestUnlockedStage` — `int`, `@Column(nullable = false)`, default 1
- `createdAt` — `Instant`, `@Column(nullable = false, updatable = false)`
- `updatedAt` — `Instant`, `@Column(nullable = false)`
- JPA lifecycle callbacks (`@PrePersist`, `@PreUpdate`) to set timestamps automatically

#### 3. UserRepository

**File**: `backend/acommon-db/src/main/java/cards/loxley/db/UserRepository.java`

**Intent**: Spring Data JPA repository for User CRUD + lookup by email.

**Contract**: Interface extending `JpaRepository<User, UUID>` with:
- `Optional<User> findByEmail(String email)` — derived query method, used by F-03 auth and S-05 campaign progress

### Success Criteria:

#### Automated Verification:

- Reactor builds: `cd backend && ./mvnw clean install -DskipTests`
- App starts with Supabase: `DATABASE_URL=<supabase-url> cd backend && ./mvnw -pl app spring-boot:run` — Flyway applies V1 migration, app starts without errors

#### Manual Verification:

- Verify `users` table exists in Supabase dashboard (SQL Editor: `SELECT * FROM users;`)
- Verify table has correct columns and constraints

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Verification & Tests

### Overview

Write an integration test in `acommon-db` that validates Flyway migration + UserRepository CRUD against H2. Verify the full reactor build passes. This ensures the persistence layer is solid before downstream changes (F-03, S-05) depend on it.

### Changes Required:

#### 1. Repository integration test

**File**: `backend/acommon-db/src/test/java/cards/loxley/db/UserRepositoryTests.java`

**Intent**: Integration test that boots a minimal Spring context with JPA + H2, runs Flyway migration, and validates basic UserRepository operations: create, find by email, update highest_unlocked_stage.

**Contract**: Test class annotated with `@DataJpaTest` + `@ActiveProfiles("test")` + `@AutoConfigureTestDatabase(replace = Replace.NONE)` (use our H2 config, not Spring's default replacement). Test methods:
- Save a User and verify it gets a generated UUID
- Find by email returns the saved User
- Find by non-existent email returns empty Optional
- Update `highestUnlockedStage` and verify persistence
- Unique constraint on email — saving duplicate email throws exception

#### 2. Test application boot class (if needed)

**File**: `backend/acommon-db/src/test/java/cards/loxley/db/TestConfig.java`

**Intent**: Minimal `@SpringBootApplication` or `@Configuration` class for `@DataJpaTest` to discover in the `acommon-db` module (since the module doesn't have its own `@SpringBootApplication`). Only needed if `@DataJpaTest` can't find a configuration class.

**Contract**: A `@SpringBootApplication` annotated class in the test source root, scanning `cards.loxley.db`.

### Success Criteria:

#### Automated Verification:

- All tests pass: `cd backend && ./mvnw clean install` (includes existing 238+ tests from other modules + new db tests)
- Specifically: `cd backend && ./mvnw -pl acommon-db test` passes

#### Manual Verification:

- Run the full app with Supabase and play a game to verify nothing regressed (game flow still works with new JPA + Flyway on the classpath)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- `UserRepositoryTests` in `acommon-db` — CRUD operations, unique constraint, find-by-email, campaign progress update
- All tests run against H2 in-memory via `@ActiveProfiles("test")`

### Integration Tests:

- Existing `LoxleyCardsApplicationTests` in `app` module should still pass (app context loads with JPA + Flyway on classpath)
- Manual startup against Supabase validates real Postgres compatibility

### Manual Testing Steps:

1. Set `DATABASE_URL` to Supabase connection string
2. Start app: `cd backend && ./mvnw -pl app spring-boot:run`
3. Verify Flyway migration log in console output (`Successfully applied 1 migration`)
4. Check Supabase dashboard — `users` table exists with correct schema
5. Play a game via frontend to verify no regression

## Performance Considerations

- HikariCP max pool size = 10 (infrastructure.md constraint for Supabase Free pgbouncer 15-connection limit)
- `open-in-view=false` — no lazy-loading pitfalls in controllers
- Single User entity with integer column — negligible overhead; no N+1 risk

## Migration Notes

- `V1__create_users_table.sql` is the first Flyway migration — no baseline or prior schema to worry about
- Migration is purely structural (columns, types, constraints) — no DB-specific functions like `gen_random_uuid()` or `now()`. JPA handles UUID generation (`@GeneratedValue(strategy = UUID)`) and timestamps (`@PrePersist`/`@PreUpdate`). This keeps the SQL compatible with both PostgreSQL (production/Supabase) and H2 (tests)

## References

- Roadmap F-02 definition: `context/foundation/roadmap.md` (line ~82)
- Infrastructure constraints: `context/foundation/infrastructure.md` (HikariCP, Supabase, pgbouncer)
- Tech stack: `context/foundation/tech-stack.md` (module layout, Spring Data JPA + HikariCP)
- Existing in-memory store: `backend/app/src/main/java/cards/loxley/app/web/GameSessionStore.java`
- Frontend campaign progress: `frontend/src/utils/campaignProgress.ts`
- Campaign stages JSON: `backend/acommon-game-engine/src/main/resources/data/campaign_stages.json`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Dependencies & Configuration

#### Automated

- [x] 1.1 Reactor builds cleanly: `cd backend && ./mvnw clean install -DskipTests`
- [x] 1.2 Dependencies resolve: `cd backend && ./mvnw -pl acommon-db dependency:tree` shows JPA, PostgreSQL, Flyway, H2

#### Manual

- [x] 1.3 Review `application.properties` for correct Supabase config pattern

### Phase 2: Entity, Repository & Migration

#### Automated

- [x] 2.1 Reactor builds: `cd backend && ./mvnw clean install -DskipTests`
- [x] 2.2 App starts with Supabase: Flyway applies V1 migration, app starts without errors

#### Manual

- [x] 2.3 Verify `users` table exists in Supabase dashboard with correct columns and constraints

### Phase 3: Verification & Tests

#### Automated

- [x] 3.1 All tests pass: `cd backend && ./mvnw clean install`
- [x] 3.2 DB module tests pass: `cd backend && ./mvnw -pl acommon-db test`

#### Manual

- [x] 3.3 Run full app with Supabase and play a game to verify no regression
