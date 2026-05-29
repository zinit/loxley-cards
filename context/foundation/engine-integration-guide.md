# Loxley Cards — Engine Integration Guide

> Przewodnik integracji z silnikiem Loxley Cards (`backend/acommon-game-engine/`) dla S-02 (REST controllers wystawiające engine przez API — `playable-game-api`) i innych konsumentów.
> Status: pasuje do stanu kodu po F-01 (230 testów zielonych w reactorze — 200 engine + 29 cli + 1 app).
> Czytelnik: osoba budująca REST/WebSocket layer w `app/` module, lub inny consumer engine'a.

---

## Jak czytać ten dokument

Trzy ścieżki dla różnych potrzeb:

- **"Chcę szybko spiąć REST z silnikiem"** → Sekcja 2 (entry points) + Sekcja 8 (sample controller, kompilujący się ~70 linii). Reszta gdy się zatniesz.
- **"Projektuję API od zera, chcę zrozumieć całość"** → Czytaj liniowo od Sekcji 1 do końca.
- **"Coś nie działa / mam dziwny błąd"** → Sekcja 6 (edge cases + invariants) i Sekcja 7 (gotchas).

Każda klasa zawsze cytowana z pełną nazwą pakietu — żeby `Cmd+P` w IDE od razu otwierał plik.

---

## 1. Wprowadzenie — co to za silnik i jak się go używa

### 1.1. Mental model

Silnik to **biblioteka Java/Spring** — kolekcja beanów które dostajesz przez Dependency Injection. Nie ma REST/WebSocket layer — to budujesz Ty. Engine udostępnia trzy rodzaje powierzchni:

**Entry points (mutacja stanu):**
- `GameStateFactory` — tworzy nową grę
- `TurnOrchestrator` — wykonuje ruch (jedyne miejsce mutacji które warstwa web ma wołać)

**Queries (pure functions, bezstanowe):**
- `MoveGenerator` — jakie ruchy są legalne
- `MoveValidator` — czy konkretny ruch jest legalny (dry-run)
- `MoveDescriber` — human-readable opis ruchu
- `CardScorer`, `RowScorer`, `BoardScorer` — punktacja
- `CampaignStageRegistry`, `OpponentProfileRegistry`, `DeckVariantLoader` — metadane

**Asynchronous notifications:**
- `MatchEventBus` + `MatchEventListener` — eventy końca rundy/meczu (do broadcasting przez WebSocket)

### 1.2. Główne flow integracji

```
HTTP request → REST controller → TurnOrchestrator.playTurn(state, move)
                                       │
                                       ▼
                              MoveExecutor mutuje GameState
                                       │
                                       ▼
                              RoundResolver (jeśli end of round)
                                       │
                                       ▼
                              MatchEventBus publishuje
                                       │
                                       ▼
                              MatchEventListeners — w tym Twój
                              WebSocketBroadcastListener wysyła event
                              do frontend'u
```

Krytyczne dla designu: **engine to mutable state**. Każdy `GameState` to konkretny mecz w toku, zachowuje stan między wywołaniami. Twoje REST/WebSocket *musi* zarządzać cyklem życia tych obiektów (sekcja 5).

### 1.3. Czego silnik nie robi

Świadomie poza scope — Twoja odpowiedzialność:

- **Persistencja** — `GameState` istnieje w pamięci, restart aplikacji = utrata gier w toku
- **Autoryzacja** — nikt nie sprawdza kto ma prawo wywołać `playTurn`
- **DTO/wireformat** — domain records są technicznie serializowalne przez Jackson, ale ślejesz ręke przeciwnika do klienta (anti-cheat hole)
- **Thread safety** — żaden bean nie jest thread-safe per `GameState`. Musisz sam serializować dostęp
- **Lifecycle gier** — start/end/timeout, sprzątanie po inactive games
- **Match-making / multiplayer** — pojedynczy `GameState` = mecz między dwoma stronami, w MVP jedna z tych stron to bot

---

## 2. Public API surface — beans do wstrzykiwania

Wszystkie poniższe klasy są `@Component` chyba że zaznaczono inaczej. Wstrzykuj przez konstruktor.

### 2.1. Entry points

#### `GameStateFactory` — tworzenie gier

```java
package cards.loxley.game.domain.state;

@Component
public class GameStateFactory {
    public GameState newGame(Deck deckP1, Deck deckP2);
    public GameState newGame(Deck deckP1, Deck deckP2, Random rng);
    public GameState newCampaignGame(CampaignStage stage);
    public GameState newCampaignGame(CampaignStage stage, Random rng);
    public Deck toDeck(DeckVariant variant);
}
```

| Metoda | Kiedy używać |
|---|---|
| `newGame(Deck, Deck)` | Custom mecz z dowolnymi taliami. Używa default `Random` (nondeterministic). |
| `newGame(Deck, Deck, Random)` | Jak wyżej, ale z konkretnym seedem. Dla testów lub reprodukowalności. |
| `newCampaignGame(CampaignStage)` | **Domyślny entry point dla S-02 (REST campaign play).** Gracz wybiera etap, fabryka konstruuje grę z odpowiednim deck variant dla bota. |
| `newCampaignGame(CampaignStage, Random)` | Seedowana wersja kampanii. |
| `toDeck(DeckVariant)` | Konwersja `DeckVariant` (JSON) → `Deck` (runtime). Rzadko potrzebne na poziomie web. |

**Co robi `newGame` pod spodem:**
1. Shuffluje obie talie
2. Rozdaje po 10 kart do hand
3. Losuje (`rng.nextBoolean()`) kto zaczyna R1
4. Zwraca `GameState` gotowy do `playTurn`

**Wyjątki:** `IllegalStateException` jeśli referenced cardId/profileId/variantId nie istnieje w `GameDefinition`.

#### `TurnOrchestrator` — wykonywanie ruchu

```java
package cards.loxley.game.engine.execution;

@Component
public class TurnOrchestrator {
    public void playTurn(GameState state, Move move);
}
```

**To jedyna metoda mutacji którą warstwa web powinna wołać.** Wszystkie inne mutatory na `GameState`/`PlayerState` są technicznie public (sekcja 4), ale ich wywoływanie omija invariants.

