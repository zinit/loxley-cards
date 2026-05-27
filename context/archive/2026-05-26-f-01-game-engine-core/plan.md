# Game Engine Core Implementation Plan

## Overview

Stand up the full Gwint-inspired game engine inside the `acommon-game-engine` Maven module (package root `cards.loxley.game`) plus a standalone Spring Boot CLI runner in a sibling module `acommon-game-cli` (package root `cards.loxley.cli`). The engine is a Spring-aware library — JSON-driven card/deck/ruleset definitions loaded by `GameDefinitionLoader`, an immutable `GameState` advanced by a sealed `Move` hierarchy through `MoveExecutor` / `TurnOrchestrator` / `RoundResolver`, a layered scoring stack (`CardScorer` → `RowScorer` → `BoardScorer`), an `AbilityRegistry` of plug-in effect classes, faction passives via a `MatchEventBus`, and three bot strategies (`RandomBot`, `HeuristicEasyBot`, `HeuristicMediumBot`) resolved by `BotStrategyResolver`. The CLI module provides a developer-facing harness for driving the engine end-to-end without REST or a frontend — bot-vs-bot evaluation across matchups, opponent-profile evaluation, a seeded bot-vs-bot simulation, and an interactive player-vs-bot terminal mode (`@Profile("cli-player")`). No REST, no DB, no web UI — those land in their own changes downstream.

## Current State Analysis

- **Module:** `backend/acommon-game-engine/` is an empty stub — `pom.xml` declares only `spring-boot-starter-test`, source/test trees under `cards.loxley.game` contain nothing.
- **Parent POM:** Spring Boot 4.0.6, Java 21, multi-module reactor. The `app` module already declares a dependency on `loxley-cards-game-engine`, so wiring into REST controllers (S-03) is unblocked by F-01 completion.
- **Architecture intent settled up front:** ability semantics, scoring order, and the card/deck/ruleset JSON shapes are decided before implementation starts — see Critical Implementation Details below.

### Key discoveries

- Spring Boot 4.0.6 ships **Jackson 3** under the `tools.jackson.*` package (not the older `com.fasterxml.jackson.*` namespace). All loaders authored in F-01 import from `tools.jackson.*`.
- Jackson 3 makes `ObjectMapper` effectively immutable: `objectMapper.copy()` is gone. The replacement is `objectMapper.rebuild().configure(...).build()`. Three loader classes use this pattern.
- Engine-internal tests use `@SpringBootTest`, which walks up the package tree looking for a `@SpringBootConfiguration`. The engine module is a library — no `main()`, no production `@SpringBootApplication`. A test-scope `@SpringBootApplication` (`EngineTestApplication`) under `src/test/java/cards/loxley/game/` is the minimal fix.
- A developer-facing CLI runner is wanted but lives in its own Maven module (`acommon-game-cli`, see Phase 7) so the engine stays a clean library (zero `main()`).

## Desired End State

An engine module that:

1. Compiles cleanly and exposes its API as Spring beans under `cards.loxley.game.*`.
2. Loads card / deck / ruleset definitions from `src/main/resources/{cards,data}/*.json` at startup via Jackson.
3. Can create a fresh `GameState` from a `GameDefinition` + `Random` seed.
4. Executes a complete best-of-3 match end to end through `TurnOrchestrator` + `RoundResolver`, with scoring respecting the full modifier ordering and every ability effect firing correctly.
5. Plays autonomous bot-vs-bot matches across all three bot strategies without errors.
6. Has its full test suite (200+ tests) green under `./mvnw -pl acommon-game-engine clean test`, and the full reactor under `./mvnw clean install`.

## What We're NOT Doing

- No REST controllers, no Spring MVC, no API surface beyond Spring beans (S-03 handles HTTP).
- No DB persistence (F-02).
- No frontend integration (S-02, S-03).
- No magic-link auth (F-03).
- No deck-building UI; decks come from JSON.
- No multiplayer; bot-only.
- No faction/leader selection at this layer; data-driven via JSON.
- No new card art / asset pipeline.
- No AI-coach (`acommon-ai`, post-MVP).

## Implementation Approach

Phased implementation against the module's known architecture intent (see Critical Implementation Details). Each phase below lands one coherent layer of the engine and gates on a manual review checkpoint before the next phase starts. Module compiles after every phase; the test suite turns green incrementally as each layer comes online.

## Critical Implementation Details

### Modifier ordering in scoring

