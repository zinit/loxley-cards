# Static UI Prototype Implementation Plan

> **Post-implementation sync (2026-05-28)**: This plan was synchronized to match shipped code after manual implementation iterations. The original greenfield-ideated plan was significantly reshaped during implementation — the 3-phase structure remains, but Phase 3 was simplified from 5 split components to 1 monolithic component, asset pipeline was added (WebP optimization), card system was upgraded from styled-div placeholders to a 28-card image deck with runtime randomization, and substantial visual polish was added on top (entrance animation, meta-strip symmetry, label visibility, etc.). See [F1] in `reviews/plan-review.md` for original assets-prerequisite note.

## Overview

Zbudować dwa statyczne ekrany gry: CampaignMap (pełnoekranowa bitmap mapa Sherwood z waypointami etapów i fit-to-viewport scaling) oraz GameBoard (plansza lustro z 6 rzędami, ręką kart, panelami leader/deck/grave) wyrenderowana na tle ilustracji przekrojonego pieńka, z entrance/exit animacją przy nawigacji. Oba ekrany zasilane hardcoded TypeScript danymi + 28-kartowym deckiem z prawdziwymi obrazami (losowane przy każdym page-load). Zero logiki gameplay — klikalne elementy jedynie nawigują między ekranami.

## Current State Analysis

Frontend scaffold (F-04) jest gotowy: React 19, Vite 8, TS 6, Tailwind v3, React Router v7 (`createBrowserRouter`). Jedyny istniejący komponent to `HomePage.tsx` — landing page z napisem "loxley-cards". Foldery `src/components/` i `src/assets/` puste.

Engine (F-01) definiuje pełny model domeny: `GameState` z dwoma `PlayerState`, `CardInstance`, abilities itd. Mock data w UI nie odwzorowuje 1:1 engine domain — używa uproszczonego flat shape (`MockGameState` z `playerRows.{close,ranged,siege}`, hand size, deck/discard counts, score, roundsWon) bardziej dopasowanego do tego co UI faktycznie renderuje. Mapowanie do engine domain stanie się tematem S-03 (REST integration).

### Key Discoveries:

- Router już skonfigurowany w `frontend/src/main.tsx:7` — `createBrowserRouter` z jedną trasą `/`
- Tailwind v3 działa — `index.css` importuje directives, ale plansza/karty używają **plain custom CSS classes** (`.game-board`, `.game-card`, `.stump-marker` itd.); Tailwind helpers stosowane tylko dla pełnoekranowych layoutów (`relative w-screen h-screen overflow-hidden`)
- Assety graficzne (mapa, marker, pieniek-blat, ikony rzędów, 28 kart) — **Daniel dostarcza jako prerequisite** przed implementacją. Wszystkie w PNG/JPG, konwertujemy do WebP w trakcie portu (cwebp lossy q=78-90)
- `cwebp` zainstalowany przez homebrew — używamy do konwersji wszystkich PNG/JPG → WebP z zachowaniem alpha channel

## Desired End State

Gracz otwiera `/` i widzi pełnoekranową mapę Sherwood Forest z 10 waypointami (drewniane pieńki) — mapa skaluje się fit-to-viewport (object-fit: contain math z `useMapTransform`). Etapy 1-3 świecą się ciepło (active state), 4-10 są zgaszone (locked). Klik na active pieniek → router push na `/game/:stageId`. Strona GameBoard renderuje się z entrance animation:
1. **Stump in** (0-700ms): pieniek-blat scaluje się od 0.05 do 1.0 z bouncy spring curve (cubic-bezier 0.34, 1.56, 0.64, 1), background fade in równolegle
2. **Content in** (700-950ms): plansza (rzędy, ręka, panele) fade-in nad pieńkiem

Plansza Gwinta: 3 rzędy bota na górze, 3 rzędy gracza na dole, panele meta (LEADER + score + cards-in-hand + DECK + GRAVE) symetrycznie po obu stronach, ręka 5 kart na samym dole. Karty z `FINAL_DECK` (28 prawdziwych obrazków Robin Hood theme — bohaterowie, specjalne, leader) losowane przy każdym page-load (15 unique cards rozdzielone na rzędy + ręka).

Klik "← Back to map" → reverse animation (content fade-out 250ms → stump scale-out 600ms) → navigate na `/`.

