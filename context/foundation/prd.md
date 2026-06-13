---
project: loxley-cards
version: 1
status: draft
created: 2026-05-24
context_type: greenfield
product_type: web-app
target_scale:
  users: small
  qps: low
  data_volume: small
timeline_budget:
  mvp_weeks: 3
  hard_deadline: null
  after_hours_only: true
---

# PRD: loxley-cards

## Vision & Problem Statement

Gwint jest zablokowany wewnątrz Witchera 3 — żeby zagrać, trzeba odpalić ogromną grę RPG, co zajmuje kilkadziesiąt minut overheadu. Nie istnieje lekka, webowa wersja tej mechaniki (rzędy / pogoda / best-of-3). Alternatywne karcianki online nie oferują tych mechanik. Pracujący dorośli z 15–20 minutami wolnego wieczorem nie mają jak zagrać szybkiej, taktycznej partii w klimacie Gwinta.

Niche jest za mały dla dużych studiów (CD Projekt nie zbuduje web-Gwinta dla 10 osób), ale wystarczający dla hobby-projektu. Loxley-cards to standalone webowa karcianka inspirowana mechaniką Gwinta — z prostym logowaniem (username + hasło, bez weryfikacji emaila), partia vs bot trwająca ~15 minut, z kampanią i progresją.

## User & Persona

**Primary persona:** Pracujący dorosły, fan Gwinta z czasów Witchera 3. Ma 15–20 minut wolnego wieczorem (po pracy, w przerwie, w podróży). Chce szybkiej, taktycznej partii bez overheadu.

**Scope:** Najpierw jeden użytkownik (autor), z otwartymi drzwiami na paczkę znajomych (5–10 osób) i potencjalnie szerszą niszę w przyszłości.

## Success Criteria

### Primary

Użytkownik otwiera URL, loguje się (username + hasło), wybiera etap kampanii, rozgrywa pełną partię vs bot (best-of-3, mechanika rzędów), widzi wynik i progres jest zapisany — następny etap odblokowany.

### Secondary

Użytkownik widzi swój własny postęp w kampanii (osobisty profil z listą ukończonych i odblokowanych etapów). Brak in-app porównywania wyników z innymi — pochwalenie się znajomym = pokazanie ekranu / screenshot.

### Guardrails

- Progres nie ginie — jeśli użytkownik wygrał etap, po powrocie następnego dnia jest odblokowany. Zero utraty danych między sesjami.

## User Stories

### US-01: Rozgrywka partii kampanii

- **Given** gracz jest zalogowany i widzi ekran kampanii z odblokowanym etapem
- **When** klika "Graj" przy etapie, rozgrywa karty w rundach (best-of-3), pasuje lub gra do końca każdej rundy
- **Then** widzi wynik partii; jeśli wygrał — następny etap odblokowany; progres zapisany trwale

### US-02: Logowanie username + hasłem

- **Given** gracz otwiera URL i nie jest zalogowany
- **When** podaje username + hasło (rejestracja przy pierwszym wejściu, login przy kolejnych) i klika "Zagraj"
- **Then** ląduje na ekranie kampanii ze swoim progresem; sesja trzymana w cookie/JWT

## Functional Requirements

- FR-001: Gracz może założyć konto (username + hasło) i zalogować się (username + hasło). Priority: must-have
  > Socrates: Świadoma decyzja w F-03 setup — magic-link wymagałby Resend + verified domeny (Daniel ma Resend pod inną domeną na free planie). Username + hasło = zero zależności od emaila, kosztem braku password reset (akceptowalne dla 5–10 znajomych; manual cleanup w DB jak ktoś zapomni hasła).
- FR-002: Gracz może przeglądać kampanię (10 etapów, liniowe odblokowanie). Priority: must-have
  > Socrates: Brak kontr-argumentu; 10 liniowych etapów wystarczy na MVP.
- FR-003: Gracz może rozpocząć partię na odblokowanym etapie. Priority: must-have
  > Socrates: Brak kontr-argumentu; predefiniowany deck per etap OK na MVP, deck building to v2.
- FR-004: Gracz może zagrać kartę z ręki do właściwego rzędu (auto-placement lub wybór rzędu). Priority: must-have
  > Socrates: Brak kontr-argumentu; w oryginale taktyka wynika z KIEDY grać, nie GDZIE.
- FR-005: Gracz może spasować (zakończyć swoją turę w rundzie). Priority: must-have
  > Socrates: Brak kontr-argumentu; pas to core mechanika Gwinta.
- FR-006: Gracz może aktywować zdolność dowódcy (leader ability) — raz na partię. Priority: must-have
  > Socrates: Brak kontr-argumentu; leader ability to kluczowy moment decyzyjny.
- FR-007: Gracz może wybrać rząd dla kart wymagających wyboru (np. horn). Priority: must-have
  > Socrates: Brak kontr-argumentu; kilka takich kart w decku wystarczy jako punkt taktyczny.
- FR-008: Gracz widzi wynik rundy i partii (best-of-3). Priority: must-have
  > Socrates: Brak kontr-argumentu; wynik rundy to feedback i closure.
- FR-009: Gracz może wrócić do kampanii po partii, progres zapisany (etap odblokowany). Priority: must-have
  > Socrates: Brak kontr-argumentu; proste odblokowanie = mniej frustracji na MVP.
