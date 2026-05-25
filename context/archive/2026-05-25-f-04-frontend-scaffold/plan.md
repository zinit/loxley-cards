# Frontend Scaffold (F-04) Implementation Plan

## Overview

Bootstrap Vite + React + TypeScript SPA w `frontend/` — z React Router v7 (`createBrowserRouter`), Tailwind CSS v3, minimalną strukturą folderów i jedną stroną landing page pod `/`. Scaffold jest architektonicznym enablerem dla S-01 (statyczny prototyp UI). Żadnego gameplay'u, assetów, komunikacji z backendem.

## Current State Analysis

- `frontend/` jest kompletnie pusty (empty directory)
- Backend żyje w `backend/` jako osobny Maven multi-module — zero coupling z frontendem
- `.gitignore` nie ma wpisów dla Node/npm/frontend artefaktów
- Tech stack doc specyfikuje: Vite + React + TS, Node 22, npm, Cloudflare Pages

### Key Discoveries:

- Backend i frontend mają osobne build pipeline'y — brak frontend-maven-plugin, deploy niezależny
- Roadmap F-04 ma jawną definicję done: "pusty projekt z `npm run dev` i jedną stroną"
- Roadmap ostrzega przed scope creep: nie dodawać komponentów UI, state management, ani assetów
- Router choice odroczona z roadmapy do tego planu — decyzja: React Router v7 z `createBrowserRouter` API
- Tailwind version choice — decyzja: v3 (stabilność, training data coverage) zamiast v4

## Desired End State

Po zakończeniu planu:
- `frontend/` zawiera działający Vite + React + TS projekt
- React Router v7 (`createBrowserRouter`) skonfigurowany z jedną trasą `/` → landing page "loxley-cards"
- Tailwind CSS v3 działa (PostCSS + `tailwind.config.js`)
- Struktura katalogów: `src/{components/, pages/, assets/}`
- `npm run dev` startuje dev server
- `npm run build` produkuje statyczny output w `frontend/dist/`
- Root `.gitignore` zawiera wpisy Node/npm/frontend

**Weryfikacja end state:** `cd frontend && npm run dev` otwiera stronę w przeglądarce z widocznym napisem "loxley-cards"; `npm run build` kończy się sukcesem i produkuje pliki w `dist/`.

## What We're NOT Doing

- State management (Zustand) — defer do S-02
- Komponenty UI gry (plansza, karty, kampania) — to S-01
- Komunikacja z backendem (REST, fetch, API client) — to S-03
- Assety graficzne — to S-01
- CI/CD pipeline dla frontendu — to deploy plan
- Testy (unit, e2e) — brak testów w scaffold, pojawią się w późniejszych slice'ach
- shadcn/ui ani żaden komponent library — decyzja odroczona do S-01
- Tailwind v4 — świadomie wybieramy v3 dla stabilności (v4 jest młodszy, API jeszcze ewoluuje)

## Implementation Approach

Trzy fazy w porządku minimalizującym ryzyko:
1. Najpierw gitignore (żeby `node_modules/` nie wpadło do staged files), potem Vite scaffold
2. Konfiguracja narzędzi (Tailwind v3, React Router v7) bez tworzenia stron
3. Struktura aplikacji + landing page + weryfikacja buildu

## Phase 1: Gitignore + Vite Scaffold

### Overview

Zaktualizować root `.gitignore` o wpisy Node/frontend, potem wygenerować szkielet Vite + React + TS w `frontend/`.

### Changes Required:

#### 1. Root .gitignore — Node/frontend entries

**File**: `.gitignore`

**Intent**: Dodać sekcję Node/frontend przed scaffoldem, żeby `node_modules/`, `dist/`, `.env` itp. były ignorowane od pierwszego momentu.

**Contract**: Nowa sekcja `### Frontend (Node/npm) ###` z wpisami: `node_modules/`, `dist/`, `.env`, `.env.local`, `.env.*.local`, `*.tsbuildinfo`.