Co robi:
1. Sprawdza `state.matchEnded()` — rzuca `IllegalStateException` jeśli mecz się skończył
2. Sprawdza `move.player() == state.currentTurn()` — rzuca `IllegalStateException` jeśli nie tura tego gracza
3. Deleguje do `MoveExecutor.execute` (który waliduje przez `MoveValidator`, rzuca `IllegalMoveException` jeśli nielegalny)
4. Jeśli `state.bothPlayersPassed()` → rozwiązuje rundę (`RoundResolver`), publishuje `RoundEnded` / `MatchEnded`
5. W przeciwnym razie: przełącza turę (chyba że tylko jeden gracz pasował, wtedy zostaje przy drugim)

### 2.2. Queries

#### `MoveGenerator` — lista legalnych ruchów

```java
package cards.loxley.game.engine.move;

@Component
public class MoveGenerator {
    public List<Move> legalMoves(GameState state, Player player);
}
```

Zwraca wszystkie legalne ruchy które `player` może wykonać z bieżącego `state`:
- Zawsze `PassMove` (chyba że gracz już spasował — wtedy lista jest **pusta**)
- `UseLeaderMove` jeśli leader nieużyty
- `PlayCardMove` dla każdej karty z hand z odpowiednim wariantem

**Ważne dla integracji:** jeśli `legalMoves` zwraca pustą listę, znaczy że gracz spasował. Nie próbuj wołać `bot.chooseMove` z pustą listą — wybuchnie.

#### `MoveValidator` — dry-run walidacja

```java
@Component
public class MoveValidator {
    public ValidationResult validate(GameState state, Move move);
}

public sealed interface ValidationResult {
    record Valid() implements ValidationResult {}
    record Invalid(String reason) implements ValidationResult {}
}
```

Sprawdza pojedynczy ruch bez mutacji. `TurnOrchestrator.playTurn` woła to wewnętrznie, ale możesz użyć w UI do live walidacji (np. dimming nielegalnych przycisków).

#### `MoveDescriber` — czytelny opis ruchu

```java
@Component
public class MoveDescriber {
    public String describe(Move move, GameState state);
}
```

Zwraca string typu `"PLAY Strach na Wróble on Trebusz Sherwoodu (SIEGE) [u3]"`. Przydatne do logów, replay messages, tooltipów. Format dla front-endu jest oczywiście inny — to tylko fallback do tekstu.

#### Scoring — `CardScorer` / `RowScorer` / `BoardScorer`

```java
package cards.loxley.game.engine.scoring;

@Component public class CardScorer {
    public int currentStrength(CardInstance card, RowState row);
}

@Component public class RowScorer {
    public int rowStrength(RowState row);
}

@Component public class BoardScorer {
    public int sideStrength(BoardSide side);
    public int playerStrength(GameState state, Player player);
}
```

Pure functions, używaj do live UI scoring. Kolejność obliczeń w `CardScorer.currentStrength`:
1. **Hero** → short-circuit do `basePower` (immune na wszystko)
2. Inaczej: zaczynamy od `basePower`
3. **Weather** active na rzędzie → siła zerowana do `1`
4. **Tight Bond** (jeśli ≥2 kopie tego samego `cardId` na rzędzie) → `× N`
5. **Morale Boost** (każdy non-hero MORALE_BOOST na rzędzie) → `+1`
6. **Horn** active na rzędzie → `× 2`

### 2.3. Campaign / opponents

#### `CampaignStageRegistry` — etapy kampanii

```java
package cards.loxley.game.engine.campaign;

@Component
public class CampaignStageRegistry {
    public Optional<CampaignStage> findByNumber(int stageNumber);
    public List<CampaignStage> all();      // posortowane po stageNumber
}

public record CampaignStage(int stageNumber, String opponentProfileId, String description) {}
```

Wczytuje 10 etapów z `data/campaign_stages.json` przy starcie. `all()` zwraca posortowane po `stageNumber`.

#### `OpponentProfileRegistry` — 5 profili trudności

```java
package cards.loxley.game.engine.opponent;

@Component
public class OpponentProfileRegistry {
    public Optional<OpponentProfile> findById(String id);
    public List<OpponentProfile> all();
}

public record OpponentProfile(
    String id, String displayName, String strategyName, 
    String deckVariantId, String description
) {}
```

Hardcoded 5 profili: `ultra_easy`, `easy`, `medium`, `hard`, `top_hard`. Mapują się na strategy + deck variant.

#### `DeckVariantLoader` — warianty talii

```java
@Component
public class DeckVariantLoader {
    public Optional<DeckVariant> findById(String id);
    public List<DeckVariant> all();
}

public record DeckVariant(
    String id, String displayName, String leaderCardId, List<DeckEntry> cards
) {
    public int totalCardCopies();
}
```

Cztery decks: `gimped_deck` (29 kart), `standard_deck` (37), `boosted_deck` (38), `boosted_plus_deck` (39).

### 2.4. Bots

#### `BotStrategy` (interface)

```java
package cards.loxley.game.engine.bot;

public interface BotStrategy {
    String name();
    Move chooseMove(GameState state, Player player, List<Move> legalMoves);
}
```

Kontrakt: `legalMoves` musi być output `MoveGenerator.legalMoves(state, player)` z tym samym `(state, player)`. Lista musi być **niepusta** — implementacje rzucą NPE na pustym wejściu.

#### Konkretne strategie

```java
@Component public class HeuristicMediumBot implements BotStrategy {}   // "heuristic-medium"
@Component public class HeuristicEasyBot   implements BotStrategy {}   // "heuristic-easy"

public class RandomBot implements BotStrategy {                        // NOT @Component
    public RandomBot(Random rng);
    public RandomBot(long seed);
    // "random"
}
```

`RandomBot` celowo nie jest bean'em — instancjujesz na żądanie z konkretnym seedem.

#### `BotStrategyResolver` — name → strategy

```java
@Component
public class BotStrategyResolver {
    public BotStrategy resolve(String name);   // np. "heuristic-medium"
}
```

Useful w controllerze:
```java
OpponentProfile profile = profileRegistry.findById(stage.opponentProfileId()).orElseThrow();
BotStrategy bot = botResolver.resolve(profile.strategyName());
```

**Uwaga:** `resolve("random")` tworzy nowy `RandomBot(new Random())` per call — nondeterministic. Dla reprodukowalnego eval użyj `new RandomBot(seed)` ręcznie.

### 2.5. Events

#### `MatchEventBus` + listener pattern

