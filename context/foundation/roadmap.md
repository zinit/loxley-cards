---
project: loxley-cards
version: 2
status: draft
created: 2026-05-25
updated: 2026-05-25
prd_version: 1
main_goal: speed
top_blocker: time
---

# Roadmap: loxley-cards

> Derived from `context/foundation/prd.md` (v1) + auto-researched codebase baseline.
> Edit-in-place; archive when superseded.
> Slices below are listed in dependency order. The "At a glance" table is the index.

## Vision recap

Gwint jest zablokowany wewnątrz Witchera 3 — nie istnieje lekka, webowa wersja tej mechaniki (rzędy, pogoda, best-of-3). Loxley-cards to standalone webowa karcianka inspirowana Gwintem: logowanie magic linkiem, partia vs bot (~15 min), kampania z progresją. Grupa docelowa: 1–10 osób, hobby-projekt budowany w 3 tygodnie wieczorami.

## North star

**S-03: Grywalna partia vs bot (z silnikiem)** — gracz rozgrywa pełną partię best-of-3 vs prawdziwy bot z backendowym engine, scoringiem i pełnym zestawem abilities, widzi wynik. To walidacja głównej hipotezy produktu: czy silnik gry działa end-to-end i czy partia jest grywalna jako produkt, nie jako mockup.

> Gwiazda przewodnia — najmniejsza końcowa funkcjonalność, której dostarczenie udowadnia główną hipotezę produktu. Umieszczona w środku sekwencji (nie na początku), bo poprzedzające ją slice'y (S-01 statyczny UI, S-02 interaktywny lokalny prototyp) świadomie wyprzedzają walidację produktową **walidacją wizualną i UX-ową** — patrz Risk przy S-01.

## At a glance

| ID   | Change ID                | Outcome (user can …)                                                              | Prerequisites      | PRD refs                          | Status   |
|------|--------------------------|-----------------------------------------------------------------------------------|--------------------|-----------------------------------|----------|
| F-01 | game-engine-core         | (foundation) silnik gry gotowy — reguły, scoring, bot, pełny zestaw abilities      | —                  | Business Logic, NFR (bot < 2s)    | done     |
| F-02 | data-persistence         | (foundation) warstwa persystencji — encje, migracje Flyway, połączenie z PostgreSQL | —                  | Guardrails, Access Control        | ready    |
| F-03 | magic-link-auth-scaffold | (foundation) auth magic-link — tokeny, walidacja, wysyłka emaila przez Resend     | F-02               | Access Control, NFR (prywatność)  | proposed |
| F-04 | frontend-scaffold        | (foundation) Vite + React + TS scaffold; routing; jedna strona "loxley-cards"    | —                  | (architectural enabler dla S-01)  | done     |
| S-01 | static-ui-prototype      | przejść przez wszystkie ekrany gry (menu, kampania, plansza, leader, score) i zobaczyć pełen wygląd z assetami; klikalne ale bez logiki | F-04               | US-01, US-02, NFR (duży ekran)    | proposed |
| S-02 | interactive-local-match  | rozegrać partię w przeglądarce — drag&drop, pas, leader, wybór rzędu — na lokalnym mockowym game state w JS | S-01               | FR-004–FR-008 (warstwa UX)        | proposed |
| S-03 | integrated-match-vs-bot  | rozegrać pełną partię vs prawdziwy bot z backendowym engine (REST API zastępuje mock) | F-01, S-02         | US-01, FR-004–FR-008              | proposed |
| S-04 | magic-link-login         | zalogować się magic linkiem i wylogować                                            | F-03               | US-02, FR-001, FR-012             | proposed |
| S-05 | campaign-progression     | przeglądać kampanię, grać etapy, widzieć zapisany progres                          | F-02, S-03, S-04   | US-01, FR-002, FR-003, FR-009–FR-011 | proposed |

## Streams

Grupowanie elementów po łańcuchu zależności — pomoc nawigacyjna. Kanoniczne uporządkowanie to graf zależności w sekcjach Foundations i Slices poniżej.

