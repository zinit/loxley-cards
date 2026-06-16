# Test Plan

> Phased test rollout for this project. Strategy is frozen at the top
> (§1–§5); cookbook patterns at the bottom (§6) fill in as phases ship.
> Read before writing any new test.
>
> Refresh: re-run `/10x-test-plan --refresh` when stale (see §8).
>
> Last updated: 2026-06-16

## 1. Strategy

Tests follow three non-negotiable principles for this project:

1. **Cost × signal.** The cheapest test that gives a real signal for the
   risk wins. Do not promote to e2e because e2e "feels safer." Do not put a
   vision model on top of a deterministic visual diff that already catches
   the regression.
2. **User concerns are first-class evidence.** Risks anchored in "the
   developer is worried about X, and the failure would surface somewhere in
   area Y" carry the same weight as PRD lines or hot-spot data.
3. **Risks are scenarios, not code locations.** This plan documents *what
   could fail* and *why we believe it's likely* — drawn from documents,
   interview, and codebase *signal* (churn, structure, test base). It does
   NOT claim to know which line owns the failure. That knowledge is
   produced by `/10x-research` during each rollout phase. If the plan and
   research disagree about where the failure lives, research is the
   ground truth.

Hot-spot scope used for likelihood weighting: `backend/app/`, `backend/acommon-game-engine/`, `backend/acommon-game-cli/`, `backend/acommon-db/`, `frontend/src/`. 12 commits in 30 days — borderline signal; likelihood ratings lean on roadmap and interview alongside churn.

## 2. Risk Map