#### 2. Vite scaffold

**File**: `frontend/` (nowy projekt)

**Intent**: Wygenerować standardowy Vite + React + TypeScript projekt w `frontend/` używając oficjalnego szablonu.

**Contract**: `npm create vite@latest frontend -- --template react-ts` uruchomiony z root repo. Następnie `cd frontend && npm install`. Wynik: `package.json`, `vite.config.ts`, `tsconfig.json`, `tsconfig.app.json`, `tsconfig.node.json`, `index.html`, `src/main.tsx`, `src/App.tsx`, standardowe pliki Vite template.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npm install` kończy się bez błędów
- `ls frontend/package.json frontend/vite.config.ts frontend/tsconfig.json` — pliki istnieją
- `cd frontend && npx tsc --noEmit` — TypeScript compilation passes

#### Manual Verification:

- `.gitignore` zawiera sekcję Node/frontend
- `git status` nie pokazuje `node_modules/` ani `dist/`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Tailwind CSS v3 + React Router v7

### Overview

Zainstalować i skonfigurować Tailwind CSS v3 (klasyczny PostCSS pipeline) oraz React Router v7 (paczka `react-router`). Bez tworzenia stron — tylko deps i konfiguracja.

### Changes Required:

#### 1. Tailwind CSS v3 installation + config

**File**: `frontend/package.json` (nowe deps), `frontend/tailwind.config.js` (nowy), `frontend/postcss.config.js` (nowy), `frontend/src/index.css`

**Intent**: Zainstalować Tailwind CSS v3 z klasycznym PostCSS pipeline'em. Świadoma decyzja v3 zamiast v4: stabilność, dojrzałość, ogromne pokrycie w dokumentacji i training data.

**Contract**: `npm install -D tailwindcss@^3 postcss autoprefixer`. `npx tailwindcss init -p` generuje `tailwind.config.js` + `postcss.config.js`. W `tailwind.config.js` ustawić `content: ["./index.html", "./src/**/*.{ts,tsx}"]`. W `src/index.css` zastąpić domyślną treść trzema dyrektywami: `@tailwind base; @tailwind components; @tailwind utilities;`. Usunąć domyślne style Vite template (`App.css`).

#### 2. React Router v7 installation

**File**: `frontend/package.json` (nowy dep)

**Intent**: Zainstalować React Router v7 (zunifikowany package `react-router`, nie `react-router-dom`). Routes definiujemy w Phase 3 — w F2 sprawdzamy że bundle przechodzi.

**Contract**: `npm install react-router`. Bez zmian w `main.tsx` / `App.tsx` — provider + routes wchodzą w Phase 3 razem ze stronami.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npx tsc --noEmit` — TypeScript passes z nowymi deps
- `cd frontend && npm run build` — build passes (Tailwind wired, Router zainstalowany)

#### Manual Verification:

- `npm run dev` startuje bez błędów w konsoli
- Tailwind działa — dodanie `className="text-red-500"` do dowolnego elementu w `App.tsx` zmienia kolor

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Folder Structure + Landing Page + Build Verification

### Overview

Stworzyć docelową strukturę katalogów, zastąpić domyślny Vite App komponentem landing page z routingiem przez `createBrowserRouter`, zweryfikować że dev i build działają end-to-end.

### Changes Required:

#### 1. Folder structure

**Files**: `frontend/src/components/`, `frontend/src/pages/`, `frontend/src/assets/` (nowe katalogi)

**Intent**: Stworzyć minimalne katalogi pod przyszłe slice'y. `pages/` od razu dostaje `HomePage.tsx` (poniżej), więc `.gitkeep` zbędny. `components/` i `assets/` chwilowo puste — `.gitkeep` żeby git śledził.

**Contract**: Trzy nowe katalogi: `src/components/`, `src/pages/`, `src/assets/`. `components/` i `assets/` zawierają `.gitkeep`; `pages/` nie (od razu zawiera `HomePage.tsx`).