```java
package cards.loxley.game.engine.event;

@Component
public class MatchEventBus {
    public void publish(MatchEvent event, GameState state);
}

public interface MatchEventListener {
    void onEvent(MatchEvent event, GameState state);
}

public sealed interface MatchEvent permits RoundEnded, MatchEnded {}

public record RoundEnded(
    int roundNumber, int p1Score, int p2Score, Optional<Player> winner
) implements MatchEvent {}

public record MatchEnded(Optional<Player> winner, int totalRounds) implements MatchEvent {}
```

**Jak dodać własny listener:** zdefiniuj klasę z `@Component implements MatchEventListener`. Spring auto-discovera ją przy starcie. Brak runtime add/remove API.

**Resilience:** wyjątek z listener'a → log warn, kontynuuje pozostałymi listenerami. Twój `RuntimeException` nie zablokuje innych broadcasterów.

**Aktualne listenery:** tylko jeden — `DrawOnRoundWinListener` (faction passive). Dla REST integration (S-02) możesz dorzucić własny event listener jeśli potrzebny; ewentualna WebSocket integration (gdyby kiedyś weszła) wymaga `WebSocketBroadcastListener`.

### 2.6. Eval (dla CI / regression testów)

```java
package cards.loxley.game.engine.eval;

@Component
public class EvalHarness {
    public EvalResult runMatches(BotStrategy bot1, BotStrategy bot2, int gameCount, long seedBase);
    public EvalResult runMatchesWithDecks(BotStrategy bot1, Deck deckBot1, 
                                          BotStrategy bot2, Deck deckBot2,
                                          int gameCount, long seedBase);
}
```

Przyda się jeśli będziesz robił CI test "czy moja zmiana w heurystyce nie złamała hierarchii". Każda gra dostaje `new Random(seedBase + i)` — reprodukowalne.

---

## 3. Struktura danych — domain records

### 3.1. Główne typy

```java
public enum Player { 
    P1, P2;
    public Player opponent();
}
public enum CardType { LEADER, UNIT, SPECIAL }
public enum RowId { CLOSE, RANGED, SIEGE }
public enum PlayTarget { GLOBAL, OWN_BOARD, OPPONENT_BOARD, SELECTED_ROW, OWN_UNIT_ON_BOARD }
```

### 3.2. Stałe definicje (immutable, z JSON)

```java
public record Card(
    String id, String name, CardType cardType, String faction,
    String description, RowId row, Integer basePower, 
    List<String> abilities, PlayTarget playTarget, 
    String role, MvpImportance mvpImportance
) {}

public record Deck(
    String id, String name, String faction, String leaderCardId,
    List<DeckEntry> cards, DeckSummary summary
) {}

public record DeckEntry(String cardId, int count) {}

public record GameDefinition(
    String schemaVersion, Ruleset ruleset, List<Row> rows,
    List<Ability> abilities, List<Card> cards, Deck deck
) {}
```

`Card` to **definicja** (jeden obiekt per typ karty), nie egzemplarz na boardzie. `CardInstance` (niżej) to egzemplarz.

### 3.3. Runtime state (mutable)

```java
public final class CardInstance {           // NOT a record (UUID generowany w konstruktorze)
    public CardInstance(Card card, Player owner);
    public String instanceId();             // UUID, niezmienny
    public Card card();
    public Player owner();
    // equals/hashCode po instanceId
}
```

**`CardInstance` jest immutable** — żeby zmienić ownera (mechanika Decoy), engine tworzy *nowy* `CardInstance` z nowym UUID. Stare referencje stają się dangling.

```java
public class RowState {                     // MUTABLE
    public RowId rowId();
    public List<CardInstance> units();      // unmodifiable view (live)
    public boolean weatherActive();
    public boolean hornActive();
    public void addUnit(CardInstance ci);
    public boolean removeUnit(String instanceId);
    public void applyWeather() / removeWeather();
    public void applyHorn() / removeHorn();
    public void clear();
}

public class BoardSide {                    // MUTABLE container of 3 RowStates
    public RowState row(RowId id);
    public RowState close() / ranged() / siege();
    public void clearAll();
    public Optional<CardInstance> findUnit(String instanceId);   // across rows
}

public class PlayerState {                  // MUTABLE
    // Read accessors
    public BoardSide board();
    public CardInstance leader();
    public String factionId();
    public List<CardInstance> hand();       // unmodifiable view
    public List<CardInstance> deck();       // unmodifiable view
    public List<CardInstance> graveyard();  // unmodifiable view
    public boolean leaderUsed();
    public boolean passed();
    public int roundsWon();
    
    // Mutators — sekcja 4 mówi czemu są public
    // drawCard(), drawCards(), removeFromHand(), addToHand(), 
    // sendToGraveyard(), markLeaderUsed(), markPassed(),
    // incrementRoundsWon(), resetForNewRound()
}

public class GameState {                    // MUTABLE root
    public PlayerState p1() / p2();
    public Player currentTurn();
    public int roundNumber();
    public boolean matchEnded();
    public Optional<Player> matchWinner();
    public List<RoundResult> roundHistory();     // unmodifiable view
    public PlayerState playerState(Player p);
    public PlayerState opponent(Player p);
    public Optional<CardInstance> findUnitAnywhere(String instanceId);
    public boolean bothPlayersPassed();
    
    public record RoundResult(int roundNumber, int p1Score, int p2Score, Optional<Player> winner) {}
}
```

### 3.4. Moves (sealed)

```java
public sealed interface Move permits PlayCardMove, PassMove, UseLeaderMove {
    Player player();
}

public record PassMove(Player player) implements Move {}
public record UseLeaderMove(Player player) implements Move {}

public record PlayCardMove(
    Player player,
    String handInstanceId,           // którą kartę z ręki
    RowId targetRow,                 // null dla GLOBAL specials i Decoy
    String targetInstanceId          // null poza Decoy
) implements Move {
    public static PlayCardMove unit(Player p, String handInstanceId, RowId targetRow);
    public static PlayCardMove spy(Player p, String handInstanceId, RowId targetRow);
    public static PlayCardMove special(Player p, String handInstanceId);
    public static PlayCardMove specialOnRow(Player p, String handInstanceId, RowId targetRow);
    public static PlayCardMove specialOnUnit(Player p, String handInstanceId, String targetInstanceId);
}
```

**Mapowanie typu ruchu na sygnaturę:**

