---
starter_id: spring
package_manager: maven
project_name: loxley-cards
hints:
  language_family: java
  team_size: solo
  deployment_target: self-host
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: verified
  path_taken: standard
  quality_override: false
  self_check_answers: null
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: false
  has_background_jobs: false
  java_group_id: cards.loxley
  java_base_package: cards.loxley
  database_provider: supabase
  mail_service: none
  domain: loxley.cards
---

## Why this stack

Solo developer shipping a Gwint-inspired card game as a split-stack monorepo (Java backend + React/TS frontend) within 3 weeks after-hours. Spring Boot is the recommended default for `(web-app, java)` and clears all four agent-friendly gates (typed, convention-based, popular in training data, well-documented). The multi-module Maven architecture with game engine, DB, and AI modules fits Spring's DI and module conventions naturally. Auth via Spring Security (username + BCrypt hash, JWT in HTTPOnly cookie) — świadomie wybrane zamiast magic-linka, żeby uniknąć zależności od email-providera (Daniel ma Resend pod inną domeną na free planie; verified domain pod `loxley.cards` byłaby blockerem do F-03). No payments, realtime, or AI integration in MVP scope. Deployment targets self-host backend (Docker on Hetzner Cloud VPS CX22 in `fsn1` Falkenstein) with managed PostgreSQL via Supabase Free tier (`aws-eu-central-1` Frankfurt) and Cloudflare Pages for the frontend. No transactional mail service in MVP (no password reset, no notifications). Domain: `loxley.cards` (apex → frontend on Cloudflare Pages, `api.loxley.cards` → backend on Hetzner). CI runs on GitHub Actions with auto-deploy-on-merge — standard for solo projects with shipping-first discipline. Full platform rationale and risk register in @context/foundation/infrastructure.md.

## Frontend (companion project)

- starter_id: vite-react
- bootstrapper_confidence: verified
- path: frontend/
- runtime: node 22
- package_manager: npm
- deployment_target: cloudflare-pages
- deployment_flow: auto-deploy-on-merge

Standalone Vite + React + TS SPA, bring-your-own backend (komunikacja z backendem przez REST API). Bootstrapper powinien obsłużyć tę część jako osobny krok lub pozostawić do ręcznego scaffoldu — frontend nie dzieli build pipeline'u z backendem, deployowany niezależnie na Cloudflare Pages.

## Database

- type: postgresql
- version: 16+ (managed by Supabase)
- provider: Supabase Free tier, region `aws-eu-central-1` (Frankfurt) — ~10ms od Hetzner Falkenstein
- connection: backend module `acommon-db` via JDBC (Spring Data JPA + HikariCP, transaction-pooled przez Supabase pgbouncer na porcie 6543)

User accounts (username + BCrypt password hash) + zapis stanu kampanii per user + progres wymagają relacyjnej DB z transakcjami. **Supabase managed PostgreSQL** eliminuje burden ops (backups, monitoring, vacuum tuning, port exposure) bez wprowadzania BaaS dependency — używamy **wyłącznie** jako Postgres provider. Supabase Auth, Storage, Realtime i Edge Functions są jawnie OUT OF SCOPE (zobacz @context/foundation/infrastructure.md).

**Spring side:** `pom.xml` dependencies — `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` driver + `flyway-core` + `flyway-database-postgresql` + `spring-boot-flyway` (SB4 per-tech autoconfig split — bez tego Flyway nie startuje). HikariCP `maximum-pool-size=10` (zostawiamy 5 connections w reserve dla Supabase Free pgbouncer limit of 15). **pgbouncer transaction-mode gotcha:** wymaga `prepareThreshold=0` w JDBC driver properties (`spring.datasource.hikari.data-source-properties.prepareThreshold=0`) — bez tego server-side prepared statements kolizjują przy connection reuse ("prepared statement S_X already exists"). Schema migrations via Flyway (forward-only); rollback przez reverse migration.

**Local dev:** decyzja w F-02 (2026-06-12) — jedna konfiguracja Supabase dla dev+serv, brak Docker compose dla lokalnego Postgresa. Lokalnie i serv używają tej samej Supabase Free instancji w Frankfurt; po skończonym dev (S-04, S-05) Daniel przepnie serv na fresh production Supabase project. `DATABASE_URL` w `backend/.env` (gitignored) lokalnie, env var w produkcji. **JDBC format wymagany** (`jdbc:postgresql://...`), nie URI z Supabase dashboard — Spring nie auto-konwertuje. Connection string: Supabase dashboard → Settings → Database → Connection string → JDBC tab, **transaction-mode pooler** (port 6543, nie 5432). Test profile (`mvn test`) używa H2 in-memory (`acommon-db/src/test/resources/application-test.properties` + `app/src/test/resources/application.properties`) — szybko, offline, brak Supabase dependency w testach.

## Backend module layout (Maven multi-module)

- **Maven groupId:** `cards.loxley` (reverse `loxley.cards` domain — standard Maven convention)
- **Base Java package:** `cards.loxley` (sub-packages per module: `cards.loxley.{app,game,cli,db,ai}.*`)

```
backend/
├── pom.xml                  # parent POM (packaging: pom, groupId: cards.loxley)
├── app/                     # main Spring Boot app — REST controllers, security, bootstrap
│                            #   package: cards.loxley.app
├── acommon-game-engine/     # core game logic — engine, scoring, bot, rules, card definitions
│                            #   package: cards.loxley.game
├── acommon-game-cli/        # standalone Spring Boot CLI runner for the engine (bot evaluation, simulation, interactive REPL)
│                            #   package: cards.loxley.cli (depends on acommon-game-engine)
├── acommon-db/              # persistence layer — JPA entities, repositories, Flyway migrations
│                            #   package: cards.loxley.db
└── acommon-ai/              # AI integrations — stub w MVP (pod stretch goal AI-coach po MVP)
                             #   package: cards.loxley.ai
```

**Status (po post-bootstrap refactor 2026-05-24, F-01 engine port + CLI runner 2026-05-26):**
- ✅ Package refactor done — kod żyje pod `cards.loxley`.
- ✅ Multi-module split done — `backend/pom.xml` jako parent (packaging `pom`), `backend/app/` jako Spring Boot bootstrap, 4 sibling moduły `acommon-{game-engine,game-cli,db,ai}/`. Maven wrapper i `.mvn/` przeniesione do `backend/`. `mvn clean install` z reactor root przechodzi (6 modułów: Parent + 5 child).
- ✅ Engine implementation done — `acommon-game-engine/` z pełnym engine (domain, loader, scoring, abilities, move pipeline, bot, faction passives, campaign, event bus) + 200 testów.
- ✅ CLI runner done — `acommon-game-cli/` z `LoxleyCliApplication` (bot evaluation, bot-vs-bot simulation, interactive `cli-player` REPL) + 29 testów.
- ⏭️ POM dependencies (jpa, postgres, security) — pending, dorzucamy w F-02 / F-03.
