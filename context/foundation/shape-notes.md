---
project: loxley-cards
context_type: brownfield
created: 2026-06-28
updated: 2026-06-28
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  gray_areas_resolved:
    - topic: "kategoria zmiany"
      decision: "nowy moduł CI (ai-review.yml obok ci.yml), zero ingerencji w istniejący pipeline"
    - topic: "wgląd"
      decision: "brak separacji ról (confirmation bias) + brak artefaktu na PR (niewidoczny review)"
    - topic: "persona"
      decision: "Daniel jako solo dev"
  frs_drafted: 5
  quality_check_status: accepted
---

# Shape Notes: f-06-ai-code-review-pipeline

## Current System

loxley-cards — webowa karcianka inspirowana Gwintem (Spring Boot 4 / Java 21 backend, React/TS frontend), MVP funkcjonalnie kompletny (auth + kampania + grywalna partia vs bot), deployed na loxley.cards. CI w `.github/workflows/ci.yml` (backend `mvnw verify` + frontend `npm run build`) shipped w F-05 (2026-06-17) — uruchamiany na każdy push/PR do main. Zero AI w pipeline. Repo publiczne na GitHubie.

## Problem Statement & Motivation

Każdy PR w loxley-cards jest implementowany i review'owany przez tę samą osobę (Daniel) w tej samej lokalnej sesji Claude. Brak zewnętrznego punktu kontrolnego oznacza: (1) confirmation bias — implementer i reviewer dzielą ten sam kontekst, regresje przechodzą niezauważone, (2) brak widocznego artefaktu na PR — lokalna sesja nie zostawia śladu na GitHubie, co uniemożliwia retrospekcję i jest niewidoczne dla zewnętrznych obserwatorów.

Driver podwójny: realna jakość (wyłapanie regresji których lokalna sesja nie złapie) + certyfikacja 10xChampion (M5L2 SDK + M5L3 pipeline, deadline 2026-07-05) — artefakt review na PR jest wymaganym dowodem.

Obecne obejście: review w głowie Daniela + lokalna sesja Claude. Koszt: zero separacji ról, zero trwałego artefaktu.

## User & Persona

**Primary persona:** Daniel — solo developer, jedyny contributor w publicznym repo loxley-cards. Potrzebuje zewnętrznego review point w swoim workflow: agent AI jako niezależny reviewer na każdym PR, zostawiający strukturalną opinię (rubryka, werdykt, komentarz) widoczną na GitHubie.

## Access Control Changes

Brak zmian w modelu uwierzytelniania aplikacji — obecny model (username + BCrypt + JWT) zachowany. AI reviewer działa jako GitHub Actions workflow z tokenami (GITHUB_TOKEN do komentowania PR, Anthropic API key do wywołań Claude) — poza istniejącym auth użytkowników aplikacji.

## Success Criteria

### Primary

Otwarcie PR na main automatycznie generuje komentarz od bota z 5-wymiarową rubryką (poprawność, idiomatyczność Java/Spring, złożoność, testy, bezpieczeństwo — każde 1-10 z uzasadnieniem) + summary + werdykt (APPROVED / NEEDS_ATTENTION / REJECTED).

### Secondary

Screen pipeline (zielony job) + screen komentarza na PR = dowód do formularza certyfikacji Champion.

### Guardrails

- Istniejący `ci.yml` nie może ulec żadnej zmianie — zero modyfikacji istniejącego pipeline.
- Runtime backend/frontend bez zmian — zmiana dotyczy wyłącznie CI layer.
- Gate jest informacyjny (komentarz), nie blokujący merge — w MVP brak status checka blokującego.

## Scope of Change

- FR-001: [new] Workflow `ai-review.yml` triggerowany na `pull_request` do main uruchamia job AI review. Priority: must-have
  > Sokrates: Rozważono kontrargument: "koszt API na każdy push do PR — przy 5-10 pushach na PR koszt Sonnet może być nieproporcjonalny." Rozwiązanie: zachowano; w MVP akceptowalny koszt przy niskim wolumenie PR (solo dev). Jeśli koszt zacznie boleć → v2: concurrency cancel-in-progress lub manual trigger.
- FR-002: [new] Skrypt `review.js` (Vercel AI SDK 6 + Zod schema) wywołuje Claude Sonnet 4.6 z diff + PR metadata (title, body). Priority: must-have
  > Sokrates: Rozważono kontrargument: "duży diff może przekroczyć context window." Rozwiązanie: zachowano; typowe PR w loxley-cards to małe-średnie diffy (jednorazowe feature/fix). Jeśli diff jest za duży → graceful failure (agent loguje warning, workflow nie crashuje). Truncation/chunking to v2.
