<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Game Engine Core

- **Plan**: context/changes/f-01-game-engine-core/plan.md
- **Scope**: All phases (1-6 of 6)
- **Date**: 2026-05-26
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Summary

Engine implemented under `cards.loxley.game.*` as planned. Subdirectory layout follows the plan exactly across `domain/`, `engine/`, `loader/`, `config/`. Jackson 3 usage in three loaders adapted to the `tools.jackson.*` package surface and the immutable-`ObjectMapper.rebuild()` pattern. `EngineTestApplication` added under `src/test/java/cards/loxley/game/` to satisfy `@SpringBootTest` bootstrap. POM picked up `spring-boot-starter` + `spring-boot-starter-json`. A new sibling module `acommon-game-cli` was added in Phase 7 to host the CLI runner (`LoxleyCliApplication` + 7 CLI source files + 5 test classes + smoke `LoxleyCliApplicationTests`); engine module stays a clean library. Test suite green: 200/200 engine + 29/29 CLI + 1/1 app = 230/230 total; full reactor `./mvnw clean install` BUILD SUCCESS across all 6 modules; `./mvnw -pl acommon-game-cli spring-boot:run` smoke-runs the full bot evaluation + bot-vs-bot simulation pipeline.

## Findings

### F1 — Spring DI inside a library module

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: `backend/acommon-game-engine/src/main/java/cards/loxley/game/**` (broad)
- **Detail**: Engine classes (effects, registries, loaders, configs) are annotated with Spring stereotypes and rely on Spring DI for wiring. AGENTS.md keeps engine logic out of controllers but does not forbid Spring inside the engine module itself, so this is within bounds. Tradeoff: a future non-Spring consumer (CLI harness, embedded host) would need a refactor pass to instantiate engine pieces by hand. For the MVP — where the only consumer is the Spring-based `app/` module — keeping the framework dependency is cheaper than maintaining a pure-Java alternative.
- **Decision**: ACCEPTED — designed-in, revisit if a non-Spring consumer appears.

### F2 — Mature test suite is the load-bearing safety net

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `backend/acommon-game-engine/src/test/java/cards/loxley/game/**` + `backend/acommon-game-cli/src/test/java/cards/loxley/cli/**`
- **Detail**: 230 tests in total — 200 in the engine module (domain, loader parse/validate, full ability effect catalogue, move generation / validation / execution, all three scoring layers, round and turn orchestration, faction passive listeners, three bot strategies, campaign and opponent registries, event bus, evaluation harness, end-to-end gameplay scenarios `GameplayIntegrationTest` / `DecoySemanticsTest` / `LoserHandSizeNotChangedTest`) plus 29 in the CLI module (board / hand renderers, move parser, player board index, CLI move command, smoke `LoxleyCliApplicationTests`). The suite is the primary correctness gate for F-01 — protect it on every future refactor.
- **Decision**: ACCEPTED — strong functional confidence; no action needed.

### F3 — `sherwood` / `Sherwood` naming preserved in class internals and JSON filenames

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: Various — JSON resource filenames (`sherwood_*.json`), some string constants, occasional method/field names.
- **Detail**: Filenames and a handful of internal strings use the Sherwood / Robin Hood naming. Sherwood and the Robin Hood legend are public-domain folklore (origin: 13th-century English), so there is no copyright or licensing concern in keeping these names. The project name `loxley-cards` references Loxley (Robin Hood's surname), so the theming is internally consistent. No rename needed.
- **Decision**: ACCEPTED — naming is on-theme and copyright-clean.

### F4 — Muster ability missing vs PRD

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence (vs PRD)
- **Location**: `engine/ability/effects/` (no `MusterEffect` class), `engine/scoring/AbilityCodes.java` (no `MUSTER` constant), zero references in tests, zero usage in any JSON resource.
- **Detail**: PRD `context/foundation/prd.md` Business Logic section lists muster among the must-have abilities: *"pogoda, horn, spy, medic, **muster**, tight bond, leader abilities"*. F-01 does not deliver muster — it has no ability code, no effect class, no test, no card data uses it. Engine implements every other PRD-listed ability plus three extras (scorch, decoy, morale boost). Adding muster later is a self-contained change touching `AbilityCodes`, a new `MusterEffect` class, `AbilityRegistry` wiring, sample cards in the JSON data, and tests.
- **Decision**: ACCEPTED as known gap for F-01 — engine is otherwise PRD-complete on abilities; muster is the single missing PRD-listed mechanic. PRD has been updated to reflect this gap explicitly. Future change can add muster if it turns out critical for the target Gwint feel.

### F5 — Witcher 3 character IP inside JSON sample data

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `src/main/resources/data/sherwood_reference_ruleset.json`, `src/main/resources/data/decks/sherwood_*_deck.json` — card identifiers and display names include CDPR-owned Witcher 3 characters: `leader_foltest_king_of_the_north`, `vernon_roche`, `sigismund_dijkstra`, `john_natalis`, `ves`, `prince_stennis`, `blue_stripes_commando`.
- **Detail**: These JSON files are actively loaded at runtime by `GameDefinitionConfig`, `CampaignStageRegistry`, and `DeckVariantLoader` (8 of 14 shipped JSON files are wired in; the other 6 sit alongside as reserved sample variants but have no code references). The named characters above are part of CD Projekt RED's Witcher franchise IP — fan-game tolerance is real-world common but not legally bulletproof. The Java engine code itself contains no W3 IP — it's the JSON data feed that does. Java is the source of truth for behavior; JSON is replaceable seed data.
- **Decision**: ACCEPTED for now — tolerable for an internal MVP demo and a teaching-context public repo (10xDevs project showcase). Before any properly public release / promotion of the game, re-theme JSON content (rename identifiers to a generic / original cast). The 6 unused JSON files are kept as reserved sample variants — safe to delete in a later cleanup pass.

### F6 — CLI runner module added as in-flight scope addition

- **Severity**: 💡 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: `backend/acommon-game-cli/**` (new module — Phase 7 of the updated plan)
- **Detail**: Originally the plan left CLI runner work out of scope on the rationale that REST in S-03 would be the canonical interaction surface. During implementation that decision was revisited: a developer-facing way to drive the engine end-to-end before REST or frontend exist turned out to be high-value (bot evaluation across matchups, opponent profile evaluation, seeded bot-vs-bot simulation, interactive `cli-player` REPL). Chose option D from the 4 alternatives (status-quo skip / fold into engine module / fold into app module / separate module): added `acommon-game-cli` as its own Maven module so the engine stays a clean library with no `main()`. The plan was updated to include Phase 7 reflecting this decision, and the impl matches the updated plan — so this is not drift, it's an in-session pivot.

  Subtlety worth flagging: `LoxleyCliApplication` lives in `cards.loxley.cli` while engine beans live in `cards.loxley.game.*`, so default `@SpringBootApplication` component scan would miss the engine. Required explicit `@SpringBootApplication(scanBasePackages = {"cards.loxley.cli", "cards.loxley.game"})`. Easy to forget if anyone ever moves classes around.
- **Decision**: ACCEPTED — provides immediate end-to-end smoke value (`./mvnw -pl acommon-game-cli spring-boot:run` runs full bot evaluation in seconds) and keeps the engine library pure. Trade-off: one extra Maven module to maintain, and the `scanBasePackages` subtlety is a small footgun for future refactors.