| Stream | Theme                       | Chain                                              | Note                                                                          |
|--------|------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------|
| A      | Frontend i rozgrywka         | `F-04` → `S-01` → `S-02` → `S-03` → `S-05`        | Gwiazda przewodnia jest w tym strumieniu (S-03); S-05 dołącza z B (wymaga S-04). |
| B      | Auth i persystencja          | `F-02` → `F-03` → `S-04`                          | Odblokowanie logowania; dołącza do A przy S-05.                              |
| C      | Silnik gry (atomic)          | `F-01`                                            | Samodzielny chunk pracy do równoległej egzekucji; dołącza do A przy S-03.    |

## Baseline

Stan codebase na 2026-05-25 (auto-researched + potwierdzone przez użytkownika). Foundations poniżej zakładają że te warstwy są na miejscu i NIE budują ich od zera.

- **Frontend:** absent — `frontend/` to pusty placeholder; brak frameworka, komponentów, routingu
- **Backend / API:** partial — Spring Boot 4.0.6 scaffold (multi-module Maven, `LoxleyCardsApplication.java`), zero kontrolerów i endpointów REST
- **Silnik gry:** absent — `acommon-game-engine/` to pusty stub; brak reguł, scoringu, bota, abilities
- **Data:** absent — `acommon-db/` pusty scaffold; brak encji JPA, repozytoriów, migracji Flyway, drivera PostgreSQL w POM
- **Auth:** absent — brak `spring-boot-starter-security`, brak implementacji magic-link, brak konfiguracji security
- **Deploy / infra:** absent — brak Dockerfile, docker-compose, CI/CD workflows, nginx config (plan udokumentowany w `infrastructure.md`)
- **Observability:** absent — brak Actuator, logback, error tracking, metryk

## Foundations

### F-01: Silnik gry

- **Outcome:** (foundation) silnik gry gotowy — reguły partii (best-of-3, rzędy close/ranged/siege), layered scoring per karta z modifier orderingiem (hero immunity → weather → tight bond → morale boost → horn), trzy strategie bota (`RandomBot` + `HeuristicEasyBot` + `HeuristicMediumBot`) przypisywane per etap kampanii przez `BotStrategyResolver`, JSON-driven sample card / deck / campaign data (`src/main/resources/{cards,data}/*.json`) ładowane przez Spring beany na starcie, abilities z Witcher 3 Gwint: weather (3 warianty rzędów), commander's horn, spy, medic, tight bond, scorch, decoy, morale boost, clear weather, hero immunity, leader abilities (obecnie CLEAR_WEATHER). Atomowy — wszystkie abilities wchodzą razem. Plus sibling module `acommon-game-cli` z `LoxleyCliApplication` (`@SpringBootApplication`) jako standalone CLI runner: tryb default robi bot evaluation (4 matchups × 50 games), profile evaluation (5 opponent profiles × 30 games) i seeded bot-vs-bot simulation; tryb `@Profile("cli-player")` to interactive REPL (player vs bot przez stdin) — daje end-to-end smoke i ręczne testowanie engine'u bez czekania na REST (S-03) lub frontend (S-01/S-02/S-03).
- **Change ID:** game-engine-core
- **PRD refs:** Business Logic (pełny opis reguł i modyfikatorów), NFR (bot < 2s)
- **Unlocks:** S-03 (integracja partii z silnikiem)
- **Prerequisites:** —
- **Parallel with:** F-02, F-04, S-01, S-02
- **Blockers:** —
- **Unknowns:** —
- **Known gap vs PRD:** **muster** ability nie zaimplementowana (zero pokrycia w kodzie, brak `MusterEffect`, brak użycia w sample JSON). Pozostałe abilities z PRD są zaimplementowane; engine dodatkowo wnosi 3 extras (scorch, decoy, morale boost) — wszystkie autentyczne Witcher 3 Gwint, włączone bez extra kosztów. Muster może zostać dodany w follow-up change jeśli okaże się krytyczny dla docelowego Gwint feel.
- **JSON sample data caveat:** aktywnie ładowane JSONy (`data/sherwood_reference_ruleset.json`, `data/decks/sherwood_*_deck.json`, `data/campaign_stages.json`) zawierają named Witcher 3 characters (Foltest, Vernon Roche, Sigismund Dijkstra, John Natalis, Ves, Stennis, Blue Stripes Commando) — CDPR IP. Akceptowalne dla wewnętrznego MVP demo i edukacyjnego publicznego repo (10xDevs showcase); przed properly public release JSON content powinien być re-themed (Java code nie ma w sobie żadnego W3 IP).
- **Risk:** Najgęstszy moduł w projekcie — pełny zestaw abilities musi być spójny i przetestowany razem. Opóźnienie tu opóźnia gwiazdę przewodnią (S-03). Atomowość świadoma: abilities są wzajemnie zależne (scoring rzędu uwzględnia naraz pogodę, horn, tight bond, spy itd.), bot wymaga kompletu reguł żeby cokolwiek sensownie wybrać, izolowane testowanie 1 ability bez reszty = absurd. Dlatego NIE rozbijamy planowania ani implementacji per-ability — wchodzi razem.
- **Status:** done