**Weryfikacja**: `npm run dev` → `/` pokazuje mapę z 10 klikalnymi pieńkami → klik na pieniek → animowany przelot do `/game/:stageId` z pełną planszą + losowymi kartami → klik Back → animowany powrót → `npm run build` przechodzi bez błędów.

## What We're NOT Doing

- Login / ekran logowania — dojdzie w S-04 (magic-link auth)
- Menu główne — osobny change
- Score modal / wynik partii — S-02 lub S-03
- Drag & drop / interakcje gry — S-02
- Pogoda / horn indicators na rzędach — S-02
- Komunikacja z backendem — S-03
- Lock/unlock logika kampanii — S-05
- State management (Zustand) — S-02
- Responsive / mobile — PRD non-goal

## Implementation Approach

Trzy fazy bottom-up: (1) fundament danych — TS typy + mock data + finalDeck z prawdziwymi obrazami, (2) CampaignMap — bitmap + fit-to-viewport + waypointy, (3) GameBoard — monolityczny komponent z planszą lustro, kartami z FINAL_DECK i entrance animacją. HomePage usunięty, root route przejmuje CampaignMap.

## Phase 1: Mock Data & Card Deck

### Overview

Zdefiniować TypeScript typy + statyczne dane mockowe + 28-kartowy deck z prawdziwymi obrazami zasilający oba ekrany.

### Changes Required:

#### 1. Campaign stages data

**File**: `frontend/src/data/campaignStages.ts` (new)

**Intent**: Statyczny array `CAMPAIGN_STAGES` z 10 etapami kampanii + stałe `MAP_WIDTH=7168`, `MAP_HEIGHT=3584` (oryginalne wymiary bitmapy mapy Sherwood w pixelach).

**Contract**: `export const CAMPAIGN_STAGES: CampaignStage[]` — 10 elementów. Każdy etap ma:
- `id: number` (1-10)
- `displayName: string` (np. "Lone bandit apprentice", "Sheriff of Nottingham")
- `description: string` (krótki klimatyczny opis)
- `position: { x: number, y: number }` — **koordynaty w pixelach** względem MAP_WIDTH×MAP_HEIGHT (NIE procenty)
- `status: 'active' | 'locked' | 'completed'` — w S-01 statyczne (1-3 active, 4-10 locked); lock/unlock logika dojdzie w S-05

#### 2. Card deck — FINAL_DECK

**File**: `frontend/src/data/finalDeck.ts` (new)

**Intent**: Centralny deck z 28 prawdziwymi kartami Robin Hood theme (units, heroes, special cards, leader), każda z bundlowanym obrazem.

**Contract**: 
```ts
export type CardType = 'UNIT' | 'HERO' | 'SPECIAL' | 'LEADER';
export interface FinalCard {
  id: string;
  name: string;          // pl: "Łucznicy z Sherwood", "Wieśniacy z Locksley", etc.
  image: string;         // import path resolved przez Vite asset bundler
  type: CardType;
  row: CardRow | null;   // null dla SPECIAL i LEADER
  power: number | null;  // null dla SPECIAL i LEADER
  ability: string | null; // np. 'HERO', 'TIGHT_BOND', 'SPY', 'MEDIC', 'COMMANDERS_HORN', etc.
}
export const FINAL_DECK: FinalCard[] = [/* 28 cards */];
```

28 importów obrazów: `import xxx from '../assets/cards/final/xxx.webp'`. Format WebP (po konwersji z PNG).

#### 3. Row types

**File**: `frontend/src/data/cards.ts` (new — minimal)

**Intent**: Wyizolowane wspólne typy używane przez Card component i finalDeck.

**Contract**: 
```ts
export type CardRow = 'CLOSE' | 'RANGED' | 'SIEGE';
```
(Plik zawiera też dead-code `MockCard` interface + `MOCK_DECK` array — pozostałość po pierwszej iteracji portu z PoC; nie referowane nigdzie w shipped kodzie. Cleanup-fodder na kolejną iterację, nie blokuje.)

#### 4. Mock game state z losowaniem

**File**: `frontend/src/data/mockGameState.ts` (new)

