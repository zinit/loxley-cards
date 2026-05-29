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

**S-03: Polished game UI** — gracz rozgrywa pełną partię best-of-3 vs prawdziwy bot w pięknym UI z S-01, podpiętym do REST API z S-02. Click-to-select kart, animacje, dynamiczne meta-tiles, end-of-round/match screens, lokalna persystencja unlocked stages. To walidacja głównej hipotezy produktu: czy silnik gry + UI grają razem jako produkt, nie jako prototyp.

> Gwiazda przewodnia — najmniejsza końcowa funkcjonalność, której dostarczenie udowadnia główną hipotezę produktu. Umieszczona w środku sekwencji (nie na początku), bo poprzedzające ją slice'y (S-01 statyczny UI z mock'iem, S-02 playable game API w prymitywnym debug UI) świadomie rozdzielają walidację wizualną od walidacji client-server — najpierw projektujemy UI z mock'iem, potem API z brzydkim frontem, S-03 łączy oba w spójną grę.

## At a glance

| ID   | Change ID                | Outcome (user can …)                                                              | Prerequisites      | PRD refs                          | Status   |
|------|--------------------------|-----------------------------------------------------------------------------------|--------------------|-----------------------------------|----------|
| F-01 | game-engine-core         | (foundation) silnik gry gotowy — reguły, scoring, bot, pełny zestaw abilities      | —                  | Business Logic, NFR (bot < 2s)    | done     |
| F-02 | data-persistence         | (foundation) warstwa persystencji — encje, migracje Flyway, połączenie z PostgreSQL | —                  | Guardrails, Access Control        | ready    |
| F-03 | magic-link-auth-scaffold | (foundation) auth magic-link — tokeny, walidacja, wysyłka emaila przez Resend     | F-02               | Access Control, NFR (prywatność)  | proposed |
| F-04 | frontend-scaffold        | (foundation) Vite + React + TS scaffold; routing; jedna strona "loxley-cards"    | —                  | (architectural enabler dla S-01)  | done     |
| S-01 | static-ui-prototype      | przejść przez 2 ekrany gry (kampania + plansza) z animowanym przejściem; klikalne ale bez logiki gameplay; menu/login/score odłożone do osobnych slice'ów | F-04               | US-01, NFR (duży ekran)    | done     |
| S-02 | playable-game-api        | rozegrać pełną partię vs bot przez REST API (lokalnie, w prymitywnym debug UI) — backend wystawia engine z F-01 jako endpointy `POST /games` / `GET /games/{id}` / `POST /games/{id}/moves` z autoreply bota; frontend dostaje API client + thin debug page do walidacji kontraktu | F-01               | US-01, FR-004–FR-008 (warstwa API + walidacja kontraktu) | done     |
| S-03 | polished-game-ui         | rozegrać pełną partię vs bot w pięknym UI z S-01 — click-to-select kart, animacje, dynamiczne meta-tiles, end-of-round/match screens, lokalna persystencja unlocked stages w `localStorage` | S-01, S-02         | US-01, FR-004–FR-008, FR-009–FR-011 | done     |
| S-04 | magic-link-login         | zalogować się magic linkiem i wylogować                                            | F-03               | US-02, FR-001, FR-012             | proposed |
| S-05 | campaign-progression     | przeglądać kampanię, grać etapy, widzieć zapisany progres                          | F-02, S-03, S-04   | US-01, FR-002, FR-003, FR-009–FR-011 | proposed |

## Streams

Grupowanie elementów po łańcuchu zależności — pomoc nawigacyjna. Kanoniczne uporządkowanie to graf zależności w sekcjach Foundations i Slices poniżej.

| Stream | Theme                       | Chain                                              | Note                                                                          |
|--------|------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------|
| A      | Frontend i rozgrywka         | `F-04` → `S-01` → `S-02` → `S-03` → `S-05`        | Gwiazda przewodnia jest w tym strumieniu (S-03); S-05 dołącza z B (wymaga S-04). |
| B      | Auth i persystencja          | `F-02` → `F-03` → `S-04`                          | Odblokowanie logowania; dołącza do A przy S-05.                              |
| C      | Silnik gry (atomic)          | `F-01`                                            | Samodzielny chunk pracy do równoległej egzekucji; dołącza do A przy S-02 (gdzie engine zostaje wystawiony przez REST). |

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