- FR-003: [new] Agent ocenia diff wg 5-wymiarowej rubryki (poprawność, idiomatyczność Java/Spring, złożoność, testy, bezpieczeństwo) — każde kryterium 1-10 z uzasadnieniem — i emituje werdykt APPROVED / NEEDS_ATTENTION / REJECTED. Priority: must-have
  > Sokrates: Rozważono kontrargument: "ocena 1-10 jest subiektywna i niestabilna — LLM może dać 7/10 raz, a 5/10 następnym razem." Rozwiązanie: zachowano; skala daje strukturę komentarzowi i czytelny at-a-glance sygnał. Niestabilność akceptowalna przy informacyjnym gate (nie blokującym). Jeśli precyzja zacznie mieć znaczenie → v2: promptfoo evaluator z ground-truth examples.
- FR-004: [new] Workflow postuje komentarz markdown na PR przez GitHub API — tabela ocen + summary + werdykt. Priority: must-have
  > Sokrates: Rozważono kontrargument: "komentarz od bota wygląda jak spam — alert fatigue po kilku PR." Rozwiązanie: zachowano; to jedyny artefakt review, bez niego pipeline nie ma outputu. Mitygacja: zwięzły format (tabela, nie wall of text), jeden komentarz per run. Jeśli spam → v2: collapsible details lub update istniejącego komentarza zamiast nowego.
- FR-005: [preserved] Istniejący `ci.yml` (backend `mvnw verify` + frontend `npm run build`) działa bez zmian. Priority: must-have
  > Sokrates: Brak kontrargumentu; izolacja ci.yml to świadoma decyzja — zero ryzyka regresji istniejącego pipeline.

## User Stories

### US-01: AI review na PR

- **Given** Daniel otwiera PR na branch `main` w repo loxley-cards
- **When** GitHub Actions automatycznie odpala job `ai-review`
- **Then** na PR pojawia się komentarz od bota z tabelą 5 ocen (1-10 + uzasadnienie) + summary + werdykt (APPROVED / NEEDS_ATTENTION / REJECTED); Daniel czyta komentarz i decyduje o merge

## Business Logic Changes

Brak zmiany logiki domenowej. Jest to zmiana infrastrukturalna/techniczna — nowy CI workflow (ai-review.yml) + skrypt Node (review.js). Logika gry (scoring, abilities, kampania, auth) pozostaje bez zmian.

## Constraints & Compatibility

- Brak integracji do zachowania — zmiana nie dotyka żadnych istniejących integracji, API ani danych.
- Brak migracji danych — zero zmian w schemacie DB lub danych.
- Jedyne nowe zależności zewnętrzne: ANTHROPIC_API_KEY (nowy GitHub Secret) + GITHUB_TOKEN (wbudowany).
- Istniejący `ci.yml` nie może być modyfikowany — izolacja workflow jest twarda zasadą.
- Kompatybilność wsteczna: n/a — nowy workflow, brak istniejących konsumentów.

## Non-Functional Requirements

- Czas wykonania workflow (checkout + diff + API call + post comment) poniżej 2 minut — dłużej oznacza, że Daniel merguje bez czytania komentarza.
- Graceful failure — jeśli API call do Claude lub posting komentarza na PR failuje, workflow kończy się warning (nie error) i nie blokuje merge ani nie psuje statusu PR.

## Non-Goals

- **Brak status checka blokującego merge** — gate jest informacyjny (komentarz), nie blokujący. Wymaga decyzji "kiedy blokujemy" i ryzyka false-positive; v2.
- **Brak labels ai-cr:passed/failed** — kosmetyka; v2.
- **Brak promptfoo evaluator** — bramka regresji jakości promptów; potrzebna dopiero gdy częsty tuning rubryki; v2.
- **Brak multi-model (haiku triage → sonnet escalation)** — optymalizacja kosztów; tylko gdy faktyczny koszt zacznie boleć; v2.
- **Brak cost dashboard / onStepFinish monitoring** — basic logs w workflow output wystarczą na MVP; pełny dashboard v2.
- **Brak tools dla agenta (readPlan, postPrComment jako tool)** — agent w MVP jest "scorerem" (structured output), nie "aktorem" (tool-calling); v2.

## Product Framing

- product_type: web-app (bez zmian — f-06 to infrastruktura CI)
- target_scale.users: small (bez zmian — pipeline jest narzędziem dev, nie produktem dla użytkowników gry)
- timeline_budget.delivery_weeks: 1
- timeline_budget.hard_deadline: 2026-07-05 (deadline certyfikacji 10xChampion)
- timeline_budget.after_hours_only: true

## Quality cross-check

All 7 brownfield elements present. Status: accepted.