**Intent**: `MOCK_GAME_STATE` — flat shape z polami rzędów, ręki, deck/discard counts, score, roundsWon. Karty losowane przy każdym page-load z `FINAL_DECK`.

**Contract**: Eksport `MOCK_GAME_STATE: MockGameState` zbudowany przez `buildMockState()` przy module load. Funkcja `buildMockState`:
- Filtruje `FINAL_DECK` do `boardUnits` (tylko UNIT/HERO z `power !== null` i `row !== null`)
- Losuje 15 unique kart przez `pickRandom(boardUnits, 15)` (sort + slice)
- Rozdziela 15 kart na: player.close (2) + player.ranged (1) + player.siege (2) + opponent.close (2) + opponent.ranged (2) + opponent.siege (1) + playerHand (5)
- Pozostałe pola (scores, roundsWon, hand sizes, deck counts) — hardcoded

Każdy page-load → nowe losowanie. Brak duplikatów w jednym refresh.

### Success Criteria:

#### Automated:
- TypeScript kompiluje (`npx tsc --noEmit`)
- Build przechodzi (`npm run build`)
- Lint przechodzi (`npm run lint`)

#### Manual:
- FINAL_DECK ma 28 wpisów z różnymi typami (UNIT/HERO/SPECIAL/LEADER) i rzędami
- Mock state losuje za każdym refresh inne karty bez duplikatów

---

## Phase 2: CampaignMap

### Overview

Pełnoekranowa bitmap mapa Sherwood Forest, 10 stump waypointów pozycjonowanych w **pixel-coordinate space** (NIE %), fit-to-viewport scaling (NIE pan/zoom — to było w pierwotnym planie, ale finalna implementacja używa prostszego object-fit: contain math). Klik na active waypoint → React Router navigate.

### Pre-step: Verify assets

Asset prerequisites (dostarcza Daniel — patrz F1 w plan-review):
- `frontend/src/assets/sherwood-map-4k.webp` (mapa kampanii, oryginał 7168×3584px → resize do 4096 wide w cwebp)
- `frontend/src/assets/markers/stump.webp` (mały pieniek-marker)
- `frontend/src/assets/markers/table-stump.webp` (duży pieniek-blat planszy, używany w Phase 3)

### Changes Required:

#### 1. CampaignMap page component

**File**: `frontend/src/pages/CampaignMap.tsx` (new)

**Intent**: Strona-kontener kampanii. Renderuje fullscreen `<img>` z bitmap mapy (object-cover), iteruje po `CAMPAIGN_STAGES`, dla każdego oblicza pozycję pixel `left = transform.offsetX + stage.position.x * transform.scale`, `top = transform.offsetY + stage.position.y * transform.scale` i renderuje `<StumpMarker>` z `useNavigate()` callback do `/game/${stage.id}` (tylko jeśli `status === 'active'`).

**Contract**: Default-exported React component. Container `relative w-screen h-screen overflow-hidden bg-black` (Tailwind). `useMapTransform(containerRef)` zwraca scale + offsets. Markery absolutnie pozycjonowane z transform translate(-50%, -50%) do centrowania.

#### 2. StumpMarker component

**File**: `frontend/src/components/campaign/StumpMarker.tsx` (new)

**Intent**: Pojedynczy waypoint — `<button>` z obrazkiem pieńka i numerem rzymskim etapu. 3 visual states zarządzane przez CSS class `stump-${status}`: active (warm glow + pulsing animation + orbiting sparkles), locked (desaturated, dark, no hover), completed (green glow).

**Contract**: Props: `number: number`, `size?: number`, `status: 'active' | 'locked' | 'completed'`, `onClick?: () => void`. Konwertuje numer na rzymski (I–X) przez lokalny `toRoman()` helper. Locked buttons mają `disabled` attr + nie reagują na onClick.

#### 3. useMapTransform hook

**File**: `frontend/src/hooks/useMapTransform.ts` (new)

**Intent**: Hook obliczający scale + offsets potrzebne do fit-to-viewport rendering mapy (object-fit: contain math). Reaguje na resize containera przez `ResizeObserver`.