- **Outcome:** gracz otwiera grę w przeglądarce → CampaignMap (fullscreen bitmap Sherwood, fit-to-viewport, 10 stump waypoints z 3 visual states: active/locked/completed, Roman numerals I–X) → klik na active pieniek → animowany przelot do GameBoard (entrance: stump-in 600ms spring → content-in 250ms fade) → plansza Gwinta w układzie lustro (6 rzędów close/ranged/siege per strona, meta-strip symmetry z LEADER+score po lewej i hand+deck+grave po prawej dla obu stron, ręka 5 kart) na tle pieńka-blatu, z losowanymi kartami z 28-elementowego FINAL_DECK (Robin Hood theme: bohaterowie, jednostki, specjalne, leader). Klik "Back to map" → reverse animation → return. Świadome zawężenie scope z roadmap (5 ekranów → 2): menu, login, score-modal odłożone do osobnych slice'ów (S-04 dla login).
- **Change ID:** static-ui-prototype
- **PRD refs:** US-01 (ekran kampanii i ekran rozgrywki — to co user widzi), NFR (gra wymaga dużego ekranu — walidacja layoutu na desktop)
- **Prerequisites:** F-04 (scaffold)
- **Parallel with:** F-01, F-02, F-03
- **Blockers:** —
- **Unknowns:**
  - Skąd assety graficzne kart (rysować, generować, używać open assets)? — Zrealizowane: 28 prawdziwych obrazów Robin Hood theme (public-domain folklore) bundlowane w `finalDeck.ts`, mapa Sherwood + pieniek-blat + ikony rzędów bundlowane w `assets/`. Wszystkie skonwertowane do WebP (cwebp lossy q=78-90).
- **Risk:** **Świadome odstępstwo od vertical-first** — UI-first dla zarządzania ryzykiem wizualnym (frontend Gwinta to najmniej znany obszar dla solo dev) i czasowym (mniejsze wymierne kroki). Pułapka: scope creep w UI — łatwo dopolerowywać assety w nieskończoność. Definicja done: każdy ekran renderuje się i jest klikalny, bez wymogu finalnej oprawy graficznej (placeholdery OK).
- **Status:** done

### S-02: Playable game API (vertical slice)

