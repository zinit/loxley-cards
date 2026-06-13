# Password Auth Scaffold Implementation Plan

## Overview

Add username + password authentication infrastructure to the Spring Boot backend: V3 Flyway migration evolving the User schema, Spring Security filter chain with JWT in HTTPOnly cookie, BCrypt password hashing, and three auth REST endpoints (`POST /auth/register`, `/auth/login`, `/auth/logout`). Game endpoints remain open (auth enforcement deferred to S-04/S-05).

## Current State Analysis

- **User entity** (`acommon-db/.../db/User.java`): has `email` (NOT NULL UNIQUE), `highestUnlockedStage`, `createdAt`, `updatedAt`. No `username` or `password_hash` fields.
- **Flyway migrations**: V1 (create users), V2 (timestamp → timestamptz). Table is empty (F-02 just shipped, zero registered users).
- **Spring Security**: absent — no starter, no config, no filters. All endpoints are wide open.
- **REST pattern**: `GameController` in `app/web/` with constructor injection, DTO records, `GlobalExceptionHandler` with `@RestControllerAdvice`.
- **CORS**: configured via `WebMvcConfigurer.addCorsMappings()` in `WebConfig.java` for `/api/**` (localhost:5173, localhost:3000).
- **Tests**: `GameControllerTests` (8 tests) uses `@SpringBootTest` + `@AutoConfigureMockMvc`, no security awareness.
- **Config**: `DATABASE_URL` loaded from `backend/.env` (gitignored); `.env.example` committed with placeholders.

### Key Discoveries:

- `spring-boot-starter-security` transitively pulls `spring-boot-security` (the SB4 per-tech autoconfig artifact) — unlike the Flyway case in F-02, we do NOT need to declare `spring-boot-security` separately. One dependency suffices.
- JJWT 0.13.0 is the latest stable release (Aug 2025). Not managed by the Spring Boot BOM — version must be declared explicitly.
- When Spring Security is on the classpath, it overrides `WebMvcConfigurer` CORS config. CORS must move to the `SecurityFilterChain` bean via `http.cors(...)` with a `CorsConfigurationSource` — otherwise preflight requests get 403'd by the security filter before reaching MVC.
- `@AutoConfigureMockMvc` in existing tests will pick up the security filter chain once `spring-boot-starter-security` is added. Must disable filters to keep game tests passing.
- Table `users` is empty — V3 migration can safely `ADD COLUMN ... NOT NULL` without DEFAULT or backfill.

## Desired End State

Three auth endpoints work end-to-end: a user can register with username + password via `POST /auth/register`, receive a JWT in an HTTPOnly cookie, log in again via `POST /auth/login`, and log out via `POST /auth/logout` (cookie cleared). The User entity has `username` (NOT NULL UNIQUE) and `passwordHash` (NOT NULL) fields; `email` is nullable and non-unique. All existing game endpoints remain open (permitAll). All existing tests pass. New auth tests cover register, login, logout, duplicate username, bad credentials, and validation errors.

**Verification**: `cd backend && ./mvnw clean install` passes (all modules). Manual curl against `/auth/register` → `/auth/login` → `/auth/logout` works with JWT cookie round-trip.

## What We're NOT Doing

- **No login UI** — that's S-04.
- **No game endpoint protection** — game endpoints stay permitAll; auth enforcement comes in S-04/S-05.
- **No GameSessionStore user association** — games remain anonymous; user-game binding is S-05 scope.
- **No password reset** — explicit PRD non-goal for MVP (manual DB intervention if needed).
- **No refresh tokens** — single JWT with 7-day expiry, no token rotation infrastructure.
- **No rate limiting** — brute-force protection deferred (acceptable for 5–10 friends).
- **No CSRF tokens** — SPA with HTTPOnly cookie + SameSite=Lax is sufficient for MVP; stateless API doesn't use session-based CSRF.
- **No email sending** — zero mail dependency per infrastructure.md.

## Implementation Approach

Bottom-up: schema first (V3 migration + entity changes), then security infrastructure (JWT + filter chain), then auth controller + DTOs, finally tests. Each phase is independently verifiable — migration can be validated before touching Java security code; security config can be validated before adding auth endpoints.