**Contract**: 
```ts
export interface MapTransform {
  scale: number;
  offsetX: number;
  offsetY: number;
  viewportWidth: number;
  viewportHeight: number;
}
export function useMapTransform(containerRef: RefObject<HTMLElement | null>): MapTransform;
```
Compute: porównuje viewport aspect ratio z MAP_ASPECT (= MAP_WIDTH/MAP_HEIGHT). Jeśli viewport jest szerszy — fit do width (`scale = width/MAP_WIDTH`, Y offset centruje), w przeciwnym razie fit do height (`scale = height/MAP_HEIGHT`, X offset centruje). Realizuje **object-cover** math (wypełnia viewport, może cropować), spójne z `<img object-cover>` w CampaignMap.

**Uwaga vs pierwotny plan**: pierwotnie planowane było pan/zoom (drag + scroll/pinch). Finalna implementacja to **fit-to-viewport scaling** (statyczna mapa, dopasowana do okna, bez user-controlled pan/zoom). Pan/zoom może wrócić w S-02/S-05 jeśli okaże się potrzebny dla nawigacji po większej mapie.

#### 4. Route update + HomePage delete

**File**: `frontend/src/main.tsx` (edit) + `frontend/src/pages/HomePage.tsx` (delete)

**Intent**: Wymienić scaffold HomePage na 2-route setup.

**Contract**: 
```tsx
const router = createBrowserRouter([
  { path: '/', element: <CampaignMap /> },
  { path: '/game/:stageId', element: <GameBoard /> },
]);
```

### Success Criteria:

#### Automated:
- TypeScript kompiluje (`npx tsc --noEmit`)
- Build przechodzi (`npm run build`)

#### Manual:
- `/` renderuje fullscreen mapę Sherwood z 10 pieńkami
- Pieńki 1-3 świecą się ciepło + pulsują (active state)
- Pieńki 4-10 zgaszone (locked state)
- Klik na pieniek 1/2/3 nawiguje do `/game/:stageId`
- Mapa skaluje się do viewportu przy zmianie rozmiaru okna (ResizeObserver)

---

## Phase 3: GameBoard z animacją

### Overview

Zbudować ekran rozgrywki jako **1 monolityczny komponent** (NIE 5 split jak w pierwotnym planie) z entrance/exit animation. Layout: pieniek-blat (`table-stump.webp`) jako tło, na nim plansza Gwinta z 6 rzędami w układzie lustro, panele meta symetryczne po obu stronach, ręka 5 kart na dole. Karty renderowane przez 1 generic Card component, pobierane z `MOCK_GAME_STATE` (losowane z FINAL_DECK).

### Changes Required:

#### 1. GameBoard page component (monolithic)

**File**: `frontend/src/pages/GameBoard.tsx` (new — pełna implementacja w jednej fazie, NIE stub→pełny jak w pierwotnym planie)

**Intent**: Strona-kontener rozgrywki z 2-state animation (stumpIn → boardIn), full inline JSX dla planszy (NIE wyizolowane podkomponenty BoardRow/PlayerPanel/ScoreBar/HandBar).

**Contract**: Default-exported component. Stan lokalny:
```ts
const [stumpIn, setStumpIn] = useState(false);
const [boardIn, setBoardIn] = useState(false);
```

Sekwencja entry (useEffect):
- t=16ms → setStumpIn(true) → CSS `.stump-in` class → pieniek scaluje 0.05→1 z spring (600ms) + background fade-in (600ms)
- t=700ms → setBoardIn(true) → CSS `.board-in` → game-board + back button opacity 0→1 (250ms)

Sekwencja exit (handleBack):
- setBoardIn(false) — content fade-out 250ms
- +250ms → setStumpIn(false) — stump scale-out 600ms
- +850ms → `navigate('/')`

JSX layout:
- `<button>` "← Back to map" w lewym górnym rogu (z-index 101)
- `<div className="game-table-stump">` — wrapper z `<img>` table-stump
- `<div className="game-board">` — kontener planszy z:
  - `.board-side-opponent`:
    - `.board-meta-opponent` z 2 grupami: `.board-meta-left` (LEADER + score) | `.board-meta-right` (hand-info + deck + grave)
    - 3× `.board-row` (siege, ranged, close — od góry)
  - `.board-divider`
  - `.board-side-player`:
    - 3× `.board-row` (close, ranged, siege)
    - `.board-meta-player` z analogiczną symetryczną strukturą left/right
  - `.board-hand` — absolutnie pozycjonowany dolny pasek z 5 kartami z ręki

