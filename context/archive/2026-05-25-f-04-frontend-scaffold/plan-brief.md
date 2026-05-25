# Frontend Scaffold (F-04) — Plan Brief

> Full plan: `context/changes/f-04-frontend-scaffold/plan.md`

## What & Why

Bootstrap Vite + React + TS SPA w `frontend/` — architektoniczny enabler dla S-01 (statyczny prototyp UI) i całego łańcucha frontend → gameplay → integracja. Bez tego nie ma gdzie budować interfejsu gry.

## Starting Point

`frontend/` jest kompletnie pusty. Backend żyje osobno w `backend/` (Maven multi-module). Root `.gitignore` nie ma wpisów Node/npm. Zero frontend kodu, zero konfiguracji.

## Desired End State

`cd frontend && npm run dev` otwiera stronę z napisem "loxley-cards". `npm run build` produkuje statyczny output w `dist/`. React Router v7 obsługuje routing przez `createBrowserRouter`, Tailwind CSS v3 gotowy do użycia. Struktura `src/{components/, pages/, assets/}` czeka na S-01.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
|---|---|---|
| Router | React Router v7 (`createBrowserRouter`) | Największy ekosystem, data loaders przydadzą się przy S-03 REST integracji |
| Styling | Tailwind CSS v3 | Stabilność (3+ lata stable, ogromne pokrycie w dokumentacji i training data) zamiast nowszego, ewoluującego v4 |
| State management | Defer do S-02 | Zustand to `npm install zustand`, zero config — niepotrzebny w scaffold |
| Folder structure | Minimal (components/, pages/, assets/) | Daje S-01 punkt startu bez pustych spekulatywnych folderów |

## Scope

**In scope:**
- Root `.gitignore` update (Node/frontend entries)
- Vite + React + TS scaffold via `npm create vite@latest`
- Tailwind CSS v3 + PostCSS + `tailwind.config.js`
- React Router v7 z `createBrowserRouter` + `RouterProvider`
- Katalogi `src/{components/, pages/, assets/}`
- Landing page "loxley-cards" pod `/`
- Weryfikacja `npm run dev` i `npm run build`

**Out of scope:**
- State management (Zustand) — S-02
- Komponenty UI gry — S-01
- Backend communication — S-03
- Assety graficzne — S-01
- CI/CD, testy, component library

## Architecture / Approach

Standalone Vite SPA w `frontend/`, zero coupling z backendem. React Router v7 z `createBrowserRouter` + `RouterProvider` (client-side routing, data-loader-ready dla S-03). Tailwind v3 z klasycznym PostCSS pipeline'em. Deployment target: Cloudflare Pages (build output `dist/`). Backend communication pojawi się w S-03 przez REST fetch do `api.loxley.cards`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. Gitignore + Vite scaffold | Działający Vite + React + TS projekt, czyste gitignore | `node_modules/` w git jeśli gitignore po scaffoldzie |
| 2. Tailwind + React Router | Skonfigurowane narzędzia, gotowe do użycia | Konfiguracja Tailwind PostCSS musi się zazębić z Vite |
| 3. Folder structure + landing page | Kompletny scaffold z jedną stroną | Scope creep — pokusa dodania UI beyond minimum |

**Prerequisites:** Node 22 zainstalowany lokalnie, npm dostępny
**Estimated effort:** ~1 sesja, 3 fazy po kilka minut każda

## Open Risks & Assumptions

- Tailwind CSS v3 (nie v4) — świadoma decyzja: stabilność i dojrzałość API, kosztem nowszego CSS-first podejścia v4
- React Router v7 — instalujemy `react-router` (nie `react-router-dom` który jest deprecated w v7); używamy `createBrowserRouter` zamiast starszego `<BrowserRouter>` dla data-loader compatibility z S-03
- Node 22 assumed — sprawdzić `node --version` przed F1, Vite 6+ wymaga ≥18

## Success Criteria (Summary)

- `cd frontend && npm run dev` startuje i pokazuje stronę "loxley-cards"
- `cd frontend && npm run build` produkuje `dist/index.html` + assets
- Tailwind utility classes renderują się poprawnie na landing page