| Co | targetRow | targetInstanceId | Factory |
|---|---|---|---|
| Unit play | `CLOSE/RANGED/SIEGE` | `null` | `.unit()` |
| Spy (też unit) | `CLOSE/RANGED/SIEGE` | `null` | `.spy()` |
| Scorch / Weather global | `null` | `null` | `.special()` |
| Horn / Weather row | `CLOSE/RANGED/SIEGE` | `null` | `.specialOnRow()` |
| Decoy | `null` | `<UUID>` | `.specialOnUnit()` |

Frontend będzie wysyłał JSON typu `{kind: "unit", handInstanceId: "...", targetRow: "CLOSE"}` — dobry pomysł żeby było po polach factory method.

### 3.5. Events (sealed)

```java
public sealed interface MatchEvent permits RoundEnded, MatchEnded {}

public record RoundEnded(
    int roundNumber, int p1Score, int p2Score, Optional<Player> winner
) implements MatchEvent {}

public record MatchEnded(
    Optional<Player> winner, int totalRounds
) implements MatchEvent {}
```

---

## 4. Mutowalność — co się zmienia gdzie

Krytyczne dla designu web layer — który stan trzeba persistować.

### 4.1. Surface mutacji

| Klasa | Mutatory | Wołane przez |
|---|---|---|
| `GameState` | `switchTurn`, `assignTurn`, `advanceRound`, `recordRound`, `endMatchWith`, `endMatchAsDraw` | `TurnOrchestrator`, `RoundResolver` |
| `PlayerState` | `drawCard*`, `removeFromHand`, `sendToGraveyard`, `addToHand`, `incrementRoundsWon`, `resetForNewRound`, `markLeaderUsed`, `markPassed` | `MoveExecutor`, `RoundResolver`, listenery, effects |
| `BoardSide` | `clearAll`, `removeUnit` (delegate) | `MoveExecutor`, `RoundResolver` |
| `RowState` | `addUnit`, `removeUnit`, `applyWeather`, `removeWeather`, `applyHorn`, `removeHorn`, `clear` | `MoveExecutor`, ability effects |
| `CardInstance` | **brak** — immutable | — |

### 4.2. Thread safety — żadnej nie ma

**Brak `synchronized`, `volatile`, atomic anything.** Każdy mutable bean (`GameState`, `PlayerState`, `BoardSide`, `RowState`) zakłada single-threaded access.

**Co to znaczy dla web layer:** dwa równoczesne requesty na ten sam `gameId` skorumpują state. Musisz serializować dostęp per game. Trzy opcje, w kolejności narastającej złożoności:

**Opcja 1 — Per-game lock w controllerze:**
```java
private final Map<String, GameState> games = new ConcurrentHashMap<>();
private final Map<String, Object> locks = new ConcurrentHashMap<>();

@PostMapping("/{gameId}/play")
public GameState play(@PathVariable String gameId, @RequestBody PlayRequest req) {
    synchronized (locks.computeIfAbsent(gameId, k -> new Object())) {
        GameState state = games.get(gameId);
        orchestrator.playTurn(state, mapMove(req, state));
        return state;
    }
}
```
Proste, działa. Lock keys nigdy nie są usuwane — minor leak ale w MVP OK.

**Opcja 2 — Single-thread executor per game:**
```java
private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();

@PostMapping("/{gameId}/play")
public CompletableFuture<GameState> play(@PathVariable String gameId, @RequestBody PlayRequest req) {
    ExecutorService exec = executors.computeIfAbsent(gameId, 
        k -> Executors.newSingleThreadExecutor());
    return CompletableFuture.supplyAsync(() -> {
        GameState state = games.get(gameId);
        orchestrator.playTurn(state, mapMove(req, state));
        return state;
    }, exec);
}
```
Lepsze gdy jakieś ruchy są wolne (np. LLM-bot w Fazie 3), ale wprowadza async (return type to `CompletableFuture` lub `DeferredResult`).

**Opcja 3 — Spring Session per game:**
Skomplikowane, niepotrzebne dla single-game-per-player MVP. Pomijam.

### 4.3. Encapsulation gaps

Świadome lub przypadkowe, ale do uwzględnienia:

- **Wszystkie mutatory `PlayerState` i `GameState` są `public`.** Cokolwiek ma referencję do `GameState` może obejść `TurnOrchestrator` i namieszać. Engine to akceptuje bo executor żyje w innym pakiecie i potrzebuje mutować — REST layer (S-02) może rozważyć fasadę `GameSession` która eksponuje tylko `playTurn`.
- **List views są live**, nie snapshoty. `hand()` zwraca `Collections.unmodifiableList(internalList)`. Jeśli przed mutacją zachowasz `List<CardInstance> snapshot = state.p1().hand()`, po mutacji `snapshot.size()` zwróci nową wartość. Snapshot tylko przy momencie wywołania (np. zaraz zserializuj do JSON).

### 4.4. Brak globalnego state