### F-02: Warstwa persystencji

- **Outcome:** (foundation) warstwa persystencji gotowa — encje JPA (User, CampaignProgress), repozytoria Spring Data, migracje Flyway, driver PostgreSQL w POM, konfiguracja HikariCP (max-pool-size=10 per infrastructure.md).
- **Change ID:** data-persistence
- **PRD refs:** Guardrails (progres nie ginie — zero utraty danych między sesjami), Access Control (user identyfikowany przez email)
- **Unlocks:** F-03 (auth potrzebuje encji User), S-05 (kampania potrzebuje persystencji progresu)
- **Prerequisites:** —
- **Parallel with:** F-01, F-04, S-01, S-02
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Ryzyko niskie; commodity warstwa. Jedyna pułapka: mismatch HikariCP pool vs Supabase pgbouncer — udokumentowane w infrastructure.md, mitygowane przez max-pool-size=10.
- **Status:** ready

### F-03: Auth magic-link

- **Outcome:** (foundation) auth magic-link gotowy — generowanie tokena JWT, walidacja tokena z linku, konfiguracja Spring Security (zabezpieczenie endpointów), integracja z Resend API do wysyłki emaili z magic linkiem.
- **Change ID:** magic-link-auth-scaffold
- **PRD refs:** Access Control (magic link na email, passwordless), NFR (jedyną daną osobową jest email — zero tracking)
- **Unlocks:** S-04 (logowanie magic linkiem)
- **Prerequisites:** F-02 (encja User i repozytorium)
- **Parallel with:** F-01, F-04, S-01, S-02
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Magic-link to niestandardowy flow auth — wymaga poprawnego łańcucha: generowanie tokena → zapis → wysyłka emaila → walidacja kliknięcia. Dostarczalność maili zależy od weryfikacji domeny w Resend (SPF/DKIM).
- **Status:** proposed

### F-04: Frontend scaffold

- **Outcome:** (foundation) Vite + React + TS scaffold istnieje; struktura projektu (folders, tsconfig, vite.config); routing (React Router lub Wouter — decyzja w `/10x-plan`); jedna strona "loxley-cards" pod `/` jako sanity-check; `npm run dev` startuje, `npm run build` produkuje statyczny output. Brak gameplay, brak assetów, brak komunikacji z backendem.
- **Change ID:** frontend-scaffold
- **PRD refs:** (architectural enabler — brak bezpośredniej FR; uzasadnienie: S-01 wymaga działającego frontendu)
- **Unlocks:** S-01 (statyczny prototyp UI potrzebuje działającego scaffoldu)
- **Prerequisites:** —
- **Parallel with:** F-01, F-02, F-03
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Niski; standardowy Vite scaffold dobrze udokumentowany. Wybór bibliotek (router, state management, drag&drop, styling) **odracza się do S-01/S-02** — tutaj tylko goły szkielet. Pułapka: scope creep ("przy okazji dodam Tailwind", "przy okazji dodam shadcn") — definicja done: pusty projekt z `npm run dev` i jedną stroną.
- **Status:** done

## Slices

### S-01: Statyczny prototyp UI

