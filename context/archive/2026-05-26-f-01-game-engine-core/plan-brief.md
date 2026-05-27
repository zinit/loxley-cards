# Game Engine Core — Plan Brief

> Full plan: `context/changes/f-01-game-engine-core/plan.md`

## What & Why

Bring up the full Gwint-inspired game engine in `backend/acommon-game-engine/` so the rest of the stack (REST controllers in S-03, frontend in S-02/S-03) has a working game brain to talk to. Engine covers: best-of-3 match flow, three rows (close/ranged/siege), layered scoring with modifiers, card abilities (spy, medic, three weather variants, commander's horn, tight bond, scorch, decoy, morale boost, clear weather, leader abilities), faction passives, three bot strategies, and JSON-driven sample card / deck / campaign data loaded at startup. **Known gap: muster** — PRD-listed but not delivered in F-01; documented and deferred (see Open Risks). A sibling module `acommon-game-cli` ships a standalone Spring Boot runner that drives the engine end-to-end from a terminal — bot evaluation, opponent-profile evaluation, seeded bot-vs-bot simulation, and an interactive player-vs-bot mode — so the engine can be exercised before any REST or frontend exists.

## Starting Point

Empty stub module at `backend/acommon-game-engine/` — `pom.xml` with only `spring-boot-starter-test`, zero Java classes under `cards.loxley.game`. No CLI runner module exists yet. The `app` module already declares a Maven dependency on the engine module, so once the engine is in place it can be wired into REST controllers later.

## Desired End State

`cd backend && ./mvnw -pl acommon-game-engine clean test` green with 200+ tests covering domain, loader, scoring, abilities, move generation/validation/execution, round resolution, turn orchestration, faction passives, bot strategies, and full gameplay integration. New module `acommon-game-cli/` builds and ships 29 more tests covering the CLI surface. Full reactor build (`./mvnw clean install`) passes across 6 modules. `./mvnw -pl acommon-game-cli spring-boot:run` boots the CLI, runs the full bot evaluation + simulation, and shuts down cleanly.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
|---|---|---|
| DI / wiring | Spring Boot DI (`@Configuration`, `@Component`, `@Service`) | Engine pieces compose naturally as Spring beans; the rest of the project already uses Spring Boot, no impedance mismatch. |
| Card / deck / ruleset storage | JSON files in `src/main/resources/{cards,data}/` loaded via Jackson | Card data evolves separately from code; new decks land as JSON edits, no recompile, no enum churn. |
| JSON loader | `GameDefinitionLoader` + `GameDefinitionValidator` (Jackson 3) | Validation up-front; bad data fails at load, not deep in the engine. |
| Move API | Sealed `Move` interface (`PlayCardMove`, `PassMove`, `UseLeaderMove`) executed via `MoveExecutor` | Pattern-matchable command shape; bot enumerates legal moves via `MoveGenerator` and scores each. |
| Scoring | Layered `CardScorer` → `RowScorer` → `BoardScorer` | Each layer composable and independently testable; modifier order (hero immunity → weather → tight bond → morale → horn) lives in `RowScorer`. |
| Abilities | `AbilityRegistry` + one effect class per ability under `engine/ability/effects/` | Adding a new ability = drop in a new effect + register; no touching the core engine. |
| Bots | Three strategies: `RandomBot`, `HeuristicEasyBot`, `HeuristicMediumBot` resolved via `BotStrategyResolver` | Campaign progression needs varied bot difficulty per stage; one bot tier is not enough. |
| Match flow | `TurnOrchestrator` + `RoundResolver` over immutable `GameState` snapshots | State transitions are pure functions returning new `GameState` — easy to test, easy to log, replay-friendly. |
| Event surface | `MatchEventBus` + listeners (e.g., `DrawOnRoundWinListener` for faction passives) | Faction passives and future hooks subscribe without core changes. |
| CLI surface | Separate Maven module `acommon-game-cli` (own `pom`, own `LoxleyCliApplication` with `@SpringBootApplication`) | Engine module stays a clean library (no `main()`); CLI is opt-in via `mvn -pl acommon-game-cli spring-boot:run`. Two profiles: default (bot evaluation + simulation) and `cli-player` (interactive REPL). |
| Card / deck sample data | JSON resources shipped as initial seed data — Witcher 3 themed (named characters: Foltest, Roche, Dijkstra, Natalis, Ves, Stennis, Blue Stripes) + Sherwood themed for campaign | Working sample data lets the engine run end-to-end now; Java code is the source of truth, JSON is replaceable. W3-IP filenames/identifiers should be re-themed before any properly public release. |

## Scope

**In scope:** the engine library itself — domain types, JSON loader + resources, move generation/validation/execution, scoring with full modifier set, all abilities, faction passives, three bot strategies, campaign stage registry, opponent profile registry, match event bus, gameplay integration tests. Plus the `acommon-game-cli` sibling module — CLI runner (board / hand renderers, move parser, REPL), `LoxleyCliApplication` with default (evaluation) and `cli-player` (interactive) profiles.

**Out of scope:** REST controllers (S-03), database persistence (F-02), magic-link auth (F-03), frontend (F-04/S-01/S-02/S-03), deck-building UI, multiplayer, faction/leader selection UI, AI-coach (`acommon-ai`).

## Architecture / Approach

Engine ships as a Spring-Boot-aware library module (not a runnable app). Game state is an immutable `GameState` record; player actions are `Move` sealed-interface instances; `MoveExecutor` applies a move and returns a new `GameState`; `TurnOrchestrator` runs turn order and `RoundResolver` decides round/match outcomes. Scoring is a three-layer cake (card → row → board) keyed off the strict modifier order. Card data lives in JSON, loaded once at startup via `GameDefinitionLoader` and validated by `GameDefinitionValidator`. Bots, abilities, and faction passives plug in via small registries so adding content is local.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. POM + module setup | Add `spring-boot-starter` + `spring-boot-starter-json`; module compiles | Dependency conflicts with parent BOM |
| 2. Domain types | `domain/card/*` + `domain/state/*` records and enums | Field omissions force rework downstream |
| 3. Loader + JSON resources | `loader/*` + `src/main/resources/{cards,data}/*.json` + `config/GameDefinitionConfig` | JSON validation gaps |
| 4. Engine subsystems | `engine/{move,execution,ability,scoring,bot,faction,campaign,opponent,eval,event}` | Modifier ordering bugs in scoring |
| 5. Tests + Spring test bootstrap | Port test suite (~200 tests) + `EngineTestApplication` (`@SpringBootApplication`) for test context | Spring context misconfiguration blocks all tests |
| 6. Reactor build verify | `./mvnw clean install` across reactor | Hidden cross-module breakage |
| 7. CLI runner module | New `acommon-game-cli` module: `LoxleyCliApplication` + ported CLI files + 29 tests + `spring-boot-maven-plugin` for `spring-boot:run` | Engine beans not discoverable from CLI package (`scanBasePackages` fix required) |

## Open Risks & Assumptions

- **Muster ability missing** — PRD lists muster as one of the must-have abilities; F-01 does not deliver it (no `MUSTER` code, no effect class, no test, no JSON usage). Engine is otherwise PRD-complete on the ability front. Adding muster is a follow-up change if it turns out critical for the target Gwint feel.
- **Witcher 3 IP inside sample JSON** — named-character card identifiers in `data/sherwood_reference_ruleset.json` and the deck JSONs are CDPR-owned (Foltest, Vernon Roche, Sigismund Dijkstra, etc.). Tolerable for an internal MVP demo; re-theme JSON content (or swap in a generic set) before the project goes properly public.
- Spring annotations live inside an engine module — fine for MVP; if a non-Spring consumer ever needs the engine, expect a refactor pass to pure Java.
- JSON deck definitions are not yet balance-tuned for the 10-stage campaign curve — playtesting happens later (S-05 territory).
- Bot difficulty progression across stages is driven by `BotStrategyResolver` mapping stage → strategy; the mapping is a guess and will be tuned.

## Success Criteria (Summary)

- `./mvnw -pl acommon-game-engine clean test` — 200+ tests pass, 0 failures, 0 errors.
- `./mvnw -pl acommon-game-cli clean test` — 29 tests pass, 0 failures, 0 errors.
- `./mvnw clean install` — full reactor BUILD SUCCESS across 6 modules.
- `./mvnw -pl acommon-game-cli spring-boot:run` — boots, runs bot evaluation + profile evaluation + bot-vs-bot simulation, shuts down cleanly (plausible win rates: e.g., heuristic-medium vs random ~78%).
- Gameplay integration tests can drive a complete best-of-3 match end to end (`GameplayIntegrationTest`, `DecoySemanticsTest`, `LoserHandSizeNotChangedTest`).
- All ability effects (weather × 3, spy, medic, scorch, commander's horn, clear weather, tight bond, decoy, morale boost) have direct unit tests; muster is a known gap (see Open Risks).
- All three bot strategies (`RandomBot`, `HeuristicEasyBot`, `HeuristicMediumBot`) play complete matches without errors.