Każdy rząd renderuje karty z mock state przez `<Card key={card.id} {...card} size={110} />`. Hand używa `size={140}`.

#### 2. Card component (generic, 1 komponent dla całej planszy)

**File**: `frontend/src/components/game/Card.tsx` (new)

**Intent**: 1 universal komponent dla wszystkich kart (board + hand). NIE wyizolowane CardView/PlayerPanel/etc. — wystarcza 1 Card.

**Contract**: Props:
```ts
interface CardProps {
  name: string;
  power: number | null;
  row: CardRow | null;
  image?: string;
  ability?: string | null;
  size?: number;
  onClick?: () => void;
  selected?: boolean;
}
```
Width = `size * 0.714` (5:7 aspect ratio). Renderuje:
- `<img>` portrait (z `image` prop, fallback do `robin-placeholder.webp` jeśli brak)
- `.game-card-frame` overlay (radial gradient vignette)
- `.game-card-power` (top-left) — okrąg z power value (renderowany jeśli `power !== null`)
- `.game-card-row-icon` (top-right) — ikona rzędu (renderowana jeśli `row !== null`, mapowanie przez `ROW_ICONS` record)
- `.game-card-name` (bottom) — nazwa karty z gradient backdrop

### Success Criteria:

#### Automated:
- TypeScript kompiluje (`npx tsc --noEmit`)
- Build przechodzi (`npm run build`)

#### Manual:
- `/game/1` renderuje pełną planszę z losowanymi kartami
- Entrance animation: pieniek skaluje się od 0.05 do 1 z bouncy spring, potem plansza+karty pojawiają się
- Layout lustro: 3 rzędy bota na górze, 3 rzędy gracza na dole, każdy z label CLOSE/RANGED/SIEGE i row-score po prawej
- Meta-panele symetryczne (oba: LEFT = LEADER+score, RIGHT = hand-info+deck+grave)
- Ręka 5 kart na samym dole z większymi kartami (size 140 vs 110 na planszy)
- Każde odświeżenie strony → inne losowe karty (15 unique z FINAL_DECK)
- Klik "Back to map" → reverse animation → return na `/`

---

## Visual Polish Iterations

Po podstawowym porcie zostało zrobione iteracyjne polishing UI/UX:

### Asset pipeline — WebP conversion

Wszystkie assety graficzne zostały skonwertowane z PNG/JPG do WebP przez `cwebp` (homebrew). Strategia:
- **Lossy q=78-90** w zależności od typu (q=78 dla foto-realistycznej mapy, q=85-90 dla ikon i kart)
- **Alpha channel preserved** dla pieńków i ikon rzędów
- **Resize do 4096 wide** dla mapy (z oryginalnego 7168 wide) — code-niezależne bo `useMapTransform` używa stałych MAP_WIDTH/HEIGHT, nie fizycznych pixeli
- Wszystkie referencje w kodzie używają `.webp` extension; oryginalne PNG/JPG usunięte z repo

Wyniki:
- Główne assety (mapa, pieńki, ikony rzędów): 23MB → 4.6MB (~80% redukcji)
- Karty (28 PNG w `cards/final/`): 75MB → 3.9MB (~95% redukcji)
- **Razem dist/ ~8MB** (vs ~70MB w wariancie PNG)

### Stage-in animation sequence

Original PoC miało jednoczesną animację (pieniek + content razem). Dla lepszego visual hierarchy zrobione sekwencyjne staging: najpierw bouncy spring pieńka (700ms), POTEM fade-in planszy (250ms). Exit reverse: content znika, pieniek się chowa, navigate.

### Meta-strip symmetry

PoC GameBoard miał asymetryczny meta-strip (bot vs gracz miały elementy w różnych miejscach). Refactor do 2 wewnętrznych grup `.board-meta-left` (LEADER + score) i `.board-meta-right` (hand-info + deck + grave) z `justify-content: space-between` — identyczna struktura dla obu stron. Gracz też dostał hand-info (`playerHand.length`) dla pełnej symetrii.

### Row height reduction

Pierwotnie `.board-row { min-height: 130px; padding: 4px 16px; }` dawało 138px row (z kartą 110px = 14px luzu). Zmniejszone do `min-height: 123px; padding: 3px 16px;` = 129px row (9px luzu) — kompromis między visual breath a unikaniem overlapu hand cards z player siege row.