Modifier ordering lives in `CardScorer.currentStrength(card, row)` — per-card, summed across the row by `RowScorer.rowStrength(row)`, summed across rows by `BoardScorer.sideStrength(side)`. Getting the order wrong silently breaks gameplay:

1. **Hero immunity** — hero cards return `basePower` immediately; all subsequent modifiers are skipped.
2. **Weather** — if the row's weather variant is active, the non-hero card's working strength drops to `1` (otherwise it stays at `basePower`).
3. **Tight bond** — cards on the row sharing the same `cardId` and the tight-bond ability multiply each other (N matching cards → ×N strength each).
4. **Morale boost** — additive `+1` per other non-hero card on the row carrying the `MORALE_BOOST` ability (a card never boosts itself).
5. **Commander's horn** — if the row has horn active, the card's working strength is doubled at the end.

### Ability scope vs PRD

PRD enumerates spy, medic, weather, horn, tight bond, muster, leader abilities. F-01 implements all of these **except muster**, plus three extras for fuller Witcher 3 Gwint coverage:

- ✅ **Implemented (PRD-listed):** weather (3 row variants — close / ranged / siege), commander's horn, spy, medic, tight bond, clear weather, leader abilities (currently `CLEAR_WEATHER`).
- ➕ **Implemented (extras beyond PRD):**
  - **Scorch** (`engine/ability/effects/ScorchEffect`) — destroy the strongest non-hero unit(s) on the board. Faithful Witcher 3 Gwint mechanic; standard effect with its own test.
  - **Decoy** — swap-in card that pulls a friendly unit back to hand. Implemented inline in `MoveValidator` / `MoveExecutor` (no standalone `DecoyEffect` class) because it operates on the move pipeline, not the ability resolution pipeline. Exercised by `DecoySemanticsTest`.
  - **Morale boost** — additive +1 modifier (see ordering above). Common Gwint ability used by several units in the shipped sample decks.
- ❌ **NOT implemented (known gap vs PRD): muster.** PRD lists muster as a must-have ability, but F-01 does not deliver it (`AbilityCodes.java` has no `MUSTER` constant, no `MusterEffect` class, no test coverage, no card uses it in any JSON resource). Accepted as a known gap for F-01 — adding muster is a follow-up change (would touch `AbilityCodes`, a new `MusterEffect`, `AbilityRegistry` wiring, sample cards in JSON, and tests).

The net delta vs PRD is +3 extras / −1 missing; engine still stays inside the spirit of "full set of abilities from Witcher 3" — muster is the only PRD-listed ability that does not currently fire.

### JSON resources are sample data, Java code is source of truth

`src/main/resources/{cards,data}/*.json` is **initial seed / sample data shipped with F-01**, used at runtime by `GameDefinitionConfig`, `CampaignStageRegistry`, and `DeckVariantLoader` to bootstrap a working game. The Java engine code (records, scoring, ability effects, move pipeline, bot strategies) is the source of truth for behavior — JSON files only feed it card definitions, decks, ruleset numbers, and campaign stage configurations.

Two consequences worth flagging:

- **Witcher 3 character names** appear inside `data/sherwood_reference_ruleset.json` and the deck JSON files (e.g., `leader_foltest_king_of_the_north`, `vernon_roche`, `sigismund_dijkstra`, `john_natalis`, `ves`, `prince_stennis`, `blue_stripes_commando`). These are CD Projekt RED IP. Acceptable as sample data for an internal MVP; before the project goes properly public-facing the JSON content should be reviewed and likely re-themed.
- **Six unused JSON files** live alongside the active ones: `cards/{gwint,robin}_{deck,logic}.json`, `data/sherwood_campaign_stages.json`, `data/sherwood_outlaws_deck.json`. Zero references from main or test code. Kept as reserved sample variants; safe to delete in any later cleanup pass.

If a JSON value diverges from what the engine actually does (e.g., an ability code in JSON the engine does not handle), the Java code wins — JSON is a data feed, not a spec.

### Jackson 3 conventions

Spring Boot 4 ships Jackson 3 with classes under `tools.jackson.databind.*` (rather than the older `com.fasterxml.jackson.databind.*` namespace). Every Jackson user (`GameDefinitionLoader`, `DeckVariantLoader`, `CampaignStageRegistry`) imports from `tools.jackson.*`. `ObjectMapper` is effectively immutable in Jackson 3 — `copy()` is gone — so call sites that customize a mapper use `objectMapper.rebuild().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()`.

### Spring context for engine-internal tests