- **Outcome:** backend wystawia 3 REST endpointy: `POST /games` (utwórz nową grę z deck'iem + zwróć initial GameState + legal moves), `GET /games/{id}` (pełen state + legal moves), `POST /games/{id}/moves` (waliduj + wykonaj ruch gracza + autoreply bota + zwróć new state + new legal moves). In-memory `GameSessionStore` (HashMap + UUID, ginie na restart — akceptowalne dla MVP). DTO serializacja `GameState` dla wire'a. Frontend dostaje API client (`fetch` wrappery w `src/api/`) i **prymitywny debug page** `/debug-game` — lista legalnych ruchów jako klikalne przyciski, JSON pretty-printed dump state, button "new game". Nie ruszamy istniejącego ładnego UI z S-01. Wszystko lokalnie (`localhost:8080` + `localhost:5173`), bez deploymentu, bez auth. **Cel: udowodnić że client-server game flow działa end-to-end + zaprojektować kontrakt API zanim podpinamy ładne UI w S-03.**
- **Change ID:** playable-game-api
- **PRD refs:** US-01 (rozgrywka partii vs bot — pełna integracja przez REST), FR-004 (zagranie karty — przez API), FR-005 (pas — przez API), FR-006 (leader — przez API), FR-007 (wybór rzędu dla horn — przez API), FR-008 (wynik rundy/partii — w odpowiedzi API), NFR (bot < 2s — synchroniczna odpowiedź)
- **Prerequisites:** F-01 (silnik — to on dostarcza `GameState`, generowanie legalnych ruchów, bot, scoring; engine-integration-guide.md jest właśnie dla tego slice'a)
- **Parallel with:** F-02, F-03, S-04
- **Blockers:** —
- **Unknowns:**
  - Kontrakt DTO — czy serializować `GameState` 1:1 (najprostsze, ale leak'uje internal model do wire'a), czy zaprojektować view-model dla frontu (bezpieczniejsze, więcej kodu)? — Owner: autor. Block: no (decyzja w `/10x-plan playable-game-api`).
  - Bot autoreply — w tym samym request (state response po ruchu gracza zawiera już state po ruchu bota) czy osobny request (dwa round-tripy)? Synchroniczna pętla w jednym requeście jest prostsza i pasuje do NFR bot < 2s; rozdzielenie ułatwia ewentualną animację "bot myśli" w S-03. — Owner: autor. Block: no.
  - Frontend `/debug-game` route — pod jakim URL'em żeby nie kolidowało z istniejącymi `/` (CampaignMap) i `/game/:stageId` (GameBoard)? — Owner: autor. Block: no.
- **Risk:** Najważniejsza decyzja architekturalna projektu (kontrakt API client-server) — jeśli kontrakt jest źle zaprojektowany, S-03 będzie wymagało refactoru. Mitygacja: debug page właśnie po to istnieje — iterujemy kontrakt z brzydkim UI taniej niż z pięknym. Drugi risk: in-memory store ginie na restart backendu — akceptowalne dla lokalnego MVP, persystencja w F-02 (osobny change). Trzeci risk: brak autoryzacji — każdy może POST na czyjąś grę po znajomości UUID; akceptowalne lokalnie, auth dochodzi w F-03/S-04.
- **Status:** done

### S-03: Polished game UI — NORTH STAR

- **Outcome:** istniejące piękne UI z S-01 dostaje życie — `CampaignMap` i `GameBoard.tsx` przepisane z mock state na `useGameState()` hook gadający z API z S-02 (`POST /games`, `GET /games/{id}`, `POST /games/{id}/moves`). Click-to-select karty w ręce → highlight legalnych rzędów (z `legalMoves` w state response) → klik rzędu → POST move → animacja karty lecącej w docelowe miejsce → re-render z nowym state (zawierającym już bot reply). Pas, Use leader, popup wyboru rzędu dla horn. Dynamiczne meta-tiles (score per row × 2 strony, total, weather, leader available/used, hand/deck/grave count, round indicator). End-of-round overlay + end-of-match screen + restart + Back to map. Lokalna persystencja unlocked stages w `localStorage` (per-user persistence przychodzi w S-05 z Supabase). Debug page z S-02 usuwamy. Bot reaguje < 2s (NFR walidacja empiryczna).
- **Change ID:** polished-game-ui
- **PRD refs:** US-01 (pełna rozgrywka partii vs bot — produkt, nie prototyp), FR-004 (zagranie karty), FR-005 (pas), FR-006 (leader), FR-007 (wybór rzędu), FR-008 (wynik), FR-009 (powrót do kampanii, progres lokalny), FR-010 (restart przegranego), FR-011 (replay ukończonych), NFR (bot < 2s — empiryczne potwierdzenie)
- **Prerequisites:** S-01 (piękne UI fundament), S-02 (API kontrakt)
- **Parallel with:** F-03, S-04
- **Blockers:** —
- **Unknowns:**
  - Czy animacja karty lecącej z ręki do rzędu musi czekać na response z API (pessimistic UI: klik → spinner → response → animacja) czy gramy optimistic UI (klik → animacja od razu → API w tle → rollback jeśli error)? — Owner: autor. Block: no (decyzja w `/10x-plan polished-game-ui`).
  - Walidacja czy `legalMoves` z API wystarczy do podświetlania legalnych celów, czy potrzeba dodatkowej logiki UI (np. shake animation na illegal click)? — Owner: autor. Block: no.
- **Risk:** Integracja UI ↔ API — najbardziej widoczny dla użytkownika moment. Mock state z S-01 (flat shape) ≠ DTO z S-02 (engine domain) — adapter w state hook konieczny. To jest gwiazda przewodnia: jeśli ta partia nie jest grywalna jako produkt, reszta roadmapy traci sens. Walidacja: zagrać 5–10 pełnych partii ręcznie, sprawdzić że abilities działają zgodnie z PRD §Business Logic, że UI jest responsywne (bot < 2s), że animacje są spójne.
- **Status:** done

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
| S-02       | playable-game-api        | Playable game API — REST endpoints + in-memory store + thin debug page | done                  | Archived 2026-05-29                          |
| S-03       | polished-game-ui         | Polished game UI (north star) — connect S-01 to S-02 API + click-to-select + animations + meta-tiles | done                  | Archived 2026-05-29                          |
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
- **S-01: gracz otwiera grę w przeglądarce → CampaignMap (fullscreen bitmap Sherwood, fit-to-viewport, 10 stump waypoints z 3 visual states + Roman numerals) → klik na active pieniek → animowany przelot do GameBoard (entrance: stump-in 600ms spring → content-in 250ms fade @ t=700ms, reverse na exit) → plansza Gwinta lustro (6 rzędów close/ranged/siege per strona, meta-strip symmetry left/right, ręka 5 kart) na tle pieńka-blatu z losowanymi kartami z 28-elementowego FINAL_DECK (Robin Hood theme — bohaterowie, jednostki, specjalne, leader, runtime randomization 15 unique per page-load). WebP asset pipeline (cwebp lossy q=78-90): główne assety 23MB → 4.6MB, karty 75MB → 3.9MB. Plus iteracje visual polish: animation sequencing, meta-strip symmetry refactor, row height tuning, label visibility z text-shadow outline, power circle font (Cinzel → system sans + lining-nums), row icon glow (4-warstwowy filter), back button miniaturization. Świadome zawężenie scope z roadmap S-01 spec (5 ekranów → 2): menu, login, score-modal odłożone do osobnych slice'ów. Plan + brief + reviews zsynchronizowane do shipped reality PRZED archive.** — Archived 2026-05-28 → `context/archive/2026-05-28-s-01-static-ui-prototype/`. Lesson: sync docs PRZED `/10x-impl-review` (zamiast po) daje cleaner review signal — skill znalazł 3 realne issues w kodzie (timeout cleanup, plan contain↔cover swap, dead cards.ts) zamiast 20 ogromnych planning-vs-port driftów. F-01 pattern "docs must match shipped code before archive" staje się standard project workflow.
- **S-02: backend wystawia 3 REST endpointy (`POST /api/games`, `GET /api/games/{id}`, `POST /api/games/{id}/moves`) z view-model DTOs hiding opponent hand (anti-cheat), sync bot autoreply w tej samej response (driveBotMoves loop wywoływany z create + moves bo engine losuje startera ~50/50), inline legal moves jako structured objects z UPPER kind values (PASS/LEADER/UNIT/SPY/SPECIAL/ROW/UNIT_TARGET), per-game synchronized lock na wszystkich 3 endpointach (włącznie z getState — F1 critical fix z impl-review zapobiegający torn read podczas bot loop), in-memory `GameSessionStore` z 30-min cleanup, custom `GameNotFoundException` → 404 + `GameStateException` wrapper → 409 (żeby nie maskować Spring MVC's IllegalStateException), bot loop z safety counter (>200 iter → exception zamiast infinite loop), CORS dla localhost:5173 (+3000 jako defensive add). Frontend: Vite proxy `/api` → :8080, fetch wrappery w `src/api/gameApi.ts` z `parseError()` fallback do `res.statusText`, TypeScript types w `src/api/types.ts` mirror Java records, `/debug-game` route (text-only lists nazw + power numbers + collapsible JSON dump — twardy anti-drift żeby nie zostać miniaturką S-03). 238 testów green (8 nowych MockMvc integration tests, włącznie z `opponentHandHidden` anti-cheat check). S-01 routes (`/`, `/game/:stageId`) NIETKNIĘTE.** — Archived 2026-05-29 → `context/archive/2026-05-29-s-02-playable-game-api/`. Lesson: `/10x-plan-review` + `/10x-impl-review` każdy znalazł realne issues (7 fixów pre-code + 4 fixów post-code), włącznie z critical race condition w getState (torn read podczas bot loop) — workflow z lekcji broni przed shipnięciem bugów na produkcję. Plus: gdy używamy parallel Claude session do implementacji, główna sesja powinna sanity-checkować pliki po każdym phase (np. weryfikacja czy fixes faktycznie wylądowały w kodzie) — `/10x-impl-review` skill marked F5 jako "Skipped" mimo że było intencją do fixu, więc trust-but-verify.
- **S-03: piękne UI z S-01 dostaje życie — `CampaignMap` + `GameBoard.tsx` przepisane z mock state na `useReducer` + `GameContext` jako state machine (`idle → loading → selecting-target → waiting-for-bot → round-ended → match-ended`), click-to-select karta w hand → filter `legalMoves` po `handInstanceId` → highlight valid targets per move kind (UNIT na player rows, SPY na opponent rows, ROW dla horn/weather, UNIT_TARGET na own units dla decoy), pessimistic UI (wait for API response zgodnie z zasadą "frontend głupi/bezstanowy"), 600ms bot-thinking pause z "Bot is thinking..." indicator. Hand-built 28-entry `cardImageMap.ts` (F1 critical plan-review fix: backend English snake_case `"little_john"` ≠ frontend Polish kebab-case `"maly-john"` — zero overlap, bez tego wszystkie karty byłyby placeholderami). `Card.tsx` zaadaptowany do API shape (`cardId`/`currentStrength`/`basePower`) — F2 plan-review fix przesunięty z Phase 6 do Phase 2 żeby Phase 4 power color-coding miał na czym pracować. Visual feedback: weather row blue tint + ❄ icon, horn golden glow + 🎺 icon, power color-coding (red dla weather-weakened, green dla boosted by horn/morale/tight-bond, cream dla hero immunity). `RoundOverlay` (3s auto-dismiss banner) + `MatchEndScreen` (VICTORY/DEFEAT + R1/R2/R3 summary + Back to Campaign / Play Again). `CampaignMap` czyta `getHighestUnlocked()` z localStorage z try/catch defensive (F2 impl-review fix dla Safari private / quota / disabled storage). Component extraction w Phase 6: `BoardRow` (6×), `MetaPanel` (2×), `PlayerHand` — `GameBoard.tsx` 627 → 344 linii. `DebugGame.tsx` i `mockGameState.ts` usunięte. 5 impl-review fixes: `botTimerRef` cleanup na unmount (F1), localStorage try/catch (F2), `submittingRef` synchronous guard against double-submit (F3 — walidacja empirycznej obserwacji "2 karty zamiast 1"), `handleBack` timer cleanup (F4), MetaPanel `_leaderUsed` unused prop remove (F5). 72 modules build, zero TS errors.** — Archived 2026-05-29 → `context/archive/2026-05-29-s-03-polished-game-ui/`. Lesson: świadomy decyzyjny separator między baseline functionality a polish — wszystkie observed UX/gameplay nuances (opponent-passed indicator missing, weather/horn empirical validation, horn doubling bug 5+10+horn → 5 vanished, emoji vs SVG icons, match-end overlay uwagi) odłożone do osobnego dedicated polish change zamiast bugfix sprintu w środku north-star slice'a — trzyma scope ciasny i waliduje baseline produktu najpierw. Plus: `/10x-plan-review` znowu dowiódł wartości — F1 (card ID mapping) złapany przed kodem oszczędził shipowania frontend bez kart; reviewer poszedł i sprawdził pliki JSON żeby zweryfikować plan's assumption "IDs match".
