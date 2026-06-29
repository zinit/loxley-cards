# Loxley Cards

> A Gwent-inspired browser card game — fast tactical matches against an AI bot, no Witcher 3 install required.

<p align="center">
  <img src="frontend/src/assets/landing-hero.webp" alt="Loxley Cards — Sherwood Forest landing scene" width="640" />
</p>

**Live:** [loxley.cards](https://loxley.cards) · **Stack:** Spring Boot 4 / Java 21 + Vite + React + TypeScript · **Author:** [Daniel Łopuszko](mailto:daniel.loposzko@gmail.com)

---

## About

Gwint — the card game from The Witcher 3 — is locked inside a 50+ hour RPG. Booting up just to play one match takes 20 minutes of overhead. Other online card games don't replicate the rows / weather / best-of-3 mechanic.

Loxley Cards is a standalone, web-based take on those mechanics, themed around Robin Hood and Sherwood Forest (folklore in the public domain, no IP concerns). Aimed at a niche too small for big studios: working adults with 15–20 minutes in the evening who want a quick, tactical match without overhead. Single-player vs AI bot, 10-stage campaign with persistent progression.

## Features

- **Full Gwent mechanics** — three combat rows (close / ranged / siege), best-of-3 matches, leader abilities, pass mechanic
- **11 card abilities** — spy, medic, tight bond, scorch, decoy, morale boost, clear weather, hero immunity, three weather variants, commander's horn
- **Deterministic modifier ordering** — hero immunity → weather → tight bond → morale boost → horn (documented and tested)
- **Three bot strategies** — RandomBot, HeuristicEasyBot, HeuristicMediumBot, assigned per campaign stage by a `BotStrategyResolver`
- **10-stage campaign** with linear unlock and per-user progress persistence
- **Click-to-select gameplay** with smooth animations, visual feedback (weather row tints, horn glows, power-color coding for boosted / weakened units)
- **Username + password auth** (Spring Security + BCrypt + JWT in HTTPOnly cookie) — no email collection, no tracking, no cookies beyond the session
- **AI code review on every PR** — custom GitHub Actions agent built on Vercel AI SDK + Claude Sonnet 4.6, posts a structured 5-criteria rubric comment

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.0.6, Java 21, Maven multi-module (5 modules) |
| Frontend | Vite, React 18, TypeScript, React Router |
| Database | PostgreSQL 17 (Supabase managed, Frankfurt), Flyway migrations, HikariCP |
| Auth | Spring Security, BCrypt, JJWT (JWT in HTTPOnly cookie) |
| Testing | JUnit 5 + MockMvc (backend, ~259 tests), Playwright (frontend E2E) |
| Deployment | Docker on Hetzner Cloud VPS (backend), Cloudflare Pages (frontend) |
| CI/CD | GitHub Actions — build/test pipeline + AI code review on every PR |

## Architecture Highlights

- **Pure engine module** (`acommon-game-engine`) with zero Spring/web dependencies — testable in isolation, runnable via standalone CLI
- **Multi-module Maven** layout with clean boundaries: `app` (REST + bootstrap), `acommon-game-engine`, `acommon-game-cli`, `acommon-db`, `acommon-ai`
- **JSON-driven game data** — cards, decks, campaign stages loaded from `src/main/resources/data/` on startup
- **REST API** with view-model DTOs hiding opponent hand (anti-cheat), per-game synchronized locking, in-memory session store
- **Two GitHub Actions workflows** in clean isolation: standard CI (Maven verify + npm build) and AI code review (composable agent, structured Zod output, deterministic verdict, update-existing-comment pattern)
- **Context-engineered development** — every shipped feature has a full audit trail in `context/archive/` (plan, plan-review, lessons learned)

## Quick Start

**Prerequisites:** Java 21, Node.js 22, Maven Wrapper (included)

### Backend

```bash
cd backend
./mvnw clean install                        # full reactor build + tests
./mvnw -pl app spring-boot:run              # REST dev server on http://localhost:8080
```

For local DB, copy `backend/.env.example` to `backend/.env` and add a Supabase JDBC URL — or use the H2 in-memory profile for tests (`./mvnw test`).

### Frontend

```bash
cd frontend
npm install
npm run dev                                 # Vite dev server on http://localhost:5173
```

### Engine CLI (no UI required, useful for testing the engine end-to-end)

```bash
cd backend
./mvnw -pl acommon-game-cli spring-boot:run                                          # bot evaluation suite + simulation
./mvnw -pl acommon-game-cli spring-boot:run -Dspring-boot.run.profiles=cli-player    # interactive player vs bot in terminal
```

## Project Context

This is a final certification project for the **[10xDevs 3.0](https://10xdevs.pl)** course — a Polish AI-assisted software engineering training. Built solo, after-hours, over roughly three weeks, using Claude Code in a structured agentic workflow.

The repository follows a deliberate context-engineering approach:

- [`context/foundation/`](context/foundation/) — PRD, tech stack, infrastructure decisions, roadmap
- [`context/changes/`](context/changes/) — active feature development plans
- [`context/archive/`](context/archive/) — shipped features with full audit trail (plan, plan-review, implementation review, lessons learned)
- [`.github/scripts/review.js`](.github/scripts/review.js) — AI code reviewer agent (built as part of the Champion certification track)
- [`AGENTS.md`](AGENTS.md) — repository conventions and guardrails (read by AI agents working on the codebase)

Every non-trivial shipped feature went through the workflow: **shape → plan → plan-review → implement → impl-review → archive**. The lessons-learned section in [`context/foundation/roadmap.md`](context/foundation/roadmap.md) captures non-obvious engineering decisions made along the way (Spring Boot 4 autoconfig split, Supabase pgbouncer JDBC gotcha, Hibernate JDBC URL password leak, etc.).

## Non-Goals (MVP)

By design, the following are **out of scope**:

- Multiplayer (single-player vs bot only)
- Deck building (predefined decks per campaign stage)
- Mobile / responsive layout (desktop-only — six rows + hand + UI need a large screen)
- Password reset / transactional email (username-based auth, manual recovery if needed)
- Leaderboard / social features / AI-generated cards

The rationale and decision history for each non-goal lives in [`context/foundation/prd.md`](context/foundation/prd.md).

## Roadmap & Status

All MVP slices are shipped. See [`context/foundation/roadmap.md`](context/foundation/roadmap.md) for the full breakdown:

| ID | Change | Status |
|---|---|---|
| F-01..F-04 | Foundations (engine, DB, auth, frontend scaffold) | done |
| F-05 | CI/CD pipeline (GitHub Actions, backend + frontend build/test) | done |
| F-06 | AI code review pipeline (Claude Sonnet 4.6 on every PR) | done |
| S-01..S-05 | Slices (UI prototype, REST API, polished game, login, campaign progression) | done |

## Acknowledgments

- **CD Projekt Red** for the original Gwint mechanics in The Witcher 3, which inspired this project
- **10xDevs** community for the training, structured workflow templates, and AI toolkit
- **Public domain Robin Hood folklore** for the thematic re-skin (no IP concerns)

## License

All rights reserved unless otherwise specified. Source available for educational reference; no warranty.

---

*Built as a 10xDevs 3.0 final certification project — Builder, Architect, and Champion tracks. Source code AI-pair-programmed with Claude Code in a structured shape → plan → review → implement workflow.*
