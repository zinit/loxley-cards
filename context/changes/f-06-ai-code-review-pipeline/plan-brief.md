# AI Code Review Pipeline — Krótki plan

> Pełny plan: `context/changes/f-06-ai-code-review-pipeline/plan.md`
> Shape notes: `context/foundation/shape-notes.md`

## Co i dlaczego

Każdy PR w loxley-cards jest implementowany i review'owany przez tę samą osobę w tej samej sesji — brak separacji ról (confirmation bias) i brak widocznego artefaktu na PR. f-06 dodaje composable agenta AI (Vercel AI SDK 6 + Claude Sonnet 4.6) jako niezależny, automatyczny reviewer: komentarz z rubryką 1-10 i werdyktem na każdym PR. Driver podwójny: realna jakość + certyfikacja 10xChampion (deadline 2026-07-05).

## Punkt wyjścia

Istniejący CI (`.github/workflows/ci.yml`) to dwa niezależne joby: backend `mvnw verify` + frontend `npm build`. Zero AI w pipeline. MVP funkcjonalnie kompletny (auth + kampania + partia vs bot), deployed na loxley.cards.

## Pożądany stan końcowy

Otwarcie PR na `main` automatycznie generuje komentarz od bota z 5-wymiarową rubryką (poprawność, idiomatyczność, złożoność, testy, bezpieczeństwo — każde 1-10), summary i werdykt (APPROVED / NEEDS_ATTENTION / REJECTED). Komentarz jest update'owany przy re-pushach (nie spam), compact dla APPROVED, expanded dla problemów. Istniejący CI i runtime bez zmian.

## Kluczowe podjęte decyzje

| Decyzja | Wybór | Dlaczego (1 zdanie) | Źródło |
|---------|-------|---------------------|--------|
| SDK | Vercel AI SDK 6 (composable) | Certyfikacja M5L2 wymaga composable agent; structured output via Zod | Shape |
| Model | Claude Sonnet 4.6 | Balans jakość/koszt; 200k context window dla diffów | Shape |
| Lokalizacja skryptu | `.github/scripts/` | Konwencja GH Actions, izolacja od kodu projektu | Plan |
| Posting komentarza | octokit (GitHub REST API) | Pełna kontrola formatu, jeden punkt integracji w skrypcie | Plan |
| Duże diffy | Skip review + info komentarz | Zero kosztów API na outlierach; graceful failure | Plan |
| Format komentarza | Tabela + conditional details + marker | Update-existing-comment via marker; compact APPROVED, expanded problems | Plan |
| Gate | Informacyjny (komentarz) | Nie blokuje merge — eliminuje false-positive risk w MVP | Shape |

## Zakres

**W zakresie:**
- `.github/workflows/ai-review.yml` — nowy workflow triggerowany na `pull_request` do `main`
- `.github/scripts/review.js` + `package.json` — composable agent z Vercel AI SDK 6 + Zod + octokit
- `ANTHROPIC_API_KEY` w GitHub Secrets
- Trial PR jako weryfikacja end-to-end

**Poza zakresem:**
- Status check blokujący merge, labels, promptfoo, multi-model, cost dashboard, tools dla agenta
- Zmiany w `ci.yml`, backend, frontend
- Chunking/splitting dużych diffów

## Architektura / Podejście

```
PR opened/updated → ai-review.yml triggered
  → checkout (fetch-depth: 0)
  → git diff origin/main...HEAD > /tmp/pr_diff.txt (temp file, not env var — 48KB GITHUB_ENV limit)
  → setup-node@v5 (Node 22) → npm ci in .github/scripts/
  → node review.js
    → diff size check (>120k → skip + info comment)
    → generateObject(anthropic/sonnet-4.6, zod schema, system prompt + diff)
    → structured JSON → markdown formatter
    → octokit: find existing comment (marker) → update or create
  → continue-on-error: true (graceful failure)
```

## Fazy w skrócie

| Faza | Co dostarcza | Kluczowe ryzyko |
|------|-------------|-----------------|
| 1. Scaffold skryptu | review.js + package.json z pełną logiką (agent + formatter + poster) | Vercel AI SDK 6 API surface może się różnić od docs |
| 2. Workflow | ai-review.yml triggerujący skrypt na PR | Multi-line env var (PR_DIFF) w GitHub Actions |
| 3. Weryfikacja E2E | Trial PR z komentarzem od bota | ANTHROPIC_API_KEY must be set in GitHub Secrets |

**Wymagania wstępne:** ANTHROPIC_API_KEY (klucz API Anthropic), repo publiczne na GitHubie z Actions enabled
**Szacowany nakład pracy:** ~2-3 wieczory w 3 fazach

## Otwarte ryzyka i założenia

- Vercel AI SDK 6 `generateObject` z `@ai-sdk/anthropic` provider — zakładamy stabilne API; w razie problemów fallback na `@anthropic-ai/sdk` z ręcznym Zod parse
- Diff delivery via temp file (nie env var) — omija limit 48KB GITHUB_ENV; diff size guard 120K w skrypcie jako dodatkowa ochrona
- Koszt API per review: ~$0.02-0.10 (Sonnet 4.6, mały-średni diff) — akceptowalny przy niskim wolumenie PR solo dev

## Kryteria sukcesu (podsumowanie)

- Trial PR triggeruje ai-review job, komentarz pojawia się na PR z rubryką + werdyktem
- Re-push update'uje istniejący komentarz (nie spam)
- Istniejący ci.yml i runtime aplikacji bez regresji