#### 2. Landing page + routing

**File**: `frontend/src/pages/HomePage.tsx` (nowy), `frontend/src/main.tsx` (edit — dodać router), `frontend/src/App.tsx` (delete)

**Intent**: Zastąpić domyślny Vite boilerplate (logo, counter) jedną stroną landing page "loxley-cards" podpiętą pod route `/` przez `createBrowserRouter` + `RouterProvider`.

**Contract**: `HomePage.tsx` — prosty komponent z nagłówkiem "loxley-cards" i minimalnym tekstem (tytuł gry, np. "Gwint-inspired card game"), z kilkoma Tailwind utility classes dla weryfikacji że styling działa. `main.tsx` — utworzyć router przez `createBrowserRouter([{ path: "/", element: <HomePage /> }])` i renderować `<RouterProvider router={router} />`. `App.tsx` z Vite template staje się zbędny — usunąć go (cały routing w `main.tsx`). Usunąć też domyślne pliki: `App.css`, `src/assets/react.svg`, `public/vite.svg`.

### Success Criteria:

#### Automated Verification:

- `cd frontend && npx tsc --noEmit` — zero errors
- `cd frontend && npm run build` — sukces, `dist/` zawiera `index.html` + JS/CSS assets
- `cd frontend && npm run lint` — zero errors (jeśli eslint jest w template)

#### Manual Verification:

- `npm run dev` → przeglądarka pokazuje stronę "loxley-cards" pod `http://localhost:5173/`
- Tailwind działa na landing page (utility classes renderują się poprawnie)
- Nawigacja do nieistniejącej ścieżki = blank page bez exception w konsoli (default routing bez `*` match nie renderuje nic — formalny 404 wchodzi w S-01)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- Brak w scope F-04 — testy pojawią się w S-01/S-02

### Manual Testing Steps:

1. `cd frontend && npm run dev` — dev server startuje
2. Otworzyć `http://localhost:5173/` — widoczna strona "loxley-cards"
3. Sprawdzić że Tailwind utility classes działają (np. kolory, spacing)
4. `npm run build` — build kończy się sukcesem
5. `ls frontend/dist/` — zawiera `index.html` i assets

## Performance Considerations

- Brak — scaffold jest minimalny. Tailwind v3 tree-shakes nieużywane klasy (purge przez `content` config). React Router v7 ładuje tylko potrzebne moduły.

## References

- Roadmap F-04: `context/foundation/roadmap.md` (linie 107-117)
- Tech stack frontend: `context/foundation/tech-stack.md` (sekcja "Frontend")

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Gitignore + Vite Scaffold

#### Automated

- [x] 1.1 `npm install` kończy się bez błędów
- [x] 1.2 `package.json`, `vite.config.ts`, `tsconfig.json` istnieją
- [x] 1.3 `npx tsc --noEmit` passes

#### Manual

- [x] 1.4 `.gitignore` zawiera sekcję Node/frontend
- [x] 1.5 `git status` nie pokazuje `node_modules/`

### Phase 2: Tailwind CSS v3 + React Router v7

#### Automated

- [x] 2.1 `npx tsc --noEmit` passes z nowymi deps
- [x] 2.2 `npm run build` passes

#### Manual

- [x] 2.3 `npm run dev` startuje bez błędów
- [x] 2.4 Tailwind utility classes działają

### Phase 3: Folder Structure + Landing Page + Build Verification

#### Automated

- [x] 3.1 `npx tsc --noEmit` zero errors
- [x] 3.2 `npm run build` sukces, `dist/` zawiera `index.html`
- [x] 3.3 `npm run lint` zero errors

#### Manual

- [x] 3.4 `npm run dev` pokazuje stronę "loxley-cards"
- [x] 3.5 Tailwind działa na landing page
- [x] 3.6 Nawigacja do nieistniejącej ścieżki nie crashuje