- **Outcome:** gracz otwiera grę w przeglądarce i może przejść przez wszystkie ekrany aplikacji: menu główne, ekran logowania (statyczny formularz email — submit nic nie robi), ekran kampanii (mapa z 10 etapami, klikalne ale bez logiki odblokowywania), ekran rozgrywki (plansza 6 rzędów close/ranged/siege per gracz, ręka kart, leader, score panel, deck/grave). Wszystkie assety graficzne są na miejscu. Klikalne, ale bez logiki gameplay'u.
- **Change ID:** static-ui-prototype
- **PRD refs:** US-01 (ekran kampanii i ekran rozgrywki — to co user widzi), US-02 (ekran logowania), NFR (gra wymaga dużego ekranu — walidacja layoutu na desktop)
- **Prerequisites:** F-04 (scaffold)
- **Parallel with:** F-01, F-02, F-03
- **Blockers:** —
- **Unknowns:**
  - Skąd assety graficzne kart (rysować, generować, używać open assets)? — Owner: autor. Block: no (na razie placeholdery wystarczą; finalna grafika do dopolerowania później).
- **Risk:** **Świadome odstępstwo od vertical-first** — UI-first dla zarządzania ryzykiem wizualnym (frontend Gwinta to najmniej znany obszar dla solo dev) i czasowym (mniejsze wymierne kroki). Pułapka: scope creep w UI — łatwo dopolerowywać assety w nieskończoność. Definicja done: każdy ekran renderuje się i jest klikalny, bez wymogu finalnej oprawy graficznej (placeholdery OK).
- **Status:** proposed

### S-02: Interaktywny lokalny prototyp partii

