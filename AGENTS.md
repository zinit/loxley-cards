# Repository Guidelines

Loxley-cards is a Gwint-inspired web card game — Spring Boot 4.0.6 backend (Java 21, Maven) with a planned React/TypeScript SPA frontend. Single-player campaign vs bot, username + password auth (BCrypt + JWT), PostgreSQL persistence.

## Hard Rules

- Keep game engine logic (scoring, card abilities, round resolution) in the `acommon-game-engine` module — not in controllers or the `app` module. See @context/foundation/tech-stack.md for the planned module layout.
- No personal data is collected by default — login uses a user-chosen `username` (any string) and a BCrypt-hashed password. The `email` column on `User` is reserved for future use and unused in MVP. **Schema state (post-F-03):** `email` is `VARCHAR(255) NULL UNIQUE` — nullable (anonymous users) but unique when set (future password reset / notification flows depend on it). Postgres treats `NULL` rows as distinct under `UNIQUE`, so multiple users without email coexist without collision (H2 behaves the same way; verified in `UserRepositoryTests`). V3 migration kept the unique index — per F-03 plan-review 2026-06-13, dropping it would require Postgres-specific `DROP CONSTRAINT users_email_key` which is not portable to H2 test profile. No tracking, analytics, or marketing cookies.
- Use Supabase ONLY as managed PostgreSQL provider (JDBC via Spring HikariCP). Do NOT add Supabase JS SDK, Supabase Auth, Supabase Storage, Realtime, or Edge Functions — these are explicit out-of-scope (see @context/foundation/infrastructure.md). Authentication is implemented natively in Spring Security (username + BCrypt password hash + JWT in HTTPOnly cookie).
- No transactional mail service in MVP. Do NOT add Resend, SMTP, Mailgun, or any email-sending dependency. No magic-link auth, no password reset (manual DB intervention if a user forgets their password), no notifications.
- Java base package is `cards.loxley` (reverse of `loxley.cards` domain). All new code goes under this package; sub-packages per concern: `cards.loxley.{app,game,cli,db,ai}`.

## Project Structure

Maven multi-module backend under `backend/` (groupId `cards.loxley`, parent `loxley-cards-parent`). Four library / runner modules + one Spring Boot bootstrap module:

```
backend/
├── pom.xml                              # parent POM (packaging: pom)
├── mvnw, mvnw.cmd, .mvn/                # Maven wrapper at reactor root
├── app/                                 # Spring Boot bootstrap (artifactId: loxley-cards-app)
│   └── src/main/java/cards/loxley/      # LoxleyCardsApplication + REST controllers + security
├── acommon-game-engine/                 # engine + scoring + bot + card defs (artifactId: loxley-cards-game-engine)
│   └── src/main/java/cards/loxley/game/
├── acommon-game-cli/                    # standalone Spring Boot CLI runner for engine (artifactId: loxley-cards-game-cli)
│   └── src/main/java/cards/loxley/cli/  # LoxleyCliApplication + board/hand renderers + move parser
├── acommon-db/                          # JPA entities + repos + Flyway migrations (loxley-cards-db)
│   └── src/main/java/cards/loxley/db/
└── acommon-ai/                          # AI integrations stub (loxley-cards-ai)
    └── src/main/java/cards/loxley/ai/

context/foundation/                      # PRD, shape notes, tech stack, infrastructure decisions
frontend/                                # placeholder for Vite + React + TS (not yet scaffolded)
```

`app/` depends on the `acommon-*` modules via `dependencyManagement` declared in `backend/pom.xml`. `acommon-game-cli` depends on `acommon-game-engine`. Note: `LoxleyCliApplication` declares `@SpringBootApplication(scanBasePackages = {"cards.loxley.cli", "cards.loxley.game"})` so engine beans (which live outside the CLI's own package tree) get discovered — keep this in mind when adding new engine subpackages.

## Build & Dev Commands

All Maven commands run from `backend/` (the reactor root). Maven wrapper lives there.

- `cd backend && ./mvnw clean install` — full multi-module build + tests + install to local `~/.m2/`
- `cd backend && ./mvnw test` — run tests across all modules
- `cd backend && ./mvnw -pl app spring-boot:run` — start the REST dev server (devtools hot-reload enabled). The `-pl app` flag targets the bootstrap module.
- `cd backend && ./mvnw -pl app package` — build only the app fat JAR (`backend/app/target/loxley-cards-app-*.jar`)
- `cd backend && ./mvnw -pl acommon-game-engine test` — run tests for a single library module
- `cd backend && ./mvnw -pl acommon-game-cli spring-boot:run` — run the standalone engine CLI: bot evaluation (4 matchups × 50 games), opponent-profile evaluation (5 profiles × 30 games), seeded bot-vs-bot simulation, then exit. Useful for smoke-testing the engine end-to-end before REST or the frontend exist.
- `cd backend && ./mvnw -pl acommon-game-cli spring-boot:run -Dspring-boot.run.profiles=cli-player` — interactive player-vs-bot REPL in the terminal (skips the bot-eval startup runner via `@Profile("!cli-player")`, hands control to `CliGameRunner` reading stdin).

## Testing

- Naming: `*Tests.java` (per existing `LoxleyCardsApplicationTests`).

## Commit & PR Conventions

- No established convention yet (single "init" commit). Use short imperative messages (e.g. "add card model", "fix round scoring").
- No CI pipeline configured. Target: GitHub Actions with auto-deploy-on-merge per @context/foundation/tech-stack.md.

## Key Context

- PRD and design decisions: @context/foundation/prd.md
- Tech stack rationale, Java package, module plan: @context/foundation/tech-stack.md
- Infrastructure decisions (platform, DB, domain, risk register): @context/foundation/infrastructure.md
- Domain: `loxley.cards` (apex → frontend on Cloudflare Pages, `api.loxley.cards` → backend on Hetzner VPS).
- Database: Supabase managed PostgreSQL (Frankfurt region) — connection via `DATABASE_URL` env var on transaction-pooled port 6543.
- Auth: Spring Security (username + BCrypt hash + JWT in HTTPOnly cookie). Secret: `JWT_SECRET` env var. No mail service.