Żadnego static cache, żadnych singletonów poza Spring beans. `CardInstance.UUID.randomUUID()` używa static thread-safe source — bezpieczne. `MatchEventBus.listeners` jest `final` po konstrukcji. **Skalujesz horyzontalnie bez side-effects między instancjami** (poza tym że `GameState`'y są in-memory, więc sticky session albo external storage).

---

## 5. Lifecycle gier — gdzie trzymać `GameState`

Krytyczna decyzja designu. Trzy wzorce, każdy ma kompromisy.

### 5.1. Wzorzec A — In-memory Map (rekomendowany dla MVP)

```java
@Service
public class GameSessionStore {
    private final Map<String, GameState> games = new ConcurrentHashMap<>();
    private final Map<String, CampaignStage> stages = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();
    
    public String create(GameState state, CampaignStage stage) {
        String id = UUID.randomUUID().toString();
        games.put(id, state);
        stages.put(id, stage);
        lastAccess.put(id, System.currentTimeMillis());
        return id;
    }
    
    public GameState find(String gameId) {
        lastAccess.put(gameId, System.currentTimeMillis());
        return games.get(gameId);
    }
    
    @Scheduled(fixedDelay = 60_000)
    public void cleanupStale() {
        long cutoff = System.currentTimeMillis() - 30 * 60_000;   // 30 min
        lastAccess.entrySet().removeIf(e -> {
            if (e.getValue() < cutoff) {
                games.remove(e.getKey());
                stages.remove(e.getKey());
                return true;
            }
            return false;
        });
    }
}
```

**Plus:** prosty, działa, zero zewnętrznych dependencies.
**Minus:** stan ginie przy restarcie. Single-node tylko.

Dla portfolio-grade MVP — wystarczy. Jeden user, jedna gra, "F5 i grasz dalej" działa do 30 min.

### 5.2. Wzorzec B — Per-session (HTTP session)

```java
@RestController
@RequestMapping("/api/game")
@SessionAttributes("game")
public class GameController {
    @ModelAttribute("game")
    public GameSession session() { return new GameSession(); }
    
    @PostMapping("/start")
    public GameState start(@ModelAttribute("game") GameSession session, ...) {
        session.state = factory.newCampaignGame(stage);
        return session.state;
    }
}
```

**Plus:** automatyczna cleanup po wygaśnięciu sesji.
**Minus:** trzeba sticky session przy load balancer. Jedna gra per user (nie da się grać dwóch równocześnie).

Pomijam ze względu na kompromisy — wzorzec A wystarczy.

### 5.3. Wzorzec C — Stateless (klient trzyma state)

Klient wysyła pełny `GameState` w body każdego requesta. Serwer aplikuje ruch i zwraca nowy state.

**Plus:** czysto stateless, skaluje horyzontalnie idealnie.
**Minus:** bardzo ciężki request payload (cały deck + hand + board). Klient łatwo oszuka (np. dorzuca Hero do hand). Anti-cheat = nightmare.

Tylko dla bardzo specyficznych use cases (np. mecze offline single player gdzie cheat jest po stronie samego gracza).

### 5.4. Rekomendacja

Dla S-02 (pierwsza REST integration): **Wzorzec A + per-game synchronized lock** (sekcja 4.2 Opcja 1). 30 linii kodu, działa.

Później (deploy na produkcję) można dorzucić Redis jako external store + sticky sessions. Migracja jest płaska bo `GameState` jest serializowalny (z zastrzeżeniami z sekcji 6).

---

## 6. Serializacja JSON — co Jackson robi domyślnie i co z tym zrobić

### 6.1. Defaults

`spring-boot-starter-json` jest na classpath. Default `ObjectMapper`:
- Records → flat JSON
- `Optional<T>` → null gdy empty, T gdy present (`jackson-datatype-jdk8` zarejestrowany)
- Nie ma żadnych `@JsonIgnore` / `@JsonProperty` w domain records
- Sealed interfaces nie mają type info — Jackson serializuje pola, ale klient nie wie *jaki* to typ

### 6.2. Per-type warnings

| Typ | Default serialization | Issues |
|---|---|---|
| `Card`, `Deck`, `GameDefinition` | Records → flat. | Static data, duże (~70 kart). Cachuj client-side. |
| `Move`, `PlayCardMove`, `PassMove`, `UseLeaderMove` | Records → flat. | **Brak type info.** Klient nie wie czy to PlayCardMove czy PassMove bez patrzenia na obecność pól. Dodaj `@JsonTypeInfo` jeśli klient ma deserializować. |
| `MatchEvent`, `RoundEnded`, `MatchEnded` | Same. | Ten sam problem. |
| `CardInstance` | Bean-style (`instanceId`/`card`/`owner` accessors). | OK, bez circular refs. |
| `GameState` | Pełny graf — `p1`, `p2`, currentTurn, roundNumber, matchEnded, matchWinner, roundHistory, bothPlayersPassed. | **Cały deck przeciwnika i jego hand serializowane.** Anti-cheat hole. |

### 6.3. Czy `objectMapper.writeValueAsString(state)` działa?

Tak. Wypluje duży, payload z wszystkimi polami. To "działa" ale **nie używaj na produkcji** — klient widzi rękę przeciwnika.

### 6.4. Co zrobić — DTO design proposal

Buduj view-models per perspektywa gracza:

```java
public record GameStateView(
    String gameId,
    int roundNumber,
    int maxRounds,
    String currentTurn,           // "P1" / "P2" — String dla JS friendliness
    boolean yourTurn,
    boolean matchEnded,
    String matchWinner,           // "P1" / "P2" / null
    PlayerView you,
    OpponentView opponent,
    List<RoundResultView> roundHistory
) {}

public record PlayerView(
    BoardSideView board,
    List<CardInstanceView> hand,           // pełne karty — to Twoja ręka
    int deckSize,                          // tylko liczba
    int graveyardSize,
    boolean leaderUsed,
    boolean passed,
    int roundsWon,
    int totalStrength
) {}

public record OpponentView(
    BoardSideView board,
    int handSize,                          // TYLKO LICZBA — nie pokazujesz kart przeciwnika
    int deckSize,
    int graveyardSize,
    boolean leaderUsed,
    boolean passed,
    int roundsWon,
    int totalStrength
) {}

public record CardInstanceView(
    String instanceId,
    String cardId,
    String name,
    String row,                            // "CLOSE" / "RANGED" / "SIEGE" / null
    Integer basePower,
    int currentStrength,                   // CardScorer.currentStrength po kontekście
    List<String> abilities,
    String cardType,                       // "UNIT" / "SPECIAL" / "LEADER"
    String owner,                          // "P1" / "P2" — dla [opp]/[mine] tagów
    String playTarget                      // dla validacji w UI
) {}

public record BoardSideView(
    RowView close,
    RowView ranged,
    RowView siege,
    int totalStrength
) {}

public record RowView(
    List<CardInstanceView> units,
    boolean weatherActive,
    boolean hornActive,
    int strength
) {}
```

Plus mapper w controllerze:

```java
@Component
public class GameStateMapper {
    private final CardScorer cardScorer;
    private final RowScorer rowScorer;
    private final BoardScorer boardScorer;
    
    public GameStateView toView(GameState state, String gameId, Player perspective) {
        Player opp = perspective.opponent();
        return new GameStateView(
            gameId,
            state.roundNumber(),
            3,                                                       // maxRounds
            state.currentTurn().name(),
            state.currentTurn() == perspective,
            state.matchEnded(),
            state.matchWinner().map(Enum::name).orElse(null),
            toPlayerView(state, perspective),
            toOpponentView(state, opp),
            state.roundHistory().stream().map(this::toRoundResultView).toList()
        );
    }
    // ... resztę helperów
}
```

To około 200 linii kodu mappera. Po jednej iteracji masz strawny payload dla frontu.

### 6.5. Move DTO (klient → serwer)

```java
public record MoveRequest(
    String kind,                  // "pass" / "leader" / "unit" / "spy" / "special" / "row" / "unit-target"
    String handInstanceId,        // null dla pass / leader
    String targetRow,             // "CLOSE" / "RANGED" / "SIEGE" / null
    String targetInstanceId       // tylko dla Decoy
) {}
```

Mapowanie w controllerze:

```java
private Move mapMove(MoveRequest req, GameState state) {
    Player p = state.currentTurn();
    return switch (req.kind()) {
        case "pass"        -> new PassMove(p);
        case "leader"      -> new UseLeaderMove(p);
        case "unit"        -> PlayCardMove.unit(p, req.handInstanceId(), 
                                                RowId.valueOf(req.targetRow()));
        case "spy"         -> PlayCardMove.spy(p, req.handInstanceId(),
                                               RowId.valueOf(req.targetRow()));
        case "special"     -> PlayCardMove.special(p, req.handInstanceId());
        case "row"         -> PlayCardMove.specialOnRow(p, req.handInstanceId(),
                                                        RowId.valueOf(req.targetRow()));
        case "unit-target" -> PlayCardMove.specialOnUnit(p, req.handInstanceId(),
                                                          req.targetInstanceId());
        default -> throw new IllegalArgumentException("Unknown move kind: " + req.kind());
    };
}
```

---

## 7. Edge cases i invariants

### 7.1. Co `TurnOrchestrator.playTurn` rzuca

| Sytuacja | Co dostaniesz |
|---|---|
| `state.matchEnded() == true` | `IllegalStateException("Match has already ended")` |
| `move.player() != state.currentTurn()` | `IllegalStateException("Not your turn: ...")` |
| Move nielegalny wg validatora | `IllegalMoveException` (extends `RuntimeException`) z reason'em |

**Jak mapować na HTTP:**

```java
@ExceptionHandler(IllegalMoveException.class)
public ResponseEntity<ErrorResponse> illegalMove(IllegalMoveException ex) {
    return ResponseEntity.status(400).body(new ErrorResponse("ILLEGAL_MOVE", ex.getMessage()));
}

@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ErrorResponse> illegalState(IllegalStateException ex) {
    return ResponseEntity.status(409).body(new ErrorResponse("INVALID_STATE", ex.getMessage()));
}
```

### 7.2. Invariants które engine utrzymuje

- `state.roundHistory().size()` == liczba ukończonych rund. R bieżąca tam nie jest dopóki obaj nie spasują.
- `state.matchEnded() == true` ⇒ nie używaj `state.currentTurn()`, używaj `state.matchWinner()`.
- `state.matchEnded() == false` ⇒ `playerState(state.currentTurn()).passed() == false` zawsze (inaczej runda już by się rozwiązała).
- Po `resolveEndOfRound` (jeśli mecz nie kończy się): `verifyCleanRoundStart` rzuca `IllegalStateException` jeśli jakikolwiek rząd jest niepusty lub jakaś flaga weather/horn ciągle aktywna. Innymi słowy: board państwa zerowy między rundami.
- `CardInstance.instanceId()` unikalny w skali meczu. **Decoy ownership transfer tworzy nowy UUID** — stare referencje na pre-decoy instancję są dangling.

### 7.3. `MoveGenerator.legalMoves` zwraca pustą listę

Tylko wtedy gdy `playerState(player).passed() == true`. Wykryj to przed wywołaniem `chooseMove` i przeskocz na opponent's turn.

```java
List<Move> legal = generator.legalMoves(state, state.currentTurn());
if (legal.isEmpty()) {
    // Current player passed — engine już o tym wie, ale TurnOrchestrator i tak by wybuchł
    // jeśli próbowałbyś wykonać move. To znak że bot powinien przejść do PassMove implicit.
    // W praktyce: oznacz że "your turn" jest fake-yours, i wywołaj playTurn z nowym Pass
    // przeciwnika żeby zamknąć rundę.
}
```

W praktyce kontroler powinien sprawdzić `state.playerState(p).passed()` i nie eksponować "your turn" w UI gdy gracz spasował.

### 7.4. Defensive copies

- `PlayerState.hand()` / `.deck()` / `.graveyard()` — `Collections.unmodifiableList`. Modyfikacja zwracanej listy rzuca `UnsupportedOperationException`. Sama lista pod spodem żyje.
- `RowState.units()` — to samo.
- `GameState.roundHistory()` — to samo.
- `MatchEventBus.listeners` — `List.copyOf` w konstruktorze.

---

## 8. Sample REST controller (~70 linii kompilującego się kodu)

To kompiluje się z obecnym `HEAD`, zwraca surowe domain objects (Jackson ślepo serializuje — przeczytaj sekcję 6.4 dla DTO design). Pokazuje że engine wiring fitsa w 70 linii.

```java
package cards.loxley.app.web;

import cards.loxley.game.domain.card.RowId;
import cards.loxley.game.domain.state.GameState;
import cards.loxley.game.domain.state.GameStateFactory;
import cards.loxley.game.domain.state.Player;
import cards.loxley.game.engine.bot.BotStrategy;
import cards.loxley.game.engine.bot.BotStrategyResolver;
import cards.loxley.game.engine.campaign.CampaignStage;
import cards.loxley.game.engine.campaign.CampaignStageRegistry;
import cards.loxley.game.engine.execution.IllegalMoveException;
import cards.loxley.game.engine.execution.TurnOrchestrator;
import cards.loxley.game.engine.move.*;
import cards.loxley.game.engine.opponent.OpponentProfile;
import cards.loxley.game.engine.opponent.OpponentProfileRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final Map<String, GameState> games = new ConcurrentHashMap<>();
    private final Map<String, String> profilesByGame = new ConcurrentHashMap<>();

    private final GameStateFactory factory;
    private final CampaignStageRegistry stageRegistry;
    private final OpponentProfileRegistry profileRegistry;
    private final BotStrategyResolver botResolver;
    private final MoveGenerator generator;
    private final TurnOrchestrator orchestrator;

    public GameController(GameStateFactory factory,
                          CampaignStageRegistry stageRegistry,
                          OpponentProfileRegistry profileRegistry,
                          BotStrategyResolver botResolver,
                          MoveGenerator generator,
                          TurnOrchestrator orchestrator) {
        this.factory = factory;
        this.stageRegistry = stageRegistry;
        this.profileRegistry = profileRegistry;
        this.botResolver = botResolver;
        this.generator = generator;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/stage/{stageNumber}")
    public Map<String, Object> startStage(@PathVariable int stageNumber) {
        CampaignStage stage = stageRegistry.findByNumber(stageNumber)
                .orElseThrow(() -> new IllegalArgumentException("Unknown stage: " + stageNumber));
        GameState state = factory.newCampaignGame(stage);
        String gameId = UUID.randomUUID().toString();
        games.put(gameId, state);
        profilesByGame.put(gameId, stage.opponentProfileId());
        return Map.of("gameId", gameId, "state", state);
    }

    @GetMapping("/{gameId}")
    public GameState getState(@PathVariable String gameId) {
        return requireGame(gameId);
    }

    @GetMapping("/{gameId}/moves")
    public List<Move> legalMoves(@PathVariable String gameId) {
        GameState state = requireGame(gameId);
        return generator.legalMoves(state, state.currentTurn());
    }

    @PostMapping("/{gameId}/move")
    public GameState play(@PathVariable String gameId, @RequestBody MoveRequest req) {
        GameState state = requireGame(gameId);
        synchronized (state) {              // per-game lock — sekcja 4.2
            Player p = state.currentTurn();
            Move humanMove = mapMove(req, p);
            orchestrator.playTurn(state, humanMove);

            // Drive bota dopóki nie jego tura lub mecz się skończy
            while (!state.matchEnded() && state.currentTurn() == Player.P2) {
                List<Move> legal = generator.legalMoves(state, Player.P2);
                if (legal.isEmpty()) break;
                
                String profileId = profilesByGame.get(gameId);
                OpponentProfile profile = profileRegistry.findById(profileId).orElseThrow();
                BotStrategy bot = botResolver.resolve(profile.strategyName());
                Move botMove = bot.chooseMove(state, Player.P2, legal);
                orchestrator.playTurn(state, botMove);
            }
            return state;
        }
    }

    private Move mapMove(MoveRequest req, Player p) {
        return switch (req.kind()) {
            case "pass"        -> new PassMove(p);
            case "leader"      -> new UseLeaderMove(p);
            case "unit"        -> PlayCardMove.unit(p, req.handInstanceId(), RowId.valueOf(req.targetRow()));
            case "spy"         -> PlayCardMove.spy(p, req.handInstanceId(), RowId.valueOf(req.targetRow()));
            case "special"     -> PlayCardMove.special(p, req.handInstanceId());
            case "row"         -> PlayCardMove.specialOnRow(p, req.handInstanceId(), RowId.valueOf(req.targetRow()));
            case "unit-target" -> PlayCardMove.specialOnUnit(p, req.handInstanceId(), req.targetInstanceId());
            default -> throw new IllegalArgumentException("Unknown move kind: " + req.kind());
        };
    }

    private GameState requireGame(String gameId) {
        GameState state = games.get(gameId);
        if (state == null) throw new IllegalArgumentException("Unknown gameId: " + gameId);
        return state;
    }

    public record MoveRequest(String kind, String handInstanceId, String targetRow, String targetInstanceId) {}
}
```

### 8.1. Czego brakuje w sample (świadomie)

- **DTO** — surowe `GameState` szle rękę przeciwnika do klienta (anti-cheat hole). Patrz 6.4.
- **Error handlery** — wszystkie wyjątki lecą jako 500. Dodaj `@ExceptionHandler` jak w 7.1.
- **Auth** — każdy z `gameId` może grać tą grą. Dodaj user session.
- **Stale game cleanup** — `games` Map rośnie w nieskończoność. Dodaj scheduled cleanup jak w 5.1.
- **WebSocket push** — `RoundEnded` / `MatchEnded` nie są broadcastowane. Patrz sekcja 9.
- **CORS** — frontend (Vite dev server) zwykle na `:5173`, Spring na `:8080`. Patrz 10.2.

Ale **engine wiring działa** — wszystko powyżej to web concerns nakładane na stabilne API.

---

## 9. WebSocket flow (opcjonalny, ale rekomendowany)

Dla mecz "live" gdzie chcesz pokazać "Bot myśli..." → "Bot zagrał kartę X" → "Koniec rundy: wygrałeś" bez polling'u.

### 9.1. Setup

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins("*").withSockJS();
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
}
```

### 9.2. Broadcaster listener

```java
@Component
public class WebSocketBroadcastListener implements MatchEventListener {
    private final SimpMessagingTemplate broker;
    
