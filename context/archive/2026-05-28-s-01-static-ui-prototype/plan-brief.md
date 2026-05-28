# Static UI Prototype — Plan Brief

> Full plan: `context/changes/s-01-static-ui-prototype/plan.md`
>
> **Post-implementation sync (2026-05-28)**: Brief został zsynchronizowany do shipped reality po manual implementation iteracjach (lesson F-01: docs muszą matchować shipped code przed archive).

## What & Why

Zbudować dwa statyczne ekrany gry karcianej: mapę kampanii (fullscreen bitmap Sherwood Forest z waypointami i fit-to-viewport scaling) oraz planszę rozgrywki (6 rzędów w layoucie lustro z kartami z 28-elementowego decka z prawdziwymi obrazami, panelami symetrycznymi i ręką, na tle ilustracji przekrojonego pieńka, z entrance/exit animacją). Walidacja wizualna i UX-owa frontendu — zanim pojawi się logika (S-02) i backend (S-03).

## Starting Point

Frontend scaffold (F-04) gotowy: React 19, Vite 8, TS 6, Tailwind v3, React Router v7. Jedyny komponent to `HomePage.tsx` z napisem "loxley-cards". Foldery `components/` i `assets/` puste. Engine (F-01) definiuje pełny model domeny — mock data odwzoruje jego kształt.

## Desired End State

Gracz otwiera `/` i widzi pełnoekranową mapę Sherwood z 10 pieńkami (etapy 1-3 active, 4-10 locked). Klik na active pieniek → animowane przejście do `/game/:stageId`: najpierw pieniek-blat scaluje się od 0.05 do 1.0 z bouncy spring (600ms), POTEM plansza fade-in nad pieńkiem (250ms). Plansza Gwinta z 6 rzędami w układzie lustro, panelami meta symetrycznymi (LEADER+score po lewej, hand-info+deck+grave po prawej dla obu stron), ręką 5 kart na dole. Karty z 28-elementowego `FINAL_DECK` (Robin Hood theme, prawdziwe obrazy) losowane przy każdym page-load. Klik "Back to map" → reverse animation → return.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| Scope ekranów | Tylko CampaignMap + GameBoard | Login (S-04), menu, score modal — osobne change'e | Plan |
| CampaignMap layout | Fullscreen bitmap + tree stump waypointy + fit-to-viewport | Wizualnie game-like, statyczne pozycjonowanie bez user pan/zoom | Plan + Implementation |
| GameBoard layout | Monolityczny komponent (NIE 5 split) z entrance animation | Port z 1 wielkiego komponentu jest prostszy w jednorazowej implementacji; refactor na podkomponenty kiedy pojawi się stan w S-02 | Implementation |
| Meta-strip symmetry | 2 wewnętrzne grupy left/right per side | LEADER+score po lewej, hand+deck+grave po prawej — identyczne dla obu stron | Implementation |
| Card assets | 28 prawdziwych obrazów w `finalDeck.ts` (Robin Hood theme) | Visual realism > abstract divs, theme legally clean (public-domain folklore) | Implementation |
| Card randomization | Random 15 unique cards per page-load (Math.random sort) | Pokazuje różnorodność deck bez backend | Implementation |
| Asset format | WebP (cwebp lossy q=78-90, alpha preserved) | ~80-95% size reduction vs PNG/JPG | Implementation |
| Animation | Bouncy spring (cubic-bezier 0.34, 1.56, 0.64, 1) + sequenced (stump → content) | Lepszy visual hierarchy niż parallel fade | Implementation |
| Power overlay font | System sans-serif + `font-variant-numeric: lining-nums tabular-nums` | Cinzel/Georgia miały inkonsystentne metrics cyfr | Implementation |
| Row icon glow | 4-warstwowy filter (2× white outline + warm halo + dark drop) | Czysta ikona widoczna na każdym tle bez ciężkiego okrągłego bg | Implementation |
| Mock data | Hardcoded flat shape (NIE znormalizowany jak engine domain) | Prostszy dla UI rendering; S-03 zrobi adapter do engine DTO | Implementation |
| Row modifiers | Czyste rzędy (bez weather/horn) | Pogoda/horn dojdzie w S-02, mniej pracy teraz | Plan |
| Graphic assets | Pre-existing (Daniel dostarcza), bundlowane przez Vite | Gotowe, bez blokera implementacji | Plan |

