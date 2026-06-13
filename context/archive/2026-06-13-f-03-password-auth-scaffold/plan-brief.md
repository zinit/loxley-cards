# Password Auth Scaffold — Plan Brief

> Full plan: `context/changes/f-03-password-auth-scaffold/plan.md`

## What & Why

Add username + password authentication to the Spring Boot backend so that S-04 (login UI) and S-05 (campaign progression) have a working auth foundation to build on. Username + password was chosen over magic-link to avoid email provider dependency (Resend verified domain blocker).

## Starting Point

User entity exists with `email` (NOT NULL UNIQUE) but no `username` or `password_hash`. No Spring Security on the classpath. REST endpoints (game controller) are wide open. CORS configured via WebMvcConfigurer. Table `users` is empty (F-02 just shipped).

## Desired End State

Three auth endpoints work end-to-end: register (`POST /auth/register`), login (`POST /auth/login`), logout (`POST /auth/logout`). JWT returned as HTTPOnly cookie (7-day expiry, HMAC-SHA256). User schema evolved: `username` NOT NULL UNIQUE + `password_hash` NOT NULL, `email` relaxed to nullable. All existing game endpoints remain open (permitAll). Full test suite green including 8 new auth integration tests.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
|---|---|---|
| JWT library | JJWT 0.13.0 (io.jsonwebtoken) | Most popular Java JWT lib, simple API, no Spring coupling. |
| JWT expiry | 7 days, no refresh token | Fits "15 min evening session" persona — login once a week max. |
| Game endpoint protection | Permit all (for now) | F-03 is foundation; S-04/S-05 will tighten access. |
| Auth code location | app module (alongside GameController) | Follows existing pattern; keeps acommon-db as pure persistence. |
| Username constraints | 3-30 chars, `^[a-zA-Z][a-zA-Z0-9_-]{2,29}$` | Prevents broken inputs while staying permissive for 5–10 friends. |
| Password validation | Minimum 8 chars | Prevents trivially weak passwords without complexity theater. |
| Existing test handling | Disable security filters via `addFilters = false` | Game tests test game behavior, not auth; avoids coupling. |

## Scope

**In scope:**
- V3 Flyway migration (username + password_hash + email relaxation)
- User entity + repository updates
- Spring Security filter chain (stateless, JWT-based)
- JwtTokenProvider + JwtAuthenticationFilter
- AuthController (register/login/logout)
- CORS migration from WebMvcConfigurer to SecurityFilterChain
- BCrypt PasswordEncoder
- Auth integration tests + existing test fixes

**Out of scope:**
- Login UI (S-04)
- Game endpoint auth enforcement (S-04/S-05)
- Password reset, refresh tokens, rate limiting, CSRF tokens
- GameSessionStore user association (S-05)

## Architecture / Approach

Request flow: Browser → `JwtAuthenticationFilter` (reads `jwt` cookie, sets SecurityContext if valid) → `SecurityFilterChain` (permitAll for all endpoints currently) → Controller. Auth endpoints create/validate users against DB and set/clear HTTPOnly cookies. No database hit per request for authenticated users — JWT is self-contained.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Schema & Persistence | V3 migration + User entity + repository + env config | Low — table is empty, straightforward DDL |
| 2. Security Infrastructure | POM deps + JwtTokenProvider + SecurityFilterChain + CORS migration | SB4 autoconfig split (mitigated: starter pulls spring-boot-security transitively) |
| 3. Auth Controller | Register/login/logout endpoints + DTOs + error handling | Cookie mechanics (HttpOnly, SameSite, no Secure for local dev) |
| 4. Tests | Fix existing tests + 8 new auth tests | Ensuring addFilters=false keeps game tests isolated |

**Prerequisites:** F-02 done (User entity + Flyway infra), `JWT_SECRET` generated locally
**Estimated effort:** ~1 session across 4 phases

## Open Risks & Assumptions

- SB4 `spring-boot-starter-security` transitively pulls the autoconfig artifact — verified via Maven Central POM, but first `mvnw clean install` after adding dep will confirm
- CORS migration from WebMvcConfigurer to SecurityFilterChain must happen atomically in Phase 2 — partial migration breaks preflight requests
- `email` unique constraint name assumed to be `users_email_key` (Postgres default) — verify during Phase 1 migration if it fails

## Success Criteria (Summary)

- `cd backend && ./mvnw clean install` — full reactor green (all modules, all tests)
- Manual curl: register → login → logout cycle works with JWT cookie round-trip
- Existing frontend on localhost:5173 still works (CORS not broken, game endpoints still open)