### Row labels visibility

`.row-label` (SIEGE/RANGED/CLOSE) były nieczytelne na ciemnozielonych/mchowych tłach (color `rgba(255,220,150,0.6)` + brak text-shadow). Naprawione: pełny kremowy color + 3-warstwowy dark text-shadow (pattern z `.stump-number` PoC) — outline przebijający przez każde tło.

### Power circle font (Cinzel → system sans)

Cinzel font używany dla title planszy ma niespójne metrics numerów (cyfry 5/7/9 wyglądały przesunięte w pionie vs 6/8). Iteracja:
1. Cinzel → Georgia + padding-bottom 2px (Georgia miała oldstyle figures z descenderami)
2. Georgia + `font-feature-settings: 'lnum'` (no-op dla TrueType w niektórych browserach)
3. **System sans-serif** (`-apple-system, BlinkMacSystemFont, 'Helvetica Neue', Arial, sans-serif`) + `font-variant-numeric: lining-nums tabular-nums` — gwarantuje consistent lining figures

Plus border 2px→1px, opacity 0.7→0.45 (cieńszy, delikatniejszy), usunięty box-shadow.

### Row icon glow (white outline emulation)

`.game-card-row-icon` była nieczytelna na różnych tłach kart. Iteracja:
1. Próba: ciemny okrąg z złotym borderem (jak power-circle) — za "kafelkowo", zasłaniała ilustrację
2. Final: **bez background/border**, czysta ikona z 4-warstwowym filter:
   - 2× tight white drop-shadow (1.5px blur, opacity 0.85) — emuluje 1px stroke wokół visible pixels
   - Warm halo glow (4px blur, opacity 0.4)
   - Dark drop (1px y, 2px blur, opacity 0.65) dla głębi
   Daje "świecenie" ikony niezależnie od tła pod nią.

### Back button miniaturization

Pierwotnie `top: 24px, left: 24px, padding: 12px 20px, font-size: 16px` — overlap z meta-opponent panelem. Zmniejszone do `top: 12px, left: 16px, padding: 5px 12px, font-size: 12px` — siedzi nad meta zamiast się z nim zlewać.

---

## Testing Strategy

### Unit Tests:

- Brak — w prototypie statycznym nie ma logiki do testowania. Testy dojdą w S-02 (interakcje) i S-03 (integracja z REST).

### Integration Tests:

- Brak — zero API, zero state management.

### Manual Testing Steps:

1. `cd frontend && npm run dev` → `/` wyświetla mapę kampanii Sherwood
2. Etapy 1-3 świecą się (active), 4-10 zgaszone (locked)
3. Klik na pieniek 1/2/3 → animowany przelot do `/game/:stageId`:
   - Pieniek scaluje się od 0.05 do 1.0 z bouncy spring (600ms)
   - Plansza + karty fade-in po pieńku (250ms, od t=700ms)
4. Plansza pokazuje losowane karty z `FINAL_DECK` (3 rzędy bota, 3 rzędy gracza, ręka 5 kart)
5. Każde odświeżenie strony (`Cmd+R`) → inne losowe karty bez duplikatów
6. Meta-panele symetryczne (LEADER+score po lewej, hand+deck+grave po prawej, identycznie u bota i gracza)
7. Klik "← Back to map" → reverse animation → return na `/`
8. `cd frontend && npm run build` → brak błędów, `dist/` ~8MB

## Performance Considerations

- WebP asset pipeline daje ~80-95% redukcji bundle size vs PNG/JPG. Initial load mapy + pieńka-blatu = ~2.6MB razem.
- Pieniek-blat (`table-stump.webp` 1.2MB) ładuje się w trakcie animacji entrance — może być widoczne lekkie opóźnienie pierwszego wejścia na `/game/:stageId`. Po pierwszym ładowaniu cache zatrzymuje asset.
- 28 kart `cards/final/*.webp` (~150-300KB each) bundlowane przez Vite — wszystkie ładowane przy pierwszej wizycie na `/game/*`. Lazy loading byłby możliwy ale niepotrzebny przy aktualnym total ~5MB.
- Animacja używa CSS `transform: scale()` (GPU-accelerated), nie modyfikuje layout.