## Critical Implementation Details

### CORS migration timing

When `spring-boot-starter-security` is added, Spring Security's filter chain runs before Spring MVC. The existing `WebConfig.addCorsMappings()` will be ignored for requests that hit the security filter. CORS must be configured inside `SecurityFilterChain` via `http.cors(cors -> cors.configurationSource(...))`. The existing `WebConfig` CORS block should be removed to avoid confusion (dual CORS config = hard-to-debug origin issues). This must happen in the same phase as the SecurityConfig — not before, not after.

### HTTPOnly cookie mechanics

JWT is set via `Set-Cookie` header in the response (not in the JSON body). Cookie attributes: `HttpOnly`, `SameSite=Lax`, `Path=/`, `Max-Age=604800` (7 days). In production (HTTPS), add `Secure` flag — but for local dev (HTTP), `Secure` must be off. Use `application.properties` flag or detect scheme. Logout = set same cookie with `Max-Age=0`.

---

## Phase 1: Schema & Persistence

### Overview

Evolve the User entity and database schema: add `username` + `password_hash` columns, relax `email` to nullable non-unique, update the JPA entity and repository, add `JWT_SECRET` to env config.

### Changes Required:

#### 1. V3 Flyway migration

**File**: `backend/acommon-db/src/main/resources/db/migration/V3__add_username_password_auth.sql`

**Intent**: Add `username` (NOT NULL UNIQUE) and `password_hash` (NOT NULL) columns to `users` table; relax `email` from NOT NULL UNIQUE to nullable non-unique. Table is empty so NOT NULL without DEFAULT is safe.

**Contract**: SQL migration with 3 portable statements (H2 + Postgres compatible): ADD COLUMN `username VARCHAR(255) NOT NULL UNIQUE`, ADD COLUMN `password_hash VARCHAR(255) NOT NULL`, ALTER COLUMN `email` DROP NOT NULL. The existing unique index on `email` is intentionally kept — it's harmless (email is nullable; if someone provides one, uniqueness is still sensible) and avoids H2/Postgres constraint-naming divergence (Postgres auto-names it `users_email_key`, H2 uses a different convention — DROP CONSTRAINT by name would fail in acommon-db tests that run Flyway against H2). JPA annotation changes to `@Column(nullable = true)` without `unique = true` so Hibernate validation passes.

#### 2. User entity

**File**: `backend/acommon-db/src/main/java/cards/loxley/db/User.java`

**Intent**: Add `username` and `passwordHash` fields to match V3 schema. Change constructor from `email` to `username` + `passwordHash`. Make `email` nullable. Keep existing lifecycle hooks.