Engine tests use `@SpringBootTest`, which needs a `@SpringBootConfiguration` discoverable upward from the test package. The engine module is a library — it has no production `@SpringBootApplication`. `EngineTestApplication` under `src/test/java/cards/loxley/game/` is a no-op `@SpringBootApplication` that satisfies the test bootstrap without leaking a `main()` into the library.

---

## Phase 1: POM dependencies

### Overview

Bring in the runtime dependencies the engine needs. Without these the port can't compile.

### Changes Required

**File:** `backend/acommon-game-engine/pom.xml`

Add two dependencies alongside the existing `spring-boot-starter-test`:

- `org.springframework.boot:spring-boot-starter` — runtime Spring DI annotations (`@Configuration`, `@Component`, `@Service`).
- `org.springframework.boot:spring-boot-starter-json` — pulls in Jackson 3 for JSON loading.

### Success Criteria

#### Automated Verification

- Module resolves dependencies: `cd backend && ./mvnw -pl acommon-game-engine dependency:resolve` succeeds.
- Jackson 3 is on the classpath: `dependency:tree` shows `tools.jackson.core:jackson-databind`.

#### Manual Verification

- POM diff reads cleanly — no version overrides, deps inherit from parent BOM.

---

## Phase 2: Domain types

### Overview

Move all immutable game-shape records and enums into `cards.loxley.game.domain.*`. These are pure data — no game logic.

### Changes Required

**Target paths:**

- `backend/acommon-game-engine/src/main/java/cards/loxley/game/domain/card/` — `Card`, `CardType`, `Row`, `RowId`, `Ability`, `Deck`, `DeckEntry`, `DeckSummary`, `GameDefinition`, `MatchFormat`, `MvpImportance`, `PlayTarget`, `Ruleset` (and supporting enums).
- `backend/acommon-game-engine/src/main/java/cards/loxley/game/domain/state/` — `BoardSide`, `CardInstance`, `GameState`, `GameStateFactory`, `Player`, `PlayerState`, `RowState`.

All records and enums sit under `cards.loxley.game.domain.*`. Domain layer is pure data — no behavior, no Spring annotations — so each type is small (typically a record or an enum) and the layer compiles standalone.

### Success Criteria

#### Automated Verification

- Domain package compiles standalone: `./mvnw -pl acommon-game-engine compile`.

#### Manual Verification

- Domain field set looks complete for downstream engine needs (no obvious missing field).

---

## Phase 3: Loader + JSON resources + config beans

### Overview

Bring in the JSON loader, the JSON resources themselves, and the Spring config bean that wires the default `GameDefinition` from the reference ruleset file.

### Changes Required

**Source:**

- `backend/acommon-game-engine/src/main/java/cards/loxley/game/loader/` — `GameDefinitionLoader`, `GameDefinitionValidator`, `GameDefinitionValidationException`.
- `backend/acommon-game-engine/src/main/java/cards/loxley/game/config/` — `GameDefinitionConfig`, `RandomConfig`.

**Resources:**

- `backend/acommon-game-engine/src/main/resources/cards/` — `gwint_deck.json`, `gwint_logic.json`, `robin_deck.json`, `robin_logic.json`.
- `backend/acommon-game-engine/src/main/resources/data/` — `campaign_stages.json`, `sherwood_campaign_stages.json`, `sherwood_reference_ruleset.json`, `sherwood_outlaws_deck.json`, plus `decks/sherwood_{standard,boosted,boosted_plus,gimped}_deck.json`.

**Adjustments during port:**

- Jackson imports rewritten to `tools.jackson.*`.
- `objectMapper.copy()` calls rewritten to `objectMapper.rebuild().configure(...).build()`.
- `GameDefinitionConfig` declares the reference-ruleset resource path as a local constant (`public static final String REFERENCE_RULESET_RESOURCE = "data/sherwood_reference_ruleset.json";`) so the config has no upstream dependency.

### Success Criteria

#### Automated Verification

- Module still compiles: `./mvnw -pl acommon-game-engine compile`.
- Loader unit tests pass once tests land (Phase 5).

#### Manual Verification

- JSON resources land in `src/main/resources/{cards,data}/` and are picked up by the loader beans on startup.

---

## Phase 4: Engine subsystems

### Overview

Port the engine itself — everything under `cards.loxley.game.engine.*`. This is the bulk of the code.

### Changes Required

Implement each subdirectory under `cards.loxley.game.engine/`:

