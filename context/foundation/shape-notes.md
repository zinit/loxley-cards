---
project: loxley-cards
context_type: greenfield
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  frs_drafted: 12
  quality_check_status: accepted
updated: 2026-05-24
---

# Shape Notes: loxley-cards

## Vision & Problem Statement

Webowa karcianka inspirowana mechaniką Gwinta z Witchera 3 — standalone, z prostym logowaniem (username + hasło, bez weryfikacji emaila), partia vs bot trwająca ~15 minut, z kampanią i progresją.

**Problem:** Gwint jest zablokowany wewnątrz Witchera 3 — żeby zagrać, trzeba odpalić ogromną grę RPG, co zajmuje kilkadziesiąt minut overheadu. Nie istnieje lekka, webowa wersja tej mechaniki (rzędy / pogoda / best-of-3). Alternatywne karcianki online nie oferują tych mechanik.

**Insight:** Niche jest za mały dla dużych studiów (CD Projekt nie zbuduje web-Gwinta dla 10 osób), ale wystarczający dla hobby-projektu. Możliwe, że coś podobnego istnieje — do zweryfikowania (open question).

## User & Persona

**Primary persona:** Pracujący dorosły, fan Gwinta z czasów W3. Ma 15–20 minut wolnego wieczorem (po pracy, w przerwie, w podróży). Chce szybkiej, taktycznej partii bez overheadu.

**Scope:** Najpierw ja sam (single user), z otwartymi drzwiami na paczkę znajomych (5–10 osób) i potencjalnie szerszą niszę w przyszłości.

## Access Control

**Auth method:** Username + hasło. Pierwsza wizyta = rejestracja (username + hasło), kolejne = login. Hasło hashowane BCryptem, sesja po zalogowaniu w JWT (cookie HTTPOnly). Bez OAuth, bez magic-linka, bez password reset (brak emaila = jak userek zapomni hasła, kontakt manualny i reset hash w DB).

**Role model:** Flat — wszyscy użytkownicy równi, jeden typ konta, brak podziału na role. Najprostszy model wystarczający dla MVP.

## Success Criteria

### Primary

Użytkownik otwiera URL, loguje się (username + hasło), wybiera etap kampanii, rozgrywa pełną partię vs bot (best-of-3, mechanika rzędów), widzi wynik i progres jest zapisany — następny etap odblokowany.

### Secondary

Użytkownik widzi swój własny postęp w kampanii (osobisty profil z listą ukończonych i odblokowanych etapów). Brak in-app porównywania wyników z innymi — pochwalenie się znajomym = pokazanie ekranu / screenshot.

### Guardrails

- Progres nie ginie — jeśli użytkownik wygrał etap, po powrocie następnego dnia jest odblokowany. Zero utraty danych między sesjami.

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

## User Stories

### US-01: Rozgrywka partii kampanii

**Given:** Gracz jest zalogowany i widzi ekran kampanii z odblokowanym etapem
**When:** Klika "Graj" przy etapie, rozgrywa karty w rundach (best-of-3), pasuje lub gra do końca każdej rundy
**Then:** Widzi wynik partii; jeśli wygrał — następny etap odblokowany; progres zapisany trwale

### US-02: Logowanie username + hasłem

**Given:** Gracz otwiera URL i nie jest zalogowany
**When:** Podaje username + hasło (rejestracja przy pierwszym wejściu, login przy kolejnych) i klika "Zagraj"
**Then:** Ląduje na ekranie kampanii ze swoim progresem; sesja trzymana w cookie/JWT

## Business Logic

Gra wymusza taktyczne zarządzanie ograniczoną ręką kart w 3 rundach (best-of-3), gdzie zwycięzca rundy to gracz z wyższą sumą siły w rzędach close/ranged/siege po zastosowaniu modyfikatorów z pogody / dowódcy / efektów abilities, a wygrana partii odblokowuje kolejny etap kampanii.

**Inputy reguły:** Karty zagrane przez gracza i bota w 3 rzędy (każda karta ma bazową siłę) + aktywne modyfikatory: pogoda (obniża siłę rzędu do 1), horn (podwaja siłę rzędu), leader ability (efekt jednorazowy na partię), efekty abilities kart (spy, medic, muster, tight bond itp. — pełny zestaw z Witchera 3).

**Output reguły:** Wynik rundy (wygrana/przegrana/remis na podstawie sumy siły) → wynik partii (best-of-3: 2 wygrane rundy = zwycięstwo) → progres kampanii (odblokowanie następnego etapu).

**Zestaw modyfikatorów na MVP:** Pełny zestaw abilities z Witchera 3 — pogoda, horn, spy, medic, muster, tight bond, leader abilities. Bot stosuje te same reguły co gracz.

## Non-Functional Requirements

- Szybkość bota: odpowiedź bota na ruch gracza < 2 sekundy. Bot jest deterministyczny (scoring-based), więc spełnienie jest trywialne.
- Prywatność: brak obowiązkowo zbieranych danych osobowych — login to username (cokolwiek user wpisze), hasło hashowane (BCrypt). Pole `email` w encji `User` jest opcjonalne (nullable; unique constraint zostaje, ale Postgres traktuje wiele wierszy z `NULL` jako distinct, więc anonymous userzy bez emaila współistnieją bez kolizji — decyzja F-03 plan-review 2026-06-13 dla H2 portability) — zachowane na przyszłość (potencjalny password reset / notyfikacje), nieużywane w MVP. Zero tracking, zero analityki, zero cookies marketingowych w MVP.
- Target platform: desktop/laptop (duży ekran). Mobile jest świadomym non-goal — 6 rzędów planszy + ręka kart + UI dowódcy/grave/deck wymaga dużego ekranu dla czytelności.

## Non-Goals

- **Brak multiplayer (PvP online)** — MVP to wyłącznie gra vs bot. Tryb gracz vs gracz to przyszłość, nie MVP.
- **Brak deck buildingu** — gracz nie składa własnego decku; każdy etap kampanii daje predefiniowany zestaw kart.
- **Brak mobile / responsive** — desktop-only. Plansza z 6 rzędami + ręka + UI wymaga dużego ekranu.
- **Brak rankingu / leaderboardu / porównywania wyników** — single-player kampania, bez social features.
- **Brak generowania kart przez AI** — talie predefiniowane w JSON, ręcznie zaprojektowane.
- **Brak wyboru dowódcy / frakcji** — w MVP: 1 leader, 1 frakcja. Wybór to v2.

## Forward: stretch-goals

- AI-coach analizujący ruchy gracza po partii — priorytet #1 po MVP, jeśli zostanie czas. Nie blokuje MVP.

## Product Framing (frontmatter)

- product_type: web-app
- target_scale.users: small (1–10 osób)
- timeline_budget.mvp_weeks: 3
- timeline_budget.hard_deadline: null
- timeline_budget.after_hours_only: true

## Quality cross-check

All 6 elements present. Status: accepted.