    public WebSocketBroadcastListener(SimpMessagingTemplate broker) {
        this.broker = broker;
    }
    
    @Override
    public void onEvent(MatchEvent event, GameState state) {
        // gameId musi być w context — np. ThreadLocal lub w GameState (extension)
        String gameId = GameContext.currentGameId();    // Twój helper
        
        switch (event) {
            case RoundEnded r -> broker.convertAndSend(
                "/topic/games/" + gameId + "/round-ended", r);
            case MatchEnded m -> broker.convertAndSend(
                "/topic/games/" + gameId + "/match-ended", m);
        }
    }
}
```

**Problem:** `MatchEventListener.onEvent` nie wie z którego `gameId` przyszedł event. Trzy rozwiązania:

1. **ThreadLocal** w controllerze — set przed `playTurn`, clear po (proste ale ugly)
2. **Wrap `GameState`** w `GameSession(GameState state, String gameId)` — i przekazuj `GameSession` zamiast `GameState` przez całą stack (czystsze, ale wymaga modyfikacji silnika)
3. **Custom event** — Twoje `WebSocketBroadcastListener` ignoruje engine events, controller publikuje *swoje* eventy z gameId po `playTurn`

Wzorzec 3 jest najczystszy:

```java
@PostMapping("/{gameId}/move")
public GameState play(...) {
    synchronized (state) {
        int roundsBefore = state.roundHistory().size();
        boolean matchEndedBefore = state.matchEnded();
        
        orchestrator.playTurn(state, humanMove);
        driveBot(...);
        
        // Wykryj eventy które się stały
        if (state.roundHistory().size() > roundsBefore) {
            GameState.RoundResult last = state.roundHistory().get(state.roundHistory().size() - 1);
            broker.convertAndSend("/topic/games/" + gameId + "/round-ended", last);
        }
        if (state.matchEnded() && !matchEndedBefore) {
            broker.convertAndSend("/topic/games/" + gameId + "/match-ended", 
                Map.of("winner", state.matchWinner().map(Enum::name).orElse(null)));
        }
        
        return state;
    }
}
```

Mniej elegancko ale działa bez modyfikacji silnika.

### 9.3. Frontend (vanilla JS / React klient)

```js
const socket = new SockJS('/ws');
const client = Stomp.over(socket);
client.connect({}, () => {
    client.subscribe('/topic/games/' + gameId + '/round-ended', msg => {
        const round = JSON.parse(msg.body);
        showRoundEndAnimation(round.winner, round.p1Score, round.p2Score);
    });
    client.subscribe('/topic/games/' + gameId + '/match-ended', msg => {
        const match = JSON.parse(msg.body);
        showMatchEndScreen(match.winner);
    });
});
```

---

## 10. Konfiguracja Spring — przygotowanie `app/` module do REST integration

**Kontekst architektury:** sekcja dotyczy modułu `backend/app/` (gdzie żyje `LoxleyCardsApplication`, planowany dom dla REST controllerów w S-02). Moduł `backend/acommon-game-cli/` (z `LoxleyCliApplication`) zostaje bez zmian — to standalone CLI runner z osobnym lifecycle'em, nie miesza się z web mode.

### 10.1. application.properties

**Obecny stan `app/src/main/resources/application.properties`:**

```properties
spring.application.name=loxley-cards
```

**Dla S-02 (REST mode) — dodaj:**

```properties
# Web layer
server.port=8080