- `engine/move/` — sealed `Move` interface + `PlayCardMove`, `PassMove`, `UseLeaderMove`; `MoveGenerator` (legal-move enumeration), `MoveValidator` (legality check), `MoveDescriber` (human-readable rendering), `ValidationResult`.
- `engine/execution/` — `MoveExecutor` (applies a single move), `TurnOrchestrator` (drives turn order across a round), `RoundResolver` (decides round winner, advances or ends match), `IllegalMoveException`.
- `engine/ability/` — `AbilityRegistry` + `effects/` (one class per ability: `WeatherCloseEffect`, `WeatherRangedEffect`, `WeatherSiegeEffect`, `ClearWeatherEffect`, `SpyEffect`, `MedicEffect`, `ScorchEffect`, `CommandersHornEffect`).
- `engine/scoring/` — `CardScorer`, `RowScorer`, `BoardScorer`.
- `engine/bot/` — `BotStrategyResolver` plus three strategies: `RandomBot`, `HeuristicEasyBot`, `HeuristicMediumBot`.
- `engine/faction/` — `FactionPassive`, `FactionPassiveRegistry`, `DrawOnRoundWinListener`.
- `engine/campaign/` — `CampaignStageRegistry` (Jackson loader for campaign stage definitions).
- `engine/opponent/` — `OpponentProfileRegistry`, `DeckVariantLoader`.
- `engine/eval/` — `EvalHarness` (bot-vs-bot evaluation utility for tuning).
- `engine/event/` — `MatchEventBus` (event hook surface used by faction passives and listeners).

Jackson conventions per the Critical Implementation Details section apply across the loaders here.

### Success Criteria

#### Automated Verification

- Full module compiles: `./mvnw -pl acommon-game-engine compile` — no missing-symbol errors.

#### Manual Verification

- Subdirectory layout matches the enumeration above; engine logic stays out of `controller`-shaped places and out of the `app` module.

---

## Phase 5: Tests + Spring test bootstrap

### Overview

Bring over the entire test suite and add the minimal `@SpringBootApplication` test fixture needed to satisfy `@SpringBootTest` bootstrap.

### Changes Required

**Test source:**

- `backend/acommon-game-engine/src/test/java/cards/loxley/game/domain/` — `CardInstanceTest`, `GameStateFactoryTest`, `PlayerStateTest`, `RowStateTest`.
- `backend/acommon-game-engine/src/test/java/cards/loxley/game/loader/` — `GameDefinitionLoaderTest`, `GameDefinitionValidatorTest`.
- `backend/acommon-game-engine/src/test/java/cards/loxley/game/engine/` — full suite covering ability effects, move generation/validation/description/execution, scoring (card/row/board), bot strategies, faction passives, campaign / opponent registries, eval harness, event bus, and end-to-end gameplay integration tests.

**Test bootstrap (new):**

- `backend/acommon-game-engine/src/test/java/cards/loxley/game/EngineTestApplication.java` — empty class annotated `@SpringBootApplication`. Test-scope only; never shipped at runtime.

CLI-related test classes live in the separate `acommon-game-cli` module (Phase 7), not here — the engine module stays a clean library with no UI surface.

### Success Criteria

#### Automated Verification

- `./mvnw -pl acommon-game-engine clean test` — 200+ tests, 0 failures, 0 errors.

#### Manual Verification

- Test counts match the test classes enumerated above (CLI tests live in `acommon-game-cli`, Phase 7).

---

## Phase 6: Reactor build verification

### Overview

Confirm the engine module plays well with the rest of the reactor — no surprise breakage in `app` or other modules from the added engine deps.

### Changes Required

No code changes — verification only.

### Success Criteria

#### Automated Verification