## References

- Engine domain model: `backend/acommon-game-engine/src/main/java/cards/loxley/game/domain/`
- Engine campaign data: `backend/acommon-game-engine/src/main/resources/data/sherwood_campaign_stages.json` — name/description źródło dla `CAMPAIGN_STAGES`
- F-04 scaffold plan: `context/archive/2026-05-25-f-04-frontend-scaffold/plan.md`
- F-01 engine plan: `context/archive/2026-05-26-f-01-game-engine-core/plan.md` (referencja dla mock state shape vs engine domain)
- Frontend entry: `frontend/src/main.tsx`

## Progress

> Wszystkie checkboxy [x] — implementation zakończona, docs zsynchronizowane do shipped reality 2026-05-28.

### Phase 1: Mock Data & Card Deck

- [x] 1.1 `campaignStages.ts` z 10 etapami + MAP_WIDTH/HEIGHT constants
- [x] 1.2 `finalDeck.ts` z 28 kartami (UNIT/HERO/SPECIAL/LEADER) i bundlowanymi obrazami
- [x] 1.3 `cards.ts` z CardRow type
- [x] 1.4 `mockGameState.ts` z `buildMockState()` + runtime randomization (15 unique per page-load)
- [x] 1.5 TypeScript kompiluje (`npx tsc --noEmit`)
- [x] 1.6 Build przechodzi (`npm run build`)

### Phase 2: CampaignMap

- [x] 2.1 Assety dostarczone i skonwertowane do WebP (sherwood-map-4k, stump, table-stump, ikony rzędów)
- [x] 2.2 `pages/CampaignMap.tsx` z fit-to-viewport scaling i waypoint positioning
- [x] 2.3 `components/campaign/StumpMarker.tsx` z 3 visual states (active/locked/completed) i Roman numerals
- [x] 2.4 `hooks/useMapTransform.ts` z fit-to-viewport math (NIE pan/zoom) + ResizeObserver
- [x] 2.5 `main.tsx` zaktualizowany (2 routes), `HomePage.tsx` usunięty
- [x] 2.6 `/` renderuje fullscreen mapę z 10 pieńkami (1-3 active, 4-10 locked)
- [x] 2.7 Klik na active pieniek nawiguje do `/game/:stageId`

### Phase 3: GameBoard z animacją

- [x] 3.1 `pages/GameBoard.tsx` jako monolithic component z 2-state animation (stumpIn → boardIn)
- [x] 3.2 `components/game/Card.tsx` jako 1 generic component (NIE 5 split: CardView/BoardRow/PlayerPanel/ScoreBar/HandBar)
- [x] 3.3 Plansza lustro: 3 rzędy bota + divider + 3 rzędy gracza + hand bar
- [x] 3.4 Meta-panele symetryczne (LEFT: LEADER+score, RIGHT: hand-info+deck+grave dla obu stron)
- [x] 3.5 Karty z `MOCK_GAME_STATE` (losowane z FINAL_DECK)
- [x] 3.6 Entrance animation: stump-in (600ms spring) → content-in (250ms fade @ t=700ms)
- [x] 3.7 Exit animation: content-out (250ms) → stump-out (600ms) → navigate('/')
- [x] 3.8 Przycisk "Back to map" działa
- [x] 3.9 Layout czytelny na 1920×1080

### Visual Polish (post-phase iterations)

- [x] P.1 Asset pipeline WebP (cwebp lossy): 23MB → 4.6MB main + 75MB → 3.9MB cards
- [x] P.2 Meta-strip symmetry refactor (2 inner groups left/right)
- [x] P.3 Row height tuning (130→123px) dla unikania hand overlap
- [x] P.4 Row labels visibility (3-warstwowy text-shadow + jaśniejszy color)
- [x] P.5 Power circle font system-sans + lining-nums (Cinzel/Georgia miały bad numeric metrics)
- [x] P.6 Row icon glow (4-warstwowy filter: 2x white outline + warm halo + dark drop)
- [x] P.7 Back button miniaturization (top 24→12, padding 12/20→5/12, font 16→12)
- [x] P.8 FinalDeck integration z runtime randomization (28 cards, 15 unique per refresh)