**Contract**: New fields: `String username` (`@Column(nullable = false, unique = true)`), `String passwordHash` (`@Column(name = "password_hash", nullable = false)`). Existing `email` field: change annotation to `@Column(nullable = true)` (remove `unique = true` — DB unique index stays but Hibernate doesn't validate it, avoiding H2/Postgres constraint-name divergence). Constructor: `public User(String username, String passwordHash)`. Keep protected no-arg constructor for JPA.

#### 3. UserRepository

**File**: `backend/acommon-db/src/main/java/cards/loxley/db/UserRepository.java`

**Intent**: Add `findByUsername` lookup method for auth; keep `findByEmail` for potential future use.

**Contract**: Add `Optional<User> findByUsername(String username)`.

#### 4. UserRepository tests

**File**: `backend/acommon-db/src/test/java/cards/loxley/db/UserRepositoryTests.java`

**Intent**: Update existing tests for new constructor signature (`username` + `passwordHash` instead of `email`). Add test for `findByUsername`. Update unique constraint test to use `username` instead of `email`.

**Contract**: All existing test methods updated to use new `User(username, passwordHash)` constructor. New test method `findByUsernameReturnsUser()`. `uniqueEmailConstraint` test renamed/replaced with `uniqueUsernameConstraint`.

#### 5. Environment config

**File**: `backend/.env.example`

**Intent**: Add `JWT_SECRET` placeholder with generation instructions.

**Contract**: New line: `JWT_SECRET=<generate with: openssl rand -base64 48>`. Add to `backend/.env` locally (actual secret, gitignored).

#### 6. Application properties

**File**: `backend/app/src/main/resources/application.properties`

**Intent**: Add `jwt.secret` and `jwt.expiration-ms` properties loaded from env.

**Contract**: `app.jwt.secret=${JWT_SECRET}` and `app.jwt.expiration-ms=604800000` (7 days in ms). **Also update `backend/app/src/test/resources/application.properties`**: add `app.jwt.secret=test-secret-for-unit-tests-only-not-production` and `app.jwt.expiration-ms=604800000` — ensures `${JWT_SECRET}` resolution doesn't break @SpringBootTest context load in subsequent phases. Change `spring.flyway.enabled=false` → `spring.flyway.enabled=true` and `spring.jpa.hibernate.ddl-auto=none` → `spring.jpa.hibernate.ddl-auto=validate` — AuthControllerTests (Phase 4) need the `users` table in H2, which Flyway creates from migrations (requires V3 to be H2-compatible — see §1 above).

### Success Criteria:

#### Automated Verification:

- V3 migration applies cleanly against Supabase: `cd backend && ./mvnw -pl acommon-db flyway:migrate` (or app startup)
- All UserRepositoryTests pass: `cd backend && ./mvnw -pl acommon-db test`
- Full reactor builds: `cd backend && ./mvnw clean install`

#### Manual Verification:

- Inspect `users` table via Supabase dashboard: `username` VARCHAR NOT NULL UNIQUE, `password_hash` VARCHAR NOT NULL, `email` nullable no unique constraint
- `.env` has `JWT_SECRET` with 48+ base64 chars
- `.env.example` has placeholder with generation instructions

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Security Infrastructure

### Overview

Add Spring Security + JJWT dependencies, create JWT token provider utility, configure SecurityFilterChain (all game endpoints permitAll, auth endpoints open, CORS migrated from WebConfig), register BCrypt PasswordEncoder bean.

### Changes Required:

#### 1. POM dependencies

**File**: `backend/app/pom.xml`

**Intent**: Add Spring Security starter and JJWT library for JWT token operations.

**Contract**: Add `org.springframework.boot:spring-boot-starter-security` (no version — managed by parent BOM). Add `io.jsonwebtoken:jjwt-api:0.13.0` (compile), `io.jsonwebtoken:jjwt-impl:0.13.0` (runtime), `io.jsonwebtoken:jjwt-jackson:0.13.0` (runtime).

#### 2. JWT token provider

**File**: `backend/app/src/main/java/cards/loxley/app/security/JwtTokenProvider.java`

**Intent**: Encapsulate JWT creation and validation. Single `@Component` that generates signed tokens from a username and validates/extracts username from a token.

**Contract**: `@Component` class in `cards.loxley.app.security` package. Constructor injects `@Value("${app.jwt.secret}")` and `@Value("${app.jwt.expiration-ms}")`. Methods: `String generateToken(String username)` — creates JWT with subject=username, issuedAt=now, expiration=now+expirationMs, signs with HMAC-SHA256 key derived from base64-decoded secret. `String extractUsername(String token)` — parses token, returns subject. `boolean isValid(String token)` — parses token, returns true if signature valid and not expired, false otherwise (catch `JwtException`).

#### 3. JWT authentication filter

**File**: `backend/app/src/main/java/cards/loxley/app/security/JwtAuthenticationFilter.java`

**Intent**: `OncePerRequestFilter` that reads the JWT from the `jwt` cookie, validates it, and sets `SecurityContextHolder` authentication. If no cookie or invalid token, request proceeds unauthenticated (permitAll endpoints still work).

**Contract**: Extends `OncePerRequestFilter`. Reads cookie named `jwt` from `HttpServletRequest`. If present and valid via `JwtTokenProvider.isValid()`, extracts username, creates `UsernamePasswordAuthenticationToken` with empty authorities, sets on `SecurityContextHolder`. Always calls `filterChain.doFilter()` — never blocks (protection is declarative in SecurityFilterChain, not in this filter).

#### 4. Security configuration

**File**: `backend/app/src/main/java/cards/loxley/app/security/SecurityConfig.java`

**Intent**: Central `@Configuration` class that defines the `SecurityFilterChain`, CORS policy, CSRF disable, session management (stateless), and `PasswordEncoder` bean.

**Contract**: `@Configuration` class. Defines:
- `@Bean SecurityFilterChain filterChain(HttpSecurity http)`: disable CSRF (`.csrf(csrf -> csrf.disable())`), set session management to STATELESS, configure authorization: `/auth/**` permitAll, `/api/**` permitAll (for now — S-04 will tighten), all other requests permitAll. Add `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`. Configure CORS via `http.cors(cors -> cors.configurationSource(corsConfigurationSource()))`.
- Private `CorsConfigurationSource corsConfigurationSource()`: same origins as current WebConfig (`http://localhost:5173`, `http://localhost:3000`), methods GET/POST, allow credentials, all headers.
- `@Bean PasswordEncoder passwordEncoder()`: returns `new BCryptPasswordEncoder()`.

#### 5. Remove CORS from WebConfig

**File**: `backend/app/src/main/java/cards/loxley/app/web/WebConfig.java`

**Intent**: Remove `addCorsMappings` override — CORS now lives in SecurityConfig's filter chain. Keep `@EnableScheduling` if used elsewhere (GameSessionStore cleanup).

**Contract**: Remove the `addCorsMappings` method body. Class can remain as `@Configuration @EnableScheduling` shell if scheduling is needed, or remove `WebMvcConfigurer` implementation entirely.

### Success Criteria:

#### Automated Verification:

- Full reactor builds: `cd backend && ./mvnw clean install`
- App starts without errors: `cd backend && ./mvnw -pl app spring-boot:run` (verify Spring Security banner in log + "SecurityFilterChain" bean logged)

#### Manual Verification:

- `curl -v http://localhost:8080/api/games -X POST -H 'Content-Type: application/json' -d '{"stageNumber":1}'` returns 200 (game endpoints still open)
- Spring Security startup log shows SecurityFilterChain with expected matchers
- No CORS errors from frontend dev server (`npm run dev` on localhost:5173, open browser console)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Auth Controller & Error Handling

### Overview

Create the auth REST controller with register, login, and logout endpoints. Add auth-specific DTOs and exception handling. Wire the controller to UserRepository, PasswordEncoder, and JwtTokenProvider.

### Changes Required:

#### 1. Auth request/response DTOs

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/AuthRequest.java`

**Intent**: DTO for both register and login requests (same shape: username + password).

**Contract**: Java record `AuthRequest(String username, String password)`.

**File**: `backend/app/src/main/java/cards/loxley/app/web/dto/AuthResponse.java`

**Intent**: DTO for successful auth responses (username echoed back; JWT is in the cookie, not in the body).

**Contract**: Java record `AuthResponse(String username)`.

#### 2. Auth controller

**File**: `backend/app/src/main/java/cards/loxley/app/web/AuthController.java`

**Intent**: REST controller handling registration, login, and logout. Sets/clears JWT cookie on responses.

**Contract**: `@RestController @RequestMapping("/auth")`. Constructor injects `UserRepository`, `PasswordEncoder`, `JwtTokenProvider`. Three endpoints:

- `POST /auth/register`: validate username (regex `^[a-zA-Z][a-zA-Z0-9_-]{2,29}$`) and password (length >= 8). Check `userRepository.findByUsername()` for duplicate → 409. Create `User(username, passwordEncoder.encode(password))`, save. Generate JWT, set cookie on response. Return 201 + `AuthResponse(username)`.
- `POST /auth/login`: find user by username → 401 if not found. Verify password with `passwordEncoder.matches()` → 401 if wrong. Generate JWT, set cookie on response. Return 200 + `AuthResponse(username)`.
- `POST /auth/logout`: set cookie with `Max-Age=0` to clear it. Return 200.

Private helper: `addJwtCookie(HttpServletResponse response, String token)` — creates `ResponseCookie` with name `jwt`, value = token, httpOnly = true, sameSite = Lax, path = `/`, maxAge = 7 days. Does NOT set `Secure` flag (local dev is HTTP; production HTTPS toggle deferred to deploy).

#### 3. Auth exception handling

**File**: `backend/app/src/main/java/cards/loxley/app/web/GlobalExceptionHandler.java`

**Intent**: Add handler for auth validation errors (username taken, bad credentials). Reuse existing `ErrorResponse` record.

**Contract**: Add two `@ExceptionHandler` methods:
- `UsernameAlreadyExistsException` → 409 CONFLICT with code `"USERNAME_TAKEN"`.
- `BadCredentialsException` (from Spring Security) → 401 UNAUTHORIZED with code `"BAD_CREDENTIALS"`.
- Validation errors (short username, short password) → 400 BAD_REQUEST with code `"VALIDATION_ERROR"` (can reuse existing `IllegalArgumentException` handler or add specific).

#### 4. Custom auth exception

**File**: `backend/app/src/main/java/cards/loxley/app/web/UsernameAlreadyExistsException.java`

**Intent**: Domain exception for duplicate username registration attempts.

**Contract**: `public class UsernameAlreadyExistsException extends RuntimeException` with `public UsernameAlreadyExistsException(String username)`.

### Success Criteria:

#### Automated Verification:

- Full reactor builds: `cd backend && ./mvnw clean install`

#### Manual Verification:

- Register: `curl -v http://localhost:8080/auth/register -X POST -H 'Content-Type: application/json' -d '{"username":"robin","password":"sherwood1"}'` → 201 + `Set-Cookie: jwt=...` with HttpOnly flag
- Login: `curl -v http://localhost:8080/auth/login -X POST -H 'Content-Type: application/json' -d '{"username":"robin","password":"sherwood1"}'` → 200 + `Set-Cookie: jwt=...`
- Logout: `curl -v http://localhost:8080/auth/logout -X POST --cookie 'jwt=...'` → 200 + `Set-Cookie: jwt=; Max-Age=0`
- Duplicate register: same curl → 409 + `{"code":"USERNAME_TAKEN",...}`
- Bad password: `curl ... -d '{"username":"robin","password":"wrongpass"}'` → 401
- Short username: `curl ... -d '{"username":"ab","password":"sherwood1"}'` → 400
- Short password: `curl ... -d '{"username":"friar_tuck","password":"short"}'` → 400

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Tests

### Overview

Fix existing GameControllerTests to bypass the new security filter chain. Add comprehensive AuthControllerTests covering registration, login, logout, validation, and error cases.

### Changes Required:

#### 1. Fix existing game tests

**File**: `backend/app/src/test/java/cards/loxley/app/web/GameControllerTests.java`

**Intent**: Existing 8 tests must keep passing without auth. Disable security filters so game tests don't require JWT.

**Contract**: Add `@AutoConfigureMockMvc(addFilters = false)` to the test class annotation (replaces plain `@AutoConfigureMockMvc`). This disables the security filter chain for these tests. No other changes to test methods.

#### 2. Auth controller tests

**File**: `backend/app/src/test/java/cards/loxley/app/web/AuthControllerTests.java`

**Intent**: Integration tests for all auth endpoints using MockMvc WITH security filters enabled (unlike game tests).

**Contract**: `@SpringBootTest` + `@AutoConfigureMockMvc` (WITH filters — no `addFilters = false`) + `@Transactional` (auto-rollback after each test — prevents cross-test data leakage, same pattern as `@DataJpaTest` in UserRepositoryTests). **Prerequisite**: app test properties must have `flyway.enabled=true` + `ddl-auto=validate` (changed from `flyway.enabled=false` + `ddl-auto=none` — GameControllerTests still work because they never touch the DB; AuthControllerTests need the `users` table in H2). Test methods:
- `register_validCredentials_returns201WithCookie` — POST /auth/register, assert 201, assert Set-Cookie header contains `jwt=`, assert HttpOnly flag, assert response body has username.
- `register_duplicateUsername_returns409` — register once, register again same username, assert 409 + `USERNAME_TAKEN`.
- `register_shortUsername_returns400` — username "ab", assert 400.
- `register_shortPassword_returns400` — password "short", assert 400.
- `login_validCredentials_returns200WithCookie` — register, then login, assert 200 + cookie.
- `login_wrongPassword_returns401` — register, then login with wrong password, assert 401.
- `login_unknownUser_returns401` — login without registering, assert 401.
- `logout_returns200WithClearedCookie` — assert Set-Cookie with Max-Age=0.

### Success Criteria:

#### Automated Verification:

- All existing GameControllerTests pass: `cd backend && ./mvnw -pl app test -Dtest=GameControllerTests`
- All new AuthControllerTests pass: `cd backend && ./mvnw -pl app test -Dtest=AuthControllerTests`
- Full reactor green: `cd backend && ./mvnw clean install`

#### Manual Verification:

- Review test output: all test names visible in Maven surefire report, zero failures, zero errors

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- `UserRepositoryTests` (updated): save with username+passwordHash, findByUsername, uniqueUsernameConstraint
- `JwtTokenProvider` could have unit tests but is covered transitively through AuthControllerTests (integration test via MockMvc exercises the full stack)

### Integration Tests:

- `AuthControllerTests`: 8 test methods covering register/login/logout happy paths + all error cases (duplicate, bad credentials, validation)
- `GameControllerTests`: 8 existing tests with security filters disabled — validates that game endpoints remain unaffected by security addition

### Manual Testing Steps:

1. Start backend: `cd backend && ./mvnw -pl app spring-boot:run`
2. Register via curl and verify JWT cookie in response
3. Login via curl with same credentials, verify cookie
4. Use cookie to access `/api/games` (should work — but it works without cookie too, since permitAll)
5. Logout via curl, verify cookie cleared (Max-Age=0)
6. Attempt duplicate registration, verify 409
7. Attempt login with wrong password, verify 401
8. Verify frontend still works from localhost:5173 (CORS not broken)

## Performance Considerations

- BCrypt rounds: default (10) is fine — ~100ms per hash on modern hardware, acceptable for 5–10 users.
- JWT validation per request: HMAC-SHA256 is sub-millisecond, negligible overhead.
- No database hit on every request — JWT is self-contained; DB is hit only on register/login.

## Migration Notes

- V3 migration is safe because `users` table is empty (F-02 shipped, zero registrations). If the table had rows, we'd need a 2-step migration (ADD nullable → backfill → ALTER SET NOT NULL).
- `email` column kept (nullable, non-unique) for potential future use (password reset, notifications). Not dropped to avoid a V4 re-add.

## References

- Roadmap F-03 definition: `context/foundation/roadmap.md:95-106`
- Infrastructure auth decisions: `context/foundation/infrastructure.md` (JWT secret mgmt, password reset strategy)
- Tech stack auth rationale: `context/foundation/tech-stack.md:29` (why username+password over magic-link)
- PRD Access Control: `context/foundation/prd.md` (flat role model, no OAuth)
- F-02 SB4 autoconfig lesson: `context/archive/2026-06-12-f-02-data-persistence/plan.md` (spring-boot-flyway pattern)
- Existing REST pattern: `backend/app/src/main/java/cards/loxley/app/web/GameController.java`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Schema & Persistence

#### Automated

- [x] 1.1 V3 migration applies cleanly against Supabase
- [x] 1.2 All UserRepositoryTests pass
- [x] 1.3 Full reactor builds

#### Manual

- [x] 1.4 Inspect users table schema in Supabase dashboard
- [x] 1.5 .env has JWT_SECRET, .env.example has placeholder

### Phase 2: Security Infrastructure

#### Automated

- [x] 2.1 Full reactor builds
- [x] 2.2 App starts without errors (Security banner in log)

#### Manual

- [x] 2.3 Game endpoints still return 200 without auth
- [x] 2.4 No CORS errors from frontend dev server

### Phase 3: Auth Controller & Error Handling

#### Automated

- [x] 3.1 Full reactor builds

#### Manual

- [x] 3.2 Register returns 201 with JWT cookie
- [x] 3.3 Login returns 200 with JWT cookie
- [x] 3.4 Logout clears cookie
- [x] 3.5 Duplicate register returns 409
- [x] 3.6 Bad credentials return 401
- [x] 3.7 Validation errors return 400

### Phase 4: Tests

#### Automated

- [x] 4.1 All GameControllerTests pass (security filters disabled)
- [x] 4.2 All AuthControllerTests pass
- [x] 4.3 Full reactor green