# Jackson
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.default-property-inclusion=non_null
```

(Spring Boot domyślnie wykrywa `spring-boot-starter-web` na classpath i automatycznie ustawia `web-application-type=servlet` — explicit deklaracja nie jest konieczna.)

### 10.2. CORS dla local dev

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowCredentials(true);
    }
}
```

Frontend domyślnie żyje na `:5173` (Vite dev server) lub `:3000` (gdy ktoś używa CRA-style setupu).

### 10.3. Profile management

Architektura jest już rozdzielona — CLI i web żyją w osobnych modułach z osobnymi `@SpringBootApplication`:

- `acommon-game-cli/LoxleyCliApplication` — default profile uruchamia bot evaluation + simulation; profile `cli-player` aktywuje `CliGameRunner` (interactive REPL).
- `app/LoxleyCardsApplication` — czyste Spring Boot bootstrap, dom dla REST controllerów. Nie ma `cli-player` runnera, nie ma `startupRunner` z eval — eval to dev-time CLI concern, nie web.

Innymi słowy: **nie trzeba mieszać profili między CLI a web** — moduły są osobne, każdy ma własny lifecycle. Jeśli chcesz w app/ jakiś warm-up bean dla web mode, zwykły `@Component implements CommandLineRunner` w app/ wystarczy (bez `@Profile`).