- `cd backend && ./mvnw clean install` — Reactor Summary shows BUILD SUCCESS across all modules.
- `LoxleyCardsApplicationTests` in `app/` still passes (engine on classpath doesn't break Spring Boot startup).

#### Manual Verification

- No new compilation warnings from the engine module.
- A spot-check sanity grep finds no `com.sherwood` package references anywhere in `backend/`.

---

## Phase 7: CLI runner module

### Overview

Add a sibling Maven module `acommon-game-cli` that provides a developer-facing CLI surface as its own Spring Boot app. Lets the engine be exercised end-to-end from a terminal — bot evaluation, profile evaluation, seeded bot-vs-bot simulation, and an interactive player-vs-bot mode — without waiting for REST (S-03) or the frontend (S-01/S-02/S-03). Keeps the engine module a clean library (no `main()`).

### Changes Required

**Parent POM** (`backend/pom.xml`)

- Add `<module>acommon-game-cli</module>` to `<modules>`.
- Add `loxley-cards-game-cli` artifact under `<dependencyManagement>` so app/sibling modules can declare a dependency without pinning a version.

**New module: `backend/acommon-game-cli/`**

- `pom.xml` — `artifactId: loxley-cards-game-cli`, parent `loxley-cards-parent`. Dependencies: `loxley-cards-game-engine` (depends on engine), `spring-boot-starter`, `spring-boot-starter-test` (test scope). `spring-boot-maven-plugin` in `<build>` so `spring-boot:run` works.
- `src/main/java/cards/loxley/cli/` — 7 CLI source files (`BoardRenderer`, `HandRenderer`, `MovesListFormatter`, `MoveParser`, `ParseResult`, `PlayerBoardIndex`, `CliGameRunner`) plus `LoxleyCliApplication` (`@SpringBootApplication` with `main()`).
- `src/test/java/cards/loxley/cli/` — 5 CLI test classes plus `LoxleyCliApplicationTests` (empty `@SpringBootTest` smoke test). One extra: a copy of `MoveTestFixtures.java` lives in this module's test scope because Maven does not share test classes across modules by default and the CLI tests use it heavily; the duplication is small (~63 lines) and isolated to test code.

**Critical adjustment during port:**

- `LoxleyCliApplication` must declare `@SpringBootApplication(scanBasePackages = {"cards.loxley.cli", "cards.loxley.game"})`. Default Spring component scan only covers the application's own package and subpackages. Without the explicit `scanBasePackages`, engine beans (`CardScorer`, `BoardScorer`, `RoundResolver`, all the ability effects, the loaders, `BotStrategyResolver`, etc.) live in `cards.loxley.game.*` and would not be discovered — every `@SpringBootTest` would fail with `NoSuchBeanDefinitionException`.
- `LoxleyCliApplication` does not redeclare the `REFERENCE_RULESET_RESOURCE` constant — the canonical version already lives in `GameDefinitionConfig` (engine module, Phase 3), and no CLI code needs to reference it directly.
- The greeting `"Sherwood Cards engine — initialized"` is rewritten to `"Loxley Cards engine — initialized"` for project branding consistency. Polish card-name strings (`"Bracia z Sherwood"`, `"Róg Sherwoodu"`, `"Trebusz Sherwoodu"`) stay as-is — those are public-domain folklore themed card data, not branding.

### Success Criteria

#### Automated Verification

- Reactor count goes from 5 to 6 modules: `./mvnw clean install` shows `Loxley Cards - Game CLI` line in the Reactor Summary, BUILD SUCCESS.
- CLI module's own test suite passes: 29 tests across 6 test classes (`HandRendererTest`, `BoardRendererTest`, `MoveParserTest`, `PlayerBoardIndexTest`, `CliMovesCommandTest`, `LoxleyCliApplicationTests`).

#### Manual Verification

- `./mvnw -pl acommon-game-cli spring-boot:run` boots, prints `"Loxley Cards engine — initialized"`, runs bot evaluation (4 matchups × 50 games), runs profile evaluation (5 opponent profiles × 30 games vs proxy), runs a seeded bot-vs-bot simulation, and shuts down cleanly. Win-rate output looks plausible (e.g., heuristic-medium beats random ~78%, heuristic-easy ~70%).
- `./mvnw -pl acommon-game-cli spring-boot:run -Dspring-boot.run.profiles=cli-player` enters interactive REPL — the bot evaluation block is skipped via `@Profile("!cli-player")`, and `CliGameRunner` takes over with the board renderer and the move parser ready for stdin input.

---

## Testing Strategy

Heavy lean on **scenario tests** — the F-01 suite exercises full-match flows, ability stacking, edge cases, and bot behavior against fixed deck/ruleset JSON. End-to-end gameplay integration tests (`GameplayIntegrationTest`, `DecoySemanticsTest`, `LoserHandSizeNotChangedTest`) cover the realistic-game path; per-feature unit tests under `engine/{ability/effects,scoring,move,execution,bot,faction,campaign,opponent,event,eval}/` cover the layered pieces.

What's covered out of the box:

- Domain: `CardInstance`, `GameStateFactory`, `PlayerState`, `RowState` correctness.
- Loader: `GameDefinitionLoader` parses, `GameDefinitionValidator` rejects malformed.
- Scoring: `CardScorer`, `RowScorer`, `BoardScorer` per modifier interaction.
- Move pipeline: `MoveGenerator`, `MoveValidator`, `MoveExecutor`, `MoveDescriber`.
- Match flow: `RoundResolver`, `TurnOrchestrator`, `LoserHandSizeNotChangedTest`.
- Abilities: each effect under `engine/ability/effects/` has a direct test.
- Bots: `HeuristicEasyBotTest`, `HeuristicMediumBotTest`, `RandomBotTest`.
- Integration: `GameplayIntegrationTest`, `DecoySemanticsTest`.
- Faction passives: `DrawOnRoundWinListenerTest`.
- Campaign / opponent: `CampaignStageRegistryTest`, `OpponentProfileRegistryTest`, `DeckVariantLoaderTest`.
- Eval / events: `EvalHarnessTest`, `MatchEventBusTest`.

## Performance Considerations

Bot evaluation is O(legal-moves × scoring-cost) per ply — both bounded and tiny for typical hand sizes. Real-world bot decisions complete in single-digit milliseconds during testing, comfortably under the < 2s NFR. Immutable `GameState` produces some GC pressure but nothing measurable at this scale.

## Migration Notes

Greenfield module from the project's perspective — no in-place migration, no existing data, no callers to renegotiate with.

## References

- PRD Business Logic: `context/foundation/prd.md`
- Roadmap F-01: `context/foundation/roadmap.md`
- Tech stack (module layout): `context/foundation/tech-stack.md`
- AGENTS.md (engine module location rule): `AGENTS.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Do not rename step titles.

### Phase 1: POM dependencies

- [x] 1.1 `./mvnw -pl acommon-game-engine dependency:resolve` succeeds
- [x] 1.2 Jackson 3 (`tools.jackson.core:jackson-databind`) on classpath via `spring-boot-starter-json`
- [x] 1.3 POM diff reviewed — clean, no version overrides

### Phase 2: Domain types

- [x] 2.1 `./mvnw -pl acommon-game-engine compile` passes after domain port
- [x] 2.2 Domain field set reviewed for completeness

### Phase 3: Loader + JSON resources + config beans

- [x] 3.1 Jackson imports rewritten to `tools.jackson.*` across loader + Jackson users
- [x] 3.2 `ObjectMapper.copy()` rewritten to `rebuild().configure(...).build()` in 3 loaders
- [x] 3.3 `GameDefinitionConfig.REFERENCE_RULESET_RESOURCE` inlined
- [x] 3.4 JSON resources copied 1:1 (4 in `cards/`, 10 in `data/`)
- [x] 3.5 Module compiles after loader + config port

### Phase 4: Engine subsystems

- [x] 4.1 Full module compiles after engine port
- [x] 4.2 Subdirectory layout matches the design (no CLI inside engine module — CLI lives in `acommon-game-cli`, Phase 7)

### Phase 5: Tests + Spring test bootstrap

- [x] 5.1 `EngineTestApplication` (`@SpringBootApplication`) added under `src/test/java/cards/loxley/game/`
- [x] 5.2 `./mvnw -pl acommon-game-engine clean test` — 200 tests, 0 failures, 0 errors
- [x] 5.3 Test counts match the planned test classes per phase (CLI tests live in `acommon-game-cli`, Phase 7)

### Phase 6: Reactor build verification

- [x] 6.1 `./mvnw clean install` — BUILD SUCCESS across all reactor modules
- [x] 6.2 `LoxleyCardsApplicationTests` (app module) still passes
- [x] 6.3 Sanity grep — no `com.sherwood` references in `backend/`

### Phase 7: CLI runner module

- [x] 7.1 Parent POM gains `<module>acommon-game-cli</module>` + `loxley-cards-game-cli` in `<dependencyManagement>`
- [x] 7.2 New module `acommon-game-cli/` with `pom.xml` (engine dep + spring-boot-starter + test scope + spring-boot-maven-plugin)
- [x] 7.3 7 CLI source files + `LoxleyCliApplication` ported under `cards.loxley.cli`
- [x] 7.4 5 CLI test files + `LoxleyCliApplicationTests` + `MoveTestFixtures` (copied to test scope) ported
- [x] 7.5 `@SpringBootApplication(scanBasePackages = {"cards.loxley.cli", "cards.loxley.game"})` — engine beans discoverable
- [x] 7.6 `./mvnw clean install` — 6 modules, BUILD SUCCESS, 29 CLI tests pass
- [x] 7.7 Smoke `./mvnw -pl acommon-game-cli spring-boot:run` — bot evaluation + profile evaluation + bot-vs-bot simulation all run, win rates look plausible
