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

Niche jest za mały dla dużych studiów (CD Projekt nie zbuduje web-Gwinta dla 10 osób), ale wystarczający dla hobby-projektu. Loxley-cards to standalone webowa karcianka inspirowana mechaniką Gwinta — dostępna w 1 klik (magic link na email), partia vs bot trwająca ~15 minut, z kampanią i progresją.

## User & Persona

**Primary persona:** Pracujący dorosły, fan Gwinta z czasów Witchera 3. Ma 15–20 minut wolnego wieczorem (po pracy, w przerwie, w podróży). Chce szybkiej, taktycznej partii bez overheadu.

**Scope:** Najpierw jeden użytkownik (autor), z otwartymi drzwiami na paczkę znajomych (5–10 osób) i potencjalnie szerszą niszę w przyszłości.

## Success Criteria

### Primary

Użytkownik otwiera URL, loguje się magic linkiem, wybiera etap kampanii, rozgrywa pełną partię vs bot (best-of-3, mechanika rzędów), widzi wynik i progres jest zapisany — następny etap odblokowany.

### Secondary

Użytkownik widzi swój własny postęp w kampanii (osobisty profil z listą ukończonych i odblokowanych etapów). Brak in-app porównywania wyników z innymi — pochwalenie się znajomym = pokazanie ekranu / screenshot.

### Guardrails

- Progres nie ginie — jeśli użytkownik wygrał etap, po powrocie następnego dnia jest odblokowany. Zero utraty danych między sesjami.

## User Stories

### US-01: Rozgrywka partii kampanii

- **Given** gracz jest zalogowany i widzi ekran kampanii z odblokowanym etapem
- **When** klika "Graj" przy etapie, rozgrywa karty w rundach (best-of-3), pasuje lub gra do końca każdej rundy
- **Then** widzi wynik partii; jeśli wygrał — następny etap odblokowany; progres zapisany trwale

### US-02: Logowanie magic linkiem

- **Given** gracz otwiera URL i nie jest zalogowany
- **When** podaje email, klika "Zagraj", przechodzi do skrzynki i klika magic link
- **Then** wraca do appki zalogowany, ląduje na ekranie kampanii ze swoim progresem

## Functional Requirements

- FR-001: Gracz może zalogować się magic linkiem (podaje email, klika link ze skrzynki). Priority: must-have
  > Socrates: Brak kontr-argumentu; magic link OK dla tej skali i persony.
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

- Odpowiedź bota na ruch gracza poniżej 2 sekund. Bot jest deterministyczny (scoring-based).
- Jedyną daną osobową jest email do logowania. Zero tracking, zero analityki, zero cookies marketingowych w MVP.
- Gra jest użyteczna na dużym ekranie (laptop/desktop). Plansza z 6 rzędami + ręka kart + UI dowódcy/grave/deck wymaga dużego ekranu dla czytelności.

## Business Logic

Gra wymusza taktyczne zarządzanie ograniczoną ręką kart w 3 rundach (best-of-3), gdzie zwycięzca rundy to gracz z wyższą sumą siły w rzędach close/ranged/siege po zastosowaniu modyfikatorów z pogody / dowódcy / efektów abilities, a wygrana partii odblokowuje kolejny etap kampanii.

**Inputy reguły:** Karty zagrane przez gracza i bota w 3 rzędy (każda karta ma bazową siłę) + aktywne modyfikatory: pogoda (obniża siłę rzędu do 1), horn (podwaja siłę rzędu), leader ability (efekt jednorazowy na partię), efekty abilities kart (spy, medic, muster, tight bond — pełny zestaw z Witchera 3).

**Output reguły:** Wynik rundy (wygrana/przegrana/remis na podstawie sumy siły) → wynik partii (best-of-3: 2 wygrane rundy = zwycięstwo) → progres kampanii (odblokowanie następnego etapu).

**Zestaw modyfikatorów na MVP:** Pełny zestaw abilities z Witchera 3 — pogoda, horn, spy, medic, muster, tight bond, leader abilities. Bot stosuje te same reguły co gracz.

## Access Control

**Auth method:** Magic link na email (passwordless). Użytkownik podaje email, dostaje link, klik = zalogowany. Bez hasła, bez OAuth.

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