### 10.4. POM dependencies dla `app/`

Dorzuć do `backend/app/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
<dependency>
    <groupId>cards.loxley</groupId>
    <artifactId>loxley-cards-game-engine</artifactId>
</dependency>
```

Ostatnia dependency (engine) prawdopodobnie już jest w `app/pom.xml` od momentu setupu projektu; sprawdź zanim dodasz duplikat.

---

## 11. Co NIE jest publicznym API

Klasy które są technicznie `@Component` lub `public`, ale **nie wołaj ich z web layer** — to internal engine wiring.

### 11.1. Internal beans

- **`MoveExecutor`** — driven by `TurnOrchestrator`. Direct call bypassuje turn-order / round-end logic.
- **`RoundResolver`** — driven by `TurnOrchestrator`. Calling `resolveEndOfRound` bez `state.bothPlayersPassed()` rzuca.
- **`AbilityRegistry`** — used by `MoveExecutor` / `UseLeader` flow + recursive Medic chain.
- **`AbilityContext`** — record przekazywany do `AbilityEffect.apply`. Nie dla web.
- **Wszystkie `*Effect`** (`WeatherCloseEffect`, `ScorchEffect`, `MedicEffect`, etc.) — discovered by `AbilityRegistry`.
- **`DrawOnRoundWinListener`** — discovered by `MatchEventBus`.
- **`FactionPassiveRegistry`** — used by listener.
- **Cały `cards.loxley.cli.*`** — CLI-specific. `BoardRenderer`, `MoveParser`, `PlayerBoardIndex`, etc. Web layer ma własny rendering (frontend).
- **`GameDefinitionLoader` / `GameDefinitionValidator`** — runtime ruleset reloading nie jest supported.

### 11.2. Internal records

- **`FactionPassive`** — internal do listener'a.
- **`AbilityContext`** — internal do effects.
- **`AbilityCodes`** — string constants dla ability kodów. **Można** matchować po nich w UI (np. lookup ikon), ale nie *zapisywać* takich stringów. Match, don't write.

---

## 12. Quick reference — dependency injection cheatsheet

Co wstrzyknąć w typowym REST controllerze gry kampanijnej:

```java
public GameController(
    GameStateFactory factory,              // start gry
    CampaignStageRegistry stageRegistry,   // lookup etapu
    OpponentProfileRegistry profileRegistry, // profile po stage
    BotStrategyResolver botResolver,        // strategy po profile.strategyName()
    MoveGenerator generator,                // legal moves
    MoveValidator validator,                // dry-run (opcjonalnie)
    MoveDescriber describer,                // text opis (opcjonalnie)
    BoardScorer boardScorer,                // total per player
    CardScorer cardScorer,                  // per-card strength
    RowScorer rowScorer,                    // per-row strength
    TurnOrchestrator orchestrator,          // playTurn (jedyna mutacja)
    GameStateMapper mapper                  // Twój DTO mapper
) { ... }
```

Dla WebSocket listener:
```java
public WebSocketBroadcastListener(SimpMessagingTemplate broker) { ... }
```

To wszystko. Engine sam się ogarnie — w multi-module reactor pamiętaj o `-pl` flag: `cd backend && ./mvnw -pl app spring-boot:run` (gdy app/ ma już REST controllers + `spring-boot-starter-web` w pomie).

---

**Koniec dokumentu.** W razie wątpliwości — kod jest ostateczną prawdą. Każda sygnatura w tym dokumencie powinna kompilować się z `HEAD`. Jeśli coś nie pasuje — sprawdź odpowiedni `*Test.java` w `backend/acommon-game-engine/src/test/java/cards/loxley/game/` (engine API) lub `backend/acommon-game-cli/src/test/java/cards/loxley/cli/` (CLI surface) — testy są żywą dokumentacją API.