- **Outcome:** gracz może rozegrać partię w przeglądarce na statycznej planszy z S-01 — drag&drop kart z ręki do rzędów, pasowanie, aktywacja leader ability, wybór rzędu dla kart wymagających wyboru (horn). Game state trzymany lokalnie w React (Context/Zustand), bot=mock w JS (np. random move lub bardzo prosta heurystyka). Bez komunikacji z backendem. Pokazuje wynik rundy i partii na bazie lokalnej (mock) logiki scoringu.
- **Change ID:** interactive-local-match
- **PRD refs:** FR-004 (zagranie karty do rzędu — warstwa UX), FR-005 (pas — UX), FR-006 (leader ability — UX), FR-007 (wybór rzędu — UX), FR-008 (wynik rundy/partii — wyświetlanie mock'owanego wyniku)
- **Prerequisites:** S-01 (statyczny UI)
- **Parallel with:** F-01, F-02, F-03, S-04
- **Blockers:** —
- **Unknowns:**
  - Kontrakt mockowanego game state — czy upodobnić go do docelowego REST DTO (zminimalizować przepisanie w S-03), czy zrobić "wygodny dla frontu" (szybciej teraz, więcej pracy później)? — Owner: autor. Block: no (decyzja w `/10x-plan interactive-local-match`).
- **Risk:** Mock'owanie game state w JS musi być na tyle wierne docelowemu kontraktowi z F-01, żeby przejście do S-03 nie wymagało przepisania store'a. Mitygacja: zanim zaprojektuje się mock state shape, zerknąć na engine API (DTO-y wystawiane przez F-01) i dopasować strukturę store'a do tego co backend zwróci.
- **Status:** proposed

### S-03: Grywalna partia vs bot (z silnikiem) — NORTH STAR

- **Outcome:** gracz rozgrywa prawdziwą partię vs prawdziwy bot — frontend wysyła ruchy do REST API (POST /games, POST /games/{id}/moves), backend wykonuje ruch gracza i ruch bota przez engine z F-01, zwraca nowy stan gry. Mock z S-02 zastąpiony prawdziwą integracją. Pełen zestaw abilities (pogoda, horn, spy, medic, muster, tight bond, leader) działa zgodnie z regułami silnika. Bot reaguje synchronicznie < 2s (NFR).
- **Change ID:** integrated-match-vs-bot
- **PRD refs:** US-01 (rozgrywka partii vs bot — pełna integracja), FR-004 (zagranie karty), FR-005 (pas), FR-006 (leader), FR-007 (wybór rzędu), FR-008 (wynik), NFR (bot < 2s)
- **Prerequisites:** F-01 (silnik), S-02 (UI i interakcja)
- **Parallel with:** F-03, S-04
- **Blockers:** —
- **Unknowns:**
  - Czy synchroniczny REST request-response wystarczy dla NFR `bot < 2s`, czy trzeba rozważyć asynchroniczność (np. polling po zleceniu ruchu)? — Owner: autor. Block: no (test empiryczny w trakcie implementacji rozstrzygnie; PvP jest w `Parked` więc websockety odpadają).
- **Risk:** Integracja — najbardziej ryzykowny moment w całej roadmapie. Mock z S-02 może nie pasować do prawdziwego engine API, mimo dopasowywania w S-02. To jest gwiazda przewodnia: jeśli ta partia nie jest grywalna jako produkt, reszta roadmapy traci sens. Walidacja: zagrać 5–10 pełnych partii ręcznie, sprawdzić że abilities działają zgodnie z PRD §Business Logic.
- **Status:** proposed

### S-04: Logowanie magic linkiem

- **Outcome:** gracz otwiera URL, podaje email, otrzymuje magic link na skrzynkę, klika link i wraca do appki zalogowany. Może się wylogować.
- **Change ID:** magic-link-login
- **PRD refs:** US-02 (logowanie magic linkiem), FR-001 (login magic linkiem), FR-012 (logout)
- **Prerequisites:** F-03 (auth scaffold)
- **Parallel with:** F-01, F-04, S-01, S-02, S-03
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Zależy od dostarczalności maili Resend. Jeśli magic link nie dociera do skrzynki (brak SPF/DKIM, spam filter), flow jest zablokowany dla użytkownika. Test: zweryfikować domenę `loxley.cards` w Resend (SPF/DKIM/DMARC) przed pierwszym uruchomieniem.
- **Status:** proposed

### S-05: Kampania i progresja

- **Outcome:** zalogowany gracz widzi ekran kampanii z 10 etapami (liniowe odblokowanie), może rozpocząć partię na odblokowanym etapie, wygrana odblokowuje następny etap, progres jest zapisany trwale (przeżywa restart przeglądarki i powrót następnego dnia), może zrestartować przegrany etap i ponownie grać ukończone etapy.
- **Change ID:** campaign-progression
- **PRD refs:** US-01 (progres zapisany trwale, następny etap odblokowany), FR-002 (przeglądanie kampanii — 10 etapów), FR-003 (rozpoczęcie partii na odblokowanym etapie), FR-009 (powrót do kampanii, progres zapisany), FR-010 (restart przegranego etapu), FR-011 (replay ukończonych etapów)
- **Prerequisites:** F-02 (persystencja), S-03 (grywalna partia), S-04 (logowanie)
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:**
  - Predefiniowane talie per etap — kto je projektuje (ręcznie autor, czy generator z F-01)? — Owner: autor. Block: no (decyzja w `/10x-plan campaign-progression`).
- **Risk:** Najbardziej zintegrowany slice — łączy silnik, auth, persystencję, frontend kampanii. Zależy od poprawności S-03 i S-04. Skala (10 etapów) jest mała — to nie skala, tylko integracja jest ryzykiem.
- **Status:** proposed

## Backlog Handoff

| Roadmap ID | Change ID                | Suggested issue title                                       | Ready for `/10x-plan` | Notes                                       |
|------------|--------------------------|-------------------------------------------------------------|-----------------------|---------------------------------------------|
| F-01       | game-engine-core         | Implement game engine: rules, scoring, bot, abilities       | yes                   | Run `/10x-plan game-engine-core`            |
| F-02       | data-persistence         | Set up JPA entities, Flyway migrations, PostgreSQL          | yes                   | Run `/10x-plan data-persistence`            |
| F-03       | magic-link-auth-scaffold | Implement magic-link auth with Resend                       | no                    | Needs F-02 done first                       |
| F-04       | frontend-scaffold        | Bootstrap Vite + React + TS scaffold (empty project)        | yes                   | Run `/10x-plan frontend-scaffold`           |
| S-01       | static-ui-prototype      | Static UI prototype — all screens, clickable, no logic      | no                    | Needs F-04 done first                       |
| S-02       | interactive-local-match  | Interactive local match — drag&drop, mock state, mock bot   | no                    | Needs S-01 done first                       |
| S-03       | integrated-match-vs-bot  | Integrated match vs bot (north star) — REST + engine + bot  | no                    | Needs F-01 + S-02 done first                |
| S-04       | magic-link-login         | Magic link login and logout flow                            | no                    | Needs F-03 done first                       |
| S-05       | campaign-progression     | Campaign screen with 10 stages and progress persistence     | no                    | Needs F-02 + S-03 + S-04 done first         |

## Open Roadmap Questions

1. **Czy istnieje już webowa gra z mechaniką Gwinta?** — Owner: autor. Block: — (nie blokuje żadnego slice'a, ale wpływa na positioning produktu). Źródło: PRD §Open Questions.

## Parked

- **Multiplayer (PvP online)** — Why parked: PRD §Non-Goals. MVP to wyłącznie gra vs bot. (Konsekwencja techniczna: w MVP nie potrzeba websocketów — REST request-response wystarczy dla bota synchronicznego.)
- **Deck building** — Why parked: PRD §Non-Goals. Predefiniowane talie per etap kampanii.
- **Mobile / responsive** — Why parked: PRD §Non-Goals. Desktop-only; 6 rzędów + ręka + UI wymaga dużego ekranu.
- **Ranking / leaderboard** — Why parked: PRD §Non-Goals. Single-player kampania, bez social features.
- **Generowanie kart przez AI** — Why parked: PRD §Non-Goals. Talie ręcznie zaprojektowane.
- **Wybór dowódcy / frakcji** — Why parked: PRD §Non-Goals. MVP: 1 leader, 1 frakcja.
- **AI-coach (analiza ruchów po partii)** — Why parked: shape-notes §Forward: stretch-goals. Priorytet po MVP, nie blokuje.
- **Deploy produkcyjny (Hetzner + Cloudflare Pages + Supabase + Resend)** — Why parked: świadomie odłożone przy `main_goal: speed`. Lokalna grywalna partia (S-03) waliduje produkt, deploy waliduje infrastrukturę — to dwie różne sprawy. Pełen plan w `context/deployment/deploy-plan.md` gotowy do egzekucji kiedy będzie potrzebny publiczny URL (znajomi, 10xBuilder cert).

## Done

- **F-04: (foundation) Vite + React + TS scaffold istnieje; struktura projektu (folders, tsconfig, vite.config); routing (React Router lub Wouter — decyzja w `/10x-plan`); jedna strona "loxley-cards" pod `/` jako sanity-check; `npm run dev` startuje, `npm run build` produkuje statyczny output. Brak gameplay, brak assetów, brak komunikacji z backendem.** — Archived 2026-05-25 → `context/archive/2026-05-25-f-04-frontend-scaffold/`. Lesson: —.
- **F-01: (foundation) silnik gry gotowy w `backend/acommon-game-engine/` — reguły partii (best-of-3, rzędy close/ranged/siege), layered scoring per karta z modifier orderingiem (hero immunity → weather → tight bond → morale boost → horn), trzy strategie bota (`RandomBot`, `HeuristicEasyBot`, `HeuristicMediumBot`) z `BotStrategyResolver`, JSON-driven sample card/deck/campaign data, abilities z Witcher 3 Gwint (weather × 3, commander's horn, spy, medic, tight bond, scorch, decoy, morale boost, clear weather, hero immunity, leader). 200 testów. Plus nowy sibling module `acommon-game-cli` (`LoxleyCliApplication`) — standalone CLI runner z bot evaluation, profile evaluation, bot-vs-bot simulation i interactive `cli-player` REPL (29 testów). Reactor: 6 modules, BUILD SUCCESS. Plus dev-facing `context/foundation/engine-integration-guide.md` — przewodnik dla S-03 (REST controllers + WebSocket): public API surface, sample REST controller (~70 linii compilujący się), Spring config preparation, DTO design proposal, edge cases. **Known gap vs PRD:** muster ability nie zaimplementowana — deferred jako follow-up change, jeśli okaże się krytyczna dla docelowego Gwint feel.** — Archived 2026-05-26 → `context/archive/2026-05-26-f-01-game-engine-core/`. Lesson: docs muszą być zsynchronizowane z faktycznym kodem (jeśli implementacja świadomie idzie poza ramy oryginalnego planu, zaktualizuj plan / brief / reviews / PRD / roadmap zanim zarchiwizujesz — archive jest read-only by convention).