- FR-010: Gracz może zrestartować przegrany etap. Priority: must-have
  > Socrates: Brak kontr-argumentu; single-player bez limitów restartów to norma.
- FR-011: Gracz może ponownie grać ukończone etapy. Priority: must-have
  > Socrates: Brak kontr-argumentu; replay daje swobodę i fun bez presji.
- FR-012: Gracz może się wylogować. Priority: must-have
  > Socrates: Brak kontr-argumentu; logout to higiena — musi być.

## Non-Functional Requirements

- Odpowiedź bota na ruch gracza poniżej 2 sekund. Aktualnie engine implementuje trzy strategie bota (random + heuristic-easy + heuristic-medium); przypisanie strategii do etapu kampanii (per `BotStrategyResolver`) będzie tuningowane podczas playtestu.
- Brak obowiązkowo zbieranych danych osobowych — login to username (cokolwiek user wpisze), hasło hashowane (BCrypt). Pole `email` w encji `User` jest **opcjonalne** (nullable; unique constraint zostaje, ale Postgres traktuje wiele wierszy z `NULL` jako distinct, więc anonymous userzy bez emaila współistnieją bez kolizji — decyzja F-03 plan-review 2026-06-13 dla H2 portability) — zachowane na przyszłość (potencjalny password reset / notyfikacje), nieużywane w MVP. Zero tracking, zero analityki, zero cookies marketingowych w MVP.
- Gra jest użyteczna na dużym ekranie (laptop/desktop). Plansza z 6 rzędami + ręka kart + UI dowódcy/grave/deck wymaga dużego ekranu dla czytelności.

## Business Logic

Gra wymusza taktyczne zarządzanie ograniczoną ręką kart w 3 rundach (best-of-3), gdzie zwycięzca rundy to gracz z wyższą sumą siły w rzędach close/ranged/siege po zastosowaniu modyfikatorów z pogody / dowódcy / efektów abilities, a wygrana partii odblokowuje kolejny etap kampanii.

**Inputy reguły:** Karty zagrane przez gracza i bota w 3 rzędy (każda karta ma bazową siłę) + aktywne modyfikatory: pogoda (3 warianty rzędów — close/ranged/siege; obniża siłę non-hero w rzędzie do 1), commander's horn (podwaja sumę rzędu), leader ability (efekt jednorazowy na partię), efekty abilities kart (spy, medic, tight bond, scorch, decoy, morale boost, clear weather, hero immunity).

**Output reguły:** Wynik rundy (wygrana/przegrana/remis na podstawie sumy siły) → wynik partii (best-of-3: 2 wygrane rundy = zwycięstwo) → progres kampanii (odblokowanie następnego etapu).

**Zestaw abilities w aktualnej implementacji engine'u:** weather (3 warianty: WEATHER_CLOSE, WEATHER_RANGED, WEATHER_SIEGE), commander's horn, spy, medic, tight bond, scorch, decoy, morale boost, clear weather, hero immunity, leader abilities (obecnie CLEAR_WEATHER). Modifier order per karta: hero immunity → weather → tight bond → morale boost → horn. Bot stosuje te same reguły co gracz.

> **Known gap vs PRD:** **muster** ability nie jest aktualnie zaimplementowana (brak `MUSTER` w `AbilityCodes`, brak `MusterEffect`, brak użycia w sample JSON). Engine jest poza tym zgodny z listą "pełen zestaw z Witchera 3"; muster może zostać dodany w follow-up change, jeśli okaże się krytyczny dla docelowego Gwint feel. Dodatkowo aktualna implementacja idzie nieco poza PRD przez **scorch**, **decoy** i **morale boost** — wszystkie autentyczne mechaniki Witcher 3 Gwint, włączone w ramach F-01 dla pełniejszego pokrycia oryginalnej mechaniki.

## Access Control

**Auth method:** Username + hasło. Pierwsza wizyta = rejestracja (username + hasło), kolejne = login. Hasło hashowane BCryptem, sesja po zalogowaniu w JWT (cookie HTTPOnly). Bez OAuth, bez magic-linka, bez password reset (brak emaila = jak userek zapomni hasła, kontakt manualny i reset hash w DB).

**Role model:** Flat — wszyscy użytkownicy równi, jeden typ konta, brak podziału na role.

## Non-Goals

- **Brak multiplayer (PvP online)** — MVP to wyłącznie gra vs bot. Tryb gracz vs gracz to przyszłość, nie MVP.
- **Brak deck buildingu** — gracz nie składa własnego decku; każdy etap kampanii daje predefiniowany zestaw kart.
- **Brak mobile / responsive** — desktop-only. Plansza z 6 rzędami + ręka + UI wymaga dużego ekranu.
- **Brak rankingu / leaderboardu / porównywania wyników** — single-player kampania, bez social features.
- **Brak generowania kart przez AI** — talie predefiniowane, ręcznie zaprojektowane.
- **Brak wyboru dowódcy / frakcji** — w MVP: 1 leader, 1 frakcja. Wybór to v2.

## Open Questions

1. **Czy istnieje już webowa gra z mechaniką Gwinta?** — Do zweryfikowania przez autora. Jeśli tak, jakie są jej braki? Block: no (nie blokuje implementacji, ale wpływa na positioning).
