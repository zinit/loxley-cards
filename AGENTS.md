# Repository Guidelines

Loxley-cards is a Gwint-inspired web card game — Spring Boot 4.0.6 backend (Java 21, Maven) with a planned React/TypeScript SPA frontend. Single-player campaign vs bot, magic-link auth, PostgreSQL persistence.

## Hard Rules

- Keep game engine logic (scoring, card abilities, round resolution) in the `acommon-game-engine` module — not in controllers or the `app` module. See @context/foundation/tech-stack.md for the planned module layout.
- Email is the only personal data stored. No tracking, analytics, or marketing cookies.
- Use Supabase ONLY as managed PostgreSQL provider (JDBC via Spring HikariCP). Do NOT add Supabase JS SDK, Supabase Auth, Supabase Storage, Realtime, or Edge Functions — these are explicit out-of-scope (see @context/foundation/infrastructure.md). Magic-link auth is implemented natively in Spring Boot.
- Magic-link emails go through the Resend API (`@RestClient` / HTTP). Do NOT attempt direct SMTP — VPS providers (Hetzner included) block port 25 by anti-spam policy.
- Java base package is `cards.loxley` (reverse of `loxley.cards` domain). All new code goes under this package; sub-packages per concern: `cards.loxley.{app,game,db,ai}`.

## Project Structure

Maven multi-module backend under `backend/` (groupId `cards.loxley`, parent `loxley-cards-parent`). Three library modules + one Spring Boot bootstrap module:

```
backend/
├── pom.xml                              # parent POM (packaging: pom)
├── mvnw, mvnw.cmd, .mvn/                # Maven wrapper at reactor root
├── app/                                 # Spring Boot bootstrap (artifactId: loxley-cards-app)
│   └── src/main/java/cards/loxley/      # LoxleyCardsApplication + REST controllers + security
├── acommon-game-engine/                 # engine + scoring + bot + card defs (artifactId: loxley-cards-game-engine)
│   └── src/main/java/cards/loxley/game/
├── acommon-db/                          # JPA entities + repos + Flyway migrations (loxley-cards-db)
│   └── src/main/java/cards/loxley/db/
└── acommon-ai/                          # AI integrations stub (loxley-cards-ai)
    └── src/main/java/cards/loxley/ai/

context/foundation/                      # PRD, shape notes, tech stack, infrastructure decisions
frontend/                                # placeholder for Vite + React + TS (not yet scaffolded)
```

`app/` depends on the three `acommon-*` modules via `dependencyManagement` declared in `backend/pom.xml`.

## Build & Dev Commands

All Maven commands run from `backend/` (the reactor root). Maven wrapper lives there.

- `cd backend && ./mvnw clean install` — full multi-module build + tests + install to local `~/.m2/`
- `cd backend && ./mvnw test` — run tests across all modules
- `cd backend && ./mvnw -pl app spring-boot:run` — start the dev server (devtools hot-reload enabled). The `-pl app` flag targets the bootstrap module.
- `cd backend && ./mvnw -pl app package` — build only the app fat JAR (`backend/app/target/loxley-cards-app-*.jar`)
- `cd backend && ./mvnw -pl acommon-game-engine test` — run tests for a single library module

## Testing

- Naming: `*Tests.java` (per existing `LoxleyCardsApplicationTests`).

## Commit & PR Conventions

- No established convention yet (single "init" commit). Use short imperative messages (e.g. "add card model", "fix round scoring").
- No CI pipeline configured. Target: GitHub Actions with auto-deploy-on-merge per @context/foundation/tech-stack.md.

## Key Context

- PRD and design decisions: @context/foundation/prd.md
- Tech stack rationale, Java package, module plan: @context/foundation/tech-stack.md
- Infrastructure decisions (platform, DB, mail, domain, risk register): @context/foundation/infrastructure.md
- Domain: `loxley.cards` (apex → frontend on Cloudflare Pages, `api.loxley.cards` → backend on Hetzner VPS).
- Database: Supabase managed PostgreSQL (Frankfurt region) — connection via `DATABASE_URL` env var on transaction-pooled port 6543.
- Mail: Resend API for magic-link delivery (`RESEND_API_KEY` env var).