## Scope

**In scope:**
- TS typy (uproszczony flat shape vs engine domain — adapter w S-03)
- Statyczne mock dane (kampania + game state z runtime randomization)
- 28-kartowy deck z prawdziwymi obrazami (`finalDeck.ts`)
- CampaignMap: bitmap background, fit-to-viewport, 10 stump waypointów (3 active + 7 locked)
- GameBoard: monolityczny komponent z entrance/exit animation, layout lustro, meta-strip symmetry, 1 generic Card komponent
- Asset pipeline: WebP conversion via cwebp (~80-95% size reduction)
- Visual polish: row labels visibility, power circle font, row icon glow, back button refit
- Routing: `/` → CampaignMap, `/game/:stageId` → GameBoard
- Usunięcie HomePage (zastąpiony CampaignMap)

**Out of scope:**
- Login / auth (S-04)
- Drag & drop / interakcje gry (S-02)
- Weather / horn indicators (S-02)
- Backend communication (S-03)
- Campaign lock/unlock (S-05)
- State management (S-02)
- Responsive / mobile

## Architecture / Approach

Bottom-up: typy + mock data + finalDeck → CampaignMap (bitmap + fit-to-viewport hook + StumpMarker komponent) → GameBoard (monolithic komponent + 1 generic Card + entrance animation). Strony w `src/pages/`, komponenty w `src/components/{campaign,game}/`, mock data + finalDeck w `src/data/`, hook w `src/hooks/`. Bez `src/types/` (typy inline w data/). Zero state management — wszystko statyczne, importowane bezpośrednio. Tailwind tylko dla fullscreen layoutów; plansza i karty używają plain custom CSS classes.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Mock Data & Card Deck | TS interfejsy + 28-card FINAL_DECK z obrazami + randomized mock state | Kształt typów uproszczony vs engine — adapter potrzebny w S-03 |
| 2. CampaignMap | Fullscreen bitmap mapa z fit-to-viewport + 10 waypointów (active/locked states) | ResizeObserver compatibility (standardowo OK w nowoczesnych przeglądarkach) |
| 3. GameBoard | Monolityczna plansza lustro z entrance animation + meta symmetry + losowane karty | Layout fit na 1920×1080 z 6 rzędami + ręką + pieńkiem-blatem; row height tuning |
| Visual Polish | Post-phase iteracje (WebP, animacje, fonts, glow effects) | Scope creep — discipline na "MVP wygląd OK", nie na perfect oprawę |

**Prerequisites:** F-04 scaffold done, assety graficzne gotowe (sherwood-map-4k, stump, table-stump, row icons, 28 card images)
**Estimated effort:** ~3-4 sesje (3 fazy + polish iterations)

## Open Risks & Assumptions

- Bitmap mapy 4K (sherwood-map-4k.webp 1.5MB po kompresji) — initial load CampaignMap może mieć krótkie opóźnienie. Akceptowalne dla statycznego prototypu.
- Pieniek-blat (`table-stump.webp` 1.2MB) ładowany w trakcie entrance animation pierwszego wejścia na `/game/*`. Po cache nie widać.
- Mock game state = 1 randomized snapshot per refresh. Jeśli mało do walidacji UI, można dodać kontrolę nad konkretnymi scenariuszami (np. ?seed=X URL param).
- Flat MockGameState shape NIE jest 1:1 z engine domain — adapter trzeba będzie napisać w S-03 (REST + DTO mapping).
- WebP browser support: 100% nowoczesne (Chrome/Firefox/Safari 14+/Edge). PRD mówi desktop-only — OK.

## Success Criteria (Summary)

- `npm run dev` → `/` pokazuje mapę Sherwood z 10 pieńkami (1-3 active, 4-10 locked)
- Klik na active pieniek → animowany przelot (spring stump-in 600ms → content fade-in 250ms) do `/game/:stageId` z planszą + losowanymi kartami
- Każde odświeżenie strony game → inne losowe karty (15 unique z FINAL_DECK)
- Klik "Back to map" → reverse animation → return na `/`
- `npm run build` przechodzi bez błędów, `dist/` ~8MB (vs ~70MB bez WebP)