The top failure scenarios this project must protect against, ordered by
risk = impact × likelihood. Risks are failure scenarios in user / business
terms, not test names. The Source column cites the *evidence that surfaced
this risk* — never a specific file as "where the failure lives" (that is
research's job, see §1 principle #3).

| # | Risk (failure scenario) | Impact | Likelihood | Source (evidence — not anchor) |
|---|------------------------|--------|------------|-------------------------------|
| 1 | Logged-in user's campaign progress disappears between sessions — user wins stage N, returns next day, stage N+1 is locked again or reverts to stage 1 | High | High | PRD Guardrails ("progres nie ginie"), interview Q1 (top worry), interview Q4 (scariest gap), roadmap S-05 (next slice, untested persistence path) |
| 2 | API contract drifts silently between backend DTOs and frontend consumers — backend changes a field name, type, or nesting; frontend renders stale/broken state; no test catches it until manual playtest | High | High | Interview Q3 (low confidence area), hot-spot dirs `app/web/` + `app/web/dto/` (30 touches/30d), archive S-03/plan.md (cardImageMap incident — zero overlap between backend and frontend card IDs) |
| 3 | Spring Security defaults silently invert auth invariants — a framework upgrade, config refactor, or new endpoint exposes authenticated data to anonymous users (or blocks legitimate users) without any visible error | High | Medium | Interview Q2 (AnonymousAuthenticationToken burn), archive F-03/plan.md (UserDetailsService bean missing = default in-memory user, "generated security password" in logs) |
| 4 | Game engine scoring produces incorrect round/match results under ability interactions — modifier stacking (weather + horn + tight bond + hero immunity) silently miscalculates; player sees "unfair" result, no crash, no error | Medium | Medium | PRD Business Logic (modifier ordering spec), archive F-01/plan.md (200 tests exist but interaction space is combinatorial), hot-spot dirs `engine/ability/effects/` + `engine/move/` (16 touches/30d) |
| 5 | Unauthenticated or wrong user accesses another user's game session or campaign progress — endpoints check "is logged in" but not "is this YOUR resource"; attacker with a valid JWT hits another user's gameId or campaign | High | Medium | PRD Access Control (flat role model, no ownership checks documented), archive S-02/plan.md ("no auth — any client with a gameId can play that game"), abuse/security lens (IDOR surface) |
| 6 | Secrets or PII leak into logs, error responses, or frontend bundle — JWT secret, DB password, or user data escapes via Hibernate URL logging, stack traces in 4xx/5xx bodies, or frontend bundle inspection | High | Low | Archive F-03/plan.md (Hibernate 7 JDBC URL leak incident — required credential rotation), tech-stack.md (public repo, educational showcase) |

### Risk Response Guidance

| Risk | What would prove protection | Must challenge | Context `/10x-research` must ground | Likely cheapest layer | Anti-pattern to avoid |
|------|----------------------------|----------------|--------------------------------------|-----------------------|----------------------|
| #1 | After winning a stage, a fresh session (new JWT, cleared localStorage) loads the same user's progress from DB and shows stage N+1 unlocked | "localStorage persistence = progress saved" — it's not; server-side persistence is the guardrail | DB write path after match win, read path on campaign load, session restore via /auth/me, relationship between game outcome and campaign_progress table | integration (Spring Boot Test + H2 Flyway schema) | Happy-path-only: testing only "save succeeds" without "read-back after session break returns same state" |
| #2 | A change to a backend DTO field name/type causes a test to fail BEFORE manual playtest | "Frontend compiles = contract is fine" — TypeScript types are hand-maintained copies, not generated from backend | DTO record shapes, frontend type definitions, field mapping in API client wrappers, Vite proxy config | contract test (backend integration test asserting JSON shape) | Snapshot test of raw JSON — breaks on harmless ordering changes, passes on semantic drift |
| #3 | An endpoint annotated `authenticated()` returns 401 (not 200 with "anonymousUser") when no JWT cookie is present; an endpoint annotated `permitAll()` is reachable without JWT | "permitAll means everyone can reach it" — yes, but `authenticated()` might not mean what you think if AnonymousAuthenticationFilter is active | SecurityConfig filter chain, which endpoints are permitAll vs authenticated, JwtAuthenticationFilter behavior on missing/invalid token | integration (MockMvc with and without JWT, assert status codes) | Testing only the happy path (valid JWT = 200) without the negative path (no JWT = 401, expired JWT = 401) |
| #4 | A hand with weather + horn + tight bond cards on the same row produces the expected score per the modifier ordering spec | "Unit tests per ability = interactions are covered" — modifier ordering is a cross-cutting concern; testing abilities in isolation misses stacking bugs | Modifier application order (hero immunity, weather, tight bond, morale boost, horn), row scoring pipeline, how multiple modifiers compose on the same card | unit (deterministic state setup, assert row score from spec) | Implementation mirror: copying the scoring formula into the test assertion instead of computing expected value from the spec independently |
| #5 | User A's JWT cannot read or modify User B's campaign progress or active game session | "JWT authentication = authorization" — authentication proves identity, not ownership; the endpoint must check that the resource belongs to the caller | Endpoint parameter binding (gameId, userId), ownership check (or lack thereof), campaign progress query scoping | integration (MockMvc with two different user JWTs, assert 403/404 on cross-access) | Testing only "authenticated user can access their own resource" without "authenticated user CANNOT access someone else's resource" |
| #6 | No secret (JWT_SECRET, DB password) appears in application logs at any log level, in any 4xx/5xx error response body, or in the frontend JS bundle | "We fixed the Hibernate leak" — the fix is config-based; a dependency upgrade or property change could revert it | Logging config, error handler response bodies, Spring Boot error attributes, frontend build output | integration (capture log output during startup, assert no password/secret patterns) + scripted check of built frontend JS | Testing only the specific Hibernate leak without checking other log paths or error response bodies |

## 3. Phased Rollout

Each row is a discrete rollout phase that will open its own change folder
via `/10x-new`. Status moves left-to-right through the values below; the
orchestrator updates Status as artifacts appear on disk.

| # | Phase name | Goal (one line) | Risks covered | Test types | Status | Change folder |
|---|-----------|-----------------|---------------|------------|--------|---------------|
| 1 | Critical-path integration | Defend campaign persistence (#1) and API contract integrity (#2) at the cheapest layer — highest-impact risks, S-05 depends on them | #1, #2 | backend integration (MockMvc + H2), contract shape assertions, Playwright E2E | complete | s-05-campaign-progression |
| 2 | Auth and security hardening | Prove auth invariants hold (#3), ownership checks exist (#5), and secrets don't leak (#6) | #3, #5, #6 | backend integration (MockMvc multi-user scenarios, log capture) | not started | — |
| 3 | Engine interaction coverage | Prove modifier stacking correctness (#4) for combinatorial ability interactions the existing 200 unit tests may not cover | #4 | unit (deterministic state, spec-derived expected values) | not started | — |
| 4 | Quality gates wiring | Lock the floor: CI gates for lint + typecheck + test suite on PR; frontend test runner bootstrap (vitest) with one smoke test | cross-cutting | CI gates, frontend test bootstrap | not started | — |

## 4. Stack

The classic test base for this project.

| Layer | Tool | Version | Notes |
|-------|------|---------|-------|
| unit + integration (backend) | JUnit 5 + AssertJ + Spring Boot Test + MockMvc | SB 4.0.6 | 48 test classes across 4 modules; H2 in-memory for DB tests via Flyway |
| unit (frontend) | none yet — see Phase 4 | — | No vitest/jest config, no test files in `frontend/src/` |
| API mocking | none yet — see Phase 1 | — | Backend integration tests use MockMvc (in-process); no MSW or similar |
| e2e | Playwright (Chromium) | 1.61 | 1 test: campaign progression across sessions (register → win → logout → login → verify). Manual server startup (no `webServer` config). `cd frontend && npx playwright test` |
| accessibility | none yet | — | Desktop-only product (PRD non-goal: no mobile); not prioritized |

**Stack grounding tools (current session):**
- Docs: none — no Context7 or framework docs MCP available in session; checked: 2026-06-16
- Search: WebSearch / WebFetch available (deferred) — not yet used; checked: 2026-06-16
- Runtime/browser: none — no Playwright MCP available; checked: 2026-06-16
- Provider/platform: none — no GitHub/Cloudflare/Supabase MCPs available; checked: 2026-06-16

## 5. Quality Gates

The full set of gates that must pass before a change reaches production.
"Required after §3 Phase N" means the gate is enforced once that rollout
phase lands; before that, the gate is `planned`.

| Gate | Where | Required? | Catches |
|------|-------|-----------|---------|
| lint + typecheck (backend: `mvn compile`; frontend: `tsc --noEmit`) | local + CI | required after §3 Phase 4 | syntactic / type drift |
| unit + integration (backend: `mvn test`) | local + CI | required after §3 Phase 1 | logic regressions, contract drift, auth invariant violations |
| frontend smoke (vitest) | local + CI | required after §3 Phase 4 | frontend build breakage, basic rendering |
| pre-deploy health check (curl /actuator/health/db) | CI deploy step | recommended after §3 Phase 4 | environment-specific failures (DB connection, missing env vars) |

## 6. Cookbook Patterns

How to add new tests in this project. Each sub-section is filled in once
the relevant rollout phase ships; before that, the sub-section reads
"TBD — see §3 Phase N."

### 6.1 Adding a backend integration test (API contract / persistence)

**Location:** `backend/app/src/test/java/cards/loxley/app/web/`
**Naming:** `<Feature>Tests.java` — e.g., `CampaignProgressionTests.java`
**Reference test:** `CampaignProgressionTests.java` (5 tests: contract shape, persistence across sessions, stage validation)

**Class scaffold:**
```java
@SpringBootTest
@AutoConfigureMockMvc          // security filters ON (JWT auth active)
@Transactional                 // auto-rollback per test for DB isolation
class YourFeatureTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;  // if you need DB setup
    private final ObjectMapper objectMapper = new ObjectMapper();  // for JSON parsing
}
```

**JWT cookie pattern** (register a user, extract JWT, use in subsequent requests):
```java
MvcResult result = mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"testuser\",\"password\":\"sherwood1\"}"))
    .andExpect(status().isCreated()).andReturn();
String setCookie = result.getResponse().getHeader("Set-Cookie");
String jwt = setCookie.substring(setCookie.indexOf("jwt=") + 4, setCookie.indexOf(";"));
// Use: mockMvc.perform(get("/auth/me").cookie(new Cookie("jwt", jwt)))
```

**Game-playing helper** (for tests that need a full match outcome): see `playGameToCompletion()` in `CampaignProgressionTests` — parses legal moves from JSON, plays first non-PASS move each turn, loops until `matchEnded=true`. Wrap in a retry loop (up to 10 attempts) because ultra_easy RandomBot can still win.

**Run commands:**
- All tests: `cd backend && ./mvnw test`
- Single class: `cd backend && ./mvnw -pl app test -Dtest=CampaignProgressionTests`
- Single method: `cd backend && ./mvnw -pl app test -Dtest=CampaignProgressionTests#winStage1_freshSession_progressPersisted`

### 6.2 Adding an auth / security integration test

TBD — see §3 Phase 2 for auth invariant, IDOR, and secret-leak detection patterns.

### 6.3 Adding an engine interaction test

TBD — see §3 Phase 3 for modifier stacking and spec-derived oracle patterns.

### 6.4 Adding a frontend test

TBD — see §3 Phase 4 for vitest bootstrap and smoke test pattern.

### 6.5 Per-rollout-phase notes

**Phase 1 (s-05-campaign-progression, 2026-06-16):**
- Game-playing helpers need retry loops in both backend and E2E tests — ultra_easy RandomBot can win multiple games in a row. Backend: 10 retries (fast, ~ms each). Playwright E2E: 5 retries (slow, ~15s each).
- Playwright `click()` on board rows fails because the hand panel CSS-overlaps them (`board-hand intercepts pointer events`). Fix: `element.evaluate(el => el.click())` dispatches a trusted DOM click that bypasses Playwright's actionability checks while still triggering React's event delegation. `force: true` and `dispatchEvent('click')` both failed to trigger React handlers.
- E2E test bootstrapped Playwright from scratch (Chromium only, no `webServer` config — servers started manually). Backend needs real Supabase DB for E2E (H2 driver is test-scoped only). Config: `playwright.config.ts` with `retries: 2` for RNG tolerance.

## 7. What We Deliberately Don't Test

Exclusions agreed during the rollout (Phase 2 interview, Q5). Future
contributors should respect these unless the underlying assumption changes.

- **Frontend visual/snapshot/pixel tests** — break constantly on style tweaks, catch nothing functional in a Sherwood-themed game with hand-crafted assets. Re-evaluate if the project adopts a design system with stable component contracts. (Source: Phase 2 interview Q5.)
- **DevTestController / CLI runner eval harness** — dev-only tools behind `@Profile("dev")` or standalone CLI module, used by one person, low blast radius. Re-evaluate if dev tools grow user-facing features. (Source: Phase 2 interview Q5, implicit.)
- **Supabase infra / deploy pipeline health** — ops concerns (TLS renewal, project pausing, Docker restart) belong to monitoring/alerting (UptimeRobot, keepalive cron), not to the test suite. Re-evaluate if CI/CD pipeline gets a deploy-and-smoke step. (Source: infrastructure.md risk register, Phase 3 challenger.)
- **Engine unit internals beyond interaction gaps** — 36 test classes / 200 tests already cover individual abilities, scoring, and bot strategies. Phase 3 adds targeted interaction coverage only. Re-evaluate if engine undergoes a major refactor or new abilities are added. (Source: test-base profile.)

## 8. Freshness Ledger

- Strategy (§1–§5) last reviewed: 2026-06-16
- Stack versions last verified: 2026-06-16
- AI-native tool references last verified: 2026-06-16 (none recommended in this rollout)

Refresh (`/10x-test-plan --refresh`) when:

- a new top-3 risk surfaces from the roadmap or archive,
- a recommended tool's `checked:` date is older than three months,
- the project's tech stack changes (new framework, new test runner),
- §7 negative-space no longer matches what the team believes.